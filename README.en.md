# Posture Dashboard · Android

[简体中文](README.md)

Turn your phone into a Bluetooth dashboard for a posture recognition device. Connect to an HC-06 module to see the current posture illustrated by a 3D figure, track time spent in each posture, and review the longest continuous duration and number of segments. You can inspect the received text or export the results for your experiment records.

This is the Android companion to the [Windows posture terminal](https://github.com/Misaka2002/Bluetooth-Terminal-for-Posture-Recognition), using the same eight-posture protocol. It runs on Android 8.0 or later. The app interface is currently in Chinese.

[View the full interface preview](docs/images/android-demo.png). This preview uses simulated data and was rendered by an Android native graphics test environment.

## Download and install

This is a test version. Visit the [Release page](https://github.com/Misaka2002/Bluetooth-Terminal-for-Posture-Recognition-Android-/releases/tag/v0.1.0) or [download the APK directly](https://github.com/Misaka2002/Bluetooth-Terminal-for-Posture-Recognition-Android-/releases/download/v0.1.0/PostureDashboard-Android-v0.1.0.apk), then install it on a phone running Android 8.0 or later. No source build is required.

## Getting started

Install the APK and tap “演示模式” (Demo mode) to explore the interface without hardware. Demo sessions and device sessions are saved separately.

To connect hardware, first pair the HC-06 in your phone's Bluetooth settings. Return to the app, refresh the paired device list, select your device, and connect. Android 12 and later require the Nearby devices permission. The app uses classic Bluetooth SPP, not BLE. It does not scan for devices or request location access.

There is no baud-rate setting on the phone. The UART settings between the HC-06 and STM32 must still match; this project's hardware link uses 9600 baud, 8N1.

The dashboard shows the current posture, continuous duration, upright percentage, posture changes, and statistics for all eight postures. “显示原始终端” (Show raw terminal) expands or collapses the received text without changing the interface width. “清屏” (Clear screen) removes displayed text without deleting logs or resetting statistics.

“新统计会话” (New session) saves the current session and starts fresh statistics. An existing Bluetooth connection can stay active. Any incomplete line left over from the previous session is discarded.

## How timing works

Timing starts when the phone creates a session. A complete, valid posture line starts or updates the current posture using its arrival time. Consecutive reports of the same posture belong to one segment. A different posture closes the previous segment and starts a new one.

Disconnecting ends the active posture immediately. If no valid classification arrives for more than two seconds, subsequent time is recorded as unknown rather than added to the last posture. Percentages use total known-posture time as their denominator and exclude unknown time. Segment counts include segments with positive duration.

The protocol does not include the MCU's startup time, so the app cannot recover activity before connection. Timing uses the phone's monotonic clock; reporting intervals and Bluetooth delay still affect accuracy.

While connected, a foreground service receives data and shows a notification with a disconnect action. A partial wake lock keeps the receiving thread active and is released on disconnect. Background restrictions differ between phones, so check screen-off reception on your device. Demo mode is intended primarily for use with the app open.

## Saving experiment records

Sessions are stored locally on the phone. Statistics are saved every 30 seconds and when changing sessions, disconnecting, leaving the screen, or ending the service normally. Force-stopping the process may lose recently unsaved statistics. The next launch starts a new session.

“导出记录” (Export records) creates a ZIP for the current session. You can save it to a location you choose or share it with another app. “历史记录” (History) lets you export earlier sessions.

| File | Contents |
| --- | --- |
| `summary.csv` | Duration, percentage, longest continuous duration, and segment count for each posture |
| `timeline.csv` | Start, end, and duration of each segment |
| `session.json` | Session source, total duration, valid frame count, and other metadata |
| `received.log` | Complete received log with timestamps |

CSV files use UTF-8 with a BOM so Excel can display Chinese text. The terminal displays up to 200 recent lines; the full log remains stored locally. The app has no network permission and does not upload records. Uninstalling removes private sessions, so export important records first.

## Device protocol

Send text lines terminated by LF or CRLF, for example:

```text
POSTURE=upright
POSTURE=forward_lean
```

| Protocol name | Posture |
| --- | --- |
| `upright` | Upright sitting |
| `forward_lean` | Forward lean |
| `backward_lean` | Backward lean |
| `left_lean` | Left lean |
| `right_lean` | Right lean |
| `forward_hunch` | Forward hunch |
| `left_hunch` | Left hunch |
| `right_hunch` | Right hunch |

Left and right refer to the person's own sides. The parser also accepts `LABEL=<name>` or a posture name on its own, without case sensitivity. Numeric `ID` messages alone are not counted. Lines containing multiple classification fields are also rejected. Lines longer than 1,024 characters are discarded in full; reception resumes at the next line.

## Building from source

Open the project in Android Studio with JDK 21, Android SDK 35, and Build Tools 35.0.0. The project includes the Gradle 8.11.1 Wrapper and uses Android Gradle Plugin 8.9.2. The first build needs internet access to download dependencies. Android Studio writes your local SDK path to `local.properties`, which is excluded from Git.

The project allows non-ASCII Windows paths and has been built and tested with JDK 21 in a directory containing Chinese characters. Gradle uses the system encoding for launch arguments while Java sources and tests use UTF-8.

On Windows, run from the project directory:

```powershell
.\gradlew.bat :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

On macOS / Linux:

```sh
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

After the build completes, install `app/build/outputs/apk/debug/app-debug.apk` on your Android phone to use the app.

`core/` contains protocol parsing, timing, and report generation. `app/` contains the Android interface, Bluetooth service, local storage, and export features. Automated tests cover parsing, fragmented input, timeouts, disconnects, and statistical consistency. This version has been tested on physical hardware. Bluetooth and background behavior should still be checked on each target device.

The 3D figure atlas is bundled in the APK and reused from the Windows version. See [asset notes](docs/assets.md) for its mapping and provenance.
