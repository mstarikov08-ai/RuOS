#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# RuOS → LineageOS 21 integration for Pixel 7 (panther).
#
# Run from the RuOS repo root, AFTER LineageOS has finished syncing and the panther
# device tree / kernel / vendor are in place (see docs/Build-LineageOS-Panther.md
# steps 1–3). Pass the LineageOS source root as $1 (default below for the Hetzner box).
#
#   ./tools/integrate_into_lineage.sh /mnt/HC_Volume_106163271/lineage
#
# It links the RuOS overlay into the tree and INJECTS the RuOS payload into the stock
# `lineage_panther` product, so the user's own build command works unchanged:
#   source build/envsetup.sh && breakfast panther && mka bacon
#
# Idempotent: safe to re-run. It only writes/links; it never starts a build.
# NOTE: LineageOS device-tree file names can drift between branches. Where this script
# can't find an expected file it prints exactly what to do by hand instead of guessing.
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

RUOS_DIR="$(cd "$(dirname "$0")/.." && pwd)"
LOS_DIR="${1:-/mnt/HC_Volume_106163271/lineage}"
log() { echo "[RuOS→LOS] $*"; }
die() { echo "[RuOS→LOS] ERROR: $*" >&2; exit 1; }

[ -d "$LOS_DIR/build/make" ] || die "$LOS_DIR is not a LineageOS/AOSP tree (no build/make). Pass the path: $0 /path/to/lineage"

# ── 1. Link the RuOS overlay dirs ──────────────────────────────────────────────
link() {  # link SRC into DST, replacing any existing entry
    local src="$1" dst="$2"
    [ -e "$dst" ] && rm -rf "$dst"
    mkdir -p "$(dirname "$dst")"
    ln -s "$src" "$dst"
}
log "Linking vendor/ruos, device/ruos"
link "$RUOS_DIR/vendor/ruos"  "$LOS_DIR/vendor/ruos"
link "$RUOS_DIR/device/ruos"  "$LOS_DIR/device/ruos"

log "Linking packages/apps/RuOS*"
for appdir in "$RUOS_DIR/packages/apps"/RuOS*; do
    [ -d "$appdir" ] || continue
    link "$appdir" "$LOS_DIR/packages/apps/$(basename "$appdir")"
done

# ── 2. SystemUI ruos-src + resources ──────────────────────────────────────────
AOSP_SYSUI="$LOS_DIR/frameworks/base/packages/SystemUI"
if [ -d "$AOSP_SYSUI" ]; then
    log "Linking SystemUI ruos-src + ruos-res"
    link "$RUOS_DIR/frameworks/base/packages/SystemUI/ruos-src" "$AOSP_SYSUI/ruos-src"
    if [ -d "$RUOS_DIR/frameworks/base/packages/SystemUI/ruos-res" ]; then
        cp -r "$RUOS_DIR/frameworks/base/packages/SystemUI/ruos-res/." "$AOSP_SYSUI/res/"
    fi
    SYSUI_BP="$AOSP_SYSUI/Android.bp"
    if [ -f "$SYSUI_BP" ] && ! grep -q "ruos-src" "$SYSUI_BP"; then
        log "ACTION NEEDED: add RuOS sources to the SystemUI module in $SYSUI_BP:"
        echo '        "ruos-src/**/*.kt",'
        echo '        "ruos-src/**/*.aidl",'
        log "  (LineageOS SystemUI is AOSP-based; it builds with platform_apis + dynamicanimation.)"
    fi
else
    log "WARNING: $AOSP_SYSUI not found — SystemUI customisations not linked."
fi

# ── 3. INJECT the RuOS payload into the lineage_panther product ────────────────
# ruos_common.mk is inject-safe (adds packages/overlays/branding, never sets
# PRODUCT_NAME/DEVICE), so inheriting it into lineage_panther keeps 'breakfast panther'
# working while pulling in all of RuOS.
RUOS_INHERIT='$(call inherit-product-if-exists, device/ruos/common/ruos_common.mk)'
INJECTED=0
for cand in \
    "$LOS_DIR/device/google/panther/lineage_panther.mk" \
    "$LOS_DIR/device/google/panther/device.mk" \
    "$LOS_DIR/device/google/pantah/lineage_panther.mk"; do
    if [ -f "$cand" ]; then
        if grep -q "ruos_common.mk" "$cand"; then
            log "RuOS already injected into $(basename "$cand")"
        else
            log "Injecting RuOS payload → $cand"
            printf '\n# ===== RuOS payload (added by integrate_into_lineage.sh) =====\n%s\n' "$RUOS_INHERIT" >> "$cand"
        fi
        INJECTED=1; break
    fi
done
[ "$INJECTED" = 1 ] || {
    log "ACTION NEEDED: could not find the lineage_panther product makefile."
    log "  Append this line to it by hand (the file that sets PRODUCT_NAME := lineage_panther):"
    log "    $RUOS_INHERIT"
}

# ── 4. Dynamic-partition + vendor board overrides (the resize-logical-partition fix) ──
BOARD_OVR="$RUOS_DIR/device/ruos/panther/board_overrides.mk"
BC_INJECTED=0
for bc in \
    "$LOS_DIR/device/google/panther/BoardConfig.mk" \
    "$LOS_DIR/device/google/pantah/BoardConfig.mk" \
    "$LOS_DIR/device/google/gs201/BoardConfigCommon.mk"; do
    if [ -f "$bc" ]; then
        if grep -q "RuOS board overrides for panther" "$bc"; then
            log "BoardConfig already has RuOS partition overrides ($(basename "$bc"))"
        else
            log "Appending RuOS dynamic-partition + vendor overrides → $bc"
            { echo ""; echo "# ===== appended by RuOS integrate_into_lineage.sh ====="; cat "$BOARD_OVR"; } >> "$bc"
        fi
        BC_INJECTED=1; break
    fi
done
[ "$BC_INJECTED" = 1 ] || log "ACTION NEEDED: append device/ruos/panther/board_overrides.mk to panther's BoardConfig.mk by hand."

# ── 5. Double-press power → MIR Pay (optional framework patch) ──────────────────
SVC="$LOS_DIR/frameworks/base/services/core/java/com/android/server"
if [ -d "$SVC" ]; then
    mkdir -p "$SVC/ruos"
    cp "$RUOS_DIR/frameworks/base/services/core/java/com/android/server/ruos/RuosPowerGesture.java" "$SVC/ruos/" 2>/dev/null || true
    log "RuosPowerGesture helper copied (patch GestureLauncherService as in integrate_into_aosp.sh §6d if you want the power-gesture remap)."
fi

# ── 6. Summary ─────────────────────────────────────────────────────────────────
log ""
log "Done. Now build (your commands, unchanged):"
log "  cd $LOS_DIR"
log "  source build/envsetup.sh"
log "  breakfast panther"
log "  mka bacon            # → out/target/product/panther/lineage-21.0-*-panther.zip"
log ""
log "Before flashing, run the RuOS static gate from the RuOS repo:"
log "  python3 $RUOS_DIR/vendor/ruos/tools/verify/verify_build.py"
log "Flash with: $RUOS_DIR/tools/flash-all.sh   (or sideload the bacon zip in recovery)"
