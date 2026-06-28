# RuOS on LineageOS 21 — Pixel 7 (panther)

Why LineageOS: it ships proper panther support (kernel, gs201 device trees, vendor wiring),
so it boots where a bare AOSP target can stall. RuOS layers on top **unchanged** — all 34
apps, SystemUI customisations, branding, gestures.

> These steps run on **your** build box (the Hetzner server). The RuOS side is just step 4,
> which `tools/integrate_into_lineage.sh` automates. Everything else is standard LineageOS.

## 0. Prereqs
- LineageOS 21 (`lineage-21.0`) synced to `/mnt/HC_Volume_106163271/lineage`.
- ~400 GB free, 32 GB+ RAM, platform-tools installed.

## 1. Device tree + dependencies
The cleanest way is to let LineageOS pull the device + its deps via the roomservice/
dependencies mechanism, which also brings `pantah`, `gs201` and the kernel:
```bash
cd /mnt/HC_Volume_106163271/lineage
source build/envsetup.sh
breakfast panther          # resolves & fetches device_google_panther + dependencies
```
If you prefer to clone manually (then re-run `breakfast panther` to fetch the rest):
```bash
git clone https://github.com/LineageOS/android_device_google_panther -b lineage-21.0 device/google/panther
git clone https://github.com/LineageOS/android_kernel_google_gs201   -b lineage-21.0 kernel/google/gs201
# panther also depends on device/google/pantah, device/google/gs201,
# hardware/google/*, and the gs201 kernel-devicetree repos — breakfast pulls these
# from the device's lineage.dependencies; let it.
```

## 2. Kernel
Handled by step 1 (`kernel/google/gs201` + its dependent dtb/dtbo repos). LineageOS builds
the kernel from source; you do **not** need a prebuilt kernel image.

## 3. Vendor (proprietary) files
LineageOS expects vendor blobs under `vendor/google_devices/` (or `vendor/google/`).
Extract them — either from the matching Google factory image, or from a device on the
matching build:
```bash
# from the factory/OTA image that MATCHES your lineage-21.0 build fingerprint:
cd device/google/panther
./extract-files.sh /path/to/panther-ota.zip      # writes vendor/google_devices/panther/...
# (pantah/gs201 have their own extract-files.sh — run those too if present)
```
You mentioned blobs already at `/mnt/HC_Volume_106163271/aosp/vendor/google_devices/`. If the
**build fingerprint matches** lineage-21.0, copy them across:
```bash
cp -a /mnt/HC_Volume_106163271/aosp/vendor/google_devices/* \
      /mnt/HC_Volume_106163271/lineage/vendor/google_devices/
```
> Mismatch warning: vendor blobs MUST match the platform build. The old `UP1A.231005.007`
> AOSP blobs may not match lineage-21.0 — if you see SELinux/HAL crashes at boot, re-extract
> from the image whose fingerprint matches the LineageOS branch.

## 4. Integrate RuOS  ← the only RuOS-specific step
```bash
cd /path/to/RuOS                      # this repo (git clone it on the box)
git checkout claude/ruos-android-rom-oi3wt1
./tools/integrate_into_lineage.sh /mnt/HC_Volume_106163271/lineage
```
What it does (idempotent, links + injects — never builds):
- links `vendor/ruos`, `device/ruos`, `packages/apps/RuOS*`, SystemUI `ruos-src`+`ruos-res`;
- **injects** `device/ruos/common/ruos_common.mk` into the stock `lineage_panther` product
  (so `breakfast panther` pulls in all RuOS packages, overlays and branding — without
  changing the product name);
- appends the dynamic-partition + vendor **board overrides** to panther's `BoardConfig.mk`
  (the fix for `resize-logical-partition:system_a:…`).
- It prints **ACTION NEEDED** lines for the two things it can't safely guess (adding
  `ruos-src` globs to `SystemUI/Android.bp`, and — if file names drifted — which product
  makefile to inject into). Do those if shown.

## 5. Build
```bash
cd /mnt/HC_Volume_106163271/lineage
source build/envsetup.sh
breakfast panther
mka bacon
```

## 6. Output
```
out/target/product/panther/lineage-21.0-<date>-UNOFFICIAL-panther.zip
```
Flash by sideload in LineageOS recovery:
```bash
adb reboot sideload
adb sideload out/target/product/panther/lineage-21.0-*-panther.zip
```
or fastboot-flash the images with `RuOS/tools/flash-all.sh` (flashes the merged super.img,
vbmeta with verity/verification disabled — avoids the resize-logical-partition error).

## Before you flash
Run the RuOS static gate from the RuOS repo — it catches the build-breakers we already hit
(member-hiding compile errors, hidden-API/sdk mismatches, phantom `PRODUCT_PACKAGES`
modules, malformed makefiles/scripts) before the multi-hour build:
```bash
python3 RuOS/vendor/ruos/tools/verify/verify_build.py
```

## First boot
```bash
adb logcat -b crash AndroidRuntime:E *:S     # any crash
adb logcat -s RuOSSystemUI:E                 # RuOS guard logs (a feature that bailed safely)
```

## Honest caveats
- I could not validate this against your *actual* synced LineageOS tree (it's on your
  server). The script is written to LineageOS-21 conventions and **detects** file names
  rather than assuming; where it can't, it tells you exactly what to edit.
- LineageOS SystemUI has diverged from AOSP. The RuOS `ruos-src` compiles against the same
  platform APIs, but if a hook references a class LineageOS moved, the build will name the
  file — send me that error and I'll adapt the hook.
