package com.ruos.update

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * Downloads a RuOS OTA zip, verifies its SHA-256, and hands it to the A/B update_engine.
 *
 * [device]: the download + SHA-256 verify are ordinary, testable code; the A/B apply talks to
 * android.os.UpdateEngine (a @SystemApi — needs platform_apis, which this app declares) and can
 * only be validated on a real LineageOS build against a correctly-signed OTA package.
 */
object UpdateInstaller {

    /** Streaming SHA-256 of a file, lowercase hex. */
    fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { ins ->
            val buf = ByteArray(1 shl 16)
            while (true) { val n = ins.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun verify(file: File, expectedSha256: String): Boolean =
        expectedSha256.isNotEmpty() && sha256(file).equals(expectedSha256, ignoreCase = true)

    /** Download [url] → [dest], reporting bytes via [onProgress]. Returns true on success. */
    fun download(url: String, dest: File, onProgress: (Long, Long) -> Unit): Boolean = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 30_000
        }
        if (conn.responseCode != 200) return false
        val total = conn.contentLengthLong
        var done = 0L
        conn.inputStream.use { ins ->
            dest.outputStream().use { out ->
                val buf = ByteArray(1 shl 16)
                while (true) {
                    val n = ins.read(buf); if (n < 0) break
                    out.write(buf, 0, n); done += n; onProgress(done, total)
                }
            }
        }
        true
    }.getOrDefault(false)

    // ── A/B payload extraction (AOSP SystemUpdaterSample algorithm) ─────────────
    data class PayloadSpec(val offset: Long, val size: Long, val properties: Array<String>)

    /** Locate payload.bin (STORED) inside the OTA zip and read payload_properties.txt. */
    fun payloadSpec(otaZip: File): PayloadSpec? = runCatching {
        ZipFile(otaZip).use { zip ->
            var offset = 0L
            var payloadOffset = 0L
            var payloadSize = 0L
            var props: Array<String> = emptyArray()
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val e = entries.nextElement()
                val extra = e.extra?.size ?: 0
                offset += 30 + e.name.toByteArray().size + extra   // local file header
                when (e.name) {
                    "payload.bin" -> { payloadOffset = offset; payloadSize = e.compressedSize }
                    "payload_properties.txt" ->
                        props = zip.getInputStream(e).bufferedReader().readLines()
                            .map { it.trim() }.filter { it.isNotEmpty() }.toTypedArray()
                }
                offset += e.compressedSize
            }
            if (payloadSize == 0L) null else PayloadSpec(payloadOffset, payloadSize, props)
        }
    }.getOrNull()

    /**
     * Kick off the A/B update. Binds android.os.UpdateEngine and calls applyPayload with the
     * offset/size of payload.bin inside the local zip and the properties as headers. On success
     * update_engine writes the inactive slot in the background; the caller reboots when it reports
     * UPDATED_NEED_REBOOT. Reflection-free; requires platform_apis.
     */
    fun applyPayload(otaZip: File, spec: PayloadSpec, onStatus: (Int, Float) -> Unit,
                     onComplete: (Int) -> Unit): Boolean = runCatching {
        val engine = android.os.UpdateEngine()
        engine.bind(object : android.os.UpdateEngineCallback() {
            override fun onStatusUpdate(status: Int, percent: Float) = onStatus(status, percent)
            override fun onPayloadApplicationComplete(errorCode: Int) = onComplete(errorCode)
        })
        engine.applyPayload("file://${otaZip.absolutePath}", spec.offset, spec.size, spec.properties)
        true
    }.getOrDefault(false)
}
