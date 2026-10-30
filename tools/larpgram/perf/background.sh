#!/bin/bash
# Copyright (c) 2026 Larpgram.
# CPU time the app uses while in the background: HOME, wait $SECS, read utime+stime of its process.
set -u
PKG=${PKG:-ru.mangokokos.larpgram.perf}
SECS=${SECS:-300}
export ANDROID_SERIAL=${ANDROID_SERIAL:-SBXDU19428000351}
adb shell am start -n "$PKG/io.element.android.x.MainActivity" >/dev/null; sleep 8
adb shell input keyevent KEYCODE_HOME; sleep 5
PID=$(adb shell pidof "$PKG" | tr -d '\r')
ticks() { adb shell cat /proc/"$PID"/stat | awk '{print $14 + $15}'; }
threads() { adb shell "for t in /proc/$PID/task/*; do echo \$(cat \$t/comm 2>/dev/null)'|'\$(awk '{print \$14+\$15}' \$t/stat 2>/dev/null); done"; }
A=$(ticks); TA=$(threads); sleep "$SECS"; B=$(ticks); TB=$(threads)
# USER_HZ is 100 on Android.
CPU_MS=$(( (B - A) * 10 ))
echo "background ${SECS}s: cpu=${CPU_MS} ms, $(( CPU_MS / SECS / 10 )).$(( CPU_MS / SECS % 10 ))% of one core"
# Busiest threads (same name summed), ms.
python3 - "$TA" "$TB" <<'PY'
import sys, collections
def parse(s):
    d = collections.Counter()
    for line in s.splitlines():
        if '|' in line:
            name, v = line.rsplit('|', 1)
            if v.strip().isdigit():
                d[name.strip()] += int(v)
    return d
a, b = parse(sys.argv[1]), parse(sys.argv[2])
diff = {k: (b[k] - a.get(k, 0)) * 10 for k in b}
for k, v in sorted(diff.items(), key=lambda x: -x[1])[:8]:
    if v > 0:
        print(f"  thread {k}: {v} ms")
PY
adb shell dumpsys meminfo "$PKG" | grep -E "TOTAL:" | sed 's/^/background mem /'
