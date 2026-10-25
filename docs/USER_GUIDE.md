# SilentFit User Guide

## Before you start (Android)

1. **Your watch must already be paired** in Android *Settings › Connected devices*. It
   appears as something like `NF 2_A1B2`.
2. **Close NoiseFit.** Only one app can hold the watch's data channel. Either uninstall
   NoiseFit or force-stop it (*Settings › Apps › NoiseFit › Force stop*) and disable its
   background activity.
3. Install SilentFit and open it.

## First launch

1. On the welcome screen tap **Continue** (or **Try demo** to explore with a simulated
   watch).
2. Under **Choose your watch**, tap **Allow** for Nearby devices (Bluetooth) and
   notifications, then pick your watch from *Paired devices*. Optionally enter your
   NoiseFit account ID (see below) to keep the existing pairing.
3. Turn on SilentFit under Android's *Notification access* (Settings › Permissions in the
   app links there). This powers notification forwarding, caller info and music control.
   Grant **Phone** and **SMS** if you want to answer calls and send quick replies.

## Switching from NoiseFit without resetting the watch

Noise watches remember which account they are bound to. If SilentFit shows *"This watch
is paired to another account"*, you have two options:

**Option A — reuse your NoiseFit account ID (keeps watch settings and data):**

1. Connect your phone to a computer with USB debugging enabled.
2. Run:
   ```bash
   adb shell grep -m1 'setUserId()' /sdcard/Android/data/com.noisefit/cache/logs/logs.txt
   ```
   The line ends with `setUserId() <ID>,<phone model>,<Android version>`.
3. In SilentFit open **Settings › About & licences › Account ID**, paste the ID and
   save, then go to **Watch** and tap **Connect**. (You can also enter it while choosing
   the watch during setup.)

**Option B — re-pair with SilentFit:** tap **Pair again** on the Watch tab and
confirm on the watch. If the watch refuses, reset it from its own settings menu and pair
again.

## Everyday use

| Tab | What you'll find |
|-----|------------------|
| **Today** | Connection and battery, activity rings (steps, calories, active minutes), heart rate, sleep, SpO₂, stress, distance and stand hours. Pull down to sync. |
| **Trends** | Week/month averages with charts. Tap a metric for day-by-day detail, sleep stages and statistics. |
| **Faces** | The face studio: presets, your designs, imported files and the faces on your watch. See [Designing Watch Faces](WATCHFACE_DESIGN.md). |
| **Watch** | Find My Watch, sync, battery, the watch's settings (monitoring, reminders, alarms, display, apps), quick replies, notifications, device details. |
| **Settings** | Profile and goals, connection and background sync, permissions, data, watch safety, connection log, account ID. |

* **Find My Phone:** start it from the watch; SilentFit rings and vibrates the phone until
  you tap **I Found It** (or stop it on the watch).
* **Calls:** the watch rings over its own Bluetooth headset link. Answer or decline on the
  watch; SilentFit relays the action to the phone.
* **Music:** play/pause/skip and volume on the watch control whichever app is playing.
* SilentFit keeps a small notification while connected so Android doesn't stop it in the
  background.
* **Widgets:** long-press the home screen › Widgets › SilentFit for *Today* (steps,
  goal, heart rate, sleep, kcal) and *Watch* (battery and connection).

## Watch faces

Open **Faces**, start from a preset or tap **+**, then drag elements on the canvas.
When the safety check below the canvas passes, tap **Install on watch** and choose:

* **Install & wear** — the face becomes current as a 3-minute trial. Keep the watch near
  the phone. If the watch restarts during the trial, SilentFit switches back to a
  built-in face and removes the new one. Tap **It works — keep it** to finish early.
* **Install only** — pick it later on the watch.

The NF 2 stores one downloaded face; SilentFit asks before replacing it.

## Watch safety

The NF 2 does not check what it receives, and a bad command can force a factory reset.
By default SilentFit only sends commands that NoiseFit itself sends to this watch:
sync, time, battery, notifications, calls, music, find watch, live data, reading
settings and verified faces.

Changing watch settings (monitoring, reminders, alarms, display, apps menu, profile,
units, quick replies) from SilentFit is **experimental**. Those screens are view-only
until you turn on **Settings › Watch safety › Experimental watch controls** and confirm.
Until then, change them in NoiseFit or on the watch.

## Mac

Pair the watch with the Mac in *System Settings › Bluetooth* (unpair it from the phone or
turn the phone's Bluetooth off first), then choose it in **Watch › Pair Watch**. Health
sync, settings, faces and Find My Watch work; notification forwarding is not available on
macOS.

## iPhone and iPad

iOS cannot connect to Classic-Bluetooth accessories like the NF 2. SilentFit for iPhone
works with Noise devices that support Bluetooth LE (Berry-Ring family) and includes a demo
watch (**Settings › Advanced › Demo Watch**) so you can explore the app.

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| Stuck on *Connecting…* | Make sure NoiseFit is closed, Bluetooth is on and the watch is nearby. Toggle the watch's Bluetooth or restart it. |
| *Paired to another account* | See "Switching from NoiseFit" above. |
| No notifications on the watch | Check **Settings › Notifications › Notification Access** shows *Allowed*, and the watch's own notification setting is on. |
| Calls can't be answered from the watch | Grant the **Phone** permission (Settings › Notifications › Bluetooth, Calls & SMS Permissions). |
| Data looks stale | Pull to refresh on Today. The **Connection log** (Settings › Support) shows what the watch reported. |
| A setting snaps back | Watch-setting changes are off by default — see *Watch safety*. |
| *Blocked for safety* when installing | The face uses something the watch has not been proven to accept. Remove the element named in the message. |
| New face disappeared | The watch disconnected during the face trial, so SilentFit restored a built-in face. Try again with the watch close to the phone, or simplify the face. |
