# RuOS product configuration for Pixel 7 (panther)
$(call inherit-product, device/google/panther/aosp_panther.mk)
$(call inherit-product, vendor/ruos/ruos.mk)
$(call inherit-product, device/ruos/common/ruos_common.mk)

PRODUCT_NAME := ruos_panther
PRODUCT_DEVICE := panther
PRODUCT_BRAND := RuOS
PRODUCT_MODEL := RuOS Phone 1
PRODUCT_MANUFACTURER := RuOS
