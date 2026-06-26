# Golos Text font family — Russian-native variable font by NTC Paratype
# Download from: https://fonts.google.com/specimen/Golos+Text
# or fonts.ruos.ru (internal mirror)
#
# Place TTF files in vendor/ruos/prebuilts/fonts/GolosText/

LOCAL_PATH := $(call my-dir)

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
