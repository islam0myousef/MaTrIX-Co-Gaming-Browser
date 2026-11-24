# 5.0.6

- targetSdk raised to 35 (Android 15).
- Release builds now minified and resource-shrunk with R8: smaller APK and obfuscated code.
- AI API key is now stored encrypted (Android Keystore via EncryptedSharedPreferences). Existing saved keys migrate automatically on first launch.
- Added a privacy policy (PRIVACY_POLICY.md).
