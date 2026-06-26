# RuOS — Android Studio Project

Open **this folder** (`studio/`) in Android Studio — not the repo root.

## Opening

1. Clone the repo
2. In Android Studio → **File → Open…** → select the `studio/` folder
3. Wait for Gradle sync (downloads dependencies, ~2 min first time)
4. Done — three modules appear in the sidebar

## Modules

| Module | What it is | Run on device? |
|---|---|---|
| `:launcher` | RuOS Home — iOS-style launcher | ✅ Yes (sideload, set as default home) |
| `:settings-app` | RuOS Settings — iOS Settings clone | ✅ Yes |
| `:demo` | Interactive preview of all system UI components | ✅ Yes — **start here** |

## Recommended first run

Select **`:demo`** in the run configuration dropdown (top toolbar) → Run.

The demo app launches on your device/emulator with a menu of every component:
- **Dynamic Island** — auto-cycles through pill → music → call → timer states
- **Control Centre** — frosted glass, all toggles interactive
- **Notification Centre** — fake grouped notifications, swipe left to dismiss
- **Lock Screen** — Golos Thin 80sp clock, parallax, swipe up to unlock
- **Gesture Navigation** — drag from bottom/edges, feel the spring physics
- **Spring Physics** — drag a ball, adjust stiffness/damping sliders live

## Source locations

The `:launcher` and `:settings-app` modules point at the AOSP source tree via `build.gradle.kts` `sourceSets` — no duplication:

```
launcher source → ../packages/apps/RuOSLauncher/src
settings source → ../packages/apps/RuOSSettings/src
```

Changes made in Android Studio edit the AOSP source directly.
The `:demo` module has its own self-contained source in `demo/src/`.

## Requirements

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK 34 installed (SDK Manager)
- Emulator: Pixel 8 API 34 recommended (has the island cutout shape)
