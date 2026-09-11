#!/usr/bin/env bash
set -eu

if ! command -v adb >/dev/null 2>&1; then
  echo "adb was not found. Install Android platform-tools first." >&2
  exit 1
fi

device_count=$(adb devices | awk 'NR > 1 && $2 == "device" { count++ } END { print count + 0 }')
if [ "$device_count" -ne 1 ]; then
  echo "Expected exactly one authorized Android device; found $device_count." >&2
  adb devices -l
  exit 1
fi

echo "Device: $(adb shell getprop ro.product.manufacturer) $(adb shell getprop ro.product.model)"
echo "Android: $(adb shell getprop ro.build.version.release) (SDK $(adb shell getprop ro.build.version.sdk))"
echo "ABI: $(adb shell getprop ro.product.cpu.abi)"
echo "Termux package:"
adb shell dumpsys package com.termux 2>/dev/null | awk '
  /versionName=|versionCode=|firstInstallTime=|lastUpdateTime=/ { print "  " $0 }
' || true

if adb shell pm path com.termux >/dev/null 2>&1; then
  echo "Termux is installed."
else
  echo "Termux is not installed. Install the current F-Droid/GitHub release before bootstrap."
fi
