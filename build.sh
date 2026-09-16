#!/usr/bin/env bash
# Build APK debug OverlayOps.
#
# Pakai JDK 17 + Android SDK (platform 34, build-tools 34.0.0).
# Ubah JAVA_HOME / ANDROID_HOME / GRADLE_BIN kalau lokasinya beda.
set -euo pipefail

export JAVA_HOME="${JAVA_HOME:-/home/user/.local/jdk17}"
export ANDROID_HOME="${ANDROID_HOME:-/home/user/.local/android-sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-/home/user/.local/gradle-home}"
GRADLE_BIN="${GRADLE_BIN:-/home/user/.local/gradle-8.7/bin/gradle}"

mkdir -p apk
"$GRADLE_BIN" --no-daemon :app:assembleDebug --console=plain
cp -f app/build/outputs/apk/debug/app-debug.apk apk/OverlayOps-1.0.0-debug.apk
echo
echo "APK siap: apk/OverlayOps-1.0.0-debug.apk"
echo "Install:  adb install -r apk/OverlayOps-1.0.0-debug.apk"
