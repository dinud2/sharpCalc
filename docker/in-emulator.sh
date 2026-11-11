#!/usr/bin/env bash
# Runs inside the emulator container.
set -e
export ANDROID_AVD_HOME=$HOME/.android/avd
mkdir -p "$ANDROID_AVD_HOME"
if ! avdmanager list avd 2>/dev/null | grep -q pixel8; then
  echo no | avdmanager create avd -n pixel8 -k "system-images;android-34;default;x86_64" -d pixel_7 --force >/dev/null
fi
rm -f $ANDROID_AVD_HOME/pixel8.avd/*.lock; emulator -avd pixel8 -no-window -no-audio -no-boot-anim -no-snapshot -gpu ${EMU_GPU:-swiftshader_indirect} -no-metrics >/tmp/emulator.log 2>&1 &
timeout 120 adb wait-for-device
for i in $(seq 1 180); do
  [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && break
  sleep 2
done
[ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ] || { echo "emulator did not boot"; tail -20 /tmp/emulator.log; exit 1; }
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -W -n app.quadline.calc/.MainActivity >/dev/null
sleep 3
bash "$1"
adb emu kill >/dev/null 2>&1 || true
