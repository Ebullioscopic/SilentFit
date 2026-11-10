# Google Play submission

Everything needed to publish SilentFit on Google Play, with the answers to give in Play
Console. Assets are in this folder.

## Build

```bash
cd Android
gradle :app:bundlePlayRelease
# → app/build/outputs/bundle/playRelease/app-play-release.aab
```

The `play` flavour is the same app as the GitHub APK without the `SEND_SMS` permission,
which Google Play restricts. In that build SMS quick replies are disabled and the app says
so on the Quick replies screen. Everything else is identical.

Checked for this build:

- target SDK 36 (Play requires 35 or later)
- 64-bit native libraries are 16 KB page aligned (CameraX and Compose libraries)
- no `QUERY_ALL_PACKAGES`, no accessibility service, no location, no `READ_SMS` /
  `READ_CALL_LOG`, no `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
- the app has no `INTERNET` permission

### Signing

Play App Signing is mandatory. When creating the app, choose **Use my own key** and upload
the existing release key (`Android/app/keystore.jks`, alias `silentfit`) with Play's PEPK
tool. Then the Play build and the GitHub APK have the same signature, and users can move
between them without uninstalling. If you let Google generate a new key instead, the two
builds cannot update each other.

Use the same keystore as the upload key, or create a separate upload key and register it
in Play Console.

### New personal developer accounts

Personal accounts created after November 2023 must run a closed test with at least
12 testers for 14 continuous days before applying for production access.

## Store listing

| Field | Value |
|---|---|
| App name | SilentFit |
| Short description (80) | Private, offline companion for your smartwatch. No account, no cloud. |
| Category | Health & Fitness |
| Tags | Fitness tracking, Smartwatch, Health |
| Email | hrhn.mudaliar251@gmail.com |
| Website | https://ebullioscopic.github.io/SilentFit/ |
| Privacy policy | https://ebullioscopic.github.io/SilentFit/privacy.html |
| App icon | `icon-512.png` (512 × 512) |
| Feature graphic | `feature-graphic.png` (1024 × 500) |
| Phone screenshots | `screenshots/1-welcome.png` … `7-pair.png` (1080 × 1920) |

Do not put "Noise" or "NoiseFit" in the app name, icon, short description or screenshots
captions. Naming the supported watch in the full description is allowed when it only
describes compatibility, as below.

### Full description

```
SilentFit is a companion app for your smartwatch that keeps everything on your phone. There is no account to create, nothing is uploaded, and the app does not even have permission to use the internet.

PAIR IN SECONDS
Open the QR code screen on your watch and scan it. SilentFit pairs over Bluetooth and walks you through the confirmation on the watch.

YOUR HEALTH, KEPT LOCAL
Steps, distance, calories, active minutes, standing hours, heart rate through the day, SpO₂, stress, sleep stages and workouts are synced from the watch in the background and kept as daily history on your phone. Weekly and monthly trends show how you're doing over time.

NOTIFICATIONS AND CALLS
App notifications appear on the watch with the app's own icon. Answer or decline calls from your wrist.

MUSIC
See what's playing and control any music app from the watch.

WATCH FACES
Design your own faces in the built-in editor, start from more than 30 presets, or import faces made in the free browser Studio. Every face is checked before it is installed.

WATCH SETTINGS
Change heart-rate, SpO₂ and stress monitoring, reminders, alarms, do-not-disturb and raise to wake. Each change is read back from the watch to confirm it.

WIDGETS
Home-screen widgets for today's activity and the watch's battery.

PRIVATE BY DESIGN
• No account, no ads, no analytics
• No internet permission
• Data stays in the app's private storage and can be deleted at any time
• Open source under the GNU AGPL v3

COMPATIBILITY
Tested with the Noise NF 2 (Bluetooth Classic). Other watches that use the same protocol may work. Try the built-in demo to explore the app without a watch.

SilentFit is an independent project. It is not affiliated with or endorsed by Noise. Health readings are estimates for general wellness and are not medical advice.
```

## App content (Policy › App content)

### Privacy policy
`https://ebullioscopic.github.io/SilentFit/privacy.html`

### Ads
No, the app does not contain ads.

### App access
All functionality is available without special access. Note for reviewers:

```
SilentFit needs a compatible smartwatch for live data. To review without one, tap "Try demo" on the first screen. Demo mode simulates a connected watch with sample data, and every screen can be opened.
```

### Content rating (IARC questionnaire)
Category: **Utility, Productivity, Communication, or Other**. Answer **No** to violence, sexuality, language,
controlled substances, gambling, user interaction / user-generated content shared with
others, sharing location, and digital purchases. Expected rating: Everyone / PEGI 3.

### Target audience
18 and over. This keeps the app outside the Families policy; the app is not designed
for children.

### Data safety

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **No** |
| Is all of the user data collected by your app encrypted in transit? | Not applicable (no data collected) |
| Do you provide a way for users to request that their data is deleted? | Not applicable; data never leaves the device. In-app: Settings › Data › Delete all data |

Why "No": Google's definition of collection is data transmitted off the device to the
developer or a third party. SilentFit has no internet permission and processes
everything on the phone. Data sent to the user's own watch over Bluetooth is transfer to
the user's own accessory, not to the developer or a third party. If a reviewer disagrees,
the alternative is to declare *Health and fitness* and *App activity › Other* as
collected, **not shared**, **processed ephemerally: no**, **required**, purpose *App
functionality*.

### Health apps declaration
Select: **Activity and fitness**, **Sleep management**, **Health and fitness tracking
(non-medical)**. The app is not a medical device, does not use Health Connect, and shows
a wellness disclaimer on the first screen, in About and in the terms of use.

### Foreground service
Declare **Connected device** (`FOREGROUND_SERVICE_CONNECTED_DEVICE`).

```
SilentFit keeps a Bluetooth connection to the user's paired smartwatch so that phone notifications, incoming calls and music controls reach the watch in real time, and health data syncs in the background. The service runs only while a watch is paired and the user has "Stay connected in background" on; it shows an ongoing notification with a Disconnect action.
```

Play asks for a short video. Record: open the app with the watch connected, lock the
phone, send a test notification and show it arriving on the watch, then show the
ongoing notification and its Disconnect action.

### Sensitive permissions in the Play build

| Permission | Shown to user | Purpose |
|---|---|---|
| Nearby devices | Setup › Permissions | Pair and stay connected to the watch (`neverForLocation`) |
| Notifications | Setup › Permissions | Connection status, find-my-phone alert |
| Phone (answer calls, phone state) | Setup › Permissions | Answer or decline calls from the watch |
| Camera | When scanning | Read the watch's pairing QR code; frames are not stored |
| Notification access | Prominent disclosure card, then system screen | Show notifications and music on the watch |

The notification-access disclosure explains what is read and where it goes before the
user is taken to Android's settings, as required by the User Data policy.

### Government apps, financial features, news, COVID-19
Not applicable / No.

## Release checklist

1. `gradle :app:bundlePlayRelease`, upload the `.aab` to **Internal testing** first.
2. Install from the internal testing link on a phone with the watch; check pairing,
   sync, notifications and calls.
3. Fill in every App content section above.
4. Run the closed test (12 testers, 14 days) if the account requires it.
5. Promote to production.
