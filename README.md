# BabyApp Smart Audio Recorder

An Android app that runs continuously, listens to ambient sound through the
microphone, and uploads short recordings whenever the peak amplitude crosses
a configurable threshold. The receiving end is a small PHP script that drops
the files into a directory.

It was originally built in 2016 for sleep / thermoregulation research. This
revision modernizes the Android side to target SDK 35 (Android 15) while
keeping the always-running design.

## How "always running" works

True unkillable background execution is not something modern Android allows
on purpose. What this app does is the closest the platform offers:

| Layer | Mechanism |
|-------|-----------|
| Service type | `BabyService` is a foreground service of type `microphone`. The OS treats microphone-typed FGS as user-essential and will not kill it except under extreme memory pressure. |
| Restart on kill | `onStartCommand` returns `START_STICKY`. If the service ever is killed, the system restarts it as soon as resources are available. |
| User visibility | A persistent low-importance notification is required for any FGS, and is part of the contract. |
| CPU wake | Microphone capture itself keeps the CPU partially awake; no `WakeLock` required. |
| Doze / App Standby | Foreground services are exempt from Doze for the work they do. The "Disable battery optimization" button in `MainActivity` opens the system screen so the user can also exempt the whole app from background restrictions. |
| Reboot | `BootReceiver` listens for `BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED`, and `MY_PACKAGE_REPLACED`. It posts a high-importance "tap to resume" notification. Android 14+ forbids launching a microphone FGS directly from `BOOT_COMPLETED`, but a notification interaction is one of the documented while-in-use exemptions, so the FGS is allowed to start once the user taps the notification. |
| First launch | `MainActivity` walks the runtime permission flow (`RECORD_AUDIO`, `POST_NOTIFICATIONS`) before starting the service. |

What can still stop it (by Android design, no app can prevent these):

- The user "Force stops" the app from system Settings.
- The user toggles "Don't allow background activity".
- The OEM has aggressive task-killer behavior (some Chinese OEM ROMs do).

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
