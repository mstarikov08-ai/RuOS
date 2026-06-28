#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# RuOS flash-all for Pixel 7 (panther) — run from the bootloader (fastboot) screen.
#
# Fixes the failure:
#   FAILED (remote: 'Invalid command resize-logical-partition:system_a:1035993088')
# by flashing the MERGED super image in fastbootd instead of resizing individual
# logical partitions. `super.img` already contains system / system_ext / product /
# vendor / vendor_dlkm / system_dlkm, so this also covers the "missing vendor.img"
# case — vendor is inside super.
#
# Usage:
#   tools/flash-all.sh                         # flash images from $ANDROID_PRODUCT_OUT
#   OUT=out/target/product/panther tools/flash-all.sh
#   BOOTLOADER_IMG=bootloader-panther-cloudripper-14.0-XXXX.img \
#   RADIO_IMG=radio-panther-g5300q-XXXX.img    tools/flash-all.sh   # also update firmware
#
# Prereqs: unlocked bootloader; recent platform-tools (fastboot >= 34); device in
# bootloader mode (adb reboot bootloader, or Power+Vol-Down).
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

OUT="${OUT:-${ANDROID_PRODUCT_OUT:-out/target/product/panther}}"
BOOTLOADER_IMG="${BOOTLOADER_IMG:-}"
RADIO_IMG="${RADIO_IMG:-}"
FB="${FASTBOOT:-fastboot}"

say()  { printf '\n\033[1;36m== %s\033[0m\n' "$*"; }
need() { [ -f "$1" ] || { echo "MISSING: $1"; exit 1; }; }
have() { [ -f "$1" ]; }

say "RuOS flash-all (panther) — images from: $OUT"
"$FB" --version >/dev/null || { echo "fastboot not found"; exit 1; }
[ -d "$OUT" ] || { echo "OUT dir not found: $OUT (build first, or set OUT=)"; exit 1; }

# super.img is required — it's the whole point. Build it with: m superimage
need "$OUT/super.img"
need "$OUT/vbmeta.img"

say "Devices in fastboot:"
"$FB" devices

# 1) Firmware (optional) — only if you pass the matching cloudripper-14.0 images.
if [ -n "$BOOTLOADER_IMG" ]; then
    need "$BOOTLOADER_IMG"
    say "Flashing bootloader"
    "$FB" flash bootloader "$BOOTLOADER_IMG"
    "$FB" reboot bootloader; sleep 5
fi
if [ -n "$RADIO_IMG" ]; then
    need "$RADIO_IMG"
    say "Flashing radio"
    "$FB" flash radio "$RADIO_IMG"
    "$FB" reboot bootloader; sleep 5
fi

# 2) vbmeta — DISABLE verity + verification so the modified /system boots.
say "Flashing vbmeta (verity + verification disabled)"
"$FB" --disable-verity --disable-verification flash vbmeta "$OUT/vbmeta.img"
have "$OUT/vbmeta_system.img" && \
    "$FB" --disable-verity --disable-verification flash vbmeta_system "$OUT/vbmeta_system.img" || true

# 3) Boot chain (whichever this build produced).
for p in boot init_boot dtbo vendor_boot vendor_kernel_boot; do
    have "$OUT/$p.img" && { say "Flashing $p"; "$FB" flash "$p" "$OUT/$p.img"; } || true
done

# 4) Dynamic partitions — enter fastbootd and flash the merged super image.
#    This is what avoids resize-logical-partition, and includes vendor.
say "Rebooting into fastbootd (userspace fastboot) for super"
"$FB" reboot fastboot; sleep 6
"$FB" devices
say "Flashing super (system + vendor + product + system_ext + *_dlkm)"
"$FB" flash super "$OUT/super.img"

# 5) Wipe userdata + reboot.
say "Wiping userdata (-w) and rebooting"
"$FB" -w reboot

say "Done. First boot optimizes apps and may take a few minutes."
echo "Watch for crashes with:  adb logcat -b crash AndroidRuntime:E *:S"
