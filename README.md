# OpenComputersAndroid

An [OpenComputers](https://github.com/MightyPirates/OpenComputers) machine
emulator for Android. It runs a real Lua VM (LuaJ — the same pure-Java Lua the
upstream mod falls back to) with the familiar `component`/`computer` API
surface, emulated GPU/screen/keyboard/filesystem/EEPROM components, and an
Android app that renders the screen and feeds touch/keyboard input into the
machine.

This is an original, MIT-licensed reimplementation of the OpenComputers
architecture — not a port of the mod's code — so any real OpenComputers Lua
software (OpenOS, custom EEPROMs) needs the documented API subset.

## Repository layout

```
app/            Android application (Kotlin, plain framework views, no androidx)
emulator-core/  Pure Kotlin/JVM emulator: machine, component bus, Lua arch,
                component implementations. Also usable standalone on desktop JVM.
docs/           ARCHITECTURE.md — design notes and OC-fidelity details
emulator-core/src/main/resources/bios/bios.lua  bundled demo BIOS flashed
                into every machine's EEPROM by Machines.demo()
```

## Requirements

- JDK 17
- Android SDK with `platforms;android-34` and `build-tools;34.0.0`
  (`ANDROID_HOME`/`ANDROID_SDK_ROOT` set, or a `local.properties` with `sdk.dir=...`)

## Build

```bash
./gradlew assembleDebug          # produces app/build/outputs/apk/debug/app-debug.apk
./gradlew :emulator-core:test    # JVM unit tests (no emulator needed)
```

## Run

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
# then launch "OC Emulator", press Power
```

The app boots the bundled BIOS: a banner screen listing components, echoing
typed keys. The EditText at the bottom forwards characters into the machine as
`key_down`/`key_up` signals; Enter is forwarded for hardware keyboards.

## Using the core standalone

```kotlin
val machine = Machines.demo(File("machine-fs"))
machine.onCrashed = { println("crashed: $it") }
machine.start()
// feed input:
(machine.components.byType("keyboard").first() as KeyboardComponent).injectText("ls")
```

## Implemented Lua API surface

- `component`: `list`, `type`, `invoke`, `methods`, `proxy`, `doc`, `fields`, `slot`
- `computer`: `pullSignal`, `pushSignal`, `uptime`, `address`, `tmpAddress`,
  `freeMemory`, `totalMemory`, `energy`, `maxEnergy`, `getArchitecture`,
  `isRobot`, `beep`, `shutdown`, `users`, `addUser`, `removeUser`
- `gpu`: `bind`, `getScreen`, `set`, `get`, `fill`, `copy`, `getResolution`,
  `setResolution`, `maxResolution`, `getViewport`, `setViewport`,
  `getBackground`, `setBackground`, `getForeground`, `setForeground`,
  `getDepth`, `setDepth`, `maxDepth`, `getPaletteColor`, `setPaletteColor`, `getSize`
- `screen`: `isOn`, `turnOn`, `turnOff`, `isPrecise`, `getPrecise`,
  `getAspectRatio`, `getKeyboards`
- `filesystem`: `isReadOnly`, `getLabel`, `setLabel`, `list`, `exists`,
  `isDirectory`, `size`, `lastModified`, `makeDirectory`, `remove`, `rename`,
  `spaceUsed`, `spaceTotal`, `open`, `read`, `write`, `seek`, `close`
- `eeprom`: `get`, `set`, `getData`, `setData`, `getSize`, `getDataSize`,
  `getChecksum`, `getLabel`, `setLabel`, `isReadonly`, `makeReadonly`

## Current limitations

- **Single-threaded call model.** All component calls execute on the machine
  thread. OC's direct-vs-synchronized call budget (`@Callback.direct`,
  `limit`) is parsed but not enforced — documented intent only.
- **No energy system.** `computer.energy`/`maxEnergy` return large constants;
  machines never run out of power.
- **Component set is small.** No network/modem, internet card, drives (only a
  single tmpfs-style filesystem component), drone/robot internals, or tier-1/2
  rendering tiers — the GPU is always tier-3 depth (8-bit palette).
- **Palette is approximate.** The default 240-color palette matches OC's
  layout (16 colors + grayscale ramp) but shades differ slightly.
- **Lua strings are byte strings.** LuaJ does not provide OC's `unicode`
  library semantics; non-ASCII glyphs may not render identically.
- **No OpenOS.** The bundled BIOS is a minimal demo; booting a real OpenOS
  image requires porting the init/eeprom → filesystem boot chain (the
  filesystem component is ready for it).
- **Sandboxing is best-effort.** `io`, `package`, `luajava`, `require`,
  `dofile`, `loadfile` are removed, but this has not been audited as a hard
  security boundary.
- **No sound.** `computer.beep` is a no-op (logs to stderr on host JVM).

## License

MIT — see [LICENSE](LICENSE).
