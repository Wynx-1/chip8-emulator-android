# CHIP-8 Emulator for Android

A fully featured CHIP-8 / SCHIP / XO-CHIP emulator for Android,
built in Kotlin. One of the most feature-rich CHIP-8 emulators
available on Android.

## Features

### Emulation
- Full CHIP-8 instruction set (all 35 opcodes)
- SCHIP (Super CHIP-8) support with 128x64 hi-res mode
- XO-CHIP support with 4-color display
- Configurable CPU quirks per ROM
- Adjustable emulation speed (1x to 40x)

### Display
- 6 color themes: Classic Green, Amber, Cyan Blue, White, Red, Matrix
- Pixel glow effect
- CRT scanline overlay
- Ghost frame flicker reduction (adjustable 0-5 frames)
- Fullscreen immersive mode
- Portrait and landscape support

### ROM Management
- Built-in ROM library with persistent storage
- Per-ROM settings saved automatically
- Supports .ch8, .CH8, .c8 files

### Developer Tools
- Live debug overlay (PC, registers, stack, timers)
- Step mode — advance one opcode at a time
- Built-in benchmark tool with device rating
- CPU quirks configurator per ROM

## How to Install

1. Download the latest APK from [Releases](../../releases)
2. Enable "Install from unknown sources" on your Android device
3. Install the APK
4. Launch and tap LOAD to add your first ROM

## How to Use

| Action | How |
|--------|-----|
| Load ROM | Tap LOAD button |
| Start game | Tap START |
| Pause/Resume | Tap PAUSE |
| Step one opcode | Pause then tap STEP |
| Debug overlay | Long-press STEP |
| Settings | Long-press RESET |
| ROM Quirks | Long-press START |
| Benchmark | Tap BENCH |

## Where to Get ROMs

Free public domain CHIP-8 ROMs:
- https://github.com/kripod/chip8-roms
- https://www.zophar.net/chip8.html

## Compatibility

- Android 7.0 (API 24) and above
- Tested on Nothing Phone 1
- All standard CHIP-8, SCHIP, and XO-CHIP ROMs

## Built With

- Kotlin
- Android Canvas (custom View rendering)
- HandlerThread for emulation loop
- SharedPreferences for ROM library and settings

## License

MIT License — free to use, modify, and distribute.
