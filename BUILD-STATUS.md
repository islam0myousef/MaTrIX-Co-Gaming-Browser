# Build and verification status — MaTRIX Co 4.1.3

## 4.1.3 JavaScript bridge security
- Version is now `4.1.3` / `versionCode = 413`.
- Removed `addJavascriptInterface`, `ShieldBridge`, and the `@JavascriptInterface` import. No JavaScript interface is registered on browser WebViews.
- Removed the `__mxb`-dependent fetch/XHR/Beacon wrappers from `Scripts.MAIN`. Network filtering remains enforced in `WebViewClient.shouldInterceptRequest`.
- The only former bridge method was a URL-blocking query; repository search confirmed there were no other bridge methods or native actions exposed through it.
- Popup creation still requires a user gesture and is rate-limited; camera/microphone permission requests and geolocation prompts remain denied by default.

## Changes retained from 4.1.2
- Version is retained from 4.1.2 / `versionCode = 412` as the previous release baseline.
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
- `applicationId` remains `com.matrixco.browser`; the current release metadata is `versionName = 4.1.3`, `versionCode = 413`.

## Static verification
- `Scripts.TIMER_ENGINE` passes JavaScript syntax validation with Node.js (retained from 4.1.2).
- Updated `Scripts.MAIN` passes JavaScript syntax validation after Kotlin placeholder substitution.
- Repository scan confirms no `addJavascriptInterface`, `@JavascriptInterface`, `ShieldBridge`, or `__mxb` references remain in application source.
- `AndroidManifest.xml` parses as valid XML.
- Runtime harness check: a 1000 ms virtual timeout at 10x fired after approximately 100 ms real time; virtual clock advanced approximately 1000 ms; reset returned speed to 1x.

- ZIP integrity is checked after packaging.
- Kotlin source changes are inspected for structural consistency.
- JavaScript engine behavior was tested in a Node.js harness; this does not replace Android WebView testing. This pass does not claim a successful Android build.

## Not completed
- `assembleDebug` was attempted but could not start because the Gradle 8.9 distribution was not cached and this environment could not resolve `services.gradle.org`; no Gradle build result is claimed.
- `assembleRelease` has not been verified.
- No APK installation or runtime test has been performed.
- Background audio behavior still needs device testing on Android 12, including screen-off playback and returning to the browser.
- Broader tab lifecycle/memory tuning remains planned for subsequent 4.1.x work.

## Build on AndroidIDE / Termux with Android SDK

```bash
chmod +x gradlew
./gradlew clean assembleDebug --no-daemon --stacktrace
```

Expected output:
`app/build/outputs/apk/debug/app-debug.apk`

Keep a backup before installing over an existing version. Android only permits in-place update when the signing certificate matches the installed app.
