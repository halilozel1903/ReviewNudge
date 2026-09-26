#!/usr/bin/env bash
# Shared helpers for scripts/screenshots.sh. Expects PKG to be set by the caller's environment
# or falls back to the sample's application id from sample/build.gradle.kts.
PKG="${PKG:-$(grep -oE 'applicationId = "[^"]+"' sample/build.gradle.kts | cut -d'"' -f2)}"
OUT="docs/screenshots"
mkdir -p "$OUT"

install_sample() {
  adb install -r sample/build/outputs/apk/debug/sample-debug.apk
  # Clean status bar via System UI demo mode.
  adb shell settings put global sysui_demo_allowed 1
  adb shell am broadcast -a com.android.systemui.demo -e command enter
  adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0941
  adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
  adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 -e mobile show -e level 4
  adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false
}

set_night_mode() {
  if [ "$1" = dark ]; then adb shell cmd uimode night yes; else adb shell cmd uimode night no; fi
  sleep 2
}

fresh_launch() {
  adb shell pm clear "$PKG" > /dev/null
  adb shell am start -W -n "$PKG/.MainActivity" "$@" > /dev/null
  sleep 6
}

capture() {
  adb exec-out screencap -p > "$OUT/$1.png"
  echo "Captured $1"
}
