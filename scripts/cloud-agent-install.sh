#!/usr/bin/env bash
# Cloud Agent bootstrap for dicomcamera (Android / Gradle).
#
# Installs the Android SDK command-line tools plus the platform and build-tools
# that the Gradle build needs, points Gradle at the SDK via local.properties,
# and warms the Gradle cache by assembling the dev debug APK.
#
# Safe to run repeatedly: every step is idempotent.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Keep the SDK outside the checkout so it survives across branches/snapshots.
ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
export ANDROID_HOME
export ANDROID_SDK_ROOT="$ANDROID_HOME"

CMDLINE_TOOLS_VERSION="15859902"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip"

# Keep these in sync with compileSdk / build-tools used by the Gradle modules.
SDK_PLATFORM="platforms;android-35"
SDK_BUILD_TOOLS="build-tools;35.0.0"

SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"

install_cmdline_tools() {
  echo "==> Installing Android command-line tools (${CMDLINE_TOOLS_VERSION})"
  local tmp_zip tmp_dir
  tmp_zip="$(mktemp --suffix=.zip)"
  tmp_dir="$(mktemp -d)"
  curl -fsSL --retry 4 --retry-delay 4 -o "$tmp_zip" "$CMDLINE_TOOLS_URL"
  unzip -q "$tmp_zip" -d "$tmp_dir"
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  rm -rf "$ANDROID_HOME/cmdline-tools/latest"
  mv "$tmp_dir/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
  rm -rf "$tmp_zip" "$tmp_dir"
}

if [ ! -x "$SDKMANAGER" ]; then
  install_cmdline_tools
fi

echo "==> Accepting SDK licenses"
# `yes` receives SIGPIPE once sdkmanager stops reading; do not let that fail the script.
set +o pipefail
yes | "$SDKMANAGER" --sdk_root="$ANDROID_HOME" --licenses >/dev/null
set -o pipefail

echo "==> Installing SDK packages"
"$SDKMANAGER" --sdk_root="$ANDROID_HOME" \
  "platform-tools" "$SDK_PLATFORM" "$SDK_BUILD_TOOLS" >/dev/null

# Point Gradle at the SDK (local.properties is gitignored).
echo "sdk.dir=$ANDROID_HOME" > "$REPO_ROOT/local.properties"
echo "==> Wrote $REPO_ROOT/local.properties (sdk.dir=$ANDROID_HOME)"

echo "==> Warming Gradle cache (assembleDevDebug)"
cd "$REPO_ROOT"
chmod +x ./gradlew
./gradlew --no-daemon :app:assembleDevDebug

echo "==> Cloud Agent install complete"
