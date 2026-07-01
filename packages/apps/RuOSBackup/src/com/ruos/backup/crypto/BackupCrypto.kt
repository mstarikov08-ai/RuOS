package com.ruos.backup.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Portable, password-based encryption for a RuOS backup file.
 *
 * The keychain's [Vault] uses an AndroidKeyStore key — deliberately NON-exportable, so it
 * can't protect a backup you need to restore after a wipe or on a new phone. A backup must be
 * openable anywhere with the user's passphrase, so here the key is DERIVED from the passphrase
 * with PBKDF2 (HMAC-SHA256, 210k iterations) and used for AES-256-GCM.
 *
 * File layout (raw bytes):  MAGIC(8) | salt(16) | iv(12) | ciphertext+tag
 * Deterministic given salt+iv, so it is round-trip-verifiable (see verify_backup.py).
 */
object BackupCrypto {

    val MAGIC = "RUOSBAK1".toByteArray(Charsets.US_ASCII)   // 8 bytes
    private const val ITERATIONS = 210_000
    private const val KEY_BITS = 256
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val TAG_BITS = 128

    fun deriveKey(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_BITS)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    /** Encrypt [plain] with [password] → the raw .ruosbak byte payload. */
    fun encrypt(plain: String, password: String): ByteArray {
        val rnd = SecureRandom()
        val salt = ByteArray(SALT_LEN).also { rnd.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { rnd.nextBytes(it) }
        val key = SecretKeySpec(deriveKey(password.toCharArray(), salt), "AES")
        val ct = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            doFinal(plain.toByteArray(Charsets.UTF_8))
        }
        return MAGIC + salt + iv + ct
    }

    /** Decrypt a .ruosbak payload with [password]. Throws on wrong password / tampering. */
    fun decrypt(blob: ByteArray, password: String): String {
        require(blob.size > MAGIC.size + SALT_LEN + IV_LEN) { "backup too short" }
        require(blob.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) { "not a RuOS backup" }
        var p = MAGIC.size
        val salt = blob.copyOfRange(p, p + SALT_LEN); p += SALT_LEN
        val iv = blob.copyOfRange(p, p + IV_LEN); p += IV_LEN
        val ct = blob.copyOfRange(p, blob.size)
        val key = SecretKeySpec(deriveKey(password.toCharArray(), salt), "AES")
        val plain = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            doFinal(ct)
        }
        return String(plain, Charsets.UTF_8)
    }
}
