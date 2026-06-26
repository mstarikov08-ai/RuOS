# RuOS for Pixel 7 (panther)

$(call inherit-product, device/google/panther/device.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/core_64_bit.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/full_base_telephony.mk)
$(call inherit-product, device/ruos/common/ruos_common.mk)

PRODUCT_NAME := ruos_pixel7
PRODUCT_DEVICE := panther
PRODUCT_BRAND := RuOS
PRODUCT_MODEL := RuOS Pixel 7
PRODUCT_MANUFACTURER := Google

PRODUCT_BUILD_PROP_OVERRIDES += \
    BuildDesc="panther-user 14 AP4A.250605.002 12786088 release-keys" \
    BuildFingerprint=google/panther/panther:14/AP4A.250605.002/12786088:user/release-keys

PRODUCT_PROPERTY_OVERRIDES += \
    ro.vendor.display.refresh_rate=90
