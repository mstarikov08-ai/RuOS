#!/usr/bin/env python3
"""
Checks RuOS binary/format assets that fail *silently* if malformed — a bad
bootanimation.zip or charger filmstrip just shows a black screen, a broken WAV is a
silent alarm. Non-zero exit on any problem (CI-friendly).

Run:  python3 vendor/ruos/tools/verify/verify_assets.py
"""
import os, sys, zipfile, wave, audioop, glob

ROOT = os.path.normpath(os.path.join(os.path.dirname(__file__), "../../../.."))
fails = 0

def fail(msg):
    global fails; print("  FAIL:", msg); fails += 1

# ── boot / shutdown animation zips ────────────────────────────────────────────
def check_bootzip(rel):
    path = os.path.join(ROOT, rel)
    print(f"== {rel} ==")
    if not os.path.exists(path): fail("missing"); return
    z = zipfile.ZipFile(path)
    names = z.namelist()
    desc = z.read("desc.txt").decode().strip().splitlines()
    hdr = desc[0].split()
    if not (len(hdr) == 3 and all(x.isdigit() for x in hdr)): fail(f"bad desc header {desc[0]!r}")
    for line in desc[1:]:
        folder = line.split()[-1]
        frames = [n for n in names if n.startswith(folder + "/") and n.endswith(".png")]
        if not frames: fail(f"part '{folder}' has no png frames")
        else: print(f"  {folder}: {len(frames)} frames")
    methods = set(i.compress_type for i in z.infolist() if i.filename.endswith(".png"))
    if not methods <= {zipfile.ZIP_STORED}: fail(f"frames not STORED (methods={methods}) — boot may not play")
    else: print("  frames STORED: OK")

check_bootzip("vendor/ruos/bootanimation/bootanimation.zip")
check_bootzip("vendor/ruos/bootanimation/shutdownanimation.zip")

# ── charger filmstrip ─────────────────────────────────────────────────────────
print("== charger ==")
try:
    from PIL import Image
    scale = Image.open(os.path.join(ROOT, "vendor/ruos/charger/battery_scale.png"))
    Image.open(os.path.join(ROOT, "vendor/ruos/charger/battery_fail.png"))
    nframes = sum(1 for l in open(os.path.join(ROOT, "vendor/ruos/charger/animation.txt"))
                  if l.startswith("frame:"))
    if scale.size[1] % nframes != 0:
        fail(f"battery_scale height {scale.size[1]} not divisible by {nframes} frames")
    else:
        print(f"  filmstrip {scale.size} / {nframes} frames = {scale.size[1]//nframes}px each: OK")
except Exception as e:
    fail(f"charger: {e}")

# ── synthesized audio ─────────────────────────────────────────────────────────
print("== audio (res/raw/*.wav) ==")
VALID_RATES = (16000, 22050, 44100, 48000)
WAVS = (glob.glob(os.path.join(ROOT, "packages/apps/**/res/raw/*.wav"), recursive=True) +
        glob.glob(os.path.join(ROOT, "frameworks/base/packages/SystemUI/ruos-res/raw/*.wav")) +
        glob.glob(os.path.join(ROOT, "vendor/ruos/media/audio/**/*.wav"), recursive=True))
for w in sorted(WAVS):
    try:
        wf = wave.open(w, 'rb')
        sr, sw, n = wf.getframerate(), wf.getsampwidth(), wf.getnframes()
        dur = n / sr if sr else 0
        peak = audioop.max(wf.readframes(n), sw) if n else 0
        peak_pct = 100 * peak / ((1 << (sw * 8 - 1)) - 1)
        name = os.path.basename(w)
        if sr not in VALID_RATES: fail(f"{name}: odd sample rate {sr}")
        elif dur < 0.3: fail(f"{name}: too short {dur:.2f}s")
        elif peak_pct < 5: fail(f"{name}: near-silent ({peak_pct:.1f}%)")
        elif peak_pct > 99.9: fail(f"{name}: clipping")
        else: print(f"  {name}: {sr}Hz {dur:.1f}s peak {peak_pct:.0f}%: OK")
        wf.close()
    except Exception as e:
        fail(f"{os.path.basename(w)}: {e}")

print(f"\n{'ALL ASSET CHECKS PASSED' if not fails else f'{fails} PROBLEM(S)'}")
sys.exit(1 if fails else 0)
