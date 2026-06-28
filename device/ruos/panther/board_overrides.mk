# ─────────────────────────────────────────────────────────────────────────────
# RuOS board overrides for panther (Pixel 7) — dynamic partitions + vendor image.
#
# WHY: RuOS adds ~30 system apps + SystemUI/framework mods, so system.img grows past
# the stock dynamic-partition GROUP budget. fastbootd then can't resize the logical
# partition and flashing dies with:
#     FAILED (remote: 'Invalid command resize-logical-partition:system_a:1035993088')
#
# This file is NOT a standalone BoardConfig.mk (AOSP errors if a device has two of those).
# It is APPENDED to the upstream device/google/pantah/panther/BoardConfig.mk by
# tools/integrate_into_aosp.sh, so every value here is a *last-wins* override of Google's.
# Because it is appended AFTER the upstream config, $(BOARD_SUPER_PARTITION_SIZE) below is
# already the device's real, hardware-correct super size — we never hard-code it.
# ─────────────────────────────────────────────────────────────────────────────

# Panther launched with dynamic partitions + virtual A/B (NOT retrofit), so the physical
# `super` holds ONE live copy of the read-only partitions. Give the single group the whole
# super minus 4 MiB of partition-table/metadata overhead — the maximum room possible, which
# comfortably fits the enlarged RuOS system. Derived from the real super size, so it stays
# correct whatever panther's actual super partition is.
BOARD_SUPER_PARTITION_GROUPS := google_dynamic_partitions
BOARD_GOOGLE_DYNAMIC_PARTITIONS_SIZE := $(shell echo $$(( $(BOARD_SUPER_PARTITION_SIZE) - 4194304 )))
BOARD_GOOGLE_DYNAMIC_PARTITIONS_PARTITION_LIST := \
    system system_ext product vendor vendor_dlkm system_dlkm

# Make sure a vendor partition is actually assembled and emitted. On panther `vendor` is a
# LOGICAL partition INSIDE super — so a "missing vendor.img" almost always means it was
# flashed separately / not built, not that the layout lacks it. These are normally inherited
# from aosp_panther; we assert them so a config regression can't silently drop vendor.
BOARD_USES_VENDORIMAGE := true
TARGET_COPY_OUT_VENDOR := vendor
BOARD_USES_VENDORDLKMIMAGE := true
TARGET_COPY_OUT_VENDOR_DLKM := vendor_dlkm

# Build the MERGED super image (super.img) by default. Flashing the single super image in
# fastbootd sidesteps per-partition resize-logical-partition entirely — and because vendor
# lives inside super, this also flashes vendor in one shot.
BOARD_BUILD_SUPER_IMAGE_BY_DEFAULT := true

# Modified /system means AVB verity must not gate boot. We flash vbmeta with
# --disable-verity --disable-verification (see tools/flash-all.sh); allow that here too.
BOARD_AVB_MAKE_VBMETA_IMAGE_ARGS += --set_hashtree_disabled_flag
