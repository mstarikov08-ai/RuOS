package com.ruos.findmy.core

import android.content.Context

/** User configuration for Find My RuOS: the secret passphrase that authorises remote
 *  SMS commands, and an optional trusted phone number they must come from. */
class FindMySettings(context: Context) {

    private val prefs = context.getSharedPreferences("ruos_findmy", Context.MODE_PRIVATE)

    var passphrase: String
        get() = prefs.getString(KEY_PASS, "") ?: ""
        set(v) { prefs.edit().putString(KEY_PASS, v.trim()).apply() }

    /** Empty = accept the command from any number (still requires the passphrase). */
    var trustedNumber: String
        get() = prefs.getString(KEY_NUM, "") ?: ""
        set(v) { prefs.edit().putString(KEY_NUM, v.trim()).apply() }

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ON, false)
        set(v) { prefs.edit().putBoolean(KEY_ON, v).apply() }

    fun isConfigured() = enabled && passphrase.length >= 4

    fun numberMatches(from: String?): Boolean {
        if (trustedNumber.isEmpty()) return true
        val a = from?.filter { it.isDigit() }?.takeLast(10) ?: return false
        val b = trustedNumber.filter { it.isDigit() }.takeLast(10)
        return a.isNotEmpty() && a == b
    }

    companion object {
        private const val KEY_PASS = "passphrase"
        private const val KEY_NUM = "trusted_number"
        private const val KEY_ON = "enabled"
    }
}
