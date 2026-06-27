#
# RuOS product makefile — included by device/google/panther/aosp_panther.mk
# (or whatever the target product mk is)
#
# Usage in device product mk:
#   $(call inherit-product, vendor/ruos/ruos.mk)
#

PRODUCT_BRAND := RuOS
PRODUCT_MANUFACTURER := RuOS
PRODUCT_NAME := ruos_panther
PRODUCT_MODEL := RuOS Phone
PRODUCT_DEVICE := panther

PRODUCT_CHARACTERISTICS := phone

# ── Branding / versioning ────────────────────────────────────────────────────

RUOS_VERSION := 1.0.0
RUOS_BUILD_DATE := $(shell date -u +%Y%m%d)
RUOS_BUILD_TYPE := BETA

PRODUCT_BUILD_PROP_OVERRIDES += \
    ro.product.name=RuOS \
    ro.product.brand=RuOS \
    ro.product.manufacturer=RuOS \
    ro.build.flavor=ruos_panther-userdebug \
    ro.build.description=ruos_panther-userdebug $(RUOS_VERSION) $(RUOS_BUILD_DATE) \
    ro.ruos.version=$(RUOS_VERSION) \
    ro.ruos.build.type=$(RUOS_BUILD_TYPE) \
    ro.ruos.build.date=$(RUOS_BUILD_DATE)

# ── Overlays ─────────────────────────────────────────────────────────────────

# Static resource overlays (applied at build time)
PRODUCT_PACKAGE_OVERLAYS += vendor/ruos/overlay

# ── Boot animation ───────────────────────────────────────────────────────────

PRODUCT_COPY_FILES += \
    vendor/ruos/bootanimation/bootanimation.zip:$(TARGET_COPY_OUT_PRODUCT)/media/bootanimation.zip \
    vendor/ruos/bootanimation/shutdownanimation.zip:$(TARGET_COPY_OUT_PRODUCT)/media/shutdownanimation.zip

# ── Offline charger / dead-battery screen (healthd/charger via minui) ──────────
PRODUCT_COPY_FILES += \
    vendor/ruos/charger/battery_fail.png:$(TARGET_COPY_OUT_SYSTEM)/etc/res/images/charger/battery_fail.png \
    vendor/ruos/charger/battery_scale.png:$(TARGET_COPY_OUT_SYSTEM)/etc/res/images/charger/battery_scale.png \
    vendor/ruos/charger/animation.txt:$(TARGET_COPY_OUT_SYSTEM)/etc/res/values/charger/animation.txt

# ── Wallpapers ───────────────────────────────────────────────────────────────

PRODUCT_COPY_FILES += \
    vendor/ruos/wallpapers/ruos_space_01.jpg:$(TARGET_COPY_OUT_PRODUCT)/media/wallpapers/ruos_space_01.jpg \
    vendor/ruos/wallpapers/ruos_space_02.jpg:$(TARGET_COPY_OUT_PRODUCT)/media/wallpapers/ruos_space_02.jpg \
    vendor/ruos/wallpapers/ruos_space_03.jpg:$(TARGET_COPY_OUT_PRODUCT)/media/wallpapers/ruos_space_03.jpg \
    vendor/ruos/wallpapers/ruos_space_04.jpg:$(TARGET_COPY_OUT_PRODUCT)/media/wallpapers/ruos_space_04.jpg \
    vendor/ruos/wallpapers/ruos_space_05.jpg:$(TARGET_COPY_OUT_PRODUCT)/media/wallpapers/ruos_space_05.jpg

# ── Default wallpaper ────────────────────────────────────────────────────────

PRODUCT_COPY_FILES += \
    vendor/ruos/wallpapers/ruos_space_01.jpg:$(TARGET_COPY_OUT_PRODUCT)/media/wallpaper/default_wallpaper.jpg

# ── RuOS apps ────────────────────────────────────────────────────────────────

PRODUCT_PACKAGES += \
    RuOSKeyboard \
    RuOSAlarm \
    RuOSNotify \
    RuOSAuth \
    RuOSStandby \
    RuOSJournal \
    RuOSFocus \
    RuOSScreenshot \
    RuOSScreenRecord \
    RuOSKeychain \
    RuOSFindMy \
    RuOSReminders \
    RuOSCalendar \
    RuOSShare \
    RuOSLauncher \
    RuOSSettings \
    RuOSCalculator \
    RuOSClock \
    RuOSNotes \
    RuOSGallery \
    RuOSFiles \
    RuOSHealth \
    RuOSCamera \
    RuOSPhone \
    RuOSContacts \
    RuOSMessages \
    RuOSWeather \
    RuOSBrowser \
    RuOSMusic \
    RuOSMaps \
    RuOSMail

# ── Default launcher ─────────────────────────────────────────────────────────

# Override the default home component so RuOS Launcher is selected on first boot
PRODUCT_PRODUCT_PROPERTIES += \
    ro.product.home=com.ruos.launcher/.RuOSLauncherActivity

# ── Default browser / search (Yandex) ────────────────────────────────────────

# These are set via the AOSP role manager and GMS device config.
# At runtime, the setup wizard will offer the user to confirm or change them.
PRODUCT_SYSTEM_DEFAULT_PROPERTIES += \
    persist.sys.default_browser=com.yandex.browser \
    persist.sys.webview_multiprocess=true

# Default search engine for Chromium-based WebView hint
PRODUCT_PRODUCT_PROPERTIES += \
    ro.ruos.default_search=https://yandex.ru/search/?text=

# ── Remove Google Search app / GSearch ───────────────────────────────────────
# The Google Search APK (com.google.android.googlequicksearchbox) is excluded.
# Yandex Browser covers web search for the Russian market.

PRODUCT_PACKAGES_DISABLEDCOMP += \
    LatinIME \
    QuickSearchBox

# ── Russian locale defaults ───────────────────────────────────────────────────

PRODUCT_SYSTEM_DEFAULT_PROPERTIES += \
    persist.sys.locale=ru-RU \
    persist.sys.language=ru \
    persist.sys.country=RU \
    persist.sys.timezone=Europe/Moscow

# ── Disable Google-specific extras ───────────────────────────────────────────

# No Google Assistant (replaced by Yandex Alice later)
PRODUCT_PRODUCT_PROPERTIES += \
    ro.opa.eligible_device=false

# No Cast receiver
PRODUCT_PRODUCT_PROPERTIES += \
    ro.com.google.chromecast.enabled=false

# ── Performance / memory ──────────────────────────────────────────────────────

PRODUCT_SYSTEM_DEFAULT_PROPERTIES += \
    ro.config.max_starting_bg=8 \
    ro.sys.fw.bservice_enable=true

# ── Fonts ─────────────────────────────────────────────────────────────────────
# Golos Text (the Russian system font) will be shipped as a prebuilt in
# vendor/ruos/fonts/ — see fonts/Android.mk
# PRODUCT_COPY_FILES will be added there.
