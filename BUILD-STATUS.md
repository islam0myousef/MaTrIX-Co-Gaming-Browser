# Build and verification status — MaTRIX Co 4.1.0

## Changes in this maintenance pass
- AudioService now creates its notification channel and promotes itself to foreground from `onCreate()`; `onStartCommand()` is a defensive fallback.
- The service declares `mediaPlayback` as its foreground service type and uses the matching manifest permission.
- Foreground promotion failures are logged and the service stops itself rather than waiting for the system foreground-service timeout.
- MainActivity logs background audio service startup failures and pauses the active WebView as a fallback.
- App version metadata is now `versionName = 4.1.0`, `versionCode = 410`; `applicationId` remains `com.matrixco.browser`.

## Static verification
- AndroidManifest.xml is parsed as XML.
- ZIP integrity is checked after packaging.
- Kotlin source changes are inspected for structural consistency.
- This pass does not claim a successful Android build or runtime verification.

## Not completed
- `assembleDebug` / `assembleRelease` have not been verified in this environment.
- No APK installation or runtime test has been performed.
- Background audio behavior still needs device testing on Android 12, including screen-off playback and returning to the browser.
- Timer/anti-fingerprinting separation, JavaScript bridge hardening, tab lifecycle redesign, and memory/renderer improvements remain planned for subsequent 4.1.x work.

## Build on AndroidIDE / Termux with Android SDK

```bash
chmod +x gradlew
./gradlew clean assembleDebug --no-daemon --stacktrace
```

Expected output:
`app/build/outputs/apk/debug/app-debug.apk`

Keep a backup before installing over an existing version. Android only permits in-place update when the signing certificate matches the installed app.
