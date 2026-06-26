# Russian app suite — prebuilt APK definitions
# Each APK must be placed at the path specified below.
# Signed with the platform certificate or the app's own release certificate.

LOCAL_PATH := $(call my-dir)

# ---------------------------------------------------------------------------
# VK — Социальная сеть ВКонтакте
# ---------------------------------------------------------------------------
include $(CLEAR_VARS)
LOCAL_MODULE := VK
LOCAL_MODULE_CLASS := APPS
LOCAL_MODULE_TAGS := optional
LOCAL_BUILT_MODULE_STEM := package.apk
LOCAL_MODULE_SUFFIX := $(COMMON_ANDROID_PACKAGE_SUFFIX)
LOCAL_PRIVILEGED_MODULE := false
LOCAL_CERTIFICATE := PRESIGNED
LOCAL_SRC_FILES := VK/VK.apk
LOCAL_OVERRIDES_PACKAGES :=
include $(BUILD_PREBUILT)

# ---------------------------------------------------------------------------
# MAX (Mail.ru Group)
# ---------------------------------------------------------------------------
include $(CLEAR_VARS)
LOCAL_MODULE := MAX
LOCAL_MODULE_CLASS := APPS
LOCAL_MODULE_TAGS := optional
LOCAL_BUILT_MODULE_STEM := package.apk
LOCAL_MODULE_SUFFIX := $(COMMON_ANDROID_PACKAGE_SUFFIX)
LOCAL_CERTIFICATE := PRESIGNED
LOCAL_SRC_FILES := MAX/MAX.apk
include $(BUILD_PREBUILT)

# ---------------------------------------------------------------------------
# RuStore
# ---------------------------------------------------------------------------
include $(CLEAR_VARS)
LOCAL_MODULE := RuStore
LOCAL_MODULE_CLASS := APPS
LOCAL_MODULE_TAGS := optional
LOCAL_BUILT_MODULE_STEM := package.apk
LOCAL_MODULE_SUFFIX := $(COMMON_ANDROID_PACKAGE_SUFFIX)
LOCAL_PRIVILEGED_MODULE := true
LOCAL_CERTIFICATE := PRESIGNED
LOCAL_SRC_FILES := RuStore/RuStore.apk
include $(BUILD_PREBUILT)

# ---------------------------------------------------------------------------
# Yandex Browser
# ---------------------------------------------------------------------------
include $(CLEAR_VARS)
LOCAL_MODULE := YandexBrowser
LOCAL_MODULE_CLASS := APPS
LOCAL_MODULE_TAGS := optional
LOCAL_BUILT_MODULE_STEM := package.apk
LOCAL_MODULE_SUFFIX := $(COMMON_ANDROID_PACKAGE_SUFFIX)
LOCAL_CERTIFICATE := PRESIGNED
LOCAL_SRC_FILES := YandexBrowser/YandexBrowser.apk
include $(BUILD_PREBUILT)

# ---------------------------------------------------------------------------
# Yandex Maps
# ---------------------------------------------------------------------------
include $(CLEAR_VARS)
LOCAL_MODULE := YandexMaps
LOCAL_MODULE_CLASS := APPS
LOCAL_MODULE_TAGS := optional
LOCAL_BUILT_MODULE_STEM := package.apk
LOCAL_MODULE_SUFFIX := $(COMMON_ANDROID_PACKAGE_SUFFIX)
LOCAL_CERTIFICATE := PRESIGNED
LOCAL_SRC_FILES := YandexMaps/YandexMaps.apk
include $(BUILD_PREBUILT)

# ---------------------------------------------------------------------------
# Yandex Go (taxi/delivery)
# ---------------------------------------------------------------------------
include $(CLEAR_VARS)
LOCAL_MODULE := YandexGo
LOCAL_MODULE_CLASS := APPS
LOCAL_MODULE_TAGS := optional
LOCAL_BUILT_MODULE_STEM := package.apk
LOCAL_MODULE_SUFFIX := $(COMMON_ANDROID_PACKAGE_SUFFIX)
LOCAL_CERTIFICATE := PRESIGNED
LOCAL_SRC_FILES := YandexGo/YandexGo.apk
include $(BUILD_PREBUILT)

# ---------------------------------------------------------------------------
# Gosuslugi (Государственные услуги)
# ---------------------------------------------------------------------------
include $(CLEAR_VARS)
LOCAL_MODULE := Gosuslugi
LOCAL_MODULE_CLASS := APPS
LOCAL_MODULE_TAGS := optional
LOCAL_BUILT_MODULE_STEM := package.apk
LOCAL_MODULE_SUFFIX := $(COMMON_ANDROID_PACKAGE_SUFFIX)
LOCAL_CERTIFICATE := PRESIGNED
LOCAL_SRC_FILES := Gosuslugi/Gosuslugi.apk
include $(BUILD_PREBUILT)

# ---------------------------------------------------------------------------
# RuTube
# ---------------------------------------------------------------------------
include $(CLEAR_VARS)
LOCAL_MODULE := RuTube
LOCAL_MODULE_CLASS := APPS
LOCAL_MODULE_TAGS := optional
LOCAL_BUILT_MODULE_STEM := package.apk
LOCAL_MODULE_SUFFIX := $(COMMON_ANDROID_PACKAGE_SUFFIX)
LOCAL_CERTIFICATE := PRESIGNED
LOCAL_SRC_FILES := RuTube/RuTube.apk
include $(BUILD_PREBUILT)

# ---------------------------------------------------------------------------
# VK Music
# ---------------------------------------------------------------------------
include $(CLEAR_VARS)
LOCAL_MODULE := VKMusic
LOCAL_MODULE_CLASS := APPS
LOCAL_MODULE_TAGS := optional
LOCAL_BUILT_MODULE_STEM := package.apk
LOCAL_MODULE_SUFFIX := $(COMMON_ANDROID_PACKAGE_SUFFIX)
LOCAL_CERTIFICATE := PRESIGNED
LOCAL_SRC_FILES := VKMusic/VKMusic.apk
include $(BUILD_PREBUILT)

# ---------------------------------------------------------------------------
# VK Pay
# ---------------------------------------------------------------------------
include $(CLEAR_VARS)
LOCAL_MODULE := VKPay
LOCAL_MODULE_CLASS := APPS
LOCAL_MODULE_TAGS := optional
LOCAL_BUILT_MODULE_STEM := package.apk
LOCAL_MODULE_SUFFIX := $(COMMON_ANDROID_PACKAGE_SUFFIX)
LOCAL_CERTIFICATE := PRESIGNED
LOCAL_SRC_FILES := VKPay/VKPay.apk
include $(BUILD_PREBUILT)

# ---------------------------------------------------------------------------
# Yandex Mail
# ---------------------------------------------------------------------------
include $(CLEAR_VARS)
LOCAL_MODULE := YandexMail
LOCAL_MODULE_CLASS := APPS
LOCAL_MODULE_TAGS := optional
LOCAL_BUILT_MODULE_STEM := package.apk
LOCAL_MODULE_SUFFIX := $(COMMON_ANDROID_PACKAGE_SUFFIX)
LOCAL_CERTIFICATE := PRESIGNED
LOCAL_SRC_FILES := YandexMail/YandexMail.apk
include $(BUILD_PREBUILT)

# ---------------------------------------------------------------------------
# Yandex Disk
# ---------------------------------------------------------------------------
include $(CLEAR_VARS)
LOCAL_MODULE := YandexDisk
LOCAL_MODULE_CLASS := APPS
LOCAL_MODULE_TAGS := optional
LOCAL_BUILT_MODULE_STEM := package.apk
LOCAL_MODULE_SUFFIX := $(COMMON_ANDROID_PACKAGE_SUFFIX)
LOCAL_CERTIFICATE := PRESIGNED
LOCAL_SRC_FILES := YandexDisk/YandexDisk.apk
include $(BUILD_PREBUILT)
