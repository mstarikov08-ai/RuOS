#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
RuOS offline-charger graphics (the screen shown when the phone is powered off and
plugged in, or pressed-while-dead). Rendered by healthd/charger via minui.

Produces:
  charger/battery_fail.png   — phone is critically dead: empty battery outline with a
                               thin red sliver at the bottom + «Подключите к зарядке».
  charger/battery_scale.png  — vertical filmstrip of 5 charging frames: green fill that
                               grows with capacity, white lightning bolt, «Зарядка».
  charger/animation.txt      — maps capacity ranges → frames (healthd animation format).

These are wired to /system/etc/res/images/charger/ and /system/etc/res/values/charger/
in ruos.mk. See docs/VisualIdentity.md for the healthd integration caveats.

Run: python3 vendor/ruos/tools/gen_charger.py
"""
import os
from PIL import Image, ImageDraw, ImageFont

ACCENT = (217, 79, 61)
GREEN = (52, 199, 89)
WHITE = (255, 255, 255)
DIM = (120, 120, 124)
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.normpath(os.path.join(HERE, "../charger"))
os.makedirs(OUT, exist_ok=True)

FW, FH = 640, 820          # per-frame canvas
BW, BH = 300, 500          # battery body
BX = (FW - BW)//2
BY = 150
CAPW, CAPH = 110, 26       # terminal cap
RAD = 46

FONTS = [
    os.path.join(HERE, "../prebuilts/fonts/GolosText/GolosText-Medium.ttf"),
    "/mnt/skills/examples/canvas-design/canvas-fonts/Jura-Light.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
]
def font(sz):
    for p in FONTS:
        if os.path.exists(p):
            try: return ImageFont.truetype(p, sz)
            except Exception: pass
    return ImageFont.load_default()

F_LABEL = font(46)

def centered(d, text, y, fnt, fill):
    b = d.textbbox((0, 0), text, font=fnt); w = b[2]-b[0]
    d.text(((FW-w)//2, y), text, font=fnt, fill=fill)

def battery_outline(d, color=WHITE, width=10):
    d.rounded_rectangle([BX, BY, BX+BW, BY+BH], radius=RAD, outline=color, width=width)
    cx = BX + (BW-CAPW)//2
    d.rounded_rectangle([cx, BY-CAPH+2, cx+CAPW, BY+6], radius=10, fill=color)

def bolt(d, cx, cy, s, fill):
    pts = [(cx-0.18*s, cy-0.42*s), (cx+0.16*s, cy-0.42*s), (cx-0.02*s, cy-0.05*s),
           (cx+0.20*s, cy-0.05*s), (cx-0.16*s, cy+0.46*s), (cx-0.02*s, cy+0.02*s),
           (cx-0.24*s, cy+0.02*s)]
    d.polygon(pts, fill=fill)

def frame_fail():
    img = Image.new("RGB", (FW, FH), (0, 0, 0)); d = ImageDraw.Draw(img)
    battery_outline(d, WHITE, 10)
    # thin red sliver at the very bottom — critically low
    pad = 26; sliver = 26
    d.rounded_rectangle([BX+pad, BY+BH-pad-sliver, BX+BW-pad, BY+BH-pad],
                        radius=10, fill=ACCENT)
    centered(d, "Подключите к зарядке", BY+BH+60, F_LABEL, WHITE)
    return img

def frame_charge(level):
    """level 0..1 fill fraction."""
    img = Image.new("RGB", (FW, FH), (0, 0, 0)); d = ImageDraw.Draw(img)
    battery_outline(d, WHITE, 10)
    pad = 26
    inner_top = BY+pad; inner_bot = BY+BH-pad
    fillh = int((inner_bot-inner_top)*level)
    if fillh > 12:
        d.rounded_rectangle([BX+pad, inner_bot-fillh, BX+BW-pad, inner_bot],
                            radius=14, fill=GREEN)
    bolt(d, BX+BW//2, BY+BH//2, 220, WHITE)
    centered(d, "Зарядка", BY+BH+60, F_LABEL, GREEN)
    return img

def build():
    frame_fail().save(os.path.join(OUT, "battery_fail.png"))
    levels = [0.10, 0.30, 0.52, 0.74, 0.96]
    strip = Image.new("RGB", (FW, FH*len(levels)), (0, 0, 0))
    for i, lv in enumerate(levels):
        strip.paste(frame_charge(lv), (0, i*FH))
    strip.save(os.path.join(OUT, "battery_scale.png"))
    with open(os.path.join(OUT, "animation.txt"), "w") as fh:
        fh.write("# RuOS charger animation (healthd format)\n")
        fh.write("animation: 0 0 4\n")
        fh.write("fail: /system/etc/res/images/charger/battery_fail.png\n")
        # frame: <display_ms> <min_capacity> <max_capacity>
        fh.write("frame: 800 0 19\n")
        fh.write("frame: 800 20 39\n")
        fh.write("frame: 800 40 59\n")
        fh.write("frame: 800 60 79\n")
        fh.write("frame: 800 80 100\n")
    print(f"charger -> {OUT}/battery_fail.png, battery_scale.png ({len(levels)} frames), animation.txt")

if __name__ == "__main__":
    build()
