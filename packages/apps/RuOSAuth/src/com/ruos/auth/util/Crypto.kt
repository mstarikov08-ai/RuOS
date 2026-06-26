package com.ruos.auth.util

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Passcode hashing. PBKDF2-HMAC-SHA256 with a per-device random salt and a high
 * iteration count — the passcode is never stored in clear. (Note: this protects the
 * RuOS app-level passcode; the hardware-backed device keyguard is gatekeeper's job.)
 */
object Crypto {
    private const val ITERATIONS = 120_000
    private const val KEY_LEN = 256

    fun newSalt(): String {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        return Base64.encodeToString(salt, Base64.NO_WRAP)
    }

    fun hash(passcode: String, saltB64: String): String {
        val salt = Base64.decode(saltB64, Base64.NO_WRAP)
        val spec = PBEKeySpec(passcode.toCharArray(), salt, ITERATIONS, KEY_LEN)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val bytes = factory.generateSecret(spec).encoded
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /** Constant-time comparison to avoid timing leaks. */
    fun verify(passcode: String, saltB64: String, expectedHashB64: String): Boolean {
        val actual = hash(passcode, saltB64)
        val a = actual.toByteArray(); val b = expectedHashB64.toByteArray()
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }
}
