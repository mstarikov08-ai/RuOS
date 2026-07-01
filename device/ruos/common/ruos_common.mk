# RuOS common device configuration
# Applied to all RuOS targets

PRODUCT_BRAND := RuOS
PRODUCT_MANUFACTURER := RuOS
PRODUCT_SYSTEM_NAME := RuOS
PRODUCT_SYSTEM_BRAND := RuOS

RUOS_VERSION_MAJOR := 1
RUOS_VERSION_MINOR := 0
RUOS_VERSION_CODE := DEV

RUOS_BUILD_TYPE ?= DEV
RUOS_BUILD_ID := RuOS-$(RUOS_VERSION_MAJOR).$(RUOS_VERSION_MINOR)-$(RUOS_BUILD_TYPE)

# --------------------------------------------------------------------------
# Display / Performance
# --------------------------------------------------------------------------
# Force 120 Hz on all supported Pixel displays
PRODUCT_PROPERTY_OVERRIDES += \
    ro.surface_flinger.enable_frame_rate_override=false \
    ro.surface_flinger.set_display_power_timer_ms=10000 \
    debug.sf.enable_advanced_sf_phase_offset=1 \
    debug.sf.high_fps_late_sf_phase_offset_ns=-1000000 \
    debug.sf.high_fps_late_app_phase_offset_ns=-1000000 \
    ro.vendor.display.mode.enable=true

# Touch sampling at max rate
PRODUCT_PROPERTY_OVERRIDES += \
    ro.input.dev.sampling_rate=1000 \
    persist.sys.touch.boost=true

# CPU scheduling for Russian apps
PRODUCT_PROPERTY_OVERRIDES += \
    ro.ruos.vip_packages=com.vk.android,ru.mail.search.mail,ru.rustore,com.yandex.browser

# --------------------------------------------------------------------------
# Gesture navigation
# --------------------------------------------------------------------------
PRODUCT_PROPERTY_OVERRIDES += \
    ro.boot.hardware.udfps=false \
    persist.wm.debug.nav_bar_gesture=true \
    persist.wm.debug.sysui_nav_bar_gestural=true

# --------------------------------------------------------------------------
# Security
# --------------------------------------------------------------------------
PRODUCT_PROPERTY_OVERRIDES += \
    ro.adb.secure=1 \
    ro.secure=1 \
    ro.debuggable=0

# Block sideloading by default; developer mode gate in Settings
PRODUCT_PROPERTY_OVERRIDES += \
    persist.sys.unknown_sources_default=false

# --------------------------------------------------------------------------
# Locale / Language
# --------------------------------------------------------------------------
PRODUCT_LOCALES := ru_RU en_US

PRODUCT_PROPERTY_OVERRIDES += \
    ro.product.locale=ru-RU \
    persist.sys.locale=ru-RU \
    persist.sys.language=ru \
    persist.sys.country=RU

# --------------------------------------------------------------------------
# Fonts — Golos Text
# --------------------------------------------------------------------------
PRODUCT_COPY_FILES += \
    vendor/ruos/prebuilts/fonts/GolosText/GolosText-Regular.ttf:$(TARGET_COPY_OUT_PRODUCT)/fonts/GolosText-Regular.ttf \
    vendor/ruos/prebuilts/fonts/GolosText/GolosText-Medium.ttf:$(TARGET_COPY_OUT_PRODUCT)/fonts/GolosText-Medium.ttf \
    vendor/ruos/prebuilts/fonts/GolosText/GolosText-Bold.ttf:$(TARGET_COPY_OUT_PRODUCT)/fonts/GolosText-Bold.ttf \
    vendor/ruos/prebuilts/fonts/GolosText/GolosText-Thin.ttf:$(TARGET_COPY_OUT_PRODUCT)/fonts/GolosText-Thin.ttf

# --------------------------------------------------------------------------
# Boot animation
# --------------------------------------------------------------------------
PRODUCT_COPY_FILES += \
    vendor/ruos/bootanimation/bootanimation.zip:$(TARGET_COPY_OUT_SYSTEM)/media/bootanimation.zip

# --------------------------------------------------------------------------
# Wallpapers
# --------------------------------------------------------------------------
PRODUCT_COPY_FILES += \
    vendor/ruos/wallpapers/ruos_space_01.jpg:$(TARGET_COPY_OUT_PRODUCT)/media/wallpapers/ruos_space_01.jpg \
    vendor/ruos/wallpapers/ruos_space_02.jpg:$(TARGET_COPY_OUT_PRODUCT)/media/wallpapers/ruos_space_02.jpg \
    vendor/ruos/wallpapers/ruos_space_03.jpg:$(TARGET_COPY_OUT_PRODUCT)/media/wallpapers/ruos_space_03.jpg \
    vendor/ruos/wallpapers/ruos_space_04.jpg:$(TARGET_COPY_OUT_PRODUCT)/media/wallpapers/ruos_space_04.jpg \
    vendor/ruos/wallpapers/ruos_space_05.jpg:$(TARGET_COPY_OUT_PRODUCT)/media/wallpapers/ruos_space_05.jpg \
    vendor/ruos/wallpapers/ruos_space_01.jpg:$(TARGET_COPY_OUT_PRODUCT)/media/wallpaper/default_wallpaper.jpg

# --------------------------------------------------------------------------
# RuOS core packages
# --------------------------------------------------------------------------
# NB: the SystemUI customisations are NOT a separate APK — they are patched into
# SystemUI via frameworks/base/.../SystemUI/ruos-src by the integration script. So there
# is no "RuOSSystemUIExtensions" module; listing it here breaks the build with
# "Can not locate config makefile for module 'RuOSSystemUIExtensions'".
# Full RuOS app set (every module in packages/apps/RuOS*/Android.bp):
PRODUCT_PACKAGES += \
    RuOSAlarm \
    RuOSAssist \
    RuOSAuth \
    RuOSBackup \
    RuOSBrowser \
    RuOSCalculator \
    RuOSCalendar \
    RuOSCamera \
    RuOSClock \
    RuOSContacts \
    RuOSEmergency \
    RuOSFiles \
    RuOSFindMy \
    RuOSFocus \
    RuOSGallery \
    RuOSHealth \
    RuOSJournal \
    RuOSKeyboard \
    RuOSKeychain \
    RuOSLauncher \
    RuOSMail \
    RuOSMaps \
    RuOSMessages \
    RuOSMusic \
    RuOSNotes \
    RuOSNotify \
    RuOSPhone \
    RuOSReminders \
    RuOSScreenRecord \
    RuOSScreenshot \
    RuOSSettings \
    RuOSShare \
    RuOSStandby \
    RuOSTextActions \
    RuOSUpdate \
    RuOSWeather

# --------------------------------------------------------------------------
# Russian app suite (prebuilt APKs)
# --------------------------------------------------------------------------
PRODUCT_PACKAGES += \
    VK \
    MAX \
    RuStore \
    YandexBrowser \
    YandexMaps \
    YandexGo \
    Gosuslugi \
    RuTube \
    VKMusic \
    VKPay \
    YandexMail \
    YandexDisk

# Default search engine — Yandex
PRODUCT_PROPERTY_OVERRIDES += \
    ro.ruos.default_search_engine=yandex.ru \
    ro.ruos.default_browser=com.yandex.browser \
    ro.ruos.default_app_store=ru.rustore

# --------------------------------------------------------------------------
# Resource overlay
# --------------------------------------------------------------------------
PRODUCT_PACKAGE_OVERLAYS += vendor/ruos/overlay
