package com.ruos.journal.model

import android.content.Context
import com.ruos.journal.util.JournalCrypto
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar

/**
 * Encrypted journal storage. The entries are serialised to JSON, encrypted with
 * [JournalCrypto] (AES-GCM, KeyStore key), and written to a single file in app
 * storage. Nothing is sent anywhere; nothing is in plaintext at rest.
 */
class JournalStore(context: Context) {

    private val file = File(context.applicationContext.filesDir, "journal.enc")
    private val prefs = context.applicationContext.getSharedPreferences("ruos_journal", Context.MODE_PRIVATE)

    fun getEntries(): MutableList<JournalEntry> {
        if (!file.exists()) return mutableListOf()
        return try {
            val json = JournalCrypto.decrypt(file.readText())
            val arr = JSONArray(json)
            (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
                .sortedByDescending { it.timestamp }.toMutableList()
        } catch (_: Exception) { mutableListOf() }
    }

    fun save(entries: List<JournalEntry>) {
        val arr = JSONArray()
        entries.forEach { arr.put(toJson(it)) }
        file.writeText(JournalCrypto.encrypt(arr.toString()))
    }

    fun upsert(entry: JournalEntry) {
        val list = getEntries()
        val idx = list.indexOfFirst { it.id == entry.id }
        if (idx >= 0) list[idx] = entry else list.add(entry)
        save(list)
    }

    fun delete(id: Long) = save(getEntries().filterNot { it.id == id })
    fun deleteAll() { if (file.exists()) file.delete() }
    fun get(id: Long): JournalEntry? = getEntries().firstOrNull { it.id == id }
    fun newId(): Long = System.currentTimeMillis()

    /** Entries written on this calendar day in a previous year ("год назад"). */
    fun onThisDayLastYears(): List<JournalEntry> {
        val now = Calendar.getInstance()
        return getEntries().filter {
            val c = Calendar.getInstance().apply { timeInMillis = it.timestamp }
            c.get(Calendar.DAY_OF_MONTH) == now.get(Calendar.DAY_OF_MONTH) &&
                c.get(Calendar.MONTH) == now.get(Calendar.MONTH) &&
                c.get(Calendar.YEAR) < now.get(Calendar.YEAR)
        }
    }

    /** Consecutive-day journaling streak ending today (or yesterday). */
    fun streak(): Int {
        val days = getEntries().map { dayKey(it.timestamp) }.toSortedSet().toList().reversed()
        if (days.isEmpty()) return 0
        var streak = 0
        var cursor = dayKey(System.currentTimeMillis())
        // Allow the streak to count if the latest entry is today or yesterday.
        if (days[0] != cursor && days[0] != cursor - 1) return 0
        cursor = days[0]
        for (d in days) {
            if (d == cursor) { streak++; cursor -= 1 } else break
        }
        return streak
    }

    private fun dayKey(ts: Long): Long = ts / 86_400_000L

    // ── Settings ──────────────────────────────────────────────────────────────

    var lockEnabled: Boolean
        get() = prefs.getBoolean("lock", true)
        set(v) = prefs.edit().putBoolean("lock", v).apply()

    var seenPrivacy: Boolean
        get() = prefs.getBoolean("seen_privacy", false)
        set(v) = prefs.edit().putBoolean("seen_privacy", v).apply()

    var reminderHour: Int
        get() = prefs.getInt("rem_h", 21)
        set(v) = prefs.edit().putInt("rem_h", v).apply()
    var reminderEnabled: Boolean
        get() = prefs.getBoolean("rem_on", false)
        set(v) = prefs.edit().putBoolean("rem_on", v).apply()

    fun suggestionEnabled(kind: String): Boolean = prefs.getBoolean("sug_$kind", true)
    fun setSuggestionEnabled(kind: String, v: Boolean) = prefs.edit().putBoolean("sug_$kind", v).apply()

    private fun toJson(e: JournalEntry) = JSONObject().apply {
        put("id", e.id); put("ts", e.timestamp); put("text", e.text)
        put("photos", JSONArray(e.photoUris)); put("tags", JSONArray(e.tags))
        e.moodId?.let { put("mood", it) }
        e.location?.let { put("loc", it) }
        e.audioPath?.let { put("audio", it) }
    }

    private fun fromJson(o: JSONObject): JournalEntry {
        fun strList(name: String): List<String> {
            val a = o.optJSONArray(name) ?: return emptyList()
            return (0 until a.length()).map { a.getString(it) }
        }
        return JournalEntry(
            id = o.getLong("id"), timestamp = o.getLong("ts"), text = o.optString("text", ""),
            photoUris = strList("photos"), tags = strList("tags"),
            moodId = if (o.has("mood")) o.getInt("mood") else null,
            location = if (o.has("loc")) o.getString("loc") else null,
            audioPath = if (o.has("audio")) o.getString("audio") else null
        )
    }
}
