#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
RuOS boot animation — calm, premium, Apple-like.

Sequence (≈3.3s intro, then a steady hold that loops until boot completes):
  part0 (plays once):
    - black screen; 'Ru' (white) + 'OS' (accent #D94F3D) fade in with a soft glow
    - the wordmark gently pulses once
    - an accent rule draws underneath, left → right
    - a subtle particle shimmer drifts across
  part1 (loops, count 0):
    - the finished wordmark holds with a faint breathing glow + slow shimmer
    - SurfaceFlinger fades this out to the lock screen when the system is ready

Output: vendor/ruos/bootanimation/bootanimation.zip  (stored, not compressed —
required by Android's bootanimation). Loose frame dirs are removed after zipping.

Run: python3 vendor/ruos/tools/gen_bootanim.py
"""
import os, math, shutil, subprocess, random
from PIL import Image, ImageDraw, ImageFont, ImageFilter

W, H, FPS = 1080, 2400, 30
ACCENT = (217, 79, 61)
WHITE = (255, 255, 255)
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "../bootanimation"))
FONT_CANDIDATES = [
    os.path.join(HERE, "../prebuilts/fonts/GolosText/GolosText-Thin.ttf"),
    os.path.join(HERE, "../prebuilts/fonts/GolosText/GolosText-Light.ttf"),
    "/mnt/skills/examples/canvas-design/canvas-fonts/Jura-Light.ttf",
    "/opt/rbenv/versions/3.3.6/lib/ruby/3.3.0/rdoc/generator/template/darkfish/fonts/Lato-Light.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
]
FSIZE = 168
random.seed(42)

def load_font():
    for p in FONT_CANDIDATES:
        if os.path.exists(p):
            try: return ImageFont.truetype(p, FSIZE), p
            except Exception: pass
    return ImageFont.load_default(), "default"

FONT, FONT_USED = load_font()

# Pre-compute layout of "Ru" + "OS"
_tmp = ImageDraw.Draw(Image.new("RGB", (10, 10)))
def tw(s): b = _tmp.textbbox((0, 0), s, font=FONT); return b[2]-b[0], b[3]-b[1]
RU_W, TXT_H = tw("Ru"); OS_W, _ = tw("OS"); FULL_W = tw("RuOS")[0]
CX = W//2; BASE_Y = H//2 - 120
TEXT_X = CX - FULL_W//2
GAP = tw("Ru")[0]  # x advance for 'Ru' before 'OS'

# Stable shimmer particle field
PARTICLES = [(random.uniform(0.1, 0.9)*W, random.uniform(0.30, 0.62)*H,
              random.uniform(1.5, 3.5), random.uniform(0, 2*math.pi)) for _ in range(46)]

def draw_wordmark(img, alpha, scale=1.0, glow=0.0):
    """Render Ru(white)+OS(accent) centred, with optional glow + scale pulse."""
    sub = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ds = ImageDraw.Draw(sub)
    a = int(255*alpha)
    ds.text((TEXT_X, BASE_Y), "Ru", font=FONT, fill=WHITE+(a,))
    ds.text((TEXT_X+GAP, BASE_Y), "OS", font=FONT, fill=ACCENT+(a,))
    if scale != 1.0:
        nw, nh = int(W*scale), int(H*scale)
        sub = sub.resize((nw, nh), Image.LANCZOS)
        ox, oy = (W-nw)//2, (H-nh)//2
        tmp = Image.new("RGBA", (W, H), (0, 0, 0, 0)); tmp.paste(sub, (ox, oy), sub); sub = tmp
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    if glow > 0:
        g = sub.filter(ImageFilter.GaussianBlur(22))
        g = Image.eval(g, lambda v: int(v*glow))
        layer = Image.alpha_composite(layer, g)
    layer = Image.alpha_composite(layer, sub)
    img.alpha_composite(layer)

def draw_accent_rule(img, progress, alpha=1.0):
    if progress <= 0: return
    full = int(FULL_W*0.82); x0 = CX-full//2
    w = int(full*min(1.0, progress)); y = BASE_Y+TXT_H+70
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.rounded_rectangle([x0, y, x0+w, y+7], radius=3, fill=ACCENT+(int(255*alpha),))
    if w < full:
        d.ellipse([x0+w-7, y-3, x0+w+7, y+10], fill=WHITE+(int(160*alpha),))
    img.alpha_composite(layer.filter(ImageFilter.GaussianBlur(0.6)))

def draw_shimmer(img, t, intensity=1.0):
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for (px, py, r, ph) in PARTICLES:
        twk = 0.5+0.5*math.sin(t*2*math.pi+ph)
        a = int(70*twk*intensity)
        if a <= 0: continue
        d.ellipse([px-r, py-r, px+r, py+r], fill=WHITE+(a,))
    img.alpha_composite(layer.filter(ImageFilter.GaussianBlur(0.8)))

def frame(): return Image.new("RGBA", (W, H), (0, 0, 0, 255))

def save(img, folder, i):
    img.convert("RGB").save(os.path.join(folder, f"{i:04d}.png"))

def build():
    p0 = os.path.join(ROOT, "part0"); p1 = os.path.join(ROOT, "part1")
    for p in (p0, p1):
        if os.path.exists(p): shutil.rmtree(p)
        os.makedirs(p)

    i = 0
    # phase A: fade in (~0.9s)
    for k in range(27):
        t = k/26; img = frame()
        draw_wordmark(img, alpha=t, glow=0.35*t)
        draw_shimmer(img, k/27, intensity=0.4*t)
        save(img, p0, i); i += 1
    # phase B: hold (~0.4s)
    for k in range(12):
        img = frame(); draw_wordmark(img, 1.0, glow=0.35)
        draw_shimmer(img, (27+k)/40, 0.45); save(img, p0, i); i += 1
    # phase C: gentle single pulse (~0.7s)
    for k in range(21):
        t = k/20
        scale = 1.0 + 0.045*math.sin(t*math.pi)
        glow = 0.35 + 0.4*math.sin(t*math.pi)
        img = frame(); draw_wordmark(img, 1.0, scale=scale, glow=glow)
        draw_shimmer(img, (40+k)/60, 0.5); save(img, p0, i); i += 1
    # phase D: accent rule draws L→R + shimmer (~1.0s)
    for k in range(30):
        t = k/29; img = frame()
        draw_wordmark(img, 1.0, glow=0.32)
        draw_accent_rule(img, progress=t)
        draw_shimmer(img, (61+k)/90, 0.7)
        save(img, p0, i); i += 1

    # part1: steady breathing hold (loops until boot done) ~1.0s
    j = 0
    for k in range(30):
        t = k/30
        glow = 0.30 + 0.12*math.sin(t*2*math.pi)
        img = frame(); draw_wordmark(img, 1.0, glow=glow)
        draw_accent_rule(img, progress=1.0)
        draw_shimmer(img, t, 0.5)
        save(img, p1, j); j += 1

    with open(os.path.join(ROOT, "desc.txt"), "w") as fh:
        fh.write(f"{W} {H} {FPS}\n")
        fh.write("p 1 0 part0\n")   # intro, once
        fh.write("p 0 0 part1\n")   # steady hold, loop until boot completes
    zip_it(ROOT, "bootanimation.zip", ["desc.txt", "part0", "part1"])
    shutil.rmtree(p0); shutil.rmtree(p1); os.remove(os.path.join(ROOT, "desc.txt"))
    print(f"font: {FONT_USED}")
    print(f"frames: part0={i} part1={j}  -> {os.path.join(ROOT,'bootanimation.zip')}")

def zip_it(root, name, members):
    out = os.path.join(root, name)
    if os.path.exists(out): os.remove(out)
    subprocess.run(["zip", "-r0", "-q", name] + members, cwd=root, check=True)

if __name__ == "__main__":
    build()
