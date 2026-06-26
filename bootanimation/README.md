# RuOS Boot Animation

**Duration:** 3 seconds total  
**Resolution:** 1080×2340 @ 60fps  
**Format:** bootanimation.zip (standard Android format)

## Structure

```
bootanimation.zip
├── desc.txt
├── part0/     — fade-in sequence (60 frames, ~1s)
│   ├── 0000.png  (black)
│   ├── ...
│   └── 0059.png  (RuOS wordmark fully visible)
└── part1/     — hold sequence (120 frames, ~2s)
    ├── 0000.png  (wordmark + red accent pulse)
    └── ...
```

## Design

- Frame 0–30:   Black screen
- Frame 30–60:  "RuOS" wordmark fades in (Golos Text Thin, white, centred)
- Frame 60–90:  Subtle red accent line (#D94F3D) fades in under the wordmark
- Frame 90–180: Hold, slight ambient breathing glow on accent

## Generating the animation

```bash
# Install Pillow + fonttools
pip install Pillow fonttools

# Run the generator
python3 vendor/ruos/tools/gen_bootanim.py

# Package
cd bootanimation && zip -r0 ../vendor/ruos/prebuilts/bootanimation.zip desc.txt part0/ part1/
```

## Font

Golos Text Thin (weight 100) from vendor/ruos/prebuilts/fonts/GolosText/
