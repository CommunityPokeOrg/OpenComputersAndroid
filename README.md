# OpenComputersAndroid

An Android-native foundation for emulating [OpenComputers](https://github.com/MightyPirates/OpenComputers)
machines: a real computer that boots an EEPROM BIOS, runs Lua 5.2 on a tick
clock, and exposes OC-style components (`gpu`, `screen`, `filesystem`,
`keyboard`, `eeprom`, `redstone`, `speaker`, `data`) to Lua programs.

The project is written in Java (not Kotlin) and deliberately keeps dependencies
to a minimum so it builds in constrained environments:

- **`:core`** — pure JVM module, zero Android dependencies. The whole emulator
  lives here and can run on any JDK 17+ (desktop included).
- **`:app`** — thin Android shell (minSdk 26): a canvas screen renderer, key
  input, redstone toggles, power/reset controls, and a beep player.

## Architecture

```
app/                        Android application (activities, views, audio)
  EmulatorRuntime           assembles a Computer + components, runs the 20 Hz tick thread
  ScreenView                renders the bound TextBuffer to a Canvas
  BeepPlayer                AudioTrack-based sine/"square" beeps
core/                       pure-Java emulator core (no Android deps)
  org.opencomputers
    Computer                component bus host, 256-deep signal queue, energy/memory, state machine
    machine/
      Machine               start/tick/stop contract
      LuaJMachine           LuaJ-based Lua 5.2 architecture: sandboxed globals,
                            OC `component.*` / `computer.*` APIs, os.sleep,
                            per-tick instruction budget, signal-yield coroutine
      Converter             LuaValue <-> Java type conversion
    api/
      Component, Callback, ComponentBus, ComputerContext, Signal, ComponentException
    component/
      GpuComponent          OC `gpu` callbacks over a TextBuffer (fg/bg, palette, viewport)
      ScreenComponent       tiers (50x16 / 80x25 / 160x50), depth, precise mode, keyboards()
      KeyboardComponent     host API that pushes `key_down`/`key_up`/`clipboard` signals
      FilesystemComponent   OC `filesystem` callbacks over an in-memory FS (open/read/write/seek/…)
      EepromComponent       code+data flash, label, read-only flag, checksum
      RedstoneComponent     per-side input/output, `redstone_changed` signal, wireless flag
      SpeakerComponent      `beep(freq, ticks)` queue drained by the host
      DataComponent         crc32/md5/sha256/base64/(de)flate, tier-gated
      TextBuffer            packed-color cell buffer with OC palette semantics
      fs/MemFileSystem      in-memory tree FS with OC-style handles
    bios/
      BootResources         EEPROM BIOS (scans filesystems for /init.lua) and the
                            stock init.lua demo (banner, key echo, S = shutdown)
```

Boot flow mirrors real OC: the machine starts, executes the EEPROM code which
iterates `component.list("filesystem")`, loads the first `/init.lua` it finds,
and calls it. Lua code yields through `computer.pullSignal`; the host resumes
the coroutine when a signal arrives or the timeout expires.

## Building

Requirements: JDK 17, Android SDK 34 (platform + build-tools), Gradle via the
checked-in wrapper.

```bash
./gradlew :core:coreTests        # run the emulator unit/integration suite
./gradlew :app:assembleDebug     # build app/build/outputs/apk/debug/app-debug.apk
```

The core suite is a zero-dependency reflective runner
(`core/src/test/java/org/opencomputers/test/Harness.java`), so it needs no
JUnit/TestNG download. If Gradle cannot reach Maven at all, the same suite runs
with plain javac:

```bash
bash scripts/test-core.sh
```

LuaJ is vendored at `core/libs/luaj-jse-3.0.1.jar` so the build works without
Maven Central access; to use the published artifact instead, switch the
dependency in `core/build.gradle` back to `implementation
'org.luaj:luaj-jse:3.0.1'`.

### Offline / restricted networks

AGP normally resolves `com.android.tools.build:aapt2` from Maven. If that fails,
point AGP at the aapt2 binary bundled with the SDK:

```bash
./gradlew :app:assembleDebug \
    -Pandroid.aapt2FromMavenOverride="$ANDROID_HOME/build-tools/34.0.0/aapt2"
```

## Running on a device / emulator

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or create an AVD and use Android Studio's run button. In the app:

- **POWER** boots the machine; the screen shows the OC-style boot banner.
- Typing echoes characters into the buffer; **S** shuts down.
- **KBD** toggles the soft keyboard; hardware keyboards work too.
- **R0–R5** toggles inject redstone input on OC sides 0–5 and fire
  `redstone_changed` signals.
- **RESET** reboots (fresh filesystem — see limitations).

## Writing programs

Anything you write via `fs` callbacks (or bake into
`core/src/main/java/org/opencomputers/bios/BootResources.java` as `INIT_LUA`)
runs inside the sandbox. Example component call inside the VM:

```lua
local gpu = component.proxy(component.list("gpu")())
gpu.set(1, 1, "hello from lua")
```

## Known limitations

- **Java, not Kotlin.** The build environment had no Kotlin Gradle plugin
  cached, so the codebase is plain Java. A Kotlin migration would be cosmetic.
- **In-memory filesystem only** — nothing persists across reboots; `reset`
  wipes the world.
- **No network card / internet component**, no `unicode` library, no
  robots/inventory/tables.
- **Energy is simplified** — the host tops the buffer up every tick
  (creative-mode power); `computer.energy()` is a stub value.
- **Instruction budget is approximated** — a Lua hook fires every 4096
  bytecode steps and crashes the machine past ~1M steps per tick rather than
  reproducing OC's exact accounting.
- **Component coverage is partial**: only the callbacks listed per component
  above are implemented; uncommon calls raise `no such method`.
- **Screen rendering** is monospace-cell only (no precise pixel mode visuals
  yet), and font metrics are approximate.
- **Single architecture**: Lua 5.2 via LuaJ 3.0.1; no architecture switching.
- The vendored LuaJ jar ships under its own license (see
  https://github.com/luaj/luaj).
