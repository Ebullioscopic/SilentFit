# SilentFit

A private, account-free companion app for **Noise smartwatches**, built from one
Swift/SwiftUI codebase for **Android, iOS, iPadOS and macOS** with [Skip](https://skip.dev).

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
| **Watch** | Find My Watch, Find My Phone, watch faces, alarms, quick replies, heart-rate/SpO₂/stress monitoring, raise to wake, Do Not Disturb, move & hydration reminders, time sync, profile & goals |
| **Reliability** | Auto-reconnect, background foreground-service on Android, connection log |

Tested against a Noise **NF 2** (ZH Berry firmware 1.0.39). Other Berry-protocol Noise
watches should work; see [docs/REVERSE_ENGINEERING.md](docs/REVERSE_ENGINEERING.md#8-adding-another-noise-model).

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
| [Platform Map](docs/PLATFORM_MAP.md) | Skip architecture, module layout, per-platform capabilities, build commands |
| [Berry Protocol](docs/PROTOCOL.md) | Transport, framing, handshake, every command, health-sync flow and record layouts |
| [NoiseFit Analysis](docs/NOISEFIT_ANALYSIS.md) | What's inside the NoiseFit APK and how it is structured |
| [Reverse Engineering](docs/REVERSE_ENGINEERING.md) | Reproducible methodology and tooling |

## Building

Requirements: macOS with Xcode, Android Studio (SDK), Homebrew, and `brew install skip gradle`.

```bash
swift test                                   # protocol & decoder tests
cd Android && gradle assembleDebug           # Android APK
adb install -r app/build/outputs/apk/debug/app-debug.apk
open Project.xcworkspace                     # iOS / iPadOS / macOS (scheme "SilentFit App")
```

## Project layout

```
Sources/SilentFitKit/      Shared model & protocol (transpiled to Kotlin)
  Protocol/                Framing, protobuf codec, commands, responses, fitness decoder
  Transport/               SPP (Android), RFCOMM (macOS), BLE (iOS), demo watch
  Phone/                   Phone-side integrations (notifications, calls, media, find phone)
  Model/                   Health history & preferences
  Skip/*.kt                Android-only Kotlin: SPP socket, NotificationListener, services
  WatchSession.swift       Connection state machine and feature orchestration
Sources/SilentFit/         SwiftUI app (Today, Trends, Watch, Settings)
Tests/                     Unit tests incl. byte-exact captured frames
Tools/                     Icon generation and reverse-engineering scripts
docs/                      Documentation
```

## Disclaimer

SilentFit is an independent personal project for interoperability with hardware the
author owns. It is not affiliated with or endorsed by Noise. No vendor code is included.
Health readings are for general wellness, not medical use.
