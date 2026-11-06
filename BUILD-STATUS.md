# Build and verification status — MaTrIX Co 4.0.0

## Completed static checks
- AndroidManifest.xml parses as valid XML.
- app/build.gradle.kts is restored and non-empty.
- Gradle wrapper targets 8.9 for AGP 8.7.3 compatibility.
- AndroidX WebKit dependency is declared.
- Performance Lab routes are wired from the home page, browser menu, and settings.
- Home page sections, GX wallpaper defaults, Security Center status dashboard, and GX-themed tab manager colors are wired.
- Storage directory-size scans are cached for 15 seconds and invalidated after cache cleanup.
- Kotlin parser pass reported no syntax-level `expecting` / unexpected-token errors in the changed Kotlin files.
- ZIP integrity is checked after packaging.

## Not completed
- `assembleDebug` could not run in this environment because `services.gradle.org` DNS/network access is unavailable; the Android SDK is also not installed in this environment.
- No APK installation, runtime UI test, game compatibility test, or dynamic security test has been performed.

## Build on AndroidIDE / Termux environment with Android SDK

```bash
chmod +x gradlew
./gradlew --version
./gradlew clean assembleDebug --no-daemon --stacktrace
```

Expected output:
`app/build/outputs/apk/debug/app-debug.apk`

Keep a backup before installing over an existing version. Android only permits in-place update when the signing certificate matches the installed app.
