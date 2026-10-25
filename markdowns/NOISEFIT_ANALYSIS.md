# NoiseFit App Analysis

Findings from reverse engineering NoiseFit for Android, used to design SilentFit.

| | |
|---|---|
| Package | `com.noisefit` |
| Version | 5.4.7 (build 879), minSdk 26, targetSdk 36 |
| Delivery | App bundle: `base.apk` (183 MB, 19 dex files) + splits `arm64_v8a` (130 MB native code), `xxhdpi`, `en`, `hi`, `ta` |
| Stack | Kotlin + Android Views/Data Binding, Hilt DI, Coroutines, Apollo GraphQL, Retrofit/OkHttp, Room/DataStore, an embedded Flutter module, Sentry, MoEngage, PostHog, Firebase |

## 1. Architecture

NoiseFit is a single "super-app" that supports roughly 120 Noise wearables built on
**about a dozen different vendor platforms**. Each platform has its own SDK and a thin
NoiseFit adapter module:

| Adapter package | Vendor SDK | Typical transport |
|-----------------|-----------|-------------------|
| `com.noisefit_zhsdk` | ZH / Zhapp (`com.zhapp.ble`) — **Berry** and legacy protocols | SPP (Berry), BLE (legacy, Berry-Ring) |
| `com.noisefit_topstep` | Topstep FitCloud (`com.topstep.*`) | BLE |
| `com.noisefit_sdk_dfuture` | DFuture | BLE |
| `com.noisefit_ryeex_sdk` | Ryeex | BLE |
| `com.noisefit.noisefit_creek` | Creek | BLE |
| `com.noisefit_cf2`, `_evolve2`, `_nav_plus`, `_hybrid`, `_colorfit_pro` | Legacy per-model stacks | BLE |
| — | IDO / VeryFit (`com.ido`, `com.veryfit`), JieLi (`com.jieli`), Realtek (`com.realsil`, `com.realtek`), SiFli (`com.sifli`), CRP (`com.crrepa`), Bluetrum, Appscomm | BLE (+ vendor OTA) |

`com.noisefit_commans` holds shared models (`DeviceType`, `ColorFitDevice`,
`ColorFitNetworkDevice`) and handler interfaces that every adapter implements.

### Device → SDK routing

There is **no local table** mapping a watch to its SDK. At pairing time the app fetches a
device catalogue from Noise's backend (`ColorFitNetworkDevice`: `namePattern`,
`matchingType`, `sdkType`, `deviceType`, `watchSubsType`…) and matches the Bluetooth
name against `namePattern`. The chosen `sdkType` and protocol (e.g. `Berry`) are then
persisted with the paired device. SilentFit avoids this dependency by targeting the Berry
protocol directly.

### The NF 2

The connected watch identifies as **`NF 2_A1B2`**, ZH SDK, protocol **Berry**
(`BPA1.240915.001`), model `59004`, firmware `1.0.39`, platform type 7. It is a
dual-mode device: an **HFP headset** for Bluetooth calling plus an **SPP data channel**.
It does not advertise over BLE.

## 2. Native code (`split_config.arm64_v8a.apk`)

| Library | Purpose |
|---------|---------|
| `libberryParsing.so` | ZH Berry fitness record parser (binary → JSON). Re-implemented in Swift by SilentFit. |
| `liblzo.so`, `libminilzo.so`, `libXClz4.so`, `libezip*.so` | Compression for watch-face and OTA uploads |
| `libjl_*`, `libRtk*`, `libsbc`, `libopus*`, `libst_*` | Vendor audio/codec/OTA support (JieLi, Realtek) |
| `libsleep_stage_algorithm.so`, `libcreek_gnss_algorithm.so` | Vendor algorithms |
| `libwhisper*.so`, `libggml*.so`, `libMicrosoft.CognitiveServices.Speech.*` | On-device / cloud voice assistant |
| `libflutter.so`, `libapp.so`, `libdartjni.so` | Embedded Flutter feature module |
| `libpython3.11.so` | Embedded Python runtime (ML features) |
| `libavcodec`…`libswscale`, `libffmpegkit*` | FFmpeg (video watch faces) |
| `libmapbox-*` | Maps for workouts |
| `libpairipcore.so` | Google Play integrity / anti-tamper |
| `libsentry*.so` | Crash reporting |

## 3. Network & privacy observations

NoiseFit requires an account and syncs health data to Noise's backend (GraphQL). It also
embeds several analytics/marketing SDKs (MoEngage, PostHog, Firebase, Sentry). The app
writes **very verbose plaintext logs** — including every Bluetooth frame, decoded health
records and notification contents — to `Android/data/com.noisefit/cache/logs/logs.txt`,
which is readable over USB debugging. These logs were the primary ground truth for this
project.

SilentFit needs **no network permission at all**.

## 4. Feature inventory (NF 2 relevant)

Observed over the captured log window (Oct 2026):

| Feature | Protocol traffic | SilentFit |
|---------|-----------------|-----------|
| Now-playing sync | `MUSIC/SYNC_MUSIC_INFO` ×1094 | ✅ |
| App notifications | `NOTIFICATION/SEND_APP_NOTIFICATION` ×372 | ✅ |
| Battery & device info | `DEVICE/*` ×151 | ✅ |
| Daily fitness sync | `SETTING_MENU/138,136,139` ×56 | ✅ |
| Quick replies | `NOTIFICATION/GET_QUICK_REPLY_LIST` | ✅ |
| Time sync | `SYSTEM_TIME/SET_SYSTEM_TIME` | ✅ |
| Calls (system notification + call state) | `NOTIFICATION/2,4` | ✅ |
| Find watch / find phone | `MICRO_FUNCTION/0,1` | ✅ |
| Watch-face change events | `NEW_WATCH_FACE/13` | ✅ list/select/remove |
| Watch-face upload, OTA, weather, GPS/AGPS, AI voice, e-SIM, offline maps | Not observed / out of scope | Roadmap |
