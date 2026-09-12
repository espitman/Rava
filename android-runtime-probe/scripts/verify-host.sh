#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROBE_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
ROOT_DIR=$(CDPATH= cd -- "$PROBE_DIR/.." && pwd)

for script in "$SCRIPT_DIR"/*.sh; do
  case "$(sed -n '1p' "$script")" in
    *bash) bash -n "$script" ;;
    *) sh -n "$script" ;;
  esac
done

node --check "$PROBE_DIR/app/src/main/assets/node-probe.js"
"$ROOT_DIR/android-installer/gradlew" -p "$PROBE_DIR" \
  :app:testDebugUnitTest :app:lintDebug :app:assembleDebug

printf '%s\n' 'Host checks passed.'
