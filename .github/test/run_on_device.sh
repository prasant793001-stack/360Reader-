#!/usr/bin/env bash
# Runs inside the emulator job: install, open each sample, capture results.
set -x
mkdir -p results
adb shell getprop ro.product.cpu.abilist > results/abilist.txt
adb install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk > results/install.txt 2>&1
APP=com.reader360.app
for f in sample.pptx sample.xlsx; do
  adb push "$f" "/data/local/tmp/$f"
  adb shell run-as $APP sh -c "mkdir -p files && cp /data/local/tmp/$f files/$f"
done
open_file() {
  local name="$1" mime="$2" wait="$3"
  adb logcat -c
  adb shell am force-stop $APP
  adb shell am start -W -a android.intent.action.VIEW \
    -d "file:///data/user/0/$APP/files/$name" -t "$mime" -n $APP/.MainActivity
  sleep "$wait"
  adb exec-out screencap -p > "results/$name.png"
  adb logcat -d -v time > "results/$name.logcat.txt"
  adb shell run-as $APP ls -la cache/rendered > "results/$name.rendered.txt" 2>&1
}
open_file sample.pptx application/vnd.openxmlformats-officedocument.presentationml.presentation 240
open_file sample.xlsx application/vnd.openxmlformats-officedocument.spreadsheetml.sheet 120
# Short summary of anything that looks like a crash.
grep -hiE "FATAL|Fatal signal|AndroidRuntime|LibreOfficeKit|libc  |DEBUG   |abort" results/*.logcat.txt | head -n 200 > results/summary.txt
exit 0
