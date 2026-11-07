# Build and verification status — MaTRIX Co 4.1.2

## Changes in this maintenance pass
- Version is now `4.1.2` / `versionCode = 412`.
- Timer virtualization has been extracted from `Scripts.MAIN` into the independent `Scripts.TIMER_ENGINE` module.
- Timer virtualization is installed at document start when WebView supports `DOCUMENT_START_SCRIPT`, independently of the fingerprint-protection preference.
- An idempotent runtime injection fallback is used for WebView providers without document-start support.
- Turbo speed is controlled through `window.__mxTimer`; it is idle at 1x until skip mode requests acceleration.
- Turbo expiry uses real wall-clock time so accelerated page timers cannot extend or shorten the configured Turbo duration unexpectedly.
- The 4.1.1 renderer-recovery changes and 4.1.0 AudioService changes are retained.

## Changes retained from 4.1.1 and 4.1.0
- Version is now `4.1.1` / `versionCode = 411`.
- `Tab.web` is replaceable so a dead WebView renderer can be recovered safely.
- `onRenderProcessGone()` removes and destroys the dead WebView, creates a fresh configured WebView, and restores the last URL for the active tab or defers restoration for a background tab.
- Existing 4.1.0 AudioService foreground-start changes are retained.

## Changes retained from 4.1.0
- AudioService now creates its notification channel and promotes itself to foreground from `onCreate()`; `onStartCommand()` is a defensive fallback.
- The service declares `mediaPlayback` as its foreground service type and uses the matching manifest permission.
- Foreground promotion failures are logged and the service stops itself rather than waiting for the system foreground-service timeout.
- MainActivity logs background audio service startup failures and pauses the active WebView as a fallback.
- `applicationId` remains `com.matrixco.browser`; the current release metadata is `versionName = 4.1.2`, `versionCode = 412`.

## Static verification
- `Scripts.TIMER_ENGINE` passes JavaScript syntax validation with Node.js.
- `Scripts.MAIN` passes JavaScript syntax validation after Kotlin placeholder substitution.
- Runtime harness check: a 1000 ms virtual timeout at 10x fired after approximately 100 ms real time; virtual clock advanced approximately 1000 ms; reset returned speed to 1x.

- AndroidManifest.xml is parsed as XML.
- ZIP integrity is checked after packaging.
- Kotlin source changes are inspected for structural consistency.
- JavaScript engine behavior was tested in a Node.js harness; this does not replace Android WebView testing. This pass does not claim a successful Android build.

## Not completed
- `assembleDebug` / `assembleRelease` have not been verified in this environment.
- No APK installation or runtime test has been performed.
- Background audio behavior still needs device testing on Android 12, including screen-off playback and returning to the browser.
- JavaScript bridge hardening and broader tab lifecycle/memory tuning remain planned for subsequent 4.1.x work.

## Build on AndroidIDE / Termux with Android SDK

```bash
chmod +x gradlew
./gradlew clean assembleDebug --no-daemon --stacktrace
```

Expected output:
`app/build/outputs/apk/debug/app-debug.apk`

Keep a backup before installing over an existing version. Android only permits in-place update when the signing certificate matches the installed app.
