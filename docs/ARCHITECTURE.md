# Architecture

OpenComputersAndroid mirrors the upstream OpenComputers architecture, adapted
to a Kotlin/JVM core that runs equally on the JVM and Android/ART.

```
app/ (Android)                    emulator-core/ (pure Kotlin/JVM)
┌───────────────────────┐         ┌───────────────────────────────────────┐
│ MainActivity          │         │ Machine ─ owns signal queue, VM       │
│  ├─ ScreenView        │ renders │   thread, ~20 Hz component ticker     │
│  └─ MachineController │ ──────► │ ComponentRegistry ─ component bus:    │
│        │ key events   │ injects │   @Callback reflection dispatch       │
│        └──────────────┼────────►│ LuaArchitecture ─ LuaJ VM, sandboxed  │
│                       │         │   globals, `component`/`computer` API │
│                       │         │ Components: screen/gpu/keyboard/fs/   │
│                       │         │ eeprom                                │
└───────────────────────┘         └───────────────────────────────────────┘
```

## Machine lifecycle

`Machine.start()` spawns a daemon thread that:

1. Constructs the `Architecture` (`LuaArchitecture`) via a factory — the VM
   needs the machine's component registry.
2. `initialize()` — LuaJ `standardGlobals()` are created, stripped of
   host-access libraries (`io`, `package`, `luajava`, `dofile`, `require`,
   `loadstring`, `loadfile`), and the `component` + `computer` libraries are
   installed. The first `eeprom` component supplies the boot code.
3. `run()` — executes the EEPROM chunk. Machine code lives until
   `computer.shutdown()` (a `MachineShutdownException` unwinding the Lua
   stack), a Lua error (→ `Machine.onCrashed`), or `stop()`.

Separately, a `ScheduledExecutorService` ticks `Component.update()` every
50 ms while the machine runs.

## Component bus

`Component` implementations expose Lua-callable methods with `@Callback`
(name/direct/limit — same annotation shape as `li.cil.oc.api.machine.Callback`).
`ComponentRegistry.invoke` performs argument coercion (Lua number →
Int/Double/Long, table → List/Map, string ↔ ByteArray) and flattening of
`Array` return values into multiple Lua results. A trailing
`vararg rest: Any?` parameter collects optional arguments.

`component.proxy(addr)` builds a Lua table of bound methods — the idiom all
OC software uses (`component.gpu.set(...)`) — so proxies work exactly like
upstream.

## Signals

Asynchronous events (keyboard input, `component_added`/`removed`, host
notifications) go through a bounded `ArrayBlockingQueue<Signal>` (256 entries,
drops when full — same behavior as OC). `computer.pullSignal([timeout])`
blocks the machine thread on that queue, matching OC's semantics where the
signal queue is the only source of async notification.

## Rendering

`ScreenComponent` owns a `TextBuffer` (glyph + packed fg/bg + palette flags per
cell — palette cells resolve at read time, so `setPaletteColor` recolors
existing text like the real mod). `GpuComponent` holds current fg/bg state and
writes through the bound screen's buffer. The app layer registers
`TextBuffer.addChangeListener` → `postInvalidate` and `ScreenView` draws runs
of same-colored cells with a monospace `Paint`.

## Why LuaJ

Upstream OC ships native Lua plus a LuaJ fallback for exotic platforms. LuaJ
is pure Java, so it runs unmodified on Android ART — no NDK, no native libs,
no ABI splits — at the cost of speed (fine for a component-layer emulator).

## Deliberate divergences from upstream

- **Threads:** upstream runs the VM on a worker and component calls hop onto
  the server thread with call budgets; here every `@Callback` runs on the
  machine thread. Components must not block long in `update()`/calls.
- **Filesystems** are host-directory-backed and sandboxed by normalized-path
  prefix checks rather than block-size emulation.
- **Energy** is stubbed unlimited; components have no tier/power cost.
- **Keyboard `code`** is 0 — the host passes Unicode code points only.
