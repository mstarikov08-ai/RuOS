package com.ruos.auth.model

import android.content.Context
import com.ruos.auth.util.Crypto

/** Require-passcode timing options (ms), iOS-style. */
enum class RequireAfter(val ms: Long, val ruName: String) {
    IMMEDIATELY(0, "Сразу"),
    MIN_1(60_000, "Через 1 мин"),
    MIN_5(5 * 60_000, "Через 5 мин"),
    HOUR_1(60 * 60_000, "Через 1 ч"),
    HOUR_4(4 * 60 * 60_000, "Через 4 ч");
    companion object { fun byName(n: String?) = values().firstOrNull { it.name == n } ?: IMMEDIATELY }
}

/**
 * RuOS app-level passcode + biometric preferences. The passcode is PBKDF2-hashed.
 * (This gates RuOS's own lock surfaces; it is not the hardware device keyguard.)
 */
class AuthStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("ruos_auth", Context.MODE_PRIVATE)

    // ── Passcode ────────────────────────────────────────────────────────────

    val isPasscodeSet: Boolean get() = prefs.contains(K_HASH)
    val passcodeLength: Int get() = prefs.getInt(K_LEN, 6)
    val isAlphanumeric: Boolean get() = prefs.getBoolean(K_ALPHA, false)

    fun setPasscode(passcode: String, alphanumeric: Boolean) {
        val salt = Crypto.newSalt()
        prefs.edit()
            .putString(K_SALT, salt)
            .putString(K_HASH, Crypto.hash(passcode, salt))
            .putInt(K_LEN, passcode.length)
            .putBoolean(K_ALPHA, alphanumeric)
            .putInt(K_FAILS, 0)
            .apply()
    }

    fun clearPasscode() = prefs.edit()
        .remove(K_SALT).remove(K_HASH).remove(K_LEN).remove(K_ALPHA).putInt(K_FAILS, 0).apply()

    fun verify(passcode: String): Boolean {
        val salt = prefs.getString(K_SALT, null) ?: return false
        val hash = prefs.getString(K_HASH, null) ?: return false
        val ok = Crypto.verify(passcode, salt, hash)
        if (ok) resetFails() else recordFail()
        return ok
    }

    // ── Fail counting / erase ────────────────────────────────────────────────

    val failCount: Int get() = prefs.getInt(K_FAILS, 0)
    fun recordFail() = prefs.edit().putInt(K_FAILS, failCount + 1).apply()
    fun resetFails() = prefs.edit().putInt(K_FAILS, 0).apply()

    var eraseAfter10: Boolean
        get() = prefs.getBoolean(K_ERASE, false)
        set(v) = prefs.edit().putBoolean(K_ERASE, v).apply()

    /** True when erase-on-failure is enabled and the limit has been hit. */
    fun shouldErase(): Boolean = eraseAfter10 && failCount >= 10

    // ── Require-after timing ──────────────────────────────────────────────────

    var requireAfter: RequireAfter
        get() = RequireAfter.byName(prefs.getString(K_REQUIRE, null))
        set(v) = prefs.edit().putString(K_REQUIRE, v.name).apply()

    fun markUnlocked() = prefs.edit().putLong(K_LAST_UNLOCK, System.currentTimeMillis()).apply()

    /** Whether the passcode/biometric must be presented again given the timing pref. */
    fun mustReauth(): Boolean {
        if (!isPasscodeSet) return false
        val since = System.currentTimeMillis() - prefs.getLong(K_LAST_UNLOCK, 0)
        return since >= requireAfter.ms
    }

    // ── Biometric usage toggles ──────────────────────────────────────────────

    fun toggle(key: String, default: Boolean = true): Boolean = prefs.getBoolean("use_$key", default)
    fun setToggle(key: String, value: Boolean) = prefs.edit().putBoolean("use_$key", value).apply()

    companion object {
        private const val K_SALT = "pc_salt"
        private const val K_HASH = "pc_hash"
        private const val K_LEN = "pc_len"
        private const val K_ALPHA = "pc_alpha"
        private const val K_FAILS = "pc_fails"
        private const val K_ERASE = "pc_erase"
        private const val K_REQUIRE = "pc_require"
        private const val K_LAST_UNLOCK = "pc_last_unlock"

        // Toggle keys
        const val USE_UNLOCK = "unlock"
        const val USE_MIRPAY = "mirpay"
        const val USE_RUSTORE = "rustore"
        const val USE_AUTOFILL = "autofill"
    }
}
