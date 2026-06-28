#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# RuOS flash-all — Pixel 7 (panther), AOSP android-14.0.0_r50, bootloader cloudripper-14.0
#
# Fixes the dynamic-partition flash failure
#   FAILED (remote: 'Invalid command resize-logical-partition:system_a:1035993088')
# by flashing the MERGED super image in fastbootd instead of resizing per-partition.
# super.img contains system / system_ext / product / vendor / vendor_dlkm / system_dlkm,
# so this also covers a "missing vendor.img" (vendor is a logical partition inside super).
#
# USAGE
#   tools/flash-all.sh [options]
#
# OPTIONS
#   -o, --out DIR        image dir (default: $ANDROID_PRODUCT_OUT, else out/target/product/panther)
#   -b, --bootloader IMG flash this bootloader image first   (bootloader-panther-cloudripper-14.0-*.img)
#   -r, --radio IMG      flash this radio image first        (radio-panther-*.img)
#   -u, --update ZIP     flash via 'fastboot update ZIP' instead of individual images
#   -s, --slot a|b       target slot for boot/vbmeta (default: current active slot)
#   -n, --dry-run        print every fastboot command without running it
#   -k, --keep-data      do NOT wipe userdata (omit -w)
#   -h, --help           this help
#
# PREREQS: unlocked bootloader; platform-tools fastboot >= 34; device in bootloader mode
#          (adb reboot bootloader, or hold Power+Vol-Down).
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

OUT="${ANDROID_PRODUCT_OUT:-out/target/product/panther}"
BOOTLOADER_IMG=""
RADIO_IMG=""
UPDATE_ZIP=""
SLOT=""
DRY=0
WIPE="-w"
FB="${FASTBOOT:-fastboot}"

usage() { sed -n '2,40p' "$0" | sed 's/^# \{0,1\}//'; exit "${1:-0}"; }

while [ $# -gt 0 ]; do
    case "$1" in
        -o|--out)        OUT="$2"; shift 2;;
        -b|--bootloader) BOOTLOADER_IMG="$2"; shift 2;;
        -r|--radio)      RADIO_IMG="$2"; shift 2;;
        -u|--update)     UPDATE_ZIP="$2"; shift 2;;
        -s|--slot)       SLOT="$2"; shift 2;;
        -n|--dry-run)    DRY=1; shift;;
        -k|--keep-data)  WIPE=""; shift;;
        -h|--help)       usage 0;;
        *) echo "unknown option: $1" >&2; usage 1;;
    esac
done

c()    { printf '\n\033[1;36m== %s\033[0m\n' "$*"; }       # section
warn() { printf '\033[1;33m!! %s\033[0m\n' "$*"; }
die()  { printf '\033[1;31mxx %s\033[0m\n' "$*" >&2; exit 1; }
need() { [ "$DRY" = 1 ] && return 0; [ -f "$1" ] || die "missing image: $1"; }
have() { [ "$DRY" = 1 ] && return 0; [ -f "$1" ]; }

# fb: run (or, in dry-run, print) a fastboot command.
fb() {
    if [ "$DRY" = 1 ]; then printf '   fastboot %s\n' "$*"; return 0; fi
    "$FB" "$@"
}
# fb_q: query a getvar value (empty on dry-run / failure).
fb_q() { [ "$DRY" = 1 ] && { echo ""; return; }; "$FB" getvar "$1" 2>&1 | sed -n "s/^$1: //p" | head -1; }

# Wait until the device is in userspace fastboot (fastbootd) — needed for super.
wait_fastbootd() {
    [ "$DRY" = 1 ] && return 0
    local i
    for i in $(seq 1 30); do
        [ "$("$FB" getvar is-userspace 2>&1 | sed -n 's/^is-userspace: //p' | head -1)" = "yes" ] && return 0
        sleep 1
    done
    die "device did not enter fastbootd (userspace fastboot)"
}

# ── preflight ─────────────────────────────────────────────────────────────────
c "RuOS flash-all (panther)  —  images: $OUT  ${DRY:+(dry-run)}"
[ "$DRY" = 1 ] || command -v "$FB" >/dev/null || die "fastboot not found (install platform-tools)"
[ -d "$OUT" ] || [ "$DRY" = 1 ] || die "image dir not found: $OUT (build first, or pass --out)"

if [ "$DRY" = 0 ]; then
    "$FB" devices | grep -q fastboot || die "no device in fastboot mode (adb reboot bootloader)"
    UNLOCKED="$(fb_q unlocked)"
    [ "$UNLOCKED" = "yes" ] || warn "bootloader reports unlocked='$UNLOCKED' — flashing will fail if locked."
    [ -z "$SLOT" ] && SLOT="$(fb_q current-slot)"; [ -z "$SLOT" ] && SLOT="a"
    c "Target slot: $SLOT"
fi

# ── fastboot update path (alternative) ──────────────────────────────────────────
if [ -n "$UPDATE_ZIP" ]; then
    need "$UPDATE_ZIP"
    c "Flashing via fastboot update: $UPDATE_ZIP"
    fb $WIPE update "$UPDATE_ZIP"
    c "Done."
    exit 0
fi

# Required images for the individual-image path.
[ "$DRY" = 1 ] || { need "$OUT/super.img"; need "$OUT/vbmeta.img"; }

# ── 1) firmware (optional) ──────────────────────────────────────────────────────
if [ -n "$BOOTLOADER_IMG" ]; then
    need "$BOOTLOADER_IMG"; c "Flashing bootloader"
    fb flash bootloader "$BOOTLOADER_IMG"; fb reboot bootloader; sleep 5
fi
if [ -n "$RADIO_IMG" ]; then
    need "$RADIO_IMG"; c "Flashing radio"
    fb flash radio "$RADIO_IMG"; fb reboot bootloader; sleep 5
fi

# ── 2) vbmeta — disable verity + verification so a modified /system boots ────────
c "Flashing vbmeta (verity + verification disabled)"
fb --disable-verity --disable-verification flash vbmeta "$OUT/vbmeta.img"
have "$OUT/vbmeta_system.img" && \
    fb --disable-verity --disable-verification flash vbmeta_system "$OUT/vbmeta_system.img" || true

# ── 3) boot chain (whichever this build produced) ───────────────────────────────
for p in boot init_boot dtbo vendor_boot vendor_kernel_boot; do
    have "$OUT/$p.img" && { c "Flashing $p"; fb flash "$p" "$OUT/$p.img"; } || true
done

# ── 4) dynamic partitions — merged super.img in fastbootd ────────────────────────
c "Rebooting into fastbootd for super"
fb reboot fastboot
wait_fastbootd
c "Flashing super (system + vendor + product + system_ext + *_dlkm)"
fb flash super "$OUT/super.img"

# ── 5) wipe + reboot ─────────────────────────────────────────────────────────────
c "Finishing ${WIPE:+(wiping userdata) }and rebooting"
fb $WIPE reboot

c "Done. First boot optimizes apps and may take a few minutes."
echo "Watch for crashes:  adb logcat -b crash AndroidRuntime:E *:S"
echo "RuOS guard logs:    adb logcat -s RuOSSystemUI:E"
