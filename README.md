# OrbitBar V1 — Hybrid Android Taskbar

A native Android taskbar that runs on top of your existing home screen and applications. Built in Java with Android platform APIs; no third-party UI libraries or analytics.

## V1 implementation

| Capability | V1 behaviour |
|---|---|
| Hybrid appearance | Switch between floating Dock, bottom Taskbar and right-edge EdgeBar, in settings or in the expanded bar. |
| Pins | Pick apps from the installed launcher-app list; long-press an app icon or list item to pin/unpin. Pins persist. |
| Recent apps | Reads foreground app usage (up to 3 days) if usage access is granted. Without it, shows apps opened through OrbitBar. This is *not* a privileged OS Recents/task list. |
| App search | Search installed, launchable applications by name directly from the expanded bar. |
| Split shortcut | Best-effort `FLAG_ACTIVITY_LAUNCH_ADJACENT` when opening an app. Depends on multi-window mode, source activity and device/OS. |
| Floating shortcut | Best-effort `ActivityOptions.setLaunchBounds` in desktop/freeform-capable environments. On ordinary phones the launch may be full screen. No attempt to invoke hidden APIs. |
| Media playback | Previous, play/pause, next and active track title when Notification Listener access is granted and a media app publishes a MediaSession. |
| Quick settings | Open Android's Internet panel, Bluetooth settings and display settings; raise media volume. |
| Background | User-initiated foreground service with persistent notification and a Stop action. No automatic startup at boot. |

## Supported devices

Android 8.0+ (API 26), target Android 15 (API 35). Intended for sideload/personal testing first. Overlay UI, media session permissions, split-screen and floating-window behavior vary across Pixel, OnePlus, Samsung, and Xiaomi/POCO firmware.

## Build an APK without a PC

1. Create a new GitHub repository called `OrbitBar`.
2. Upload the **contents of this project folder** so `app/`, `build.gradle`, and `.github/` are at repository root. The `.github` directory may be hidden in Android file pickers; uploading/extracting via GitHub's browser editor or Git on a computer may be easier.
3. Go to **Actions → OrbitBar Android Debug APK → Run workflow**, or push to the `main` branch to trigger an automatic build.
4. When the workflow succeeds, open its run, scroll to **Artifacts**, and select `OrbitBar-v1-debug-apk`.
5. Unzip the downloaded artifact and install `app-debug.apk` on your Android phone, after allowing installs from your browser/files app.

The GitHub workflow provisions JDK 17, Android SDK 35 and Gradle 8.9, then runs `gradle assembleDebug`. If you do have Android Studio, import the project folder and run the `app` configuration instead.

**Important:** This deliverable includes complete source and a CI build workflow. It was not compiled or run on an Android emulator in the authoring environment (Android SDK and Gradle are unavailable there). Run the included workflow to establish an actual APK build and device test, and inspect the GitHub Actions logs if it fails.

## First-use setup

1. Open OrbitBar.
2. Choose Dock / Taskbar / EdgeBar.
3. Grant **Display over other apps** (mandatory). Optional: **Recent app usage**, **Media session controls**, **Notifications**.
4. Select pinned apps.
5. Tap **Start OrbitBar**. Tap search/menu to expand the overlay, switch its design or use Split/Float modes.
6. Stop it from the app or its foreground-service notification.

## Permissions and privacy

- `SYSTEM_ALERT_WINDOW`: draw a user-requested taskbar on top of other applications.
- `PACKAGE_USAGE_STATS`: optional on-device app usage ordering; not retained as a usage log.
- Notification Listener: optional on-device access to active MediaSessions for playback controls. The application never uploads or logs notification contents.
- `POST_NOTIFICATIONS`: show a visible notification on Android 13+.
- Foreground service permissions: keep the user-requested taskbar alive. The `specialUse` type needs policy assessment if submitted to Google Play.
- The Android 11+ launcher-app `<queries>` filter allows discovering launchable apps without `QUERY_ALL_PACKAGES`.

No network permission is requested. There is no server, account login, telemetry or third-party SDK.

## Design and code layout

- `MainActivity.java`: configuration, permissions and pin picker
- `OverlayService.java`: foreground overlay lifecycle, three layouts, search and app launching
- `AppRepository.java`: installed apps and recent usage access
- `MediaHelper.java`, `MediaListenerService.java`: optional active media sessions
- `Prefs.java`: local persistent settings and pins
- `Ui.java`: reusable design primitives
- `.github/workflows/android-debug.yml`: GitHub Actions APK build

## Planned follow-up versions

- V1.1: draggable edge handle, adjustable dock opacity/size, better landscape/tablet layouts, dynamic theme
- V1.2: built-in workspace profiles and paired split-screen app shortcuts
- V1.3: OEM-specific freeform compatibility helpers where permitted, accessibility improvements, richer media artwork

## Known limitations

- Android has restrictions on background activity launches and overlays. Some manufacturers aggressively stop foreground services or suppress overlay windows.
- Android deliberately does not give ordinary apps a complete, authoritative list of system recent tasks. Usage history is the best permission-based approximation.
- Split-screen and freeform shortcuts are not guaranteed. On unsupported devices they may launch fullscreen; users can open split-screen manually through the system Recents UI.
- Internet, Bluetooth and brightness are settings shortcuts, **not** unauthorized direct system toggles.
- Media controls work with media players offering active Android MediaSessions and require notification-listener access.
- The app does not automatically restart after reboot and does not require Accessibility Service access.
