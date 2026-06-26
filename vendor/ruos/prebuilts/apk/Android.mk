# Russian app suite — prebuilt APK definitions
#
# Place each APK in the matching subdirectory before building.
# Modules whose APK file is absent are skipped automatically so the
# build continues without them.  Add them when you have the binaries.
#
# Example layout:
#   vendor/ruos/prebuilts/apk/VK/VK.apk
#   vendor/ruos/prebuilts/apk/RuStore/RuStore.apk
#   ...

LOCAL_PATH := $(call my-dir)

# Macro: define a prebuilt APK only if the file exists
define add-prebuilt-apk
$(eval _apk_file := $(LOCAL_PATH)/$(1)/$(1).apk)
$(if $(wildcard $(_apk_file)), \
    $(eval include $(CLEAR_VARS)) \
    $(eval LOCAL_MODULE := $(1)) \
    $(eval LOCAL_MODULE_CLASS := APPS) \
    $(eval LOCAL_MODULE_TAGS := optional) \
    $(eval LOCAL_BUILT_MODULE_STEM := package.apk) \
    $(eval LOCAL_MODULE_SUFFIX := $(COMMON_ANDROID_PACKAGE_SUFFIX)) \
    $(eval LOCAL_PRIVILEGED_MODULE := $(2)) \
    $(eval LOCAL_CERTIFICATE := PRESIGNED) \
    $(eval LOCAL_SRC_FILES := $(1)/$(1).apk) \
    $(eval include $(BUILD_PREBUILT)) \
, \
    $(warning RuOS: prebuilt APK not found: $(1)/$(1).apk — skipping) \
)
endef

$(call add-prebuilt-apk,VK,false)
$(call add-prebuilt-apk,MAX,false)
$(call add-prebuilt-apk,RuStore,true)
$(call add-prebuilt-apk,YandexBrowser,false)
$(call add-prebuilt-apk,YandexMaps,false)
$(call add-prebuilt-apk,YandexGo,false)
$(call add-prebuilt-apk,Gosuslugi,false)
$(call add-prebuilt-apk,RuTube,false)
$(call add-prebuilt-apk,VKMusic,false)
$(call add-prebuilt-apk,VKPay,false)
$(call add-prebuilt-apk,YandexMail,false)
$(call add-prebuilt-apk,YandexDisk,false)
