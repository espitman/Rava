#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROBE_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
APK="$PROBE_DIR/app/build/outputs/apk/debug/app-debug.apk"
PACKAGE=ir.rava.runtimeprobe
ACTIVITY=.MainActivity

if [ ! -f "$APK" ]; then
  printf 'APK is missing: %s\n' "$APK" >&2
  exit 1
fi
DEVICE_COUNT=$(adb devices | awk 'NR > 1 && $2 == "device" { count++ } END { print count + 0 }')
if [ "$DEVICE_COUNT" -ne 1 ]; then
  printf 'Expected exactly one authorized device, found %s.\n' "$DEVICE_COUNT" >&2
  exit 1
fi

RESULT=$(mktemp "${TMPDIR:-/tmp}/rava-device-provider-result.XXXXXX")
cleanup_result() {
  if [ -f "$RESULT" ]; then
    find "$RESULT" -delete
  fi
}
trap cleanup_result EXIT HUP INT TERM

adb install -r "$APK"
DEVICE_APK=$(adb shell pm path "$PACKAGE" | sed -n 's/^package://p' | tr -d '\r')
LOCAL_SHA=$(shasum -a 256 "$APK" | awk '{print $1}')
DEVICE_SHA=$(adb shell sha256sum "$DEVICE_APK" | awk '{print $1}')
if [ "$LOCAL_SHA" != "$DEVICE_SHA" ]; then
  printf 'Installed APK hash mismatch: local %s, device %s\n' "$LOCAL_SHA" "$DEVICE_SHA" >&2
  exit 1
fi

run_probe() {
  requested=$1
  adb shell am force-stop "$PACKAGE"
  adb shell run-as "$PACKAGE" rm -f files/last-command.txt
  adb shell am start -n "$PACKAGE/$ACTIVITY" --es probe "$requested" >/dev/null
  attempt=0
  while [ "$attempt" -lt 45 ]; do
    sleep 1
    if adb shell run-as "$PACKAGE" test -f files/last-command.txt; then
      adb exec-out run-as "$PACKAGE" cat files/last-command.txt > "$RESULT"
      cat "$RESULT"
      return 0
    fi
    attempt=$((attempt + 1))
  done
  printf 'Timed out waiting for %s.\n' "$requested" >&2
  exit 1
}

run_probe node
grep -Fq '"platform":"android"' "$RESULT"
grep -Fq '"arch":"arm64"' "$RESULT"
grep -Fq 'سلام از راوا' "$RESULT"
grep -Fq '"name":"https","ok":true,"status":204' "$RESULT"
grep -Fq '"name":"dns-resolve4","ok":true' "$RESULT"
grep -Fq '"name":"child-process-shell","ok":true,"value":"rava-shell"' "$RESULT"
grep -Fq '"name":"complete","ok":true' "$RESULT"
grep -Fq '[exit] 0' "$RESULT"
FIRST_RESTART=$(sed -n 's/.*"name":"restart","count":\([0-9][0-9]*\).*/\1/p' "$RESULT")

run_probe node
SECOND_RESTART=$(sed -n 's/.*"name":"restart","count":\([0-9][0-9]*\).*/\1/p' "$RESULT")
if [ -z "$FIRST_RESTART" ] || [ "$SECOND_RESTART" -ne $((FIRST_RESTART + 1)) ]; then
  printf 'Restart counter did not increment: %s -> %s\n' "$FIRST_RESTART" "$SECOND_RESTART" >&2
  exit 1
fi

run_probe gemini-version
grep -Fxq '0.59.0' "$RESULT"
grep -Fq '[exit] 0' "$RESULT"
printf 'Node/Gemini device smoke test passed; APK SHA-256 %s.\n' "$LOCAL_SHA"
