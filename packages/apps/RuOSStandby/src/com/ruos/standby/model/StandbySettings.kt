package com.ruos.standby.model

import android.content.Context

/** Night-mode (red tint) policy, iOS-style. */
enum class NightMode(val ruName: String) {
    AUTOMATIC("Автоматически"), ALWAYS("Всегда"), NEVER("Никогда");
    companion object { fun byName(n: String?) = values().firstOrNull { it.name == n } ?: AUTOMATIC }
}

/** Persisted StandBy preferences + last-used page (per the spec's "remembers layout"). */
class StandbySettings(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("ruos_standby", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", true)
        set(v) = prefs.edit().putBoolean("enabled", v).apply()

    var nightMode: NightMode
        get() = NightMode.byName(prefs.getString("night", null))
        set(v) = prefs.edit().putString("night", v.name).apply()

    var showNotifications: Boolean
        get() = prefs.getBoolean("notifs", true)
        set(v) = prefs.edit().putBoolean("notifs", v).apply()

    var motionToWake: Boolean
        get() = prefs.getBoolean("motion", true)
        set(v) = prefs.edit().putBoolean("motion", v).apply()

    var lastPage: Int
        get() = prefs.getInt("page", 0)
        set(v) = prefs.edit().putInt("page", v).apply()

    // Remembered widget choices for page 2 (top/bottom slots).
    var topWidget: Int
        get() = prefs.getInt("w_top", 0)
        set(v) = prefs.edit().putInt("w_top", v).apply()
    var bottomWidget: Int
        get() = prefs.getInt("w_bottom", 1)
        set(v) = prefs.edit().putInt("w_bottom", v).apply()

    // Remembered clock style for page 1.
    var clockStyle: Int
        get() = prefs.getInt("clock", 0)
        set(v) = prefs.edit().putInt("clock", v).apply()
}
