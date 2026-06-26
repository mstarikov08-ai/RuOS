#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
RuOS visual identity generator — single source of truth.

Emits, from one icon spec, BOTH:
  * high-resolution SVG masters         -> vendor/ruos/branding/icons/svg/<name>.svg
  * Android squircle VectorDrawables    -> packages/apps/<App>/res/drawable/ic_launcher.xml
  * the master logo (wordmark + symbol) -> vendor/ruos/branding/logo/*.svg
  * a Settings "About" symbol drawable  -> RuOSSettings res/drawable/ruos_logo.xml

Design language (see DESIGN.md):
  accent #D94F3D, iOS superellipse squircle (corner radius ~22% of size),
  light source top-left, subtle two-tone gradients, bold simple glyphs.

Run:  python3 vendor/ruos/branding/gen_branding.py
No third-party deps. Pure text output; deterministic.
"""

import os, math

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.normpath(os.path.join(HERE, "../../.."))
SVG_DIR = os.path.join(HERE, "icons", "svg")
LOGO_DIR = os.path.join(HERE, "logo")
os.makedirs(SVG_DIR, exist_ok=True)
os.makedirs(LOGO_DIR, exist_ok=True)

VIEW = 108.0
ACCENT = "#D94F3D"

# ── geometry helpers (path data valid in BOTH svg `d` and android pathData) ────

def f(x): return f"{x:.2f}".rstrip("0").rstrip(".")

def circle(cx, cy, r):
    return (f"M{f(cx-r)},{f(cy)} a{f(r)},{f(r)} 0 1,0 {f(2*r)},0 "
            f"a{f(r)},{f(r)} 0 1,0 {f(-2*r)},0 Z")

def rrect(x, y, w, h, r):
    return (f"M{f(x+r)},{f(y)} L{f(x+w-r)},{f(y)} "
            f"A{f(r)},{f(r)} 0 0 1 {f(x+w)},{f(y+r)} "
            f"L{f(x+w)},{f(y+h-r)} A{f(r)},{f(r)} 0 0 1 {f(x+w-r)},{f(y+h)} "
            f"L{f(x+r)},{f(y+h)} A{f(r)},{f(r)} 0 0 1 {f(x)},{f(y+h-r)} "
            f"L{f(x)},{f(y+r)} A{f(r)},{f(r)} 0 0 1 {f(x+r)},{f(y)} Z")

def squircle(size=VIEW, inset=1.0, n=5.0, steps=96):
    """iOS-style superellipse. inset keeps the shape off the canvas edge."""
    a = size/2 - inset
    c = size/2
    pts = []
    for i in range(steps):
        t = 2*math.pi*i/steps
        ct, st = math.cos(t), math.sin(t)
        x = c + a*math.copysign(abs(ct)**(2/n), ct)
        y = c + a*math.copysign(abs(st)**(2/n), st)
        pts.append((x, y))
    d = "M" + f(pts[0][0]) + "," + f(pts[0][1])
    for (x, y) in pts[1:]:
        d += " L" + f(x) + "," + f(y)
    return d + " Z"

# A layer = dict(d=..., fill=solid|grad|None, stroke=color|None, sw=.., xform=(tx,ty,s))
def solid(color): return ("solid", color)
def grad(x1, y1, x2, y2, stops): return ("grad", (x1, y1, x2, y2, stops))

# ── glyph builders (drawn directly in the 108 viewport) ───────────────────────

def gear():  # Material settings gear, 24-unit, placed via transform
    return ("M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14"
            ".23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7"
            "-1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36"
            " 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12"
            ".21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58c-.18"
            ".14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94"
            "l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13"
            "-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61"
            "l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6"
            " 3.6-1.62 3.6-3.6 3.6z")

def heart():
    return ("M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 "
            "3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78"
            "-3.4 6.86-8.55 11.54L12 21.35z")

def handset():
    return ("M6.62 10.79c1.44 2.83 3.76 5.14 6.59 6.59l2.2-2.2c.27-.27.67-.36 1.02-.24 "
            "1.12.37 2.33.57 3.57.57.55 0 1 .45 1 1V20c0 .55-.45 1-1 1-9.39 0-17-7.61-17"
            "-17 0-.55.45-1 1-1h3.5c.55 0 1 .45 1 1 0 1.25.2 2.45.57 3.57.11.35.03.74"
            "-.25 1.02l-2.2 2.2z")

def bubble():
    return ("M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2z")

def folder():
    return ("M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1"
            "-.9-2-2-2h-8l-2-2z")

def book():
    return ("M18 2H6c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V4c0-1.1-.9-2"
            "-2-2zM7 4h4v9l-2-1.4L7 13V4z")

def place(d, size_target=58.0, dy=0.0):
    """Wrap a 24-unit glyph path with a centering transform."""
    s = size_target/24.0
    tx = (VIEW - size_target)/2.0
    ty = (VIEW - size_target)/2.0 + dy
    return (d, (tx, ty, s))

# ── icon catalogue ────────────────────────────────────────────────────────────
# Each entry: app folder name, file slug, list of layers.

def squircle_layer(fill): return dict(d=squircle(), fill=fill)

def L(d, fill=None, stroke=None, sw=0.0, xform=None):
    return dict(d=d, fill=fill, stroke=stroke, sw=sw, xform=xform)

WHITE = solid("#FFFFFF")

def icon_phone():
    bg = grad(14, 10, 96, 100, [(0, "#42E27A"), (1, "#1FAE54")])
    gd, xf = place(handset(), 58)
    return [squircle_layer(bg), L(gd, fill=WHITE, xform=xf)]

def icon_messages():
    bg = grad(14, 10, 96, 100, [(0, "#5BE36B"), (1, "#23B84B")])
    gd, xf = place(bubble(), 58, dy=-1)
    return [squircle_layer(bg), L(gd, fill=WHITE, xform=xf)]

def icon_camera():
    bg = grad(14, 10, 96, 100, [(0, "#48484A"), (1, "#1C1C1E")])
    return [
        squircle_layer(bg),
        L(circle(54, 56, 23), fill=solid("#5A5A5C")),                 # lens housing
        L(circle(54, 56, 18), fill=grad(40, 42, 70, 74,
            [(0, "#2C2C2E"), (1, "#0E0E0F")])),                        # glass
        L(circle(48, 50, 6), fill=solid("#7FD7E8")),                   # teal reflection
        L(circle(60, 62, 3), fill=solid("#FFFFFF")),                   # spec highlight
        L(circle(78, 36, 4), fill=solid("#FF9F0A")),                   # shutter/indicator
    ]

def icon_photos():
    bg = grad(12, 10, 96, 100, [(0, "#FFFFFF"), (1, "#EFEFF4")])
    cx, cy, R, pr = 54, 54, 15, 13
    cols = ["#FF453A", "#FF9F0A", "#FFD60A", "#34C759", "#0A84FF", "#AF52DE"]
    petals = []
    for i, col in enumerate(cols):
        a = math.radians(i*60 - 90)
        px, py = cx + R*math.cos(a), cy + R*math.sin(a)
        petals.append(L(circle(px, py, pr), fill=solid(col)))
    petals.append(L(circle(cx, cy, 8), fill=solid("#FFFFFF")))
    return [squircle_layer(bg)] + petals

def icon_clock():
    bg = grad(14, 10, 96, 100, [(0, "#1A1A1C"), (1, "#000000")])
    layers = [squircle_layer(bg), L(circle(54, 54, 40), stroke="#FFFFFF", sw=4)]
    for i in range(12):                                               # dial ticks
        a = math.radians(i*30)
        r1, r2 = 36, (30 if i % 3 == 0 else 33)
        x1, y1 = 54 + r1*math.sin(a), 54 - r1*math.cos(a)
        x2, y2 = 54 + r2*math.sin(a), 54 - r2*math.cos(a)
        layers.append(L(f"M{f(x1)},{f(y1)} L{f(x2)},{f(y2)}",
                        stroke="#FFFFFF", sw=(3 if i % 3 == 0 else 2)))
    layers.append(L("M54,54 L54,30", stroke="#FFFFFF", sw=4))         # minute hand
    layers.append(L("M54,54 L70,54", stroke="#FFFFFF", sw=4))         # hour hand
    layers.append(L("M54,60 L54,26", stroke=ACCENT, sw=2))            # second hand
    layers.append(L(circle(54, 54, 3), fill=solid("#FFFFFF")))
    layers.append(L(circle(54, 54, 2), fill=solid(ACCENT)))
    return layers

def icon_calculator():
    bg = grad(14, 10, 96, 100, [(0, "#2C2C2E"), (1, "#161618")])
    layers = [squircle_layer(bg),
              L(rrect(26, 22, 56, 16, 5), fill=solid("#1C1C1E"))]     # display
    x0, y0, gap, r = 32, 50, 15, 5
    for row in range(4):
        for col in range(4):
            cx, cy = x0 + col*gap, y0 + row*gap
            orange = (col == 3)
            layers.append(L(circle(cx, cy, r),
                            fill=solid("#FF9F0A" if orange else "#EBEBF0")))
    return layers

def icon_notes():
    bg = grad(14, 10, 96, 100, [(0, "#FFF3C4"), (1, "#FFD84A")])
    layers = [squircle_layer(bg),
              L(rrect(26, 24, 56, 60, 8), fill=solid("#FFFFFF")),     # paper
              L(rrect(26, 24, 56, 14, 8), fill=solid("#FFC93C"))]     # header band
    for i in range(4):                                                # ruled lines
        y = 48 + i*9
        layers.append(L(f"M34,{f(y)} L74,{f(y)}", stroke="#D9D9DE", sw=2.4))
    return layers

def icon_settings():
    bg = grad(14, 10, 96, 100, [(0, "#7A7A80"), (1, "#39393C")])
    gd, xf = place(gear(), 64)
    return [squircle_layer(bg), L(gd, fill=solid("#EDEDF0"), xform=xf)]

def icon_journal():
    bg = grad(12, 10, 96, 100, [(0, "#FFFFFF"), (1, "#F4ECDA")])
    gd, xf = place(book(), 56)
    return [squircle_layer(bg), L(gd, fill=grad(40, 40, 72, 76,
            [(0, "#E76A56"), (1, ACCENT)]), xform=xf)]

def icon_files():
    bg = grad(14, 10, 96, 100, [(0, "#37A6F5"), (1, "#1577D6")])
    gd, xf = place(folder(), 60)
    return [squircle_layer(bg), L(gd, fill=WHITE, xform=xf)]

def icon_health():
    bg = grad(12, 10, 96, 100, [(0, "#FFFFFF"), (1, "#FFEDED")])
    gd, xf = place(heart(), 56)
    return [squircle_layer(bg), L(gd, fill=grad(40, 38, 72, 78,
            [(0, "#FF4E5E"), (1, "#E0394A")]), xform=xf)]

def icon_weather():
    bg = grad(14, 8, 96, 102, [(0, "#5BBDFF"), (1, "#1E84E0")])
    return [
        squircle_layer(bg),
        L(circle(44, 44, 14), fill=grad(34, 34, 56, 56,
            [(0, "#FFE36B"), (1, "#FFC22E")])),                       # sun
        # cloud: three lobes + base, white, overlapping the sun
        L(circle(54, 64, 14), fill=solid("#FFFFFF")),
        L(circle(70, 60, 11), fill=solid("#FFFFFF")),
        L(circle(44, 62, 10), fill=solid("#FFFFFF")),
        L(rrect(38, 62, 40, 14, 7), fill=solid("#FFFFFF")),
    ]

ICONS = [
    ("RuOSPhone",      "phone",      icon_phone),
    ("RuOSMessages",   "messages",   icon_messages),
    ("RuOSCamera",     "camera",     icon_camera),
    ("RuOSGallery",    "photos",     icon_photos),
    ("RuOSClock",      "clock",      icon_clock),
    ("RuOSCalculator", "calculator", icon_calculator),
    ("RuOSNotes",      "notes",      icon_notes),
    ("RuOSSettings",   "settings",   icon_settings),
    ("RuOSJournal",    "journal",    icon_journal),
    ("RuOSFiles",      "files",      icon_files),
    ("RuOSHealth",     "health",     icon_health),
    ("RuOSWeather",    "weather",    icon_weather),
]

# ── emitters ──────────────────────────────────────────────────────────────────

def fill_svg(defs, fill, idx):
    if fill is None: return "none"
    kind, val = fill
    if kind == "solid": return val
    x1, y1, x2, y2, stops = val
    gid = f"g{idx}"
    s = "".join(f'<stop offset="{o}" stop-color="{c}"/>' for o, c in stops)
    defs.append(f'<linearGradient id="{gid}" x1="{f(x1)}" y1="{f(y1)}" '
                f'x2="{f(x2)}" y2="{f(y2)}" gradientUnits="userSpaceOnUse">{s}</linearGradient>')
    return f"url(#{gid})"

def to_svg(layers):
    defs, body = [], []
    for i, ly in enumerate(layers):
        if ly.get("stroke"):
            attrs = (f'd="{ly["d"]}" fill="none" stroke="{ly["stroke"]}" '
                     f'stroke-width="{f(ly["sw"])}" stroke-linecap="round" '
                     f'stroke-linejoin="round"')
        else:
            fattr = fill_svg(defs, ly.get("fill"), i)
            attrs = f'd="{ly["d"]}" fill="{fattr}"'
        path = f'<path {attrs}/>'
        xf = ly.get("xform")
        if xf:
            tx, ty, s = xf
            path = f'<g transform="translate({f(tx)},{f(ty)}) scale({f(s)})">{path}</g>'
        body.append(path)
    defs_s = f"<defs>{''.join(defs)}</defs>" if defs else ""
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" '
            f'width="108" height="108">{defs_s}{"".join(body)}</svg>\n')

def fill_vd(fill):
    """Return (inline_color_or_None, gradient_xml_or_None)."""
    if fill is None: return ("#00000000", None)
    kind, val = fill
    if kind == "solid": return (val, None)
    x1, y1, x2, y2, stops = val
    items = "".join(f'<item android:offset="{o}" android:color="{c}"/>' for o, c in stops)
    g = (f'<gradient android:type="linear" android:startX="{f(x1)}" android:startY="{f(y1)}" '
         f'android:endX="{f(x2)}" android:endY="{f(y2)}">{items}</gradient>')
    return (None, g)

def to_vd(layers):
    out = ['<vector xmlns:android="http://schemas.android.com/apk/res/android"',
           '    xmlns:aapt="http://schemas.android.com/aapt"',
           '    android:width="108dp" android:height="108dp"',
           '    android:viewportWidth="108" android:viewportHeight="108">']
    for ly in layers:
        color, gxml = fill_vd(ly.get("fill"))
        path_attrs = [f'        android:pathData="{ly["d"]}"']
        if ly.get("stroke"):
            path_attrs.append(f'        android:strokeColor="{ly["stroke"]}"')
            path_attrs.append(f'        android:strokeWidth="{f(ly["sw"])}"')
            path_attrs.append('        android:strokeLineCap="round"')
            path_attrs.append('        android:strokeLineJoin="round"')
            path_attrs.append('        android:fillColor="#00000000"')
        elif gxml is None:
            path_attrs.append(f'        android:fillColor="{color}"')
        path_open = "<path\n" + "\n".join(path_attrs)
        inner = ""
        if gxml is not None and not ly.get("stroke"):
            inner = (f'>\n        <aapt:attr name="android:fillColor">\n'
                     f'            {gxml}\n        </aapt:attr>\n      </path>')
            path_block = "      " + path_open + inner
        else:
            path_block = "      " + path_open + " />"
        xf = ly.get("xform")
        if xf:
            tx, ty, s = xf
            path_block = (f'  <group android:translateX="{f(tx)}" android:translateY="{f(ty)}" '
                          f'android:scaleX="{f(s)}" android:scaleY="{f(s)}">\n  {path_block}\n  </group>')
        out.append(path_block)
    out.append('</vector>')
    return "\n".join(out) + "\n"

# ── master logo ───────────────────────────────────────────────────────────────

def write_logo():
    # Symbol: an abstract "Ru" mark — an upward chevron (ascent / technology /
    # premium) cradled by a rounded base (the squircle language), in accent red.
    # Reads at any size, works in one colour.
    sym_paths = (
        # rounded shield/base
        f'<path d="{squircle(120, inset=6, n=4.2)}" fill="{ACCENT}"/>'
        # white upward mark: stylised "R" leg + rising stroke
        '<path d="M40,84 L40,40 Q40,34 46,34 L66,34 Q80,34 80,48 '
        'Q80,60 67,62 L82,84 L70,84 L57,63 L51,63 L51,84 Z '
        'M51,44 L51,54 L64,54 Q69,54 69,49 Q69,44 64,44 Z" fill="#FFFFFF"/>'
    )
    symbol = (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 120" '
              f'width="120" height="120">{sym_paths}</svg>\n')
    with open(os.path.join(LOGO_DIR, "ruos_symbol.svg"), "w") as fh: fh.write(symbol)

    mono = (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 120" '
            f'width="120" height="120">'
            f'<path d="{squircle(120, inset=6, n=4.2)}" fill="#000000"/>'
            '<path d="M40,84 L40,40 Q40,34 46,34 L66,34 Q80,34 80,48 '
            'Q80,60 67,62 L82,84 L70,84 L57,63 L51,63 L51,84 Z '
            'M51,44 L51,54 L64,54 Q69,54 69,49 Q69,44 64,44 Z" fill="#FFFFFF"/></svg>\n')
    with open(os.path.join(LOGO_DIR, "ruos_symbol_mono.svg"), "w") as fh: fh.write(mono)

    # Wordmark: 'Ru' white, 'OS' accent, thin elegant. Text element with Golos Text
    # (system fallback). Kept as text so it stays crisp and editable; the accent
    # underline is a drawn rule for guaranteed brand consistency.
    wordmark = (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 520 180" width="520" height="180">'
        '<rect width="520" height="180" fill="none"/>'
        '<text x="260" y="104" text-anchor="middle" font-family="Golos Text, sans-serif" '
        'font-weight="200" font-size="120" letter-spacing="2">'
        '<tspan fill="#FFFFFF">Ru</tspan><tspan fill="' + ACCENT + '">OS</tspan></text>'
        '<rect x="150" y="126" width="220" height="4" rx="2" fill="' + ACCENT + '"/>'
        '</svg>\n'
    )
    with open(os.path.join(LOGO_DIR, "ruos_wordmark.svg"), "w") as fh: fh.write(wordmark)

    # Android VectorDrawable of the symbol for Settings → About.
    vd = ('<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
          '    android:width="120dp" android:height="120dp"\n'
          '    android:viewportWidth="120" android:viewportHeight="120">\n'
          f'  <path android:pathData="{squircle(120, inset=6, n=4.2)}" android:fillColor="{ACCENT}" />\n'
          '  <path android:pathData="M40,84 L40,40 Q40,34 46,34 L66,34 Q80,34 80,48 '
          'Q80,60 67,62 L82,84 L70,84 L57,63 L51,63 L51,84 Z '
          'M51,44 L51,54 L64,54 Q69,54 69,49 Q69,44 64,44 Z" android:fillColor="#FFFFFF" />\n'
          '</vector>\n')
    dst = os.path.join(REPO, "packages/apps/RuOSSettings/res/drawable/ruos_logo.xml")
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    with open(dst, "w") as fh: fh.write(vd)

# ── main ──────────────────────────────────────────────────────────────────────

def main():
    for app, slug, fn in ICONS:
        layers = fn()
        with open(os.path.join(SVG_DIR, f"{slug}.svg"), "w") as fh:
            fh.write(to_svg(layers))
        res = os.path.join(REPO, "packages/apps", app, "res/drawable")
        os.makedirs(res, exist_ok=True)
        with open(os.path.join(res, "ic_launcher.xml"), "w") as fh:
            fh.write(to_vd(layers))
        print(f"  {app:16s} -> svg/{slug}.svg + {app}/res/drawable/ic_launcher.xml")
    write_logo()
    print(f"Logos -> {os.path.relpath(LOGO_DIR, REPO)}/  + RuOSSettings/res/drawable/ruos_logo.xml")

if __name__ == "__main__":
    main()
