#!/usr/bin/env python3
# Copyright (c) 2026 Larpgram.
"""Parse `dumpsys gfxinfo <pkg> framestats` and print frame-time stats.

Usage: adb shell dumpsys gfxinfo <pkg> framestats | gfx.py [label]
Frame time = FRAME_COMPLETED - INTENDED_VSYNC (ns), as in the Android docs.
"""
import sys

def parse(text):
    frames, header = [], None
    for line in text.splitlines():
        line = line.strip()
        if line.startswith("Flags,"):
            header = line.rstrip(",").split(",")
            continue
        if header and line and line[0].isdigit():
            vals = line.rstrip(",").split(",")
            if len(vals) < len(header):
                continue
            row = dict(zip(header, vals))
            if row.get("Flags") != "0":  # skip non-normal frames (first draw, resize...)
                continue
            t = (int(row["FrameCompleted"]) - int(row["IntendedVsync"])) / 1e6
            if 0 < t < 2000:
                frames.append(t)
        elif header and line.startswith("---PROFILEDATA---") is False and not line:
            pass
    return frames

def pct(xs, p):
    if not xs:
        return 0.0
    s = sorted(xs)
    return s[min(len(s) - 1, int(round(p / 100 * (len(s) - 1))))]

def summary(frames, text):
    janky = None
    for line in text.splitlines():
        if "Janky frames:" in line and janky is None:
            janky = line.split(":", 1)[1].strip()
    n = len(frames)
    over = sum(1 for f in frames if f > 16.7)
    return {
        "frames": n,
        "jank%": round(100 * over / n, 1) if n else 0,
        "p50": round(pct(frames, 50), 1),
        "p90": round(pct(frames, 90), 1),
        "p99": round(pct(frames, 99), 1),
        ">16ms": over,
        "gfx_janky": janky,
    }

if __name__ == "__main__":
    text = sys.stdin.read()
    s = summary(parse(text), text)
    label = sys.argv[1] if len(sys.argv) > 1 else ""
    print(label, " ".join(f"{k}={v}" for k, v in s.items()))
