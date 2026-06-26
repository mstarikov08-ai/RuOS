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

# ── italic tricolour wordmark (precomputed once) ──────────────────────────────
# Forward 11° slant (shear), tight tracking, white→blue→red flag flow across "RuOS".
FLAG_W = (255, 255, 255)
FLAG_B = (30, 91, 214)     # royal blue
FLAG_R = ACCENT            # accent red (#D94F3D) ties the flag to the rest of RuOS
SHEAR = 0.194              # tan(11°)
TRACKING = -10             # tight, confident spacing

_tmp = ImageDraw.Draw(Image.new("RGB", (10, 10)))
_bb = _tmp.textbbox((0, 0), "RuOS", font=FONT)
TXT_H = _bb[3]-_bb[1]
CX = W//2; BASE_Y = H//2 - 120

def _measure(text):
    return sum(FONT.getlength(c) for c in text) + TRACKING*(len(text)-1)

WORD_W = _measure("RuOS")

def _word_mask():
    m = Image.new("L", (W, H), 0); d = ImageDraw.Draw(m)
    x = CX - WORD_W/2
    for ch in "RuOS":
        d.text((x, BASE_Y), ch, font=FONT, fill=255)
        x += FONT.getlength(ch) + TRACKING
    pivot = BASE_Y + TXT_H/2
    return m.transform((W, H), Image.AFFINE, (1, SHEAR, -SHEAR*pivot, 0, 1, 0),
                       resample=Image.BICUBIC)

def _flag_grad():
    left = CX - WORD_W/2 - 28; right = CX + WORD_W/2 + 28
    stops = [(0.0, FLAG_W), (0.34, FLAG_W), (0.50, FLAG_B), (0.66, FLAG_B), (1.0, FLAG_R)]
    row = Image.new("RGB", (W, 1)); px = row.load()
    def lerp(a, b, t): return tuple(int(a[i]+(b[i]-a[i])*t) for i in range(3))
    for x in range(W):
        t = 0.0 if right == left else (x-left)/(right-left)
        t = max(0.0, min(1.0, t)); col = stops[0][1]
        for k in range(len(stops)-1):
            o0, c0 = stops[k]; o1, c1 = stops[k+1]
            if t <= o1:
                tt = 0 if o1 == o0 else (t-o0)/(o1-o0); col = lerp(c0, c1, max(0, min(1, tt))); break
            col = c1
        px[x, 0] = col
    return row.resize((W, H))

SHEARED = _word_mask()
GRAD_IMG = _flag_grad()
COLOURED = GRAD_IMG.convert("RGBA"); COLOURED.putalpha(SHEARED)

# accent rule, italic + tricolour, parallel to the wordmark
RULE_W = int(WORD_W*0.86); RULE_X = CX-RULE_W//2; RULE_Y = BASE_Y+TXT_H+70
def _rule_mask():
    m = Image.new("L", (W, H), 0); d = ImageDraw.Draw(m)
    d.rounded_rectangle([RULE_X, RULE_Y, RULE_X+RULE_W, RULE_Y+7], radius=3, fill=255)
    pivot = RULE_Y+3
    return m.transform((W, H), Image.AFFINE, (1, SHEAR, -SHEAR*pivot, 0, 1, 0),
                       resample=Image.BICUBIC)
RULE_COLOURED = GRAD_IMG.convert("RGBA"); RULE_COLOURED.putalpha(_rule_mask())

# Stable shimmer particle field
PARTICLES = [(random.uniform(0.1, 0.9)*W, random.uniform(0.30, 0.62)*H,
              random.uniform(1.5, 3.5), random.uniform(0, 2*math.pi)) for _ in range(46)]

def _fade(im, a):
    if a >= 1.0: return im
    r, g, b, al = im.split(); al = al.point(lambda v: int(v*a))
    return Image.merge("RGBA", (r, g, b, al))

def draw_wordmark(img, alpha, scale=1.0, glow=0.0):
    """Composite the italic tricolour wordmark with optional glow + scale pulse + fade."""
    work = COLOURED
    if scale != 1.0:
        nw, nh = int(W*scale), int(H*scale)
        s = work.resize((nw, nh), Image.LANCZOS)
        work = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        work.paste(s, ((W-nw)//2, (H-nh)//2), s)
    result = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    if glow > 0:
        result = Image.alpha_composite(result, _fade(work.filter(ImageFilter.GaussianBlur(22)), glow))
    result = Image.alpha_composite(result, work)
    img.alpha_composite(_fade(result, alpha))

def draw_accent_rule(img, progress, alpha=1.0):
    if progress <= 0: return
    w = int(RULE_W*min(1.0, progress))
    cut = RULE_COLOURED.copy()
    if progress < 1.0:
        a = cut.split()[3]; dd = ImageDraw.Draw(a)
        dd.rectangle([RULE_X+w, 0, W, H], fill=0)   # hide the not-yet-drawn part
        cut.putalpha(a)
        d2 = ImageDraw.Draw(cut)
        d2.ellipse([RULE_X+w-7, RULE_Y-4, RULE_X+w+7, RULE_Y+11], fill=WHITE+(int(170*alpha),))
    img.alpha_composite(_fade(cut, alpha))

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
