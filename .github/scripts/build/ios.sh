#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
APP_NAME="ohmyssh"
FILE_NAME="ohmyssh"
DIST_DIR="$ROOT_DIR/dist"
VERSION_NAME="${OHMYSSH_VERSION_NAME:-}"
VERSION_CODE="${OHMYSSH_VERSION_CODE:-}"
RELEASE_TAG="${OHMYSSH_RELEASE_TAG:-}"
SYM_ROOT="$ROOT_DIR/build/ios"
BUILD_APP="$SYM_ROOT/Release-iphoneos/$APP_NAME.app"

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "The iOS IPA can only be built on macOS." >&2
  exit 1
fi

if [[ -z "$VERSION_NAME" ]]; then
  VERSION_NAME="$(sed -nE 's/^ohmysshVersionName=([0-9A-Za-z._-]+).*/\1/p' "$ROOT_DIR/gradle.properties" | head -n 1)"
fi

if [[ -z "$VERSION_NAME" ]]; then
  echo "App version not found. Set OHMYSSH_VERSION_NAME or ohmysshVersionName in gradle.properties." >&2
  exit 1
fi

if [[ -z "$VERSION_CODE" ]]; then
  VERSION_CODE="$(sed -nE 's/^ohmysshVersionCode=([0-9]+).*/\1/p' "$ROOT_DIR/gradle.properties" | head -n 1)"
fi

for command_name in xcodebuild zip; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "$command_name is required." >&2
    exit 1
  fi
done

# Xcode runs ./gradlew itself for the shared framework, so the version has to
# travel in the environment rather than on our command line.
export ORG_GRADLE_PROJECT_ohmysshVersionName="$VERSION_NAME"
export ORG_GRADLE_PROJECT_ohmysshVersionCode="$VERSION_CODE"
export ORG_GRADLE_PROJECT_ohmysshReleaseTag="$RELEASE_TAG"

mkdir -p "$DIST_DIR"
rm -rf "$SYM_ROOT/Release-iphoneos"

echo "Building iOS release (no codesign)..."
xcodebuild \
  -project "$ROOT_DIR/iosApp/iosApp.xcodeproj" \
  -target iosApp \
  -configuration Release \
  -sdk iphoneos \
  -arch arm64 \
  SYMROOT="$SYM_ROOT" \
  MARKETING_VERSION="$VERSION_NAME" \
  CURRENT_PROJECT_VERSION="$VERSION_CODE" \
  CODE_SIGNING_ALLOWED=NO \
  CODE_SIGNING_REQUIRED=NO \
  CODE_SIGN_IDENTITY="" \
  CODE_SIGN_ENTITLEMENTS="" \
  DEVELOPMENT_TEAM="" \
  build

if [[ ! -d "$BUILD_APP" ]]; then
  echo "Expected app bundle not found: $BUILD_APP" >&2
  exit 1
fi

OUT_FILE="$DIST_DIR/${FILE_NAME}_${VERSION_NAME}_ios_arm64.ipa"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/${FILE_NAME}-ipa.XXXXXX")"
trap 'rm -rf "$TMP_DIR"' EXIT

mkdir -p "$TMP_DIR/Payload"
cp -R "$BUILD_APP" "$TMP_DIR/Payload/$APP_NAME.app"
xattr -cr "$TMP_DIR/Payload"

rm -f "$OUT_FILE"
(cd "$TMP_DIR" && zip -qry "$OUT_FILE" Payload)

echo "Built iOS IPA:"
echo "$OUT_FILE"
