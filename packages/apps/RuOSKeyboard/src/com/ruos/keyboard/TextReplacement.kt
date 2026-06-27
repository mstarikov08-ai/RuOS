package com.ruos.keyboard

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** One text-replacement rule: typing [shortcut] expands to [phrase] (iOS Text Replacement). */
data class Replacement(val shortcut: String, val phrase: String)

/**
 * Persists the user's text-replacement rules and expands a finished word into its phrase.
 * Mirrors iOS «Замена текста»: type a shortcut, it expands on the next boundary (space,
 * return, punctuation). Plain JSON in SharedPreferences — no secrets.
 *
 * The matching + case logic is pure ([expand]) so it can be round-trip/behaviour-verified.
 */
class TextReplacementStore(context: Context) {

    private val prefs = context.getSharedPreferences("ruos_text_replace", Context.MODE_PRIVATE)
    private var cache: List<Replacement>? = null

    fun all(): List<Replacement> {
        cache?.let { return it }
        val raw = prefs.getString(KEY, null)
        val list = if (raw == null) DEFAULTS else runCatching { parse(raw) }.getOrDefault(DEFAULTS)
        cache = list
        return list
    }

    fun save(items: List<Replacement>) {
        cache = items
        prefs.edit().putString(KEY, serialize(items)).apply()
    }

    fun upsert(r: Replacement) {
        val list = all().toMutableList()
        val i = list.indexOfFirst { it.shortcut.equals(r.shortcut, ignoreCase = true) }
        if (i >= 0) list[i] = r else list.add(r)
        save(list)
    }

    fun delete(shortcut: String) = save(all().filterNot { it.shortcut.equals(shortcut, ignoreCase = true) })

    /** Expand [typed] if it matches a shortcut (case-insensitively), else null. */
    fun expand(typed: String): String? {
        val match = all().firstOrNull { it.shortcut.equals(typed, ignoreCase = true) } ?: return null
        return adaptCase(typed, match.phrase)
    }

    companion object {
        private const val KEY = "rules"
        val DEFAULTS = listOf(
            Replacement("омг", "о, мой бог"),
            Replacement("спс", "спасибо"),
            Replacement("кмк", "как мне кажется"),
            Replacement("др", "день рождения"))

        /**
         * Carry the typed word's casing onto the expansion, like iOS:
         *  - ALL CAPS shortcut → upper-case the phrase ("СПС" → "СПАСИБО")
         *  - Capitalised shortcut → capitalise the first letter ("Спс" → "Спасибо")
         *  - otherwise verbatim
         */
        fun adaptCase(typed: String, phrase: String): String {
            if (typed.isEmpty() || phrase.isEmpty()) return phrase
            val letters = typed.filter { it.isLetter() }
            return when {
                letters.length > 1 && letters.all { it.isUpperCase() } -> phrase.uppercase()
                typed.first().isUpperCase() -> phrase.replaceFirstChar { it.uppercaseChar() }
                else -> phrase
            }
        }

        fun serialize(items: List<Replacement>): String {
            val arr = JSONArray()
            items.forEach { arr.put(JSONObject().apply { put("sc", it.shortcut); put("ph", it.phrase) }) }
            return arr.toString()
        }

        fun parse(raw: String): List<Replacement> {
            val arr = JSONArray(raw)
            val out = ArrayList<Replacement>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val sc = o.optString("sc"); val ph = o.optString("ph")
                if (sc.isNotEmpty() && ph.isNotEmpty()) out.add(Replacement(sc, ph))
            }
            return out
        }
    }
}
