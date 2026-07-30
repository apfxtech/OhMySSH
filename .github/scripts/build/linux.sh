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

case "$(uname -m)" in
  x86_64 | amd64) TARGET_ARCH="x64" ;;
  aarch64 | arm64) TARGET_ARCH="arm64" ;;
  armv7l | armv7*) TARGET_ARCH="armv7" ;;
  *) TARGET_ARCH="$(uname -m)" ;;
esac

BUILD_DIR="$ROOT_DIR/shared/build/compose/binaries/main/app/$APP_NAME"
OUT_FILE="$DIST_DIR/${APP_NAME}_${VERSION_NAME}_linux_${TARGET_ARCH}"

if [[ ! -x "$ROOT_DIR/gradlew" ]]; then
  echo "Gradle wrapper not found or not executable: $ROOT_DIR/gradlew" >&2
  exit 1
fi

if ! command -v tar >/dev/null 2>&1; then
  echo "tar is required." >&2
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

echo "Building Linux release (jpackage app image with a bundled runtime)..."
(cd "$ROOT_DIR" && ./gradlew :shared:createDistributable "${GRADLE_ARGS[@]}")

if [[ ! -x "$BUILD_DIR/bin/$APP_NAME" ]]; then
  echo "Expected app binary not found: $BUILD_DIR/bin/$APP_NAME" >&2
  exit 1
fi

TMP_DIR="$(mktemp -d)"
ARCHIVE="$TMP_DIR/payload.tar.gz"
STUB="$TMP_DIR/stub.sh"
trap 'rm -rf "$TMP_DIR"' EXIT

tar -C "$BUILD_DIR" -czf "$ARCHIVE" .

cat > "$STUB" <<'STUB'
#!/usr/bin/env bash
set -Eeuo pipefail

APP_NAME="ohmyssh"
WORK_DIR="${TMPDIR:-/tmp}/${APP_NAME}-self-$$"
MARKER="__OHMYSSH_PAYLOAD_BELOW__"

cleanup() {
  local tmp_root="${TMPDIR:-/tmp}"
  local expected_prefix="${tmp_root%/}/${APP_NAME}-self-"

  if [[ -n "${WORK_DIR:-}" && "$WORK_DIR" == "$expected_prefix"* ]]; then
    rm -rf -- "$WORK_DIR"
  else
    echo "Refusing to remove unexpected work directory: ${WORK_DIR:-<unset>}" >&2
  fi
}
trap cleanup EXIT

mkdir -p "$WORK_DIR"
ARCHIVE_LINE="$(awk "/^$MARKER$/ { print NR + 1; exit 0; }" "$0")"
if [[ -z "$ARCHIVE_LINE" ]]; then
  echo "Embedded payload marker not found." >&2
  exit 1
fi

tail -n +"$ARCHIVE_LINE" "$0" | tar -xz -C "$WORK_DIR"
chmod +x "$WORK_DIR/bin/$APP_NAME"
exec "$WORK_DIR/bin/$APP_NAME" "$@"

__OHMYSSH_PAYLOAD_BELOW__
STUB

cat "$STUB" "$ARCHIVE" > "$OUT_FILE"
chmod +x "$OUT_FILE"

echo "Built single-file executable:"
echo "$OUT_FILE"
