#!/usr/bin/env bash
# Run interaction assertions at three viewport sizes, including enlarged fonts and dark mode.
set -euo pipefail
restore() {
  adb shell wm size reset >/dev/null
  adb shell wm density reset >/dev/null
  adb shell settings put system font_scale 1.0
  adb shell cmd uimode night no
}
trap restore EXIT
adb logcat -c
for profile in compact standard large; do
  case "$profile" in
    compact) size=720x1280; density=320; font=1.3; night=yes ;;
    standard) size=1080x2400; density=420; font=1.0; night=no ;;
    large) size=1440x2560; density=480; font=1.0; night=no ;;
  esac
  adb shell wm size "$size"
  adb shell wm density "$density"
  adb shell settings put system font_scale "$font"
  adb shell cmd uimode night "$night"
  adb shell am force-stop app.quadline.calc
  adb shell am start -W -n app.quadline.calc/.MainActivity >/dev/null
  sleep 2
  echo "Checking $profile ($size, density $density, font $font, dark $night)"
  SHOTS_DIR="dist/shots/$profile" bash docker/shots.sh
done
