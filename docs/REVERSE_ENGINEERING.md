# Reverse-Engineering Methodology

How the Berry protocol was recovered, so the work can be reproduced or extended (for
example, to support another Noise model). Everything here was done for interoperability
with a watch the author owns.

> **Keep vendor material out of the repository.** Decompiled sources, APKs, recovered
> `.proto` files and NoiseFit logs (which contain personal health data, notification
> contents and account identifiers) live in a sibling folder, `../SilentFit-re/`, that is
> never committed.

## 1. Tooling

```bash
brew install jadx apktool
python3 -m venv .venv && .venv/bin/pip install protobuf
```

## 2. Pull the app and its logs

```bash
mkdir -p ../SilentFit-re/apk ../SilentFit-re/device
for p in $(adb shell pm path com.noisefit | sed 's/package://'); do adb pull "$p" ../SilentFit-re/apk/; done
adb pull /sdcard/Android/data/com.noisefit/cache ../SilentFit-re/device/
```

NoiseFit's logs (`cache/logs/logs.txt`, `cache/appLogs/logs.txt`) record each Bluetooth
frame (`SPP write/received`), the SDK's decoded view of each protobuf, and the native
fitness parser's JSON output. They are the single most valuable artifact.

## 3. Identify the watch's SDK

```bash
adb shell dumpsys bluetooth_manager | sed -n '/Bonded devices:/,/^$/p'
adb shell dumpsys companiondevice | grep -i noisefit
grep -m3 'deviceProtocol' ../SilentFit-re/device/cache/logs/logs.txt
```

The NF 2 logs `ControlBleTools … deviceProtocol:Berry` and `BerryBluetoothService …
connectSpp`, identifying the ZH SDK's Berry stack over SPP.

## 4. Decompile

```bash
jadx -j 8 --deobf -d ../SilentFit-re/jadx-out ../SilentFit-re/apk/base.apk
```

Key classes (ZH SDK):

| Class | Role |
|-------|------|
| `com.zhapp.ble.BerryBluetoothService` | SPP socket, framing, ACKs, large-file reassembly |
| `com.zhapp.ble.BleCommonAttributes` | Frame heads, protocol constants |
| `com.zhapp.ble.bean.berry.CmdDataBean`, `CmdAskBean`, `UserIdBean`, `ProtocolBean`, `BerryDeviceInfoBean` | Frame and private-command layouts |
| `com.zhapp.ble.AbstractC32818a$a` (obfuscated) | Command builders (`type`, `id`, protobuf body) |
| `com.zhapp.ble.parsing.BerryParsing` | Response dispatch |
| `com.zhapp.ble.parsing.BerryFitnessParsing` | JNI bridge to `libberryParsing.so` |
| `com.zhapp.ble.utils.UuidUtils` | SPP and GATT UUIDs |

## 5. Recover the protobuf schemas

Generated protobuf-java classes embed their serialized `FileDescriptorProto` as
ISO-8859-1 string literals passed to `internalBuildGeneratedFileFrom`. Extract and render
them back to `.proto`:

```bash
.venv/bin/python Tools/re/extract_protos.py ../SilentFit-re/jadx-out/sources/com/p310zh/ble ../SilentFit-re/protos
```

Two families exist: `wear` (legacy BLE) and `sa_wear` (Berry). Berry uses `sa_wear`.

## 6. Recover the fitness record layouts

`libberryParsing.so` turns binary records into JSON. Rather than disassembling it, the
layouts were recovered by **known-plaintext comparison**: the logs contain both the raw
transfer and the parser's JSON for the same records. A small solver searched each record
for the byte encodings (u8/u16/u32 LE) of every JSON value and array, which yielded field
offsets directly. Ambiguous fields (sleep stage codes, percent widths) were resolved
across several nights with different values.

The resulting decoder is regression-tested against 13 real sync sessions:

```bash
.venv/bin/python Tools/re/export_captures.py ../SilentFit-re/device/cache/logs/logs.txt ../SilentFit-re/analysis/captures.json
SILENTFIT_CAPTURES=../SilentFit-re/analysis/captures.json swift test --filter CapturedSyncTests
```

## 7. Probe the radio

A short CoreBluetooth script scanned from macOS while the phone's Bluetooth was turned
off (`adb shell cmd bluetooth_manager disable`, re-enabled afterwards). The NF 2 never
advertised over BLE, confirming it is a Classic-only device — the reason iOS cannot
connect to it (see [PLATFORM_MAP.md](PLATFORM_MAP.md)).

## 8. Adding another Noise model

1. Find its protocol in NoiseFit's logs (`deviceProtocol:` or the SDK tag).
2. If it is **Berry** or **Berry-Ring**, SilentFit should work as-is (Berry-Ring via
   `BleTransport`).
3. Otherwise, identify the adapter package in [NOISEFIT_ANALYSIS.md](NOISEFIT_ANALYSIS.md),
   repeat steps 4–6 for that SDK, and add a new protocol implementation behind
   `WatchSession`.
