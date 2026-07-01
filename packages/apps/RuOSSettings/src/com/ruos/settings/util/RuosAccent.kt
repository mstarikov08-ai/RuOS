package com.ruos.settings.util

import android.content.Context
import android.provider.Settings

/**
 * The user's chosen interactive tint ("accent"), like iOS's per-device tint colour. Stored in
 * Settings.Secure so every RuOS app + SystemUI can read it with a ContentResolver — no shared
 * library needed. RuOSSettings (platform-signed, holds WRITE_SECURE_SETTINGS) writes it; apps
 * read it. Default is the iOS system blue the UI already uses, so nothing regresses if unset.
 *
 * Any app themes itself by replacing a hardcoded tint with `RuosAccent.read(context)`.
 */
object RuosAccent {
    const val KEY = "ruos_accent_color"
    val DEFAULT = 0xFF0A84FF.toInt()      // iOS system blue (current de-facto tint)

    /** Curated iOS-style tints offered in the picker (first = default). */
    val SWATCHES = listOf(
        0xFF0A84FF, 0xFFD94F3D, 0xFFFF9F0A, 0xFF34C759,
        0xFF5E5CE6, 0xFFFF2D55, 0xFF30B0C7, 0xFFAF52DE
    ).map { it.toInt() }

    /** Read the accent (always opaque). Falls back to [DEFAULT] if unset or unreadable. */
    fun read(context: Context): Int {
        val v = runCatching { Settings.Secure.getInt(context.contentResolver, KEY, DEFAULT) }
            .getOrDefault(DEFAULT)
        return v or 0xFF000000.toInt()    // force full alpha — a stored 0 must not be transparent
    }

    /** Persist a new accent. Returns false if the write was denied (no WRITE_SECURE_SETTINGS). */
    fun write(context: Context, color: Int): Boolean =
        runCatching {
            Settings.Secure.putInt(context.contentResolver, KEY, color or 0xFF000000.toInt())
        }.getOrDefault(false)
}
