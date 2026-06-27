#!/usr/bin/env python3
"""
Synthesizes the RuOS "plug-in" charging chime (res/raw/ruos_charge.wav) — a soft, bright
two-note rise (like iOS's connect confirmation), bell-like with a gentle attack and a long
decay. Deterministic; no external audio assets. 44.1 kHz, 16-bit mono.

Run:  python3 vendor/ruos/tools/gen_charge_sound.py
Output: frameworks/base/packages/SystemUI/ruos-res/raw/ruos_charge.wav
"""
import math, os, struct, wave

ROOT = os.path.normpath(os.path.join(os.path.dirname(__file__), "../../.."))
OUT = os.path.join(ROOT, "frameworks/base/packages/SystemUI/ruos-res/raw/ruos_charge.wav")
SR = 44100

def note(freq, t0, dur, amp):
    """A bell-ish note: fundamental + 2nd partial, soft attack, exponential decay."""
    out = []
    n = int(dur * SR)
    for i in range(n):
        t = i / SR
        env = (1 - math.exp(-t / 0.006)) * math.exp(-t / (dur * 0.5))   # attack + decay
        s = (math.sin(2 * math.pi * freq * t)
             + 0.35 * math.sin(2 * math.pi * freq * 2 * t)
             + 0.12 * math.sin(2 * math.pi * freq * 3 * t))
        out.append((t0 + t, amp * env * s))
    return out

def main():
    # Two rising notes: E5 → A5, the second overlapping the tail of the first.
    events = note(659.25, 0.00, 0.42, 0.45) + note(880.00, 0.11, 0.55, 0.55)
    total = int(0.70 * SR)
    buf = [0.0] * total
    for t, v in events:
        idx = int(t * SR)
        if 0 <= idx < total:
            buf[idx] += v
    peak = max(abs(x) for x in buf) or 1.0
    norm = 0.89 / peak
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with wave.open(OUT, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, x * norm)) * 32767)) for x in buf))
    print(f"wrote {OUT}  ({total/SR:.2f}s, peak {int(peak*norm*100)}%)")

if __name__ == "__main__":
    main()
