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

printf '%s\n' 'Device facts:'
adb shell getprop ro.build.version.release
adb shell getprop ro.build.version.sdk
adb shell getprop ro.product.cpu.abilist
adb shell getconf PAGESIZE
adb shell df -h /data

adb install -r "$APK"
adb shell am force-stop "$PACKAGE"
adb logcat -c
adb shell am start -n "$PACKAGE/$ACTIVITY"

printf '%s\n' 'The probe is open. Run the embedded-native and available provider buttons, then inspect/copy the visible stdout and stderr.'
printf '%s\n' 'For crash diagnostics: adb logcat -d AndroidRuntime:E libc:F DEBUG:F *:S'
