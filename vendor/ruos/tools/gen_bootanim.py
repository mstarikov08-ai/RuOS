#!/usr/bin/env python3
"""
RuOS boot animation generator.
Produces 180 frames (60fps, 3 seconds) at 1080x2340.

Requirements:
    pip install Pillow

Usage:
    python3 gen_bootanim.py
    cd ../../../bootanimation
    zip -r0 ../vendor/ruos/prebuilts/bootanimation.zip desc.txt part0/ part1/
"""

from PIL import Image, ImageDraw, ImageFont
import os, math

W, H = 1080, 2340
ACCENT = (217, 79, 61)      # #D94F3D
WHITE  = (255, 255, 255)
BLACK  = (0, 0, 0)

FONT_PATH = os.path.join(os.path.dirname(__file__), "../prebuilts/fonts/GolosText/GolosText-Thin.ttf")
FONT_SIZE = 180

script_dir = os.path.dirname(os.path.abspath(__file__))
root = os.path.join(script_dir, "../../../bootanimation")
part0_dir = os.path.join(root, "part0")
part1_dir = os.path.join(root, "part1")
os.makedirs(part0_dir, exist_ok=True)
os.makedirs(part1_dir, exist_ok=True)

# Load font — fall back to default if not present
try:
    font = ImageFont.truetype(FONT_PATH, FONT_SIZE)
except Exception:
    font = ImageFont.load_default()
    print(f"WARNING: Golos Text not found at {FONT_PATH}. Using default font.")

TEXT = "RuOS"
TOTAL_FRAMES_PART0 = 60   # ~1s fade-in
TOTAL_FRAMES_PART1 = 120  # ~2s hold with accent pulse

def text_bbox(draw, text, font):
    bbox = draw.textbbox((0, 0), text, font=font)
    return bbox[2] - bbox[0], bbox[3] - bbox[1]

def make_frame(alpha_text, alpha_accent, accent_y_offset=0):
    img = Image.new("RGB", (W, H), BLACK)
    draw = ImageDraw.Draw(img)

    tw, th = text_bbox(draw, TEXT, font)
    cx = (W - tw) // 2
    cy = (H - th) // 2 - 40

    # Draw wordmark
    col = tuple(int(c * alpha_text) for c in WHITE)
    draw.text((cx, cy), TEXT, font=font, fill=col)

    # Draw accent line
    if alpha_accent > 0:
        line_w = int(tw * 0.6)
        lx = (W - line_w) // 2
        ly = cy + th + 16 + accent_y_offset
        accent_col = tuple(int(c * alpha_accent) for c in ACCENT) + (255,)
        img2 = img.convert("RGBA")
        d2 = ImageDraw.Draw(img2)
        d2.rectangle([lx, ly, lx + line_w, ly + 4], fill=accent_col)
        img = img2.convert("RGB")

    return img

print(f"Generating {TOTAL_FRAMES_PART0} frames for part0 (fade-in)...")
for i in range(TOTAL_FRAMES_PART0):
    t = i / TOTAL_FRAMES_PART0
    # Text fades in from frame 30 to 60
    alpha_text = max(0.0, (t - 0.5) * 2) if t >= 0.5 else 0.0
    # Accent fades in with text
    alpha_accent = max(0.0, (t - 0.65) * 3) if t >= 0.65 else 0.0
    alpha_accent = min(1.0, alpha_accent)
    frame = make_frame(alpha_text, alpha_accent)
    frame.save(os.path.join(part0_dir, f"{i:04d}.png"))

print(f"Generating {TOTAL_FRAMES_PART1} frames for part1 (hold)...")
for i in range(TOTAL_FRAMES_PART1):
    t = i / TOTAL_FRAMES_PART1
    # Gentle breathing pulse on accent
    pulse = 0.75 + 0.25 * math.sin(t * math.pi * 2)
    frame = make_frame(1.0, pulse)
    frame.save(os.path.join(part1_dir, f"{i:04d}.png"))

print("Done. Now run:")
print("  cd bootanimation")
print("  zip -r0 ../vendor/ruos/prebuilts/bootanimation.zip desc.txt part0/ part1/")
