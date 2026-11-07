# Build and verification status — MaTRIX Co 4.1.1

## Changes in this maintenance pass
- Version is now `4.1.1` / `versionCode = 411`.
- `Tab.web` is replaceable so a dead WebView renderer can be recovered safely.
- `onRenderProcessGone()` removes and destroys the dead WebView, creates a fresh configured WebView, and restores the last URL for the active tab or defers restoration for a background tab.
- Existing 4.1.0 AudioService foreground-start changes are retained.

## Changes retained from 4.1.0
- AudioService now creates its notification channel and promotes itself to foreground from `onCreate()`; `onStartCommand()` is a defensive fallback.
- The service declares `mediaPlayback` as its foreground service type and uses the matching manifest permission.
- Foreground promotion failures are logged and the service stops itself rather than waiting for the system foreground-service timeout.
- MainActivity logs background audio service startup failures and pauses the active WebView as a fallback.
- `applicationId` remains `com.matrixco.browser`; the current release metadata is `versionName = 4.1.1`, `versionCode = 411`.

## Static verification
- AndroidManifest.xml is parsed as XML.
- ZIP integrity is checked after packaging.
- Kotlin source changes are inspected for structural consistency.
- This pass does not claim a successful Android build or runtime verification.

## Not completed
- `assembleDebug` / `assembleRelease` have not been verified in this environment.
- No APK installation or runtime test has been performed.
- Background audio behavior still needs device testing on Android 12, including screen-off playback and returning to the browser.
- Timer/anti-fingerprinting separation, JavaScript bridge hardening, and broader tab lifecycle/memory tuning remain planned for subsequent 4.1.x work.

## Build on AndroidIDE / Termux with Android SDK

```bash
chmod +x gradlew
./gradlew clean assembleDebug --no-daemon --stacktrace
```

Expected output:
`app/build/outputs/apk/debug/app-debug.apk`

Keep a backup before installing over an existing version. Android only permits in-place update when the signing certificate matches the installed app.
