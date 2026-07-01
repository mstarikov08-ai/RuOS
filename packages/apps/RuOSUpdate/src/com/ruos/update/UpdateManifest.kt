package com.ruos.update

import org.json.JSONObject

/**
 * A RuOS update, described by a JSON manifest hosted on the build server:
 *   { "version":"1.1.0", "build":20260701, "url":"https://…/ota.zip",
 *     "sha256":"…", "size":1234567, "changelog":"…" }
 *
 * `build` is a monotonic stamp (YYYYMMDD or an incrementing int); it's the authoritative
 * newer/older signal, with the semver string as the human label and tiebreaker.
 */
data class UpdateManifest(
    val version: String,
    val build: Long,
    val url: String,
    val sha256: String,
    val size: Long,
    val changelog: String,
) {
    companion object {
        fun parse(json: String): UpdateManifest {
            val o = JSONObject(json)
            return UpdateManifest(
                version = o.optString("version", "0.0.0"),
                build = o.optLong("build", 0L),
                url = o.getString("url"),
                sha256 = o.optString("sha256", "").lowercase().trim(),
                size = o.optLong("size", 0L),
                changelog = o.optString("changelog", ""),
            )
        }

        /**
         * Newer if the manifest build stamp is higher; if builds tie (or are missing), fall
         * back to comparing the semver strings numerically. Pure + verifiable.
         */
        fun isNewer(curVersion: String, curBuild: Long, m: UpdateManifest): Boolean {
            if (m.build != curBuild) return m.build > curBuild
            return compareVersions(m.version, curVersion) > 0
        }

        /** Compare "a.b.c" numerically. Missing parts count as 0. Returns -1 / 0 / 1. */
        fun compareVersions(a: String, b: String): Int {
            val pa = a.split('.'); val pb = b.split('.')
            for (i in 0 until maxOf(pa.size, pb.size)) {
                val x = pa.getOrNull(i)?.toIntOrNull() ?: 0
                val y = pb.getOrNull(i)?.toIntOrNull() ?: 0
                if (x != y) return if (x > y) 1 else -1
            }
            return 0
        }
    }
}
