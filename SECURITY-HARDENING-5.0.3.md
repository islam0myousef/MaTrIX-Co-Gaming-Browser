# MaTRIX Co 5.0.3 — Security hardening release

## Changes
- SSL certificate errors are always cancelled; one-time and persistent proceed exceptions were removed.
- Mixed content is always blocked globally and per-site; the old compatibility toggle and per-site legacy override were removed from active behavior.
- WebView geolocation is disabled at the settings layer.
- Safe Browsing is enabled wherever the Android WebView API supports it.
- App updater fails closed unless GitHub release metadata provides a valid SHA-256 digest.
- Update download URL is required to use HTTPS and the expected github.com host.
- Version bumped to 5.0.3 / versionCode 503.

## Deliberately unchanged
- File access remains enabled because the current offline-page feature uses app-managed file:// content.
  Disabling it safely requires migrating offline content to AndroidX WebKit WebViewAssetLoader and testing
  all offline save/open flows; disabling it blindly would break an existing feature.
- targetSdk remains 34 to avoid an untested platform-behavior migration in this security patch.
- R8/minification remains disabled until keep rules and reflection/serialization paths are verified.
- This patch does not claim Chromium-level site isolation or Brave-equivalent fingerprinting protection.

## Verification status
- Source-level assertions and ZIP integrity are checked by the packaging step.
- Android Gradle build and on-device security/performance tests have not been run in this environment.
