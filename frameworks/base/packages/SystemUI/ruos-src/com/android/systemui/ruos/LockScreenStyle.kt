package com.android.systemui.ruos

import android.content.Context
import android.graphics.Typeface
import android.provider.Settings

/**
 * Shared lock-screen customisation contract. RuOSSettings writes these Settings.Secure keys and the
 * keyguard [LockScreenView] reads them, so the user's clock style, lock-screen widgets and
 * always-on-display preference take effect without a reboot. Keys and defaults live here so both
 * sides agree byte-for-byte.
 */
object LockScreenStyle {

    const val KEY_CLOCK_STYLE = "ruos_clock_style"     // one of CLOCK_*
    const val KEY_WIDGETS = "ruos_lock_widgets"        // comma list of WIDGET_* tokens
    const val KEY_AOD = "doze_always_on"               // standard AOSP AOD toggle (0/1)

    const val CLOCK_THIN = 0     // iOS-style thin, large
    const val CLOCK_BOLD = 1     // heavy weight
    const val CLOCK_SERIF = 2    // serif elegant
    const val CLOCK_COMPACT = 3  // smaller, top-aligned

    const val WIDGET_DATE = "date"
    const val WIDGET_BATTERY = "battery"
    const val WIDGET_ALARM = "alarm"
    const val WIDGET_WEATHER = "weather"

    val DEFAULT_WIDGETS = listOf(WIDGET_DATE)

    fun clockStyle(context: Context): Int =
        runCatching { Settings.Secure.getInt(context.contentResolver, KEY_CLOCK_STYLE, CLOCK_THIN) }
            .getOrDefault(CLOCK_THIN)

    fun widgets(context: Context): List<String> {
        val raw = runCatching { Settings.Secure.getString(context.contentResolver, KEY_WIDGETS) }.getOrNull()
        if (raw.isNullOrBlank()) return DEFAULT_WIDGETS
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** The clock face's typeface + text size (sp) for a given style. */
    fun clockTypeface(style: Int): Typeface = when (style) {
        CLOCK_BOLD -> Typeface.create("sans-serif", Typeface.BOLD)
        CLOCK_SERIF -> Typeface.create("serif", Typeface.NORMAL)
        CLOCK_COMPACT -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
        else -> Typeface.create("sans-serif-thin", Typeface.NORMAL)
    }

    fun clockSizeSp(style: Int): Float = when (style) {
        CLOCK_BOLD -> 76f
        CLOCK_SERIF -> 72f
        CLOCK_COMPACT -> 56f
        else -> 80f
    }
}
