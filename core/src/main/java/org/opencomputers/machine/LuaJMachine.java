package org.opencomputers.machine;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaThread;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.DebugLib;
import org.luaj.vm2.lib.jse.JsePlatform;
import org.opencomputers.api.ComponentException;
import org.opencomputers.api.Signal;

import java.util.List;
import java.util.Map;

/**
 * Lua 5.2 machine architecture on the LuaJ VM.
 *
 * Execution model: the EEPROM boot code runs inside a {@link LuaThread}
 * coroutine. {@code computer.pullSignal([timeout])} yields the coroutine with a
 * marker; {@link #tick()} resumes it once a signal is queued or the deadline
 * passes. Component calls are synchronous Java functions.
 *
 * Safety: a stripped global set (no io, luajava, dofile, require; os limited to
 * pure functions; os.sleep reimplemented over pullSignal), and a per-tick
 * instruction budget enforced with an interpreter debug hook — a script that
 * exceeds the budget crashes the machine instead of hanging the host.
 */
public class LuaJMachine implements Machine {
    /** Max VM instructions per tick before the machine crashes. */
    public static final long INSTRUCTION_BUDGET_PER_TICK = 1_000_000;
    /** Hook granularity: budget checked every this-many instructions. */
    private static final int HOOK_COUNT = 4096;

    private static final LuaString YIELD_PULL_SIGNAL = LuaString.valueOf("__oc_pull_signal");

    private final MachineHost host;
    private Globals globals;
    private LuaThread thread;
    private volatile boolean running;
    private volatile String lastError;

    private boolean waitingForSignal;
    private double pullDeadline = Double.POSITIVE_INFINITY;
    private boolean resumeUnconditionally;
    private long instructionsThisTick;
    private boolean done;
    private boolean doneOk;
    private String doneError;

    public LuaJMachine(MachineHost host) {
        this.host = host;
    }

    @Override
    public void start() {
        globals = JsePlatform.standardGlobals();
        globals.load(new DebugLib()); // needed for the instruction-budget hook
        stripGlobals(globals);
        installApis(globals);

        String bootCode = bootCode();
        if (bootCode == null || bootCode.trim().isEmpty()) {
            lastError = "no bootable medium found (eeprom is empty)";
            return;
        }
        LuaValue chunk;
        try {
            chunk = globals.load(bootCode, "=bios");
        } catch (LuaError e) {
            lastError = "bios load error: " + e.getMessage();
            return;
        }
        // Trampoline: pcall the BIOS inside the thread so completion and
        // errors are reported through __oc_done instead of VM internals.
        globals.set("__oc_done", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                done = true;
                doneOk = args.checkboolean(1);
                doneError = args.arg(2).optjstring(null);
                return NONE();
            }
        });
        LuaValue main;
        try {
            main = globals.load(
                    "local f = ...\nlocal ok, err = pcall(f)\n__oc_done(ok, err)",
                    "=main");
        } catch (LuaError e) {
            lastError = "vm init error: " + e.getMessage();
            return;
        }
        thread = new LuaThread(globals, main);
        installBudgetHook();
        // The debug library is only needed to install the hook; it must not be
        // visible to sandboxed Lua code (it would be an escape hatch).
        globals.set("debug", LuaValue.NIL);
        running = true;
        resume(LuaValue.varargsOf(new LuaValue[]{chunk}));
    }

    private String bootCode() {
        for (Map.Entry<String, String> e : host.componentEntries()) {
            if (!e.getValue().equals("eeprom")) {
                continue;
            }
            Object[] code = host.invokeComponent(e.getKey(), "get", new Object[0]);
            if (code.length > 0 && code[0] != null) {
                return code[0].toString();
            }
        }
        return null;
    }

    private static void stripGlobals(Globals g) {
        for (String name : new String[]{
                "io", "luajava", "dofile", "loadfile", "require", "package",
                "collectgarbage"}) {
            g.set(name, LuaValue.NIL);
        }
        LuaValue os = g.get("os");
        if (os.istable()) {
            for (String name : new String[]{
                    "execute", "exit", "remove", "rename", "getenv",
                    "setlocale", "tmpname", "setenv"}) {
                os.set(name, LuaValue.NIL);
            }
        }
        // load(): allow source chunks only — reject precompiled/binary input.
        final LuaValue rawLoad = g.get("load");
        g.set("load", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                LuaValue src = args.arg1();
                if (src.isstring()) {
                    String s = src.tojstring();
                    if (!s.isEmpty() && s.charAt(0) == 0x1B) {
                        throw new LuaError("loading bytecode is not supported");
                    }
                }
                return rawLoad.invoke(args);
            }
        });
    }

    private void installApis(Globals g) {
        g.set("component", componentLib());
        g.set("computer", computerLib());
        // OC userspace helpers, implemented over the machine primitives.
        g.load("""
                function os.sleep(seconds)
                  local deadline = computer.uptime() + (seconds or 0)
                  while computer.uptime() < deadline do
                    computer.pullSignal(deadline - computer.uptime())
                  end
                end
                """, "=prelude").call();
    }

    private LuaTable componentLib() {
        LuaTable t = new LuaTable();
        t.set("list", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                String filter = args.isnoneornil(1) ? null : args.checkjstring(1);
                boolean exact = args.optboolean(2, false);
                LuaTable out = new LuaTable();
                for (Map.Entry<String, String> e : host.componentEntries()) {
                    if (filter == null || (exact ? e.getValue().equals(filter)
                            : e.getValue().contains(filter))) {
                        out.set(e.getKey(), e.getValue());
                    }
                }
                // OC parity: component.list returns an iterator table —
                // `for addr, ctype in component.list("gpu") do` calls the
                // table itself via __call to get the next (addr, type) pair.
                LuaTable meta = new LuaTable();
                meta.set("__call", new VarArgFunction() {
                    @Override
                    public Varargs invoke(Varargs a) {
                        LuaValue key = a.arg(3);
                        Varargs next = out.next(key.isnil() ? LuaValue.NIL : key);
                        if (next.arg1().isnil()) {
                            return LuaValue.NIL;
                        }
                        return next;
                    }
                });
                out.setmetatable(meta);
                return out;
            }
        });
        t.set("invoke", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                String address = args.checkjstring(1);
                String method = args.checkjstring(2);
                Object[] callArgs = Converter.toObjects(args.subargs(3));
                try {
                    return Converter.toVarargs(host.invokeComponent(address, method, callArgs));
                } catch (ComponentException e) {
                    throw new LuaError(e.getMessage());
                }
            }
        });
        t.set("type", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                String type = host.componentType(arg.checkjstring());
                return type == null ? NIL : valueOf(type);
            }
        });
        t.set("methods", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                Map<String, String> methods = host.componentMethods(arg.checkjstring());
                LuaTable out = new LuaTable();
                for (String name : methods.keySet()) {
                    out.set(name, LuaValue.TRUE);
                }
                return out;
            }
        });
        t.set("doc", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                Map<String, String> methods = host.componentMethods(args.checkjstring(1));
                String doc = methods.get(args.checkjstring(2));
                return doc == null ? NIL : valueOf(doc);
            }
        });
        t.set("slot", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                return valueOf("-1");
            }
        });
        t.set("fields", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                return new LuaTable();
            }
        });
        t.set("proxy", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                String address = arg.checkjstring();
                if (host.componentType(address) == null) {
                    throw new LuaError("no such component");
                }
                LuaTable proxy = new LuaTable();
                proxy.set("address", address);
                proxy.set("type", host.componentType(address));
                proxy.set("slot", "-1");
                for (String methodName : host.componentMethods(address).keySet()) {
                    // NB: named deliberately — LibFunction has a `name` field
                    // that would shadow a captured `name` local here.
                    proxy.set(methodName, new VarArgFunction() {
                        @Override
                        public Varargs invoke(Varargs args) {
                            try {
                                return Converter.toVarargs(host.invokeComponent(
                                        address, methodName, Converter.toObjects(args)));
                            } catch (ComponentException e) {
                                throw new LuaError(e.getMessage());
                            }
                        }
                    });
                }
                return proxy;
            }
        });
        return t;
    }

    private LuaTable computerLib() {
        LuaTable t = new LuaTable();
        t.set("pullSignal", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                double timeout = args.isnoneornil(1) ? -1 : args.checkdouble(1);
                return globals.yield(LuaValue.varargsOf(YIELD_PULL_SIGNAL, valueOf(timeout)));
            }
        });
        t.set("pushSignal", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                String name = args.checkjstring(1);
                return valueOf(host.pushSignal(name, Converter.toObjects(args.subargs(2))));
            }
        });
        t.set("uptime", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                return valueOf(host.uptime());
            }
        });
        t.set("address", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                return valueOf(host.address());
            }
        });
        t.set("energy", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                return valueOf(host.energy());
            }
        });
        t.set("maxEnergy", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                return valueOf(host.maxEnergy());
            }
        });
        t.set("freeMemory", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                return valueOf(host.freeMemory());
            }
        });
        t.set("totalMemory", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                return valueOf(host.totalMemory());
            }
        });
        t.set("beep", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                double freq = args.optdouble(1, 440);
                double dur = args.optdouble(2, 0.1);
                // Route through the speaker component if one is attached.
                for (Map.Entry<String, String> e : host.componentEntries()) {
                    if (e.getValue().equals("speaker")) {
                        try {
                            host.invokeComponent(e.getKey(), "beep",
                                    new Object[]{freq, dur * 20});
                        } catch (ComponentException ignored) {
                        }
                        return NONE();
                    }
                }
                return NONE();
            }
        });
        t.set("shutdown", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                host.requestShutdown(args.optboolean(1, false));
                return NONE();
            }
        });
        t.set("isRobot", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                return FALSE;
            }
        });
        t.set("getArchitecture", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                return valueOf("Lua 5.2");
            }
        });
        t.set("setArchitecture", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                throw new LuaError("only the Lua 5.2 architecture is available");
            }
        });
        return t;
    }

    private void installBudgetHook() {
        LuaValue debug = globals.get("debug");
        if (!debug.istable()) {
            return; // hooks unavailable; run without a budget guard
        }
        LuaValue hook = new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                instructionsThisTick += HOOK_COUNT;
                if (instructionsThisTick > INSTRUCTION_BUDGET_PER_TICK) {
                    throw new LuaError("instruction budget exhausted");
                }
                return LuaValue.NIL;
            }
        };
        debug.get("sethook").invoke(LuaValue.varargsOf(new LuaValue[]{
                thread, hook, LuaString.valueOf(""), LuaValue.valueOf(HOOK_COUNT)}));
    }

    @Override
    public void tick() {
        if (!running || thread == null) {
            return;
        }
        instructionsThisTick = 0;
        if (waitingForSignal) {
            Signal s = host.pollSignal();
            if (s != null) {
                LuaValue[] vals = new LuaValue[s.args().length + 1];
                vals[0] = LuaValue.valueOf(s.name());
                for (int i = 0; i < s.args().length; i++) {
                    vals[i + 1] = Converter.toLua(s.args()[i]);
                }
                waitingForSignal = false;
                resume(LuaValue.varargsOf(vals));
            } else if (host.uptime() >= pullDeadline) {
                waitingForSignal = false;
                resume(NONE());
            }
        } else if (resumeUnconditionally) {
            resume(NONE());
        }
    }

    private void resume(Varargs args) {
        Varargs result;
        try {
            result = thread.resume(args);
        } catch (LuaError e) {
            crash(e.getMessage());
            return;
        } catch (Throwable t) {
            crash("vm fault: " + t);
            return;
        }
        // LuaJ's resume() prepends a status flag like Lua's coroutine.resume:
        // (true, yielded-values...) on suspend, (false, error) on fault.
        if (result != null && result.arg1().isboolean() && !result.arg1().toboolean()) {
            crash(result.arg(2).optjstring("unknown vm error"));
            return;
        }
        if (done) {
            running = false;
            if (!doneOk) {
                lastError = doneError == null ? "unknown error" : doneError;
            }
            return;
        }
        waitingForSignal = result != null
                && result.narg() >= 2
                && result.arg(2).raweq(YIELD_PULL_SIGNAL);
        if (waitingForSignal) {
            double timeout = result.arg(3).optdouble(-1);
            pullDeadline = timeout < 0
                    ? Double.POSITIVE_INFINITY
                    : host.uptime() + timeout;
        }
        resumeUnconditionally = !waitingForSignal;
    }

    private void crash(String message) {
        lastError = message == null ? "unknown vm error" : message;
        running = false;
        waitingForSignal = false;
    }

    @Override
    public void stop() {
        running = false;
        thread = null;
        globals = null;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public String lastError() {
        return lastError;
    }

    private static Varargs NONE() {
        return LuaValue.NONE;
    }
}
