# SilentFit

A private, account-free companion app for **Noise smartwatches**. The Android app is
native **Kotlin + Jetpack Compose** in the Nothing design language; the Apple apps
(iOS, iPadOS, macOS) are **SwiftUI** from the shared Swift package.

SilentFit speaks the watch's native **Berry** protocol directly — no NoiseFit, no cloud,
no sign-in, and no network permission. Health data stays on your device.

## Features

| | |
|---|---|
| **Health** | Steps, distance, calories, active minutes, stand hours, continuous heart rate, sleep stages & score, SpO₂, stress — synced from the watch and kept as local history |
| **Dashboard** | Activity rings, metric tiles, hourly charts, weekly/monthly trends, per-day detail |
| **Notifications** | App notifications, incoming/missed calls and SMS forwarded to the watch (Android) |
| **Calls** | Answer, decline or mute from the watch; quick replies by SMS (Android) |
| **Music** | Now-playing on the watch; play/pause/skip/volume control any player (Android) |
| **Watch** | Find My Watch, Find My Phone, time sync, live steps/heart rate, watch settings (read; changing them is opt-in, see below) |
| **Face studio** | Drag-and-drop editor, JSON code files, `.sfface` import/export, Nothing-style presets (6 analogue, 5 digital, all tuned for legibility on the LCD), safe install with automatic rollback (Android) |
| **Widgets** | Today (dot-matrix steps, goal segments, heart rate, sleep, kcal) and Watch (battery, connection) home-screen widgets (Android) |
| **Reliability** | Auto-reconnect with backoff, low-priority foreground service, connection log |
| **Safety** | Only commands observed from the official app are sent by default; faces are checked against the official layout before upload |

Tested against a Noise **NF 2** (ZH Berry firmware 1.0.39). Other Berry-protocol Noise
watches may work for sync and notifications; see [docs/REVERSE_ENGINEERING.md](docs/REVERSE_ENGINEERING.md#8-adding-another-noise-model).
The command allowlist and face rules were derived on the NF 2 only.

## Platform support

| | Android | macOS | iPhone / iPad |
|---|:---:|:---:|:---:|
| NF 2 (Bluetooth Classic) | ✅ | ✅ | ❌ (iOS limitation) |
| Berry BLE devices | — | — | ✅ experimental |
| Notifications, calls, music | ✅ | — | — |

Details and the reasoning are in [docs/PLATFORM_MAP.md](docs/PLATFORM_MAP.md).

## Documentation

| Document | Contents |
|----------|----------|
| [User Guide](docs/USER_GUIDE.md) | Setup, switching from NoiseFit, everyday use, troubleshooting |
| [Designing Watch Faces](docs/WATCHFACE_DESIGN.md) | Face studio, presets, the `.sfface.json` code format |
| [Watch-Face Format](docs/WATCHFACE_FORMAT.md) | WF container, PAR image codec, data ids, install flow, safety rules |
| [Platform Map](docs/PLATFORM_MAP.md) | Architecture, per-platform capabilities, battery strategy, build commands |
| [Berry Protocol](docs/PROTOCOL.md) | Transport, framing, handshake, every command, command safety policy, health-sync flow |
| [NoiseFit Analysis](docs/NOISEFIT_ANALYSIS.md) | What's inside the NoiseFit APK and how it is structured |
| [Reverse Engineering](docs/REVERSE_ENGINEERING.md) | Reproducible methodology and tooling |

## Watch safety

The NF 2 firmware does not validate what it receives; a malformed face once left a test
watch boot-looping until a factory reset. SilentFit therefore:

* sends only commands captured from NoiseFit on this watch (or read-only requests proven
  safe) — changing watch settings is behind **Settings › Experimental watch controls**
* accepts only faces whose every record matches the official face layout
* asks before installing, and rolls a new face back automatically if the watch
  disconnects within 3 minutes

## Building

Requirements: JDK 17+, Android SDK, Gradle 9 (`brew install gradle`); Xcode for Apple.

```bash
cd Android
gradle :core:test :app:assembleDebug           # protocol, face codec & safety tests + APK
adb install -r app/build/outputs/apk/debug/app-debug.apk

swift test                                       # Swift package tests (Apple)
open Project.xcworkspace                         # iOS / iPadOS / macOS (scheme "SilentFit App")
```

Set `SILENTFIT_FACE_SAMPLE=/path/to/official-face.bin` to also run the byte-exact codec
regression against an official face (not included in the repository).

## Project layout

```
Android/core/              Pure Kotlin/JVM library, unit-tested
  protocol/                Framing, protobuf codec, commands, command policy, responses
  fitness/                 Health record decoder
  watchface/               PAR codec, WF container, FaceSpec, compiler, FaceSafety
Android/app/               Jetpack Compose app
  watch/                   SPP link, WatchSession (handshake, sync, uploads, face trial), service
  facestudio/              Canvas rasterizer, presets
  ui/                      Today, Trends, Faces (studio + code), Watch, Settings
  widget/                  Glance widgets
Sources/                   Swift package and SwiftUI app (Apple platforms)
Tools/                     Icon generation and helper scripts
docs/                      Documentation
```

## Disclaimer

SilentFit is an independent personal project for interoperability with hardware the
author owns. It is not affiliated with or endorsed by Noise. No vendor code is included.
Health readings are for general wellness, not medical use.
