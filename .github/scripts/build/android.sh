#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
APP_NAME="ohmyssh"
DIST_DIR="$ROOT_DIR/dist"
VERSION_NAME="${OHMYSSH_VERSION_NAME:-}"
VERSION_CODE="${OHMYSSH_VERSION_CODE:-}"
RELEASE_TAG="${OHMYSSH_RELEASE_TAG:-}"

if [[ -z "$VERSION_NAME" ]]; then
  VERSION_NAME="$(sed -nE 's/^ohmysshVersionName=([0-9A-Za-z._-]+).*/\1/p' "$ROOT_DIR/gradle.properties" | head -n 1)"
fi

if [[ -z "$VERSION_NAME" ]]; then
  echo "App version not found. Set OHMYSSH_VERSION_NAME or ohmysshVersionName in gradle.properties." >&2
  exit 1
fi

if [[ ! -x "$ROOT_DIR/gradlew" ]]; then
  echo "Gradle wrapper not found or not executable: $ROOT_DIR/gradlew" >&2
  exit 1
fi

GRADLE_ARGS=(
  "-PohmysshVersionName=$VERSION_NAME"
  "-PohmysshReleaseTag=$RELEASE_TAG"
)
if [[ -n "$VERSION_CODE" ]]; then
  GRADLE_ARGS+=("-PohmysshVersionCode=$VERSION_CODE")
fi

mkdir -p "$DIST_DIR"

# One APK covers every device: the app carries no native libraries, so there is
# nothing for an ABI split to strip.
echo "Building Android release APK..."
(cd "$ROOT_DIR" && ./gradlew :androidApp:assembleRelease "${GRADLE_ARGS[@]}")

APK_DIR="$ROOT_DIR/androidApp/build/outputs/apk/release"
APK="$(find "$APK_DIR" -maxdepth 1 -type f -name '*-release.apk' | sort | head -n 1)"

if [[ -z "$APK" ]]; then
  echo "Expected release APK not found in: $APK_DIR" >&2
  exit 1
fi

cp "$APK" "$DIST_DIR/${APP_NAME}_${VERSION_NAME}_android_universal.apk"

echo "Built Android APK:"
find "$DIST_DIR" -maxdepth 1 -type f -name "${APP_NAME}_${VERSION_NAME}_android_*.apk" -print | sort
