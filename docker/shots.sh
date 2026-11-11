#!/usr/bin/env bash
# Drives the running app by tapping keys (found by their accessibility descriptions) and takes
# screenshots into dist/shots (V-6, V-7).
set -euo pipefail
shots_dir=${SHOTS_DIR:-dist/shots}
mkdir -p "$shots_dir"
snap() { sleep 0.7; adb exec-out screencap -p > "$shots_dir/$1.png"; echo "shot $1 $(stat -c %s $shots_dir/$1.png)"; }
adb shell input keyevent KEYCODE_WAKEUP; adb shell wm dismiss-keyguard; sleep 3
adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
adb shell cat /sdcard/ui.xml | sed 's/></>\n</g' > /tmp/ui.txt
echo "nodes with descriptions: $(grep -c 'content-desc="[^"]' /tmp/ui.txt)"
# tap <start of the key's description>
tap() {
  for name in "$@"; do
    line=$(grep -m1 "content-desc=\"$name[,\"]" /tmp/ui.txt || true)
    [ -n "$line" ] || { echo "no key: $name" >&2; exit 1; }
    read -r x1 y1 x2 y2 <<< "$(echo "$line" | grep -o 'bounds="[^"]*"' | grep -o '[0-9]\+' | tr '\n' ' ')"
    adb shell input tap $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 ))
    sleep 0.25
  done
}
assert_display() {
  adb shell uiautomator dump /sdcard/check.xml >/dev/null 2>&1
  adb shell cat /sdcard/check.xml | sed 's/></>\n</g' > /tmp/check.txt
  if ! grep -F 'content-desc=' /tmp/check.txt | grep -F "$1" >/dev/null; then
    echo "Display assertion failed: $1" >&2
    cat /tmp/check.txt >&2
    exit 1
  fi
  echo "PASS: $1"
}
tap home "on, clear"
snap 01_start
tap 2 plus 3 equals
assert_display '5.'
tap "on, clear"
tap 1 fraction 2 "cursor right" plus "square root" 8
snap 02_writeview_entry
tap equals
snap 03_exact_result
tap change
assert_display "3.328427125"
snap 04_change_decimal
tap "second function"
snap 05_2ndf_active
tap "second function" mode
snap 06_mode_menu
tap 1 1 2 comma 5 equals 1 2 comma 2 4 equals
assert_display "STAT"
snap 07_stat_table
tap home "open parenthesis" 5 "close parenthesis" square "cursor left" backspace
snap 08_cannot_delete
tap "on, clear" "on, clear" 1 divide 0 equals
assert_display "ERROR"
snap 09_error
tap "on, clear" alpha "open parenthesis" 0 "cursor right" 3 "cursor right" alpha recall square equals
assert_display "9."
snap 10_integral
# state survives the process being killed (N-2)
tap "on, clear" 4 2 times 2
adb shell am force-stop app.quadline.calc; sleep 1
adb shell am start -W -n app.quadline.calc/.MainActivity >/dev/null; sleep 2
snap 11_after_restart
tap equals
assert_display "84."
# A completed ANS expression must retain its answer across process death.
tap "on, clear" 2 plus 3 equals times 2 equals
assert_display "10."
adb shell am force-stop app.quadline.calc
adb shell am start -W -n app.quadline.calc/.MainActivity >/dev/null
sleep 2
assert_display "10."
snap 12_result_after_restart
if adb logcat -d -s AndroidRuntime:E | grep -q FATAL; then
  adb logcat -d -s AndroidRuntime:E
  exit 1
fi
echo "PASS: no fatal exceptions"
