#!/usr/bin/env python3
# Copyright (c) 2026 Larpgram.
"""Summarise `dumpsys gfxinfo <pkg>` for the app's window(s).

Usage: adb shell dumpsys gfxinfo <pkg> | gfx.py [label]

Uses the per-window HISTOGRAM (every frame since `gfxinfo reset`), not the PROFILEDATA rows:
EMUI on the Honor keeps only ~10 of those. Windows with no frames are skipped, the others are
summed. Reports frames, counts over 33/50 ms, late time, % of frames over 16 ms, p50/p90/p99 and Android's own "janky" count.
"""
import re
import sys


def windows(text):
    """Yield (histogram dict ms->count, janky count) per window section."""
    parts = re.split(r"^Window: ", text, flags=re.M)[1:]
    for part in parts:
        hist_line = re.search(r"^HISTOGRAM: (.*)$", part, flags=re.M)
        janky = re.search(r"^Janky frames: (\d+)", part, flags=re.M)
        if not hist_line:
            continue
        hist = {}
        for item in hist_line.group(1).split():
            ms, count = item.split("=")
            hist[int(ms.rstrip("ms"))] = int(count)
        yield hist, int(janky.group(1)) if janky else 0


def percentile(hist, p):
    total = sum(hist.values())
    if total == 0:
        return 0
    target = p / 100 * total
    acc = 0
    for ms in sorted(hist):
        acc += hist[ms]
        if acc >= target:
            return ms
    return max(hist)


def summary(text):
    merged, janky = {}, 0
    for hist, j in windows(text):
        if sum(hist.values()) == 0:
            continue
        for ms, c in hist.items():
            merged[ms] = merged.get(ms, 0) + c
        janky += j
    n = sum(merged.values())
    over = sum(c for ms, c in merged.items() if ms > 16)
    over33 = sum(c for ms, c in merged.items() if ms > 33)
    over50 = sum(c for ms, c in merged.items() if ms > 50)
    # Time lost to late frames: how far past one vsync (16.7 ms) each slow frame ran.
    late_ms = sum(c * (ms - 16.7) for ms, c in merged.items() if ms > 16)
    return {
        "frames": n,
        ">33ms": over33,
        ">50ms": over50,
        "late_ms": int(late_ms),
        "over16%": round(100 * over / n, 1) if n else 0,
        "p50": percentile(merged, 50),
        "p90": percentile(merged, 90),
        "p99": percentile(merged, 99),
        ">16ms": over,
        "android_janky%": round(100 * janky / n, 1) if n else 0,
    }


if __name__ == "__main__":
    s = summary(sys.stdin.read())
    label = sys.argv[1] if len(sys.argv) > 1 else ""
    print(label, " ".join(f"{k}={v}" for k, v in s.items()))
