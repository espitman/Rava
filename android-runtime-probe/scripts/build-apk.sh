#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROBE_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
ROOT_DIR=$(CDPATH= cd -- "$PROBE_DIR/.." && pwd)

"$SCRIPT_DIR/prepare-termux-node-runtime.sh"
"$SCRIPT_DIR/fetch-gemini-cli.sh"
"$ROOT_DIR/android-installer/gradlew" -p "$PROBE_DIR" :app:assembleRuntimeProbe

printf 'APK: %s\n' "$PROBE_DIR/app/build/outputs/apk/debug/app-debug.apk"
