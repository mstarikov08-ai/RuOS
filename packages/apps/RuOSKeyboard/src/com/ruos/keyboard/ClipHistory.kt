package com.ruos.keyboard

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** One remembered clipboard entry. [pinned] items survive the ring-buffer trim. */
data class ClipItem(val text: String, val time: Long, val pinned: Boolean = false)

/**
 * Persists a short history of copied text, iOS-style «Universal Clipboard» but local: the
 * keyboard captures new clips while it's the active IME, and [ClipboardActivity] lets the user
 * re-paste, pin or clear them. A bounded ring buffer (pinned entries excepted) so it never grows
 * without limit; plain text only (images/URIs are ignored). No secrets are stored beyond what the
 * user already copied, and the whole thing can be wiped from the panel.
 *
 * Serialize/parse and the trim rule are pure (companion) so they can be round-trip-verified.
 */
class ClipHistoryStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(): List<ClipItem> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching { parse(raw) }.getOrDefault(emptyList())
    }

    /** Record a freshly-copied clip. De-dupes (moves an identical entry to the top) and trims. */
    fun add(text: String) {
        val t = text.trim()
        if (t.isEmpty() || t.length > MAX_LEN) return
        val current = all()
        // Preserve pinned flag if this text was already pinned.
        val wasPinned = current.any { it.text == t && it.pinned }
        val deduped = current.filterNot { it.text == t }
        val next = trim(listOf(ClipItem(t, System.currentTimeMillis(), wasPinned)) + deduped)
        save(next)
    }

    fun setPinned(text: String, pinned: Boolean) =
        save(all().map { if (it.text == text) it.copy(pinned = pinned) else it })

    fun delete(text: String) = save(all().filterNot { it.text == text })

    /** Wipe everything except pinned entries (a full wipe uses [clearAll]). */
    fun clearUnpinned() = save(all().filter { it.pinned })

    fun clearAll() = prefs.edit().remove(KEY).apply()

    private fun save(items: List<ClipItem>) =
        prefs.edit().putString(KEY, serialize(trim(items))).apply()

    companion object {
        private const val PREFS = "ruos_clipboard"
        private const val KEY = "history"
        const val MAX_UNPINNED = 30
        const val MAX_LEN = 20_000

        /**
         * Keep pinned items (always) plus the most-recent [MAX_UNPINNED] unpinned ones, preserving
         * input order. Pure so the bound is testable.
         */
        fun trim(items: List<ClipItem>): List<ClipItem> {
            var kept = 0
            return items.filter { it.pinned || kept++ < MAX_UNPINNED }
        }

        fun serialize(items: List<ClipItem>): String {
            val arr = JSONArray()
            items.forEach {
                arr.put(JSONObject().apply {
                    put("t", it.text); put("ts", it.time); put("p", it.pinned)
                })
            }
            return arr.toString()
        }

        fun parse(raw: String): List<ClipItem> {
            val arr = JSONArray(raw)
            val out = ArrayList<ClipItem>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val t = o.optString("t")
                if (t.isNotEmpty()) out.add(ClipItem(t, o.optLong("ts"), o.optBoolean("p")))
            }
            return out
        }
    }
}
