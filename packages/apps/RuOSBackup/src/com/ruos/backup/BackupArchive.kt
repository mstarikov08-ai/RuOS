package com.ruos.backup

import android.content.Context
import android.net.Uri
import com.ruos.backup.crypto.BackupCrypto
import org.json.JSONObject

/**
 * Collects each app's serialised state into one manifest, encrypts it (password-based, so it
 * restores after a wipe / on another phone), and reverses the process on restore. The plaintext
 * manifest is:
 *   { "ruos_backup":1, "created":<ms>, "device":<codename>, "sections": { "<pkg>":"<json>", … } }
 */
object BackupArchive {

    private const val VERSION = 1

    /** Query every participating app's provider and build the encrypted payload. */
    fun create(context: Context, password: String): ByteArray {
        val sections = JSONObject()
        for ((_, pkg) in BackupContract.SECTIONS) {
            val json = readSection(context, pkg) ?: continue   // app absent / empty → skip
            sections.put(pkg, json)
        }
        val root = JSONObject()
            .put("ruos_backup", VERSION)
            .put("created", System.currentTimeMillis())
            .put("device", android.os.Build.DEVICE)
            .put("sections", sections)
        return BackupCrypto.encrypt(root.toString(), password)
    }

    /** Decrypt and push each section back to its app. Returns the number restored. */
    fun restore(context: Context, blob: ByteArray, password: String): Int {
        val root = JSONObject(BackupCrypto.decrypt(blob, password))
        require(root.optInt("ruos_backup") == VERSION) { "unsupported backup version" }
        val sections = root.optJSONObject("sections") ?: return 0
        var n = 0
        val it = sections.keys()
        while (it.hasNext()) {
            val pkg = it.next()
            if (writeSection(context, pkg, sections.getString(pkg))) n++
        }
        return n
    }

    /** Parse just the header (no password) for a preview: created time + section count. */
    fun peek(blob: ByteArray): Boolean =
        blob.size > BackupCrypto.MAGIC.size &&
            blob.copyOfRange(0, BackupCrypto.MAGIC.size).contentEquals(BackupCrypto.MAGIC)

    private fun readSection(context: Context, pkg: String): String? = runCatching {
        val uri = Uri.parse("content://${BackupContract.authority(pkg)}/${BackupContract.PATH}")
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(BackupContract.COLUMN)
                if (idx >= 0) return@use c.getString(idx)
            }
            null
        }
    }.getOrNull()

    private fun writeSection(context: Context, pkg: String, json: String): Boolean = runCatching {
        val uri = Uri.parse("content://${BackupContract.authority(pkg)}/${BackupContract.PATH}")
        context.contentResolver.call(uri, BackupContract.METHOD_IMPORT, json, null)
        true
    }.getOrDefault(false)
}
