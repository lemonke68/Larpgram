#!/bin/bash
# Copyright (c) 2026 Larpgram.
# Repeatable perf scenarios on a real device. See docs/perf.md.
# Usage: tools/larpgram/perf/run.sh <scenario> [runs]
#   start      cold start, am start -W (TotalTime) x runs
#   list       fling the chat list down/up (home screen must be open)
#   folders    swipe between folders left/right (home screen, folders present)
#   timeline   fling an open chat up/down
#   chat       open/close the first chat row
# Frame stats come from dumpsys gfxinfo framestats, parsed by gfx.py.
set -u
PKG=${PKG:-ru.mangokokos.larpgram.perf}
ACT=io.element.android.x.MainActivity
export ANDROID_SERIAL=${ANDROID_SERIAL:-SBXDU19428000351}
DIR=$(cd "$(dirname "$0")" && pwd)
RUNS=${2:-3}
W=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1 | cut -dx -f1)
H=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1 | cut -dx -f2)
CX=$((W / 2))

reset() { adb shell dumpsys gfxinfo "$PKG" reset >/dev/null; }
stats() { adb shell dumpsys gfxinfo "$PKG" framestats | python3 "$DIR/gfx.py" "$1"; }

case "$1" in
  start)
    for i in $(seq 1 "$RUNS"); do
      adb shell am force-stop "$PKG"; sleep 2
      adb shell am start -W -n "$PKG/$ACT" | grep -E "TotalTime" | sed "s/^/run $i /"
      sleep 6
    done ;;
  list|timeline)
    for i in $(seq 1 "$RUNS"); do
      reset
      for k in 1 2 3; do adb shell input swipe $CX $((H * 3 / 4)) $CX $((H / 4)) 120; sleep 0.8; done
      for k in 1 2 3; do adb shell input swipe $CX $((H / 4)) $CX $((H * 3 / 4)) 120; sleep 0.8; done
      sleep 1; stats "$1#$i"
    done ;;
  folders)
    for i in $(seq 1 "$RUNS"); do
      reset
      for k in 1 2; do adb shell input swipe $((W * 3 / 5)) $((H / 2)) $((W / 5)) $((H / 2)) 180; sleep 1.2; done
      for k in 1 2; do adb shell input swipe $((W * 2 / 5)) $((H / 2)) $((W * 4 / 5)) $((H / 2)) 180; sleep 1.2; done
      sleep 1; stats "folders#$i"
    done ;;
  chat)
    ROW_Y=${ROW_Y:-$((H * 28 / 100))}
    for i in $(seq 1 "$RUNS"); do
      reset
      for k in 1 2 3; do adb shell input tap $CX "$ROW_Y"; sleep 1.5; adb shell input keyevent KEYCODE_BACK; sleep 1.5; done
      stats "chat#$i"
    done ;;
  *) echo "unknown scenario"; exit 1 ;;
esac
