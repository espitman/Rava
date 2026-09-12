#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROBE_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)

# shellcheck source=../versions.env
. "$PROBE_DIR/versions.env"

DOWNLOAD_DIR=${RAVA_DOWNLOAD_DIR:-"$PROBE_DIR/downloads"}
ARCHIVE="$DOWNLOAD_DIR/gemini-cli-$GEMINI_CLI_VERSION.tgz"
ASSET_PARENT="$PROBE_DIR/app/src/main/assets"
ASSET_DIR="$ASSET_PARENT/gemini"

mkdir -p "$DOWNLOAD_DIR" "$ASSET_PARENT"

if [ ! -e "$ASSET_DIR" ]; then
  if [ ! -f "$ARCHIVE" ]; then
    curl --fail --location --retry 3 \
      "https://registry.npmjs.org/@google/gemini-cli/-/gemini-cli-$GEMINI_CLI_VERSION.tgz" \
      --output "$ARCHIVE"
  fi

  ACTUAL_INTEGRITY=$(openssl dgst -sha512 -binary "$ARCHIVE" | openssl base64 -A)
  if [ "$ACTUAL_INTEGRITY" != "$GEMINI_CLI_INTEGRITY_SHA512" ]; then
    printf 'Gemini CLI integrity mismatch.\nExpected: %s\nActual:   %s\n' \
      "$GEMINI_CLI_INTEGRITY_SHA512" "$ACTUAL_INTEGRITY" >&2
    exit 1
  fi

  STAGING=$(mktemp -d "${TMPDIR:-/tmp}/rava-gemini-cli.XXXXXX")
  cleanup_staging() {
    if [ -d "$STAGING" ]; then
      find "$STAGING" -depth -delete
    fi
  }
  trap cleanup_staging EXIT HUP INT TERM
  tar -xzf "$ARCHIVE" -C "$STAGING" package/bundle package/LICENSE package/package.json
  mv "$STAGING/package/bundle" "$ASSET_DIR"
  install -m 644 "$STAGING/package/LICENSE" "$ASSET_DIR/LICENSE"
  install -m 644 "$STAGING/package/package.json" "$ASSET_DIR/package.json"
fi

if [ ! -f "$ASSET_DIR/gemini.js" ]; then
  printf '%s\n' 'Gemini package did not contain bundle/gemini.js.' >&2
  exit 1
fi

printf '%s  %s\n' "$GEMINI_CLI_ENTRY_SHA256" "$ASSET_DIR/gemini.js" | shasum -a 256 -c -
INSTALLED_VERSION=$(sed -n 's/^[[:space:]]*"version": "\([^"]*\)",*$/\1/p' "$ASSET_DIR/package.json" | head -n 1)
if [ "$INSTALLED_VERSION" != "$GEMINI_CLI_VERSION" ]; then
  printf 'Generated Gemini package version mismatch: expected %s, found %s\n' \
    "$GEMINI_CLI_VERSION" "${INSTALLED_VERSION:-missing}" >&2
  exit 1
fi

if command -v node >/dev/null 2>&1; then
  node "$ASSET_DIR/gemini.js" --version
else
  printf '%s\n' 'Host Node is unavailable; skipped host-side bundle startup check.'
fi

if [ -f "$ARCHIVE" ]; then
  shasum -a 256 "$ARCHIVE"
fi
shasum -a 256 "$ASSET_DIR/gemini.js"
if [ "${RAVA_KEEP_DOWNLOADS:-0}" != 1 ] && [ -f "$ARCHIVE" ]; then
  find "$ARCHIVE" -delete
fi
printf 'Prepared Gemini CLI %s assets at %s\n' "$GEMINI_CLI_VERSION" "$ASSET_DIR"
