# MaTRIX Co 5.0.4 — Playback & Compatibility Repair

This repair is based on the supplied 5.0.3 Security-Hardening source.

## Changes
- Restored the existing Mixed Content preference in Settings.
- Restored the per-site mixed-content compatibility exception.
- Kept strict mixed-content blocking as the default (`MIXED_CONTENT_NEVER_ALLOW`).
- Explicitly set `mediaPlaybackRequiresUserGesture = false` so WebView does not impose an extra gesture gate on media playback.
- Kept the 5.0.3 SSL fail-closed behavior, Safe Browsing enabled, and geolocation disabled.

## Important
This is a source project ZIP, not a prebuilt APK. A Gradle build and on-device playback tests are still required. The media gesture setting does not guarantee that every DRM-protected stream is supported by the installed Android System WebView.
