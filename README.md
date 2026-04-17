# BabyApp Smart Audio Recorder

An Android app that runs continuously, listens to ambient sound through the
microphone, and uploads short recordings whenever the peak amplitude crosses
a configurable threshold. The receiving end is a small PHP script that drops
the files into a directory.

It was originally built in 2016 for sleep / thermoregulation research. This
revision modernizes the Android side to target SDK 35 (Android 15) while
keeping the always-running design.

## Download

- **Latest release** (always points to the newest version):
  [`app-debug.apk`](https://github.com/robinvanemden/BabyApp/releases/latest/download/app-debug.apk)
- **All releases:**
  [github.com/robinvanemden/BabyApp/releases](https://github.com/robinvanemden/BabyApp/releases)
- **Pinned to v2.1.0:**
  [`app-debug.apk`](https://github.com/robinvanemden/BabyApp/releases/download/v2.1.0/app-debug.apk)

This is a debug-signed APK suitable for sideloading. Enable
*Settings → Apps → Special access → Install unknown apps* for your
browser or file manager, then open the downloaded `.apk` to install.

## How "always running" works

Modern Android does not allow truly unkillable background execution. The
OS reserves the right to terminate any process. What this app does is
combine every documented mechanism the platform offers, so the service
behaves as "always running" in every realistic scenario except a deliberate
user action to stop it.

### The seven mechanisms, in detail

**1. Microphone-typed foreground service**

`BabyService` declares `android:foregroundServiceType="microphone"` in
`AndroidManifest.xml` and calls `startForeground(id, notif, FOREGROUND_SERVICE_TYPE_MICROPHONE)`
on entry. Microphone FGS is treated by Android as user-essential. Unlike
`dataSync` (capped at 6 h/day on Android 15) or `shortService` (3 min hard
limit), `microphone` has **no platform-imposed time limit** and is the very
last category the OS will reclaim under memory pressure. See
`BabyService.kt` (`startForegroundCompat`).

**2. `START_STICKY` restart contract**

`onStartCommand` returns `Service.START_STICKY`. If the OS ever does kill
the process — for memory pressure, OEM cleanup, etc. — Android schedules
the service for restart as soon as resources free up. The service will be
re-created with a `null` intent and pick up where it left off. Note this
only fires for **OS-initiated** kills, not for a user "Force stop", which
intentionally suspends `START_STICKY` until the user next interacts with
the app — by Android design.

**3. Persistent low-importance notification**

Every FGS is required to show a user-visible notification. We post one to
the `baby_app_listening` channel at `IMPORTANCE_LOW` (silent, no sound).
The notification's `ContentIntent` re-opens `MainActivity`, so the user
always has a path back into the app.

**4. Microphone capture keeps the CPU awake**

Active mic capture is enough to prevent the CPU from entering deep idle.
We do **not** acquire a `WakeLock`, which keeps battery use down and
avoids triggering the Play Store's wakelock policy.

**5. Doze / App Standby exemption**

Foreground services are automatically exempt from Doze for the work they
do; the recording loop is unaffected. For network uploads during Doze,
the "Disable battery optimization" button in `MainActivity` deep-links
the user to `Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`, where
they can opt the whole app out of background restrictions.

**6. Boot resumption (`BootReceiver`)**

The `BootReceiver` listens for three intents:

- `BOOT_COMPLETED` — normal post-reboot.
- `LOCKED_BOOT_COMPLETED` — direct-boot phase, *before* the user
  enters their PIN. Posting a notification works in this phase even
  though credential-encrypted user data is unavailable.
- `MY_PACKAGE_REPLACED` — fired after the app itself is updated, so
  the resume prompt comes back without the user having to launch the
  Play Store.

In all three cases, the receiver posts a high-importance notification
("Tap to resume listening"). It does **not** start the FGS directly,
because Android 14+ forbids launching a microphone FGS from
`BOOT_COMPLETED` (the `RECORD_AUDIO` permission is "while-in-use" and
the app is considered backgrounded at boot). A notification interaction
is one of the documented while-in-use exemptions, so tapping the
notification → `MainActivity` → `startForegroundService(BabyService)`
is allowed and resumes recording with one tap.

**7. Runtime permission flow on first launch**

`MainActivity` walks `RECORD_AUDIO` and (on Android 13+) `POST_NOTIFICATIONS`
through the standard `ActivityResultContracts.RequestMultiplePermissions`
contract before starting the FGS. If any permission is denied, the service
is not started and the user sees a toast.

### What can still stop it

By Android design, no app can override these — and trying to is what gets
apps rejected from the Play Store:

- **User taps "Force stop"** in system Settings. `START_STICKY` is
  intentionally not honoured after this.
- **User toggles "Don't allow background activity"** for the app.
- **Aggressive OEM task killers** on some Chinese OEM ROMs
  (Xiaomi MIUI, Huawei EMUI, OPPO ColorOS) ignore Android's FGS contract
  entirely. There is no portable workaround beyond asking the user to
  whitelist the app on those vendors' settings.
- **Disk full** — the recording loop pauses uploads when free space drops
  below 10 % until cleanup catches up.

## Recording logic

The recording loop lives entirely in `BabyService`:

1. Open a `MediaRecorder` writing 3GPP / AMR-NB to
   `getExternalFilesDir("recordings")` (scoped storage, no permission needed).
2. Every second, sample `getMaxAmplitude()` and feed it to `AmplitudeGate`.
3. `AmplitudeGate` emits one of three decisions:
   - `CONTINUE` while sound is loud or silence has not yet lasted long enough.
   - `STOP_AND_KEEP` when silence persists after at least one loud sample —
     the file is kept and queued for upload.
   - `STOP_AND_DISCARD` when silence persists with no loud samples — the file
     is deleted.
4. When a file is kept, every file currently in the recordings directory is
   uploaded to the configured endpoint via `Uploader` (OkHttp multipart).
   Successful uploads delete the local file.

The amplitude logic is pulled out into a tiny pure class precisely so it can
be unit-tested on the JVM without a device.

## Configuration

| Where | What |
|-------|------|
| `app/src/main/res/values/strings.xml` | `file_upload_url` — must be HTTPS. Cleartext is blocked by `network_security_config.xml`. |
| `BabyService` companion | `AMPLITUDE_THRESHOLD`, `SILENCE_TICKS_BEFORE_STOP`, `TICK_INTERVAL_MS`. |

The receiving PHP script lives in `php_scripts/fileupload/upload.php`.

## Build

Requirements:

- JDK 17
- Android SDK with platform `android-35` and build-tools 35.0.0
  (`sdkmanager "platforms;android-35" "build-tools;35.0.0"`)

```bash
./gradlew assembleDebug    # build the debug APK
./gradlew lintDebug        # static analysis
./gradlew testDebugUnitTest # JVM unit tests
```

CI runs the same three commands on every push / PR — see
`.github/workflows/android.yml`. Each successful CI run publishes the
debug APK as a workflow artifact (`babyapp-debug-apk`), downloadable
from the run's page on GitHub. Pushing a tag of the form `vX.Y.Z` also
publishes a GitHub Release with the APK attached.

## Project layout

```
app/src/main/java/com/hollandhaptics/babyapp/
    AmplitudeGate.kt   pure decision logic, fully tested
    BabyService.kt     microphone foreground service
    BootReceiver.kt    posts the resume notification
    MainActivity.kt    permissions + start button
    Uploader.kt        OkHttp multipart upload
app/src/test/java/.../AmplitudeGateTest.kt
php_scripts/fileupload/upload.php
```

## License

Attribution-ShareAlike 4.0 International
http://creativecommons.org/licenses/by-sa/4.0/

## Credits

Originally commissioned by the Amsterdam Emotion Regulation Lab
(Hans IJzerman, VU University Amsterdam) as part of research into
social thermoregulatory patterns in mothers and newborns.
Original development by Johny Gorissen (Holland Haptics) and
Robin van Emden.
