package com.ruos.update

import java.net.HttpURLConnection
import java.net.URL

/**
 * Reads the device's current RuOS version/build and asks the build server whether a newer one
 * exists. The manifest URL points at the RuOS OTA server (per-device channel).
 */
object UpdateChecker {

    /** Per-device update channel. Replace the host with the real RuOS OTA endpoint. */
    const val MANIFEST_URL = "https://ota.ruos.ru/panther/latest.json"

    /** ro.* system property (reflection — SystemProperties is @hide). */
    fun prop(key: String, def: String = ""): String = runCatching {
        Class.forName("android.os.SystemProperties")
            .getMethod("get", String::class.java, String::class.java)
            .invoke(null, key, def) as String
    }.getOrDefault(def)

    fun currentVersion() = prop("ro.ruos.version", "0.0.0")
    // ro.ruos.build.date is the monotonic YYYYMMDD stamp set in vendor/ruos/ruos.mk.
    fun currentBuild() = prop("ro.ruos.build.date", "0").toLongOrNull() ?: 0L

    fun fetchManifest(url: String = MANIFEST_URL): UpdateManifest? = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 15_000
        }
        if (conn.responseCode != 200) return null
        UpdateManifest.parse(conn.inputStream.bufferedReader().use { it.readText() })
    }.getOrNull()

    /** Returns the manifest if it describes a NEWER build than this device, else null. */
    fun check(): UpdateManifest? {
        val m = fetchManifest() ?: return null
        return if (UpdateManifest.isNewer(currentVersion(), currentBuild(), m)) m else null
    }
}
