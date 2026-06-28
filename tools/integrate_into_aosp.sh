#!/bin/bash
#
# RuOS → AOSP Integration Script
#
# Run this from the RUOS repo root while the AOSP tree is at ~/aosp
# (or pass the AOSP path as the first argument).
#
# Usage: ./tools/integrate_into_aosp.sh [/path/to/aosp]
#
# This script does NOT interrupt a running build — it only writes files
# that will be picked up by the NEXT invocation of make.
#

set -euo pipefail

RUOS_DIR="$(cd "$(dirname "$0")/.." && pwd)"
AOSP_DIR="${1:-$HOME/aosp}"

if [ ! -d "$AOSP_DIR/build/make" ]; then
    echo "ERROR: $AOSP_DIR does not look like an AOSP tree."
    echo "       Run: $0 /path/to/aosp"
    exit 1
fi

log() { echo "[RuOS] $*"; }

# ── 1. Symlink vendor/ruos into the AOSP tree ──────────────────────────────
log "Linking vendor/ruos → $AOSP_DIR/vendor/ruos"
if [ -e "$AOSP_DIR/vendor/ruos" ]; then
    echo "  (already exists — removing old link/dir)"
    rm -rf "$AOSP_DIR/vendor/ruos"
fi
ln -s "$RUOS_DIR/vendor/ruos" "$AOSP_DIR/vendor/ruos"

# ── 2. Symlink device/ruos into the AOSP tree ──────────────────────────────
log "Linking device/ruos → $AOSP_DIR/device/ruos"
if [ -e "$AOSP_DIR/device/ruos" ]; then
    rm -rf "$AOSP_DIR/device/ruos"
fi
ln -s "$RUOS_DIR/device/ruos" "$AOSP_DIR/device/ruos"

# ── 3. Symlink all RuOS app packages ───────────────────────────────────────
log "Linking packages/apps/RuOS* → $AOSP_DIR/packages/apps/"
for appdir in "$RUOS_DIR/packages/apps"/RuOS*; do
    appname="$(basename "$appdir")"
    dest="$AOSP_DIR/packages/apps/$appname"
    if [ -e "$dest" ]; then
        rm -rf "$dest"
    fi
    ln -s "$appdir" "$dest"
    log "  Linked $appname"
done

# ── 4. Patch device/google/panther/aosp_panther.mk ─────────────────────────
PANTHER_MK="$AOSP_DIR/device/google/panther/aosp_panther.mk"
if [ ! -f "$PANTHER_MK" ]; then
    log "WARNING: $PANTHER_MK not found — skipping device patch."
    log "         Manually add: \$(call inherit-product, vendor/ruos/ruos.mk)"
else
    MARKER="# RuOS integration"
    if grep -q "$MARKER" "$PANTHER_MK"; then
        log "aosp_panther.mk already patched — skipping."
    else
        log "Patching $PANTHER_MK"
        cat >> "$PANTHER_MK" << 'EOF'

# RuOS integration
$(call inherit-product, vendor/ruos/ruos.mk)
$(call inherit-product, device/ruos/common/ruos_common.mk)
EOF
        log "  Done."
    fi
fi

# ── 5. Disable stock Launcher3 so RuOS Launcher takes over ─────────────────
COMMON_MK="$RUOS_DIR/device/ruos/common/ruos_common.mk"
if ! grep -q "Launcher3QuickStep" "$COMMON_MK"; then
    log "Disabling Launcher3QuickStep in ruos_common.mk"
    cat >> "$COMMON_MK" << 'EOF'

# Disable stock AOSP launcher — RuOS Launcher is the default
PRODUCT_PACKAGES_DISABLEDCOMP += \
    Launcher3QuickStep \
    Launcher3
EOF
fi

# ── 6. Register Golos Text font in AOSP font config ────────────────────────
FONTS_DIR="$AOSP_DIR/frameworks/base/data/fonts"
FONT_XML="$AOSP_DIR/frameworks/base/data/fonts/fonts.xml"
if [ -f "$FONT_XML" ]; then
    if ! grep -q "GolosText" "$FONT_XML"; then
        log "NOTE: fonts.xml exists but GolosText is not registered."
        log "      Add the following to $FONT_XML inside <familyset>:"
        cat << 'FONT_SNIPPET'
    <!-- RuOS: Golos Text (Russian system font) -->
    <family name="ruos-golos">
        <font weight="400" style="normal">GolosText-Regular.ttf</font>
        <font weight="500" style="normal">GolosText-Medium.ttf</font>
        <font weight="700" style="normal">GolosText-Bold.ttf</font>
        <font weight="100" style="normal">GolosText-Thin.ttf</font>
    </family>
FONT_SNIPPET
    fi
fi

# ── 6b. Fold RuOS SystemUI sources into the SystemUI build ─────────────────────
# The gesture engine, Dynamic Island, Control Center etc. live in
# frameworks/base/packages/SystemUI/ruos-src/. AOSP's SystemUI does NOT compile
# that directory by default, so we link it in and add it to the module's srcs.
RUOS_SYSUI_SRC="$RUOS_DIR/frameworks/base/packages/SystemUI/ruos-src"
AOSP_SYSUI="$AOSP_DIR/frameworks/base/packages/SystemUI"
if [ -d "$AOSP_SYSUI" ]; then
    log "Linking SystemUI ruos-src → $AOSP_SYSUI/ruos-src"
    if [ -e "$AOSP_SYSUI/ruos-src" ]; then rm -rf "$AOSP_SYSUI/ruos-src"; fi
    ln -s "$RUOS_SYSUI_SRC" "$AOSP_SYSUI/ruos-src"

    SYSUI_BP="$AOSP_SYSUI/Android.bp"
    if [ -f "$SYSUI_BP" ] && ! grep -q "ruos-src" "$SYSUI_BP"; then
        log "NOTE: add the RuOS sources to the SystemUI module in $SYSUI_BP"
        log "      Inside the SystemUI-core filegroup / android_library srcs, add:"
        cat << 'BP_SNIPPET'
        // RuOS customisations (gesture engine, Dynamic Island, Control Center)
        "ruos-src/**/*.kt",
        "ruos-src/**/*.aidl",
BP_SNIPPET
        log "      SystemUI already builds with platform_apis + dynamicanimation,"
        log "      so SurfaceControl/recents/SpringAnimation are on the classpath."
    fi
    log ""
    log "      Then call the engine from a CoreStartable. Minimal hook:"
    cat << 'HOOK_SNIPPET'
        // in a RuOS CoreStartable.start() — wrap in try/catch as a final safety net so
        // a RuOS SystemUI failure can never crash SystemUI / boot-loop the device:
        try {
            val m = com.android.systemui.ruos.RuOSSystemUIModule()
            m.initGestureNavigation(context)
            // m.initDynamicIsland(context, statusBarWindow)
            // m.initSystemPanels(context, windowManager)
        } catch (t: Throwable) {
            android.util.Log.e("RuOSSystemUI", "RuOS SystemUI init failed", t)
        }
HOOK_SNIPPET

    # RuOS SystemUI resources (e.g. the charging chime res/raw/ruos_charge.wav). These
    # must land in SystemUI's own res/ so R.raw resolves at runtime.
    RUOS_SYSUI_RES="$RUOS_DIR/frameworks/base/packages/SystemUI/ruos-res"
    if [ -d "$RUOS_SYSUI_RES" ]; then
        log "Copying SystemUI ruos-res → $AOSP_SYSUI/res"
        cp -r "$RUOS_SYSUI_RES/." "$AOSP_SYSUI/res/"
    fi
else
    log "WARNING: $AOSP_SYSUI not found — cannot link SystemUI ruos-src."
fi

# ── 6c. SELinux: allow SystemUI to monitor gesture input ───────────────────────
log ""
log "SELinux: the gesture engine calls InputManager.monitorGestureInput(), which"
log "requires the systemui domain to hold the gesture-monitor capability. If you"
log "see avc denials for 'monitorGestureInput' add to your device sepolicy:"
cat << 'SEPOLICY_SNIPPET'
    # device/ruos/common/sepolicy/systemui.te
    allow systemui_app input_service:service_manager find;
    # (AOSP grants monitorGestureInput to system_app/systemui by default on
    #  user-debug; only needed if your policy is stricter.)
SEPOLICY_SNIPPET

# ── 6d. Double-press power → MIR Pay ───────────────────────────────────────────
# Places the helper class into the AOSP services source (compiles via the globbed
# services srcs — no Android.bp change) and patches GestureLauncherService so the
# double-press-power gesture launches MIR Pay instead of the camera.
SVC_RUOS_DIR="$AOSP_DIR/frameworks/base/services/core/java/com/android/server/ruos"
SVC_HELPER_SRC="$RUOS_DIR/frameworks/base/services/core/java/com/android/server/ruos/RuosPowerGesture.java"
if [ -d "$AOSP_DIR/frameworks/base/services/core/java/com/android/server" ]; then
    log "Installing RuosPowerGesture helper → $SVC_RUOS_DIR"
    mkdir -p "$SVC_RUOS_DIR"
    cp "$SVC_HELPER_SRC" "$SVC_RUOS_DIR/RuosPowerGesture.java"

    GLS="$AOSP_DIR/frameworks/base/services/core/java/com/android/server/GestureLauncherService.java"
    if [ -f "$GLS" ] && ! grep -q "RuosPowerGesture" "$GLS"; then
        log "Patching GestureLauncherService for double-press-power → MIR Pay"
        if ( cd "$AOSP_DIR" && patch -p1 --fuzz=3 \
                < "$RUOS_DIR/vendor/ruos/patches/gesture-launcher-mirpay.patch" ); then
            log "  Patch applied."
        else
            log "  WARNING: auto-patch failed (method may have drifted). Apply by hand:"
            log "    In handleCameraGesture(), before the StatusBarManagerInternal line, add:"
            log "      if (com.android.server.ruos.RuosPowerGesture.launchMirPay(mContext)) return;"
        fi
    else
        log "GestureLauncherService already patched (or not found) — skipping."
    fi
else
    log "WARNING: AOSP services source not found — skipping power-gesture remap."
fi

# ── 6e. Grant RuOSNotify notification-listener access by default ────────────────
log ""
log "RuOSNotify needs notification-listener access to drive banners/badges. It is"
log "not auto-granted by a static overlay; enable it on the device once with:"
cat << 'NLS_SNIPPET'
    adb shell cmd notification allow_listener \
        com.ruos.notify/com.ruos.notify.service.RuOSNotificationListener
    # or persist in the product: add the component to
    #   Settings.Secure.enabled_notification_listeners
    # via a device default in frameworks/base/.../settings/DefaultSettingsProvider,
    # and grant SYSTEM_ALERT_WINDOW (RuOSNotify is privileged/platform-signed).
NLS_SNIPPET

# ── 6f. RuOSFocus notification-policy access (for DND mirroring) ────────────────
log ""
log "RuOSFocus filters banners inside RuOSNotify (no extra grant needed). To ALSO"
log "mirror a focus to the system Do-Not-Disturb policy (so the framework + other"
log "apps go quiet too), grant notification-policy access once:"
cat << 'FOCUS_SNIPPET'
    adb shell cmd notification allow_dnd com.ruos.focus
    # or persist via Settings.Secure: add com.ruos.focus to the policy-access grant.
    # If not granted, FocusController.applyDnd() degrades to a no-op and only the
    # RuOSNotify in-house banner/sound filtering applies (still the core behaviour).
FOCUS_SNIPPET

# ── 6g. Dynamic-partition + vendor board overrides (panther) ───────────────────
# RuOS enlarges /system past the stock dynamic-partition group budget, which makes
# fastbootd fail with 'resize-logical-partition:system_a:...'. We can't drop a second
# BoardConfig.mk for device 'panther' (AOSP errors on duplicates), so APPEND the RuOS
# overrides to Google's BoardConfig — last assignment wins, group is derived from the
# real super size. Idempotent.
PANTHER_BC="$AOSP_DIR/device/google/pantah/panther/BoardConfig.mk"
BOARD_OVR="$RUOS_DIR/device/ruos/panther/board_overrides.mk"
RUOS_BC_MARK="RuOS board overrides for panther"
if [ -f "$PANTHER_BC" ]; then
    if grep -q "$RUOS_BC_MARK" "$PANTHER_BC"; then
        log "Panther BoardConfig already has RuOS partition overrides — skipping."
    else
        log "Appending RuOS dynamic-partition + vendor overrides → $PANTHER_BC"
        {
            echo ""
            echo "# ===== appended by RuOS integrate_into_aosp.sh ====="
            cat "$BOARD_OVR"
        } >> "$PANTHER_BC"
    fi
else
    log "WARNING: $PANTHER_BC not found — cannot apply partition overrides."
    log "         (Sync the panther device tree, or append device/ruos/panther/"
    log "          board_overrides.mk to the correct BoardConfig.mk by hand.)"
fi

# ── 7. Summary ──────────────────────────────────────────────────────────────
log ""
log "Integration complete. To build RuOS:"
log ""
log "  cd $AOSP_DIR"
log "  source build/envsetup.sh"
log "  lunch ruos_panther-userdebug   # or aosp_panther-userdebug"
log "  make -j\$(nproc) && make superimage   # superimage = merged super.img"
log ""
log "Then flash from the bootloader screen (avoids resize-logical-partition):"
log "  adb reboot bootloader"
log "  OUT=\$ANDROID_PRODUCT_OUT $RUOS_DIR/tools/flash-all.sh"
log ""
log "If 'lunch ruos_panther' is not found, use 'aosp_panther-userdebug'"
log "and the ruos_common.mk include in aosp_panther.mk will apply RuOS."
