# Golos Text font family — Russian-native variable font by NTC Paratype
# Download from: https://fonts.google.com/specimen/Golos+Text
#
# Place TTF files in vendor/ruos/prebuilts/fonts/GolosText/
# before building.  If the files are absent the font modules are skipped
# and the build continues without them.

LOCAL_PATH := $(call my-dir)

ifneq ($(wildcard $(LOCAL_PATH)/GolosText/GolosText-Regular.ttf),)

include $(CLEAR_VARS)
LOCAL_MODULE := GolosText-Regular
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_CLASS := ETC
LOCAL_MODULE_PATH := $(TARGET_OUT_PRODUCT)/fonts
LOCAL_SRC_FILES := GolosText/GolosText-Regular.ttf
LOCAL_MODULE_SUFFIX := .ttf
include $(BUILD_PREBUILT)

include $(CLEAR_VARS)
LOCAL_MODULE := GolosText-Medium
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_CLASS := ETC
LOCAL_MODULE_PATH := $(TARGET_OUT_PRODUCT)/fonts
LOCAL_SRC_FILES := GolosText/GolosText-Medium.ttf
LOCAL_MODULE_SUFFIX := .ttf
include $(BUILD_PREBUILT)

include $(CLEAR_VARS)
LOCAL_MODULE := GolosText-Bold
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_CLASS := ETC
LOCAL_MODULE_PATH := $(TARGET_OUT_PRODUCT)/fonts
LOCAL_SRC_FILES := GolosText/GolosText-Bold.ttf
LOCAL_MODULE_SUFFIX := .ttf
include $(BUILD_PREBUILT)

include $(CLEAR_VARS)
LOCAL_MODULE := GolosText-Thin
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_CLASS := ETC
LOCAL_MODULE_PATH := $(TARGET_OUT_PRODUCT)/fonts
LOCAL_SRC_FILES := GolosText/GolosText-Thin.ttf
LOCAL_MODULE_SUFFIX := .ttf
include $(BUILD_PREBUILT)

else
$(warning RuOS: Golos Text fonts not found in vendor/ruos/prebuilts/fonts/GolosText/)
$(warning RuOS: Download from https://fonts.google.com/specimen/Golos+Text and place TTFs there.)
endif
