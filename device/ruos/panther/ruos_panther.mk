# RuOS product configuration for Pixel 7 (panther).
#
# Base is LineageOS 21 (it has proper panther support). On a LineageOS tree the
# real build target is the stock `lineage_panther` product (built with
# `breakfast panther`), into which tools/integrate_into_lineage.sh injects RuOS —
# so you normally do NOT lunch this `ruos_panther` product on LineageOS.
#
# This makefile remains as a standalone RuOS product for either base: it inherits
# whichever panther product the tree actually provides (LineageOS first, AOSP as a
# fallback), then layers RuOS on top.
$(call inherit-product-if-exists, device/google/panther/lineage_panther.mk)
$(call inherit-product-if-exists, device/google/panther/aosp_panther.mk)
$(call inherit-product, vendor/ruos/ruos.mk)
$(call inherit-product, device/ruos/common/ruos_common.mk)

PRODUCT_NAME := ruos_panther
PRODUCT_DEVICE := panther
PRODUCT_BRAND := RuOS
PRODUCT_MODEL := RuOS Phone 1
PRODUCT_MANUFACTURER := RuOS
