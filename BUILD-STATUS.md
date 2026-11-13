# Build and verification status — MaTRIX Co 4.2.0

## 4.2.0 Privacy, Security & Gaming Consolidation
- Version: `4.2.0`; `versionCode = 420`.
- Canvas perturbation now uses a per-document seed and deterministic pixel selection, so repeated reads within the same page receive consistent changes.
- Audio readout perturbation uses stable per-document offsets instead of fresh random offsets on every read.
- Removed hardcoded 412×915 screen spoofing; actual screen geometry and color depth remain available for layout/game compatibility.
- WebView remote debugging is enabled only for debug builds (`BuildConfig.DEBUG`) and disabled for release builds.
- Retained native request filtering, JavaScript-bridge removal, third-party-cookie preference, mixed-content restrictions, Safe Browsing preference, external-scheme controls, sanitized downloads, renderer recovery, game-mode renderer priority, and sleeping background tabs.
- Corrected a duplicate insecure-HTTP fallback toast.

## 4.2.0 verification
- `Scripts.TIMER_ENGINE`, `Scripts.MAIN` (after placeholder substitution), and `Scripts.HARDEN` pass `node --check`.
- `AndroidManifest.xml` parses successfully.
- Android Gradle build was attempted; it stopped before compilation because this environment could not resolve `services.gradle.org` (`UnknownHostException`). No APK build success is claimed.
- Physical-device WebView tests remain outstanding.

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


## 4.1.4 — Safe Navigation & Downloads
- Hardened external scheme handling: only user-initiated `mailto`, `tel`, and `sms` links can be handed to Android; arbitrary `intent://` and unknown schemes are blocked.
- Download URLs are restricted to HTTP(S), reject embedded credentials, and filenames are sanitized before storage.
- Download Referer is limited to same-origin and origin-root only.
- Static checks performed during packaging; Android Gradle build and device runtime tests are not claimed unless run separately.


## v4.2.1 — BuildConfig Generation Fix
- Enabled `android.buildFeatures.buildConfig = true` for AGP 8+.
- Fixes Kotlin compilation reference in `MxApp.kt`.
- Gradle build not executed in packaging environment; run `./gradlew assembleRelease` to verify.


## v4.3.0 — Privacy Hardening & Smart URL
- Version: 4.3.0 (versionCode 430).
- Updated `Scripts.kt`: native per-tab, origin-derived seed (kept out of page-readable Web Storage), AudioAnalyser frequency and time-domain perturbation, and no mutation of live AudioBuffer samples.
- Updated `UrlUtil.kt` and `MainActivity.kt`: safe URL normalization and protocol typo repair; no speculative TLD guessing.
- Static checks: JavaScript syntax check passed; URL resolver compiled and passed focused cases for bare domains, typo protocols, and search fallback; Manifest XML and ZIP integrity checked.
- Android Gradle build was attempted but could not start because this environment cannot resolve `services.gradle.org` (UnknownHostException). Build/APK status is unverified.
- Known limitation: WebRTC may still expose local host candidates on some WebView providers; this release does not claim complete WebRTC leak prevention.


## v4.3.1 — Canvas interceptor correction
- Removed duplicate/random Canvas mutation from MAIN; MAIN now only reports suspected Canvas reads.
- HARDEN remains the sole Canvas mutation layer and now targets only small detached canvases.
- Static source review and archive integrity checks are required; Android Gradle build and on-device BrowserLeaks validation remain pending.


## v4.3.2 — Canvas export coverage
- Removed the DOM-detachment restriction from the small-canvas export path (`toDataURL` / `toBlob`).
- Export protection works on a temporary canvas copy, leaving the original page canvas unchanged.
- Connected-canvas `getImageData` remains untouched for compatibility; this leaves a possible fingerprinting path that requires separate testing.
- JavaScript syntax and ZIP integrity checks are required; Android Gradle build and BrowserLeaks device validation are pending.


## v4.3.3 — Canvas pipeline and injection diagnostics
- Expanded deterministic `getImageData()` perturbation to small connected and detached canvases; only the returned ImageData is changed.
- Retained temporary-copy export mutation for `toDataURL()` / `toBlob()`.
- Reordered runtime fallback so hardening is evaluated before MAIN.
- Added `__mxH`, `__mxCanvasProtection`, and `__mxDocumentStart` diagnostic markers and explicit log output when document-start injection is unavailable.
- Pending: Android release build, device validation, BrowserLeaks signature change, valid image rendering, and repeatability tests. No claim of successful APK build.


## v4.4.0 — Automatic Threat Feed Updates
- Added `ThreatFeedWorker.kt` using AndroidX WorkManager 2.11.2.
- Unique periodic work scheduled every 6 hours, constrained to connected network.
- App startup schedules background refresh; existing MainActivity stale-feed check remains as foreground fallback.
- RuleUpdater freshness interval changed from 24h to 6h.
- Last-known-good database is retained when source downloads fail or validation thresholds are not met.
- Scope limitation: this refreshes the existing domain-based threat feeds; it does not dynamically update browser-engine fixes or fingerprint countermeasure code.
- Pending: Android Gradle Release build, WorkManager execution on Android 12/ColorOS, and confirmation of actual feed refresh from app logs/UI.


## v4.5.0 changes (2026-11-07)
- `Updater.kt`: compact neon update dialog with animated GIF background, progress/status states, pause action, and Glide cleanup.
- `Updater.kt`: persistent partial APK download with HTTP Range and If-Range (ETag/Last-Modified), Content-Range validation, SHA-256 validation, and safe fallback to a clean download when the server does not support resume.
- `app/src/main/res/drawable-nodpi/matrix_dragon.gif`: user-supplied 498×280, 59-frame dragon animation.
- `app/build.gradle.kts`: version 4.5.0 / code 450 and Glide 4.16.0.
- Not yet built or device-tested. Resume depends on the release server supporting byte ranges and stable validators.


## 4.9.0 AdBlock rebuild

A new independent ABP-compatible network-rule engine was added. See `ADBLOCK-REBUILD-4.9.0.md` for supported syntax and limitations. The Android Gradle build was not verified in this environment because the Gradle 8.11.1 distribution could not be downloaded.


## 5.0.3 — AdBlock rebuild version label
- Updated Android `versionName` to `5.0.3` and `versionCode` to `500`.
- The AdBlock engine implementation is unchanged from the prior 4.9.0 rebuild package; this is a version-number update only.
- Android Gradle build and on-device AdBlock test remain unverified.
