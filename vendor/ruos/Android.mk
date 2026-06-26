# vendor/ruos top-level Android.mk
# Pulls in all submodules: prebuilt APKs and fonts.

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

# Prebuilt APKs (VK, RuStore, YandexBrowser, etc.)
include $(LOCAL_PATH)/prebuilts/apk/Android.mk

# Golos Text font prebuilts
include $(LOCAL_PATH)/prebuilts/fonts/Android.mk
