package com.ruos.notify.model

import android.content.Context
import org.json.JSONObject

/** Per-app notification preferences, edited in Settings → Уведомления → [app]. */
data class AppNotifPrefs(
    var allow: Boolean = true,
    var persistent: Boolean = false,   // banner style: false=temporary, true=persistent
    var sounds: Boolean = true,
    var badges: Boolean = true,
    var lockScreen: Boolean = true,
    var grouping: Boolean = true
)

class NotifSettings(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("ruos_notify_settings", Context.MODE_PRIVATE)

    fun forApp(pkg: String): AppNotifPrefs {
        val json = prefs.getString(pkg, null) ?: return AppNotifPrefs()
        return try {
            val o = JSONObject(json)
            AppNotifPrefs(
                allow = o.optBoolean("allow", true),
                persistent = o.optBoolean("persistent", false),
                sounds = o.optBoolean("sounds", true),
                badges = o.optBoolean("badges", true),
                lockScreen = o.optBoolean("lockScreen", true),
                grouping = o.optBoolean("grouping", true)
            )
        } catch (_: Exception) { AppNotifPrefs() }
    }

    fun save(pkg: String, p: AppNotifPrefs) {
        val o = JSONObject().apply {
            put("allow", p.allow); put("persistent", p.persistent); put("sounds", p.sounds)
            put("badges", p.badges); put("lockScreen", p.lockScreen); put("grouping", p.grouping)
        }
        prefs.edit().putString(pkg, o.toString()).apply()
    }

    /** Known packages we have explicit prefs for (others use defaults). */
    fun configuredPackages(): Set<String> = prefs.all.keys
}
