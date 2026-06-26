# RuOS for Pixel 9 (tokay)

$(call inherit-product, device/google/tokay/device.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/core_64_bit.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/full_base_telephony.mk)
$(call inherit-product, device/ruos/common/ruos_common.mk)

PRODUCT_NAME := ruos_pixel9
PRODUCT_DEVICE := tokay
PRODUCT_BRAND := RuOS
PRODUCT_MODEL := RuOS Pixel 9
PRODUCT_MANUFACTURER := Google

PRODUCT_BUILD_PROP_OVERRIDES += \
    BuildDesc="tokay-user 14 AP4A.250605.002 12786088 release-keys" \
    BuildFingerprint=google/tokay/tokay:14/AP4A.250605.002/12786088:user/release-keys

# 120 Hz always-on for LTPO panel
PRODUCT_PROPERTY_OVERRIDES += \
    ro.surface_flinger.set_touch_timer_ms=200 \
    ro.surface_flinger.set_idle_timer_ms=100 \
    ro.surface_flinger.use_content_detection_for_refresh_rate=false \
    ro.vendor.display.refresh_rate=120 \
    persist.sys.displayinfostore.enable=true
