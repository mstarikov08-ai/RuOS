#!/usr/bin/env bash
# Extract the OFFICIAL launcher icons from third-party APKs.
#
# IMPORTANT: RuOS does NOT redraw third-party icons. At runtime the launcher renders
# each installed app's own icon (AppRepository uses PackageManager.loadIcon), so RuStore,
# Yandex (Карты/Браузер/Музыка/Go), VK, MAX, MIR Pay, Госуслуги, RuTube and every other
# pre-installed app already show their official icons with no work from us.
#
# This script is only for producing a marketing/QA contact sheet of those official icons
# from a folder of the actual APKs. It requires the real APKs (not in this repo) and
# aapt2 (from the Android SDK build-tools) + unzip.
#
# Usage: tools/pull_thirdparty_icons.sh <dir-with-apks> <out-dir>
set -euo pipefail

APK_DIR="${1:?usage: pull_thirdparty_icons.sh <apk-dir> <out-dir>}"
OUT_DIR="${2:?usage: pull_thirdparty_icons.sh <apk-dir> <out-dir>}"
mkdir -p "$OUT_DIR"

command -v aapt2 >/dev/null || { echo "aapt2 not found (Android SDK build-tools)"; exit 1; }

for apk in "$APK_DIR"/*.apk; do
    [ -e "$apk" ] || continue
    pkg=$(aapt2 dump packagename "$apk" 2>/dev/null || basename "$apk" .apk)
    # resolve the application android:icon resource path, prefer the highest density
    icon=$(aapt2 dump badging "$apk" 2>/dev/null \
        | sed -n "s/.*application-icon-640:'\([^']*\)'.*/\1/p" | head -1)
    [ -z "$icon" ] && icon=$(aapt2 dump badging "$apk" 2>/dev/null \
        | sed -n "s/application: .*icon='\([^']*\)'.*/\1/p" | head -1)
    if [ -n "$icon" ]; then
        # adaptive icons are XML; fall back to any PNG/WEBP in mipmap-xxxhdpi
        case "$icon" in
            *.png|*.webp) unzip -o -j "$apk" "$icon" -d "$OUT_DIR" >/dev/null
                          mv "$OUT_DIR/$(basename "$icon")" "$OUT_DIR/$pkg.${icon##*.}" ;;
            *) echo "  $pkg: adaptive/xml icon — extract foreground manually if needed" ;;
        esac
        echo "  $pkg -> $OUT_DIR/$pkg"
    else
        echo "  $pkg: icon not found"
    fi
done
echo "Done. Official icons in $OUT_DIR (used for QA/marketing only; runtime uses the APKs directly)."
