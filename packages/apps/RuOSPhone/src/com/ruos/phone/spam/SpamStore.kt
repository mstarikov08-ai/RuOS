package com.ruos.phone.spam

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists the user's spam rules (blocked/allowed numbers, prefixes, keywords, the short-code
 * toggle) plus a rolling log of what was recently blocked. JSON in SharedPreferences — no secrets.
 * [toJson]/[fromJson] are pure so the rules round-trip and cross the process boundary to
 * RuOSMessages via [SpamProvider] byte-for-byte.
 */
class SpamStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun rules(): SpamFilter.Rules {
        val raw = prefs.getString(KEY_RULES, null) ?: return DEFAULT
        return runCatching { fromJson(raw) }.getOrDefault(DEFAULT)
    }

    fun saveRules(r: SpamFilter.Rules) = prefs.edit().putString(KEY_RULES, toJson(r)).apply()

    fun blockNumber(number: String) {
        val r = rules()
        saveRules(r.copy(blockedNumbers = r.blockedNumbers + SpamFilter.normalize(number)))
    }

    fun unblockNumber(number: String) {
        val r = rules(); val n = SpamFilter.normalize(number)
        saveRules(r.copy(blockedNumbers = r.blockedNumbers.filterNot { SpamFilter.normalize(it) == n }.toSet()))
    }

    /** Append a blocked-call/SMS record to the rolling log (newest first, capped). */
    fun logBlocked(number: String, kind: String) {
        val arr = runCatching { JSONArray(prefs.getString(KEY_LOG, "[]")) }.getOrDefault(JSONArray())
        val next = JSONArray().apply {
            put(JSONObject().apply { put("n", number); put("k", kind); put("t", System.currentTimeMillis()) })
            for (i in 0 until minOf(arr.length(), LOG_CAP - 1)) put(arr.get(i))
        }
        prefs.edit().putString(KEY_LOG, next.toString()).apply()
    }

    fun blockedLog(): List<Triple<String, String, Long>> {
        val arr = runCatching { JSONArray(prefs.getString(KEY_LOG, "[]")) }.getOrDefault(JSONArray())
        val out = ArrayList<Triple<String, String, Long>>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(Triple(o.optString("n"), o.optString("k"), o.optLong("t")))
        }
        return out
    }

    fun clearLog() = prefs.edit().remove(KEY_LOG).apply()

    companion object {
        private const val PREFS = "ruos_spam"
        private const val KEY_RULES = "rules"
        private const val KEY_LOG = "log"
        private const val LOG_CAP = 100
        val DEFAULT = SpamFilter.Rules(blockUnknownShort = false)

        fun toJson(r: SpamFilter.Rules): String = JSONObject().apply {
            put("numbers", JSONArray(r.blockedNumbers.toList()))
            put("prefixes", JSONArray(r.blockedPrefixes))
            put("keywords", JSONArray(r.blockedKeywords))
            put("allowed", JSONArray(r.allowedNumbers.toList()))
            put("unknownShort", r.blockUnknownShort)
        }.toString()

        fun fromJson(raw: String): SpamFilter.Rules {
            val o = JSONObject(raw)
            fun arr(name: String): List<String> {
                val a = o.optJSONArray(name) ?: return emptyList()
                return (0 until a.length()).map { a.getString(it) }
            }
            return SpamFilter.Rules(
                blockedNumbers = arr("numbers").toSet(),
                blockedPrefixes = arr("prefixes"),
                blockedKeywords = arr("keywords"),
                allowedNumbers = arr("allowed").toSet(),
                blockUnknownShort = o.optBoolean("unknownShort", false)
            )
        }
    }
}
