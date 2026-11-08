#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

REPORT_DIR="$HOME/matrix-reports"
mkdir -p "$REPORT_DIR"
STAMP="$(date +%Y%m%d_%H%M%S)"
LOG="$REPORT_DIR/matrix_4.5.5_build_${STAMP}.txt"
exec > >(tee -a "$LOG") 2>&1

fail() { echo "ERROR: $*" >&2; exit 1; }

[[ -f "$ROOT/gradlew" ]] || fail "gradlew not found; run this script from the extracted project."
command -v java >/dev/null 2>&1 || fail "Java/JDK is required."
command -v unzip >/dev/null 2>&1 || fail "unzip is required."

echo "MaTRIX Co 4.5.5 Universal Release build"
echo "Project: $ROOT"
echo "Log: $LOG"
echo "Architecture: $(uname -m)"

# Confirm that this source package is the intended release.
grep -q 'versionCode = 455' app/build.gradle.kts || fail "Expected versionCode 455."
grep -q 'versionName = "4.5.5"' app/build.gradle.kts || fail "Expected versionName 4.5.5."

# Preserve the exact original gradle.properties and restore it on every exit.
GP="$ROOT/gradle.properties"
GP_BACKUP="$(mktemp)"
cp -p "$GP" "$GP_BACKUP"
restore_gradle_properties() {
  cp -p "$GP_BACKUP" "$GP"
  rm -f "$GP_BACKUP"
}
trap restore_gradle_properties EXIT INT TERM

AAPT2=""
CANDIDATES=(
  "$HOME/android-sdk/build-tools/36.0.0/aapt2"
  "/data/data/com.itsaky.androidide/files/home/android-sdk/build-tools/36.0.0/aapt2"
  "${ANDROID_HOME:-}/build-tools/36.0.0/aapt2"
  "${ANDROID_SDK_ROOT:-}/build-tools/36.0.0/aapt2"
)
for candidate in "${CANDIDATES[@]}"; do
  if [[ -n "$candidate" && -x "$candidate" ]]; then
    AAPT2="$candidate"
    break
  fi
done

if [[ -n "$AAPT2" ]]; then
  echo "Using local AAPT2 override: $AAPT2"
  "$AAPT2" version
  if grep -q '^android\.aapt2FromMavenOverride=' "$GP"; then
    sed -i "s|^android\\.aapt2FromMavenOverride=.*|android.aapt2FromMavenOverride=$AAPT2|" "$GP"
  else
    printf '\nandroid.aapt2FromMavenOverride=%s\n' "$AAPT2" >> "$GP"
  fi
else
  echo "No local AAPT2 override found; Gradle will use its configured AAPT2 artifact."
fi

chmod +x ./gradlew

echo
echo "==> Run full Release lint analysis"
./gradlew --no-daemon --no-parallel --max-workers=1 lintRelease

echo
echo "==> Build universal Release APK (skip lintVitalRelease only)"
./gradlew --no-daemon --no-parallel --max-workers=1 assembleRelease -x lintVitalRelease

APK="$ROOT/app/build/outputs/apk/release/app-release-unsigned.apk"
if [[ -f "$APK" ]]; then
  echo
  echo "BUILD SUCCESSFUL"
  ls -lh "$APK"
  echo "APK: $APK"
  echo "This APK is unsigned unless MX_KEYSTORE signing variables were configured."
else
  fail "Gradle completed but expected APK was not found: $APK"
fi

echo
echo "Build log: $LOG"
echo "No Git commit or push was performed."
