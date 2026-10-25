# SilentFit User Guide

## Before you start (Android)

1. **Your watch must already be paired** in Android *Settings › Connected devices*. It
   appears as something like `NF 2_A1B2`.
2. **Close NoiseFit.** Only one app can hold the watch's data channel. Either uninstall
   NoiseFit or force-stop it (*Settings › Apps › NoiseFit › Force stop*) and disable its
   background activity.
3. Install SilentFit and open it.

## First launch

1. Read the welcome screen and tap **Continue**.
2. Allow **Nearby devices** (Bluetooth), **Notifications**, and optionally **Phone** and
   **SMS** (for answering calls and quick replies from the watch).
3. Tap **Get Started**. Android opens *Notification access* — turn on **SilentFit**. This
   powers notification forwarding, caller info and music control.
4. In the **Watch** tab, tap **Pair Watch** and choose your watch.

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
3. In SilentFit open **Settings › Advanced › Watch Account ID**, paste the ID, then go to
   **Watch** and tap **Connect**.

**Option B — re-pair with SilentFit:** tap **Pair With SilentFit** on the Watch tab and
confirm on the watch. If the watch refuses, reset it from its own settings menu and pair
again.

## Everyday use

| Tab | What you'll find |
|-----|------------------|
| **Today** | Connection and battery, activity rings (steps, calories, active minutes), heart rate, sleep, SpO₂, stress, distance and stand hours. Pull down to sync. |
| **Trends** | Week/month averages with charts. Tap a metric for day-by-day detail, sleep stages and statistics. |
| **Watch** | Find My Watch, sync, watch faces, quick replies, alarms, heart-rate/SpO₂/stress monitoring, raise to wake, Do Not Disturb, move and hydration reminders, device details. |
| **Settings** | Profile and goals, notification forwarding, sync options, account ID, demo watch, connection log. |

* **Find My Phone:** start it from the watch; SilentFit rings and vibrates the phone until
  you tap **I Found It** (or stop it on the watch).
* **Calls:** the watch rings over its own Bluetooth headset link. Answer or decline on the
  watch; SilentFit relays the action to the phone.
* **Music:** play/pause/skip and volume on the watch control whichever app is playing.
* SilentFit keeps a small notification while connected so Android doesn't stop it in the
  background.

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
| Data looks stale | Pull to refresh on Today. The **Connection Log** (Settings › Advanced) shows what the watch reported. |
