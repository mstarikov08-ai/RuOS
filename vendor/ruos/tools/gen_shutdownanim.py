#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
RuOS shutdown animation — graceful power-off.

Sequence (plays once, ≈1.9s):
  - screen already fading to black; the RuOS wordmark fades in with a soft glow
  - holds briefly
  - wordmark fades out cleanly → black → power off

Android plays /system/media/shutdownanimation.zip on shutdown (same format as the
boot animation). A *restart* shows this shutdown animation, then replays the normal
bootanimation.zip — there is no separate "restart" asset in AOSP.

Output: vendor/ruos/bootanimation/shutdownanimation.zip
Run: python3 vendor/ruos/tools/gen_shutdownanim.py
"""
import os, shutil, subprocess
from PIL import Image
import gen_bootanim as gb   # reuse the exact same wordmark / shimmer / layout

ROOT = gb.ROOT

def build():
    p0 = os.path.join(ROOT, "sd_part0")
    if os.path.exists(p0): shutil.rmtree(p0)
    os.makedirs(p0)

    i = 0
    # fade in (~0.6s)
    for k in range(18):
        t = k/17; img = gb.frame()
        gb.draw_wordmark(img, alpha=t, glow=0.35*t)
        gb.draw_shimmer(img, k/18, intensity=0.3*t)
        gb.save(img, p0, i); i += 1
    # hold (~0.5s)
    for k in range(15):
        img = gb.frame(); gb.draw_wordmark(img, 1.0, glow=0.35)
        gb.draw_shimmer(img, (18+k)/33, 0.35); gb.save(img, p0, i); i += 1
    # fade out (~0.8s)
    for k in range(24):
        t = 1.0 - k/23; img = gb.frame()
        gb.draw_wordmark(img, alpha=t, glow=0.35*t)
        gb.draw_shimmer(img, (33+k)/57, 0.3*t); gb.save(img, p0, i); i += 1

    # bootanimation expects the folder to be named in desc.txt; use a clean tree
    final = os.path.join(ROOT, "part0")
    if os.path.exists(final): shutil.rmtree(final)
    os.rename(p0, final)
    with open(os.path.join(ROOT, "desc.txt"), "w") as fh:
        fh.write(f"{gb.W} {gb.H} {gb.FPS}\n")
        fh.write("p 1 0 part0\n")
    out = "shutdownanimation.zip"
    if os.path.exists(os.path.join(ROOT, out)): os.remove(os.path.join(ROOT, out))
    subprocess.run(["zip", "-r0", "-q", out, "desc.txt", "part0"], cwd=ROOT, check=True)
    shutil.rmtree(final); os.remove(os.path.join(ROOT, "desc.txt"))
    print(f"frames: {i}  -> {os.path.join(ROOT, out)}")

if __name__ == "__main__":
    build()
