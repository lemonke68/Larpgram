#!/bin/bash
# Copyright (c) 2026 Larpgram.
# Full scenario pass on the Honor (see docs/perf.md). Expects the app open on the chat list
# of an account with folders, row 1 = a chat to open, and a chat named $TIMELINE_CHAT.
set -euo pipefail
PKG=${PKG:-ru.mangokokos.larpgram.perf}
export ANDROID_SERIAL=${ANDROID_SERIAL:-SBXDU19428000351}
DIR=$(cd "$(dirname "$0")" && pwd)
TIMELINE_CHAT=${TIMELINE_CHAT:-test-channel}
RUNS=${RUNS:-3}
reset() { adb shell dumpsys gfxinfo "$PKG" reset >/dev/null; }
stats() { adb shell dumpsys gfxinfo "$PKG" framestats | python3 "$DIR/gfx.py" "$1"; }
tap_text() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null
  b=$(adb exec-out cat /sdcard/ui.xml | tr '>' '\n' | grep -F "text=\"$1\"" | head -1 | grep -o 'bounds="[^"]*"' | grep -oE '[0-9]+' | tr '\n' ' ')
  set -- $b; adb shell input tap $(( ($1+$3)/2 )) $(( ($2+$4)/2 ))
}
"$DIR/run.sh" list "$RUNS"
"$DIR/run.sh" folders "$RUNS"
adb shell input swipe 300 1100 900 1100 180; sleep 2
CHAT_OPEN_SECS=2.5 CHAT_CLOSE_SECS=2 "$DIR/run.sh" chat "$RUNS"
tap_text "$TIMELINE_CHAT"; sleep 3
for k in 1 2 3 4 5 6; do adb shell input swipe 540 700 540 1900 100; sleep 0.6; done
"$DIR/run.sh" timeline "$RUNS"
for i in $(seq 1 "$RUNS"); do
  reset
  for k in 1 2 3; do adb shell "input motionevent DOWN 180 1300; sleep 1.0; input motionevent UP 180 1300"; sleep 1.5; adb shell input keyevent KEYCODE_BACK; sleep 1.5; done
  stats "menu#$i"
done
adb shell input tap 400 2195; sleep 1.5
for i in $(seq 1 "$RUNS"); do
  reset
  for w in perf test typing in composer here; do adb shell input text "$w%s"; done
  sleep 1; stats "type#$i"
  # Clear outside the measured interval in one input process (same 45 delete keys).
  DELETE_KEYS=()
  for k in {1..45}; do DELETE_KEYS+=(67); done
  adb shell input keyevent "${DELETE_KEYS[@]}"
done
adb shell input keyevent KEYCODE_BACK; sleep 1; adb shell input keyevent KEYCODE_BACK; sleep 1.5
adb shell dumpsys meminfo "$PKG" | grep -E "TOTAL:|Native Heap:|Java Heap:|Graphics:" | sed 's/^/mem-after-suite /'
