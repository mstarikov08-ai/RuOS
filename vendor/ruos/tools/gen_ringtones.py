#!/usr/bin/env python3
"""
Synthesizes the RuOS branded sound set — a ringtone, a notification tone and an alarm —
so the ROM ships its own sounds instead of falling back to the base LineageOS ones.
Deterministic, no external assets. 44.1 kHz, 16-bit mono WAV.

Run:  python3 vendor/ruos/tools/gen_ringtones.py
Outputs under vendor/ruos/media/audio/{ringtones,notifications,alarms}/.
"""
import math, os, struct, wave

ROOT = os.path.normpath(os.path.join(os.path.dirname(__file__), "../../.."))
BASE = os.path.join(ROOT, "vendor/ruos/media/audio")
SR = 44100

def bell(freq, dur, amp=0.5, decay=0.35):
    """Marimba/bell-ish note: fundamental + partials, soft attack, exp decay."""
    out = []
    n = int(dur * SR)
    for i in range(n):
        t = i / SR
        env = (1 - math.exp(-t / 0.004)) * math.exp(-t / (dur * decay))
        s = (math.sin(2*math.pi*freq*t)
             + 0.4 * math.sin(2*math.pi*freq*2*t)
             + 0.18 * math.sin(2*math.pi*freq*3.01*t))
        out.append(amp * env * s)
    return out

def mix_at(buf, samples, start_s):
    idx = int(start_s * SR)
    for k, v in enumerate(samples):
        j = idx + k
        if 0 <= j < len(buf): buf[j] += v

def write(rel, buf):
    peak = max(1e-9, max(abs(x) for x in buf))
    norm = 0.9 / peak
    path = os.path.join(BASE, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with wave.open(path, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, x*norm))*32767)) for x in buf))
    print(f"  {rel}  ({len(buf)/SR:.1f}s)")

# ── Ringtone: a warm rising arpeggio motif, repeated (loopable) ────────────────
def ringtone():
    total = int(6.0 * SR); buf = [0.0]*total
    # A major-ish pentatonic climb: A4 C#5 E5 A5, motif every 1.5 s
    notes = [440.00, 554.37, 659.25, 880.00]
    for rep in range(4):
        base = rep * 1.5
        for i, f in enumerate(notes):
            mix_at(buf, bell(f, 0.55, amp=0.5, decay=0.4), base + i*0.16)
        mix_at(buf, bell(587.33, 0.7, amp=0.45, decay=0.5), base + 0.72)   # resolve
    write("ringtones/RuOS_Signature.wav", buf)

# ── Notification: a gentle two-note ding ───────────────────────────────────────
def notification():
    total = int(1.1 * SR); buf = [0.0]*total
    mix_at(buf, bell(880.00, 0.5, amp=0.55, decay=0.5), 0.00)
    mix_at(buf, bell(1174.66, 0.6, amp=0.55, decay=0.5), 0.13)
    write("notifications/RuOS_Note.wav", buf)

# ── Alarm: an insistent repeating triad pulse ──────────────────────────────────
def alarm():
    total = int(4.0 * SR); buf = [0.0]*total
    for rep in range(8):
        base = rep * 0.5
        mix_at(buf, bell(659.25, 0.22, amp=0.6, decay=0.5), base)
        mix_at(buf, bell(659.25, 0.22, amp=0.6, decay=0.5), base + 0.24)
    write("alarms/RuOS_Alarm.wav", buf)

if __name__ == "__main__":
    print("Generating RuOS sound set:")
    ringtone(); notification(); alarm()
