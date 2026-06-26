# RuOS for Pixel 8 (shiba)

$(call inherit-product, device/google/shiba/device.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/core_64_bit.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/full_base_telephony.mk)
$(call inherit-product, device/ruos/common/ruos_common.mk)

PRODUCT_NAME := ruos_pixel8
PRODUCT_DEVICE := shiba
PRODUCT_BRAND := RuOS
PRODUCT_MODEL := RuOS Pixel 8
PRODUCT_MANUFACTURER := Google

PRODUCT_BUILD_PROP_OVERRIDES += \
    BuildDesc="shiba-user 14 AP4A.250605.002 12786088 release-keys" \
    BuildFingerprint=google/shiba/shiba:14/AP4A.250605.002/12786088:user/release-keys

PRODUCT_PROPERTY_OVERRIDES += \
    ro.vendor.display.refresh_rate=120
