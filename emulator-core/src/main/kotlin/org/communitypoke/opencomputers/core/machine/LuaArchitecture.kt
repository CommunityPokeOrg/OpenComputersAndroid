package org.communitypoke.opencomputers.core.machine

import org.communitypoke.opencomputers.core.component.EepromComponent
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.VarArgFunction
import org.luaj.vm2.lib.jse.JsePlatform
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Lua 5.3-ish architecture backed by LuaJ (the same pure-Java Lua runtime the
 * upstream OpenComputers mod uses on platforms without native Lua — which is
 * what makes it a good fit for Android/ART).
 *
 * Boot flow mirrors OC: the machine looks for an `eeprom` component, loads its
 * stored code, and runs it as the BIOS. The globals are sandboxed to the OC
 * surface — no `io`, `package`, `luajava`, `dofile`, `require` — plus the
 * `component` and `computer` libraries.
 */
class LuaArchitecture(private val machine: Machine) : Architecture {

    private var globals: Globals? = null
    private var bootCode: String? = null
    private val users = LinkedHashSet<String>()

    override fun initialize(): Boolean {
        val eeprom = machine.components.byType("eeprom")
            .filterIsInstance<EepromComponent>()
            .firstOrNull() ?: return false
        val code = eeprom.codeString
        if (code.isBlank()) return false

        val g = JsePlatform.standardGlobals()
        sandbox(g)
        g.set("component", componentLib())
        g.set("computer", computerLib())
        globals = g
        bootCode = code
        return true
    }

    override fun run() {
        val g = globals ?: error("architecture not initialized")
        val chunk = g.load(bootCode ?: "", "=bios")
        chunk.call()
    }

    override fun close() {
        globals = null
        bootCode = null
        users.clear()
    }

    /** Remove host-VM escape hatches; replace `os` with the in-machine subset. */
    private fun sandbox(g: Globals) {
        for (name in arrayOf("io", "package", "luajava", "dofile", "loadfile", "require", "loadstring", "collectgarbage")) {
            g.set(name, LuaValue.NIL)
        }
        val os = LuaTable()
        os.set("time", fn { LuaValue.valueOf(System.currentTimeMillis() / 1000.0) })
        os.set("clock", fn { LuaValue.valueOf(machine.uptimeSeconds) })
        os.set("date", fn { args ->
            val epochSeconds = args.arg(2).optdouble(System.currentTimeMillis() / 1000.0)
            LuaValue.valueOf(
                DateTimeFormatter.ISO_LOCAL_DATE_TIME
                    .withZone(ZoneOffset.UTC)
                    .format(Instant.ofEpochMilli((epochSeconds * 1000).toLong()))
            )
        })
        os.set("difftime", fn { args -> LuaValue.valueOf(args.checkdouble(1) - args.checkdouble(2)) })
        g.set("os", os)
    }

    private fun componentLib(): LuaTable {
        val t = LuaTable()
        t.set("list", fn { args ->
            val filter = args.arg(1).takeUnless { it.isnil() }?.tojstring()
            val exact = args.arg(2).optboolean(false)
            // OC semantics: component.list returns an iterator yielding
            // (address, type) pairs, so `for a in component.list("gpu")` works.
            val entries = machine.components.list(filter, exact).entries.toList()
            var index = 0
            fn {
                if (index >= entries.size) LuaValue.NIL
                else {
                    val e = entries[index++]
                    LuaValue.varargsOf(arrayOf(LuaValue.valueOf(e.key), LuaValue.valueOf(e.value)))
                }
            }
        })
        t.set("type", fn { args ->
            val address = args.checkjstring(1)
            LuaInterop.toLua(machine.components.typeOf(address))
        })
        t.set("methods", fn { args ->
            val address = args.checkjstring(1)
            LuaInterop.toLua(machine.components.methodsOf(address))
        })
        t.set("invoke", fn { args ->
            invokeFromLua(args)
        })
        t.set("proxy", fn { args ->
            val address = args.checkjstring(1)
            val component = machine.components.get(address)
                ?: throw ComponentException("no component with address '$address'")
            val proxy = LuaTable()
            proxy.set("address", address)
            proxy.set("type", component.type)
            val methods = machine.components.methodsOf(address).orEmpty()
            for (name in methods.keys) {
                proxy.set(name, fn { callArgs ->
                    val forwarded = ArrayList<LuaValue>(callArgs.narg() + 2)
                    forwarded.add(LuaValue.valueOf(address))
                    forwarded.add(LuaValue.valueOf(name))
                    for (i in 1..callArgs.narg()) forwarded.add(callArgs.arg(i))
                    invokeFromLua(LuaValue.varargsOf(forwarded.toTypedArray()))
                })
            }
            proxy
        })
        t.set("doc", fn { LuaValue.NIL })
        t.set("fields", fn { LuaTable() })
        t.set("slot", fn { LuaValue.valueOf(-1) })
        return t
    }

    private fun invokeFromLua(args: Varargs): Varargs {
        val address = args.checkjstring(1)
        val method = args.checkjstring(2)
        val forwarded = LuaInterop.argsFromLua(args, 3)
        return try {
            LuaInterop.toVarargs(machine.invokeComponent(address, method, forwarded))
        } catch (e: ComponentException) {
            throw org.luaj.vm2.LuaError("component '$method': ${e.message}")
        }
    }

    private fun computerLib(): LuaTable {
        val t = LuaTable()
        t.set("pullSignal", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val timeoutMillis = if (args.narg() > 0 && !args.arg(1).isnil()) {
                    (args.arg(1).todouble() * 1000).toLong().coerceAtLeast(0)
                } else {
                    Long.MAX_VALUE
                }
                val signal = machine.pullSignal(timeoutMillis) ?: return LuaValue.NIL
                val out = ArrayList<LuaValue>(signal.args.size + 1)
                out.add(LuaValue.valueOf(signal.name))
                signal.args.forEach { out.add(LuaInterop.toLua(it)) }
                return LuaValue.varargsOf(out.toTypedArray())
            }
        })
        t.set("pushSignal", fn { args ->
            val name = args.checkjstring(1)
            machine.pushSignal(name, *LuaInterop.argsFromLua(args, 2))
            LuaValue.TRUE
        })
        t.set("uptime", fn { LuaValue.valueOf(machine.uptimeSeconds) })
        t.set("address", fn { LuaValue.valueOf(machine.id) })
        t.set("tmpAddress", fn {
            val tmp = machine.components.byType("filesystem")
                .filterIsInstance<org.communitypoke.opencomputers.core.component.FileSystemComponent>()
                .firstOrNull { it.isTemporary }
            LuaInterop.toLua(tmp?.address)
        })
        t.set("freeMemory", fn { LuaValue.valueOf(Runtime.getRuntime().freeMemory().toDouble()) })
        t.set("totalMemory", fn { LuaValue.valueOf(Runtime.getRuntime().totalMemory().toDouble()) })
        t.set("energy", fn { LuaValue.valueOf(1_000_000.0) })
        t.set("maxEnergy", fn { LuaValue.valueOf(1_000_000.0) })
        t.set("getArchitecture", fn { LuaValue.valueOf("Lua 5.3") })
        t.set("isRobot", fn { LuaValue.FALSE })
        t.set("beep", fn { args ->
            val freq = args.arg(1).optdouble(440.0)
            val dur = args.arg(2).optdouble(0.1)
            System.err.printf("computer.beep(%.0f Hz, %.2f s)%n", freq, dur)
            LuaValue.NIL
        })
        t.set("shutdown", fn { args ->
            val reboot = args.arg(1).optboolean(false)
            throw MachineShutdownException(reboot)
        })
        t.set("users", fn { LuaInterop.toLua(users.toList()) })
        t.set("addUser", fn { args ->
            val name = args.checkjstring(1)
            if (users.size >= 32) throw org.luaj.vm2.LuaError("too many users")
            users.add(name)
            LuaValue.TRUE
        })
        t.set("removeUser", fn { args ->
            LuaValue.valueOf(users.remove(args.checkjstring(1)))
        })
        return t
    }

    private fun fn(block: (Varargs) -> Varargs): VarArgFunction =
        object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs = block(args)
        }
}
