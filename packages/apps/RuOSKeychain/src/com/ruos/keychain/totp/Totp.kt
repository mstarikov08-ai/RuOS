package com.ruos.keychain.totp

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * RFC 6238 TOTP (the same algorithm Google Authenticator / iOS verification codes use):
 * HMAC-SHA1 over a 30-second time counter, truncated to 6 digits. Pure, testable, no deps.
 */
object Totp {

    /** Current 6-digit code for a Base32 [secret]. Returns "------" if the secret is bad. */
    fun code(secret: String, timeSec: Long = System.currentTimeMillis() / 1000,
             digits: Int = 6, period: Int = 30): String {
        val key = base32Decode(secret) ?: return "-".repeat(digits)
        if (key.isEmpty()) return "-".repeat(digits)
        val counter = timeSec / period
        val msg = ByteArray(8)
        var v = counter
        for (i in 7 downTo 0) { msg[i] = (v and 0xFF).toByte(); v = v shr 8 }
        val hash = runCatching {
            Mac.getInstance("HmacSHA1").apply { init(SecretKeySpec(key, "HmacSHA1")) }.doFinal(msg)
        }.getOrNull() ?: return "-".repeat(digits)
        val offset = (hash[hash.size - 1].toInt() and 0x0F)
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
            ((hash[offset + 1].toInt() and 0xFF) shl 16) or
            ((hash[offset + 2].toInt() and 0xFF) shl 8) or
            (hash[offset + 3].toInt() and 0xFF)
        val mod = Math.pow(10.0, digits.toDouble()).toInt()
        return (binary % mod).toString().padStart(digits, '0')
    }

    /** Seconds remaining in the current window (for the countdown ring). */
    fun secondsRemaining(period: Int = 30, timeSec: Long = System.currentTimeMillis() / 1000): Int =
        (period - (timeSec % period)).toInt()

    /** Parse an otpauth://totp/Issuer:account?secret=...&issuer=... URI. */
    fun parseUri(uri: String): Triple<String, String, String>? {
        if (!uri.startsWith("otpauth://totp/")) return null
        return runCatching {
            val rest = uri.removePrefix("otpauth://totp/")
            val label = rest.substringBefore('?')
            val query = rest.substringAfter('?', "")
            val params = query.split('&').mapNotNull {
                val kv = it.split('=', limit = 2); if (kv.size == 2) kv[0] to kv[1] else null
            }.toMap()
            val secret = params["secret"] ?: return null
            val decodedLabel = java.net.URLDecoder.decode(label, "UTF-8")
            // The issuer query param may be URL-encoded (spaces, Cyrillic) — decode it.
            val issuerParam = params["issuer"]?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
            val issuer = issuerParam ?: decodedLabel.substringBefore(':', "").ifEmpty { decodedLabel }
            val account = decodedLabel.substringAfter(':', decodedLabel)
            Triple(issuer.trim(), account.trim(), secret.trim())
        }.getOrNull()
    }

    private fun base32Decode(s: String): ByteArray? {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val clean = s.trim().replace(" ", "").replace("-", "").uppercase().trimEnd('=')
        if (clean.isEmpty()) return ByteArray(0)
        var buffer = 0; var bits = 0
        val out = ArrayList<Byte>()
        for (c in clean) {
            val idx = alphabet.indexOf(c)
            if (idx < 0) return null
            buffer = (buffer shl 5) or idx; bits += 5
            if (bits >= 8) { bits -= 8; out.add(((buffer shr bits) and 0xFF).toByte()) }
        }
        return out.toByteArray()
    }
}
