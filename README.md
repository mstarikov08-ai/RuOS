# RuOS

**Russian-first mobile OS based on AOSP android-14.0.0_r50**

RuOS delivers iOS 18–level gesture physics, spring animations, and design language with the Russian digital ecosystem at its core.

## Project Structure

```
RuOS/
├── device/ruos/          — Device targets and board configs
├── vendor/ruos/          — Vendor overlays, prebuilt APKs, fonts
├── packages/apps/
│   ├── RuOSLauncher/     — iOS-style launcher (no app drawer)
│   ├── RuOSSettings/     — iOS Settings clone
│   └── RuOSBootAnimation/
├── frameworks/base/packages/SystemUI/ruos-src/
│   └── com/android/systemui/ruos/
│       ├── DynamicIsland.kt
│       ├── ControlCenter.kt
│       ├── NotificationCenter.kt
│       ├── LockScreen.kt
│       ├── AppSwitcher.kt
│       └── GestureNavigation.kt
├── overlay/ruos/         — Resource overlays (fonts, colours, dims)
└── bootanimation/        — 3-second branded boot animation
```

## Quick Start (inside AOSP tree)

```bash
# From AOSP root
ln -s /path/to/RuOS/device/ruos device/ruos
ln -s /path/to/RuOS/vendor/ruos vendor/ruos
ln -s /path/to/RuOS/packages/apps/RuOSLauncher packages/apps/RuOSLauncher
ln -s /path/to/RuOS/packages/apps/RuOSSettings packages/apps/RuOSSettings
ln -s /path/to/RuOS/frameworks/base/packages/SystemUI/ruos-src \
      frameworks/base/packages/SystemUI/src/com/android/systemui/ruos

source build/envsetup.sh
lunch ruos_pixel9-userdebug
m -j$(nproc)
```

## Key Design Principles

- **Spring physics everywhere** — SpringAnimation + VelocityTracker on every gesture
- **SurfaceControl transactions** — all animations on render thread, never UI thread
- **Velocity matching** — gestures follow finger 1:1 until release
- **Haptic feedback** — every meaningful interaction
- **Golos Text** — Russian-native variable font throughout

## Accent Colour

`#D94F3D` — Russian red, not aggressive

## Version

- System name: RuOS
- Version: 1.0
- Build: RuOS-1.0-DEV
- Base: android-14.0.0_r50
