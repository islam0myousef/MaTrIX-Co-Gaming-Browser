# MaTRIX Co 4.5.5 — Universal Compatibility & Safer Navigation

## Highlights
- Universal Android package configuration: no ABI filters or ABI splits; current source package contains no native `.so` libraries.
- HTTPS upgrade failures no longer silently downgrade to unencrypted HTTP.
- Users can approve HTTP fallback once or remember the choice for a specific host.
- HTTP navigation is available for a general-purpose browser; WebView mixed content remains blocked.
- In-app APK updates require a valid GitHub SHA-256 asset digest and a matching downloaded file.
- Release lint is enabled and run explicitly by `build-universal-release.sh`; only `lintVitalRelease` is excluded from the assemble task.
- Added repository ignore rules and the ProGuard rules file referenced by Gradle.

## Verification status
- ZIP integrity and all XML files validated.
- TIMER_ENGINE, MAIN (with build-time placeholders substituted), HARDEN (with seed substituted), and READER JavaScript syntax checks passed.
- ABI configuration reviewed: no filters/splits and no packaged native `.so` files.
- Android Gradle build, APK signing, install, and physical-device tests have not been run in this packaging environment.
