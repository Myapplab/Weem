#!/usr/bin/env bash
# Android SDK'yı kurar (bulut oturumu; dl.google.com açıkken) ve local.properties'e yazar. Kurulu ise hızlı geçer.
# Oturum başlangıç kancası çağırır; elle de çalışır. Sürümler app/build.gradle.kts ile aynı (compile 36).
set -euo pipefail

SDK=${ANDROID_HOME:-/opt/android-sdk}
ROOT=$(cd "$(dirname "$0")/.." && pwd)
PKGS=("platforms;android-36" "build-tools;36.0.0" "platform-tools")

if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  tmp=$(mktemp -d)
  curl -sSfo "$tmp/clt.zip" https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip
  unzip -q "$tmp/clt.zip" -d "$tmp"
  mkdir -p "$SDK/cmdline-tools"
  rm -rf "$SDK/cmdline-tools/latest"
  mv "$tmp/cmdline-tools" "$SDK/cmdline-tools/latest"
  rm -rf "$tmp"
fi
if [ ! -d "$SDK/platforms/android-36" ] || [ ! -d "$SDK/build-tools/36.0.0" ]; then
  yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" "${PKGS[@]}" >/dev/null
fi
# Gradle SDK'yı buradan bulur (local.properties git'e girmez).
echo "sdk.dir=$SDK" > "$ROOT/local.properties"
echo "Android SDK: $SDK"
