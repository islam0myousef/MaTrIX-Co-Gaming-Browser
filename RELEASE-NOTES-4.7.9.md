# MaTrIX Co Gaming Browser 4.7.9

## Shield / Ad blocking
- Added a lightweight ABP network-rule matcher for URL/path rules instead of relying only on blocked hostnames.
- Added support for `@@` exception rules, `domain=` include/exclude scopes, first-/third-party conditions, and common resource-type options.
- Persisted supported network rules in the Shield database and reloads them when filter lists are refreshed.
- Kept hostname blocking and cosmetic filtering in place as complementary layers.
- Added request-type inference from WebView `Accept` headers to apply resource-specific rules.

## Notes
- This improves compatibility with EasyList/AdGuard-style filters, but does not implement every uBlock Origin or AdGuard procedural/scriptlet feature.
- The release project requires a local Android/Gradle build to produce a device-installable APK.
