package com.ruos.reminders.store

import android.content.Context
import com.ruos.reminders.model.Reminder
import com.ruos.reminders.model.ReminderList
import org.json.JSONArray
import org.json.JSONObject

/** Plain JSON persistence for lists + reminders (no secrets, so no encryption). */
class ReminderStore(context: Context) {

    private val prefs = context.getSharedPreferences("ruos_reminders", Context.MODE_PRIVATE)

    init {
        if (!prefs.contains(KEY_LISTS)) {
            saveLists(listOf(
                ReminderList("default", "Напоминания", 0xFFFF9F0A),
                ReminderList("shopping", "Покупки", 0xFF34C759)))
        }
    }

    fun lists(): List<ReminderList> {
        val raw = prefs.getString(KEY_LISTS, null) ?: return emptyList()
        val arr = JSONArray(raw); val out = ArrayList<ReminderList>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(ReminderList(o.getString("id"), o.getString("name"), o.optLong("color", 0xFFFF9F0A)))
        }
        return out
    }

    fun saveLists(lists: List<ReminderList>) {
        val arr = JSONArray()
        lists.forEach { arr.put(JSONObject().apply { put("id", it.id); put("name", it.name); put("color", it.colorHex) }) }
        prefs.edit().putString(KEY_LISTS, arr.toString()).apply()
    }

    fun all(): List<Reminder> {
        val raw = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        val arr = JSONArray(raw); val out = ArrayList<Reminder>()
        for (i in 0 until arr.length()) out.add(fromJson(arr.getJSONObject(i)))
        return out
    }

    fun forList(listId: String) = all().filter { it.listId == listId }
        .sortedWith(compareBy({ it.completed }, { if (it.hasTime) it.dueMillis else Long.MAX_VALUE }, { -it.createdAt }))

    fun byId(id: String) = all().firstOrNull { it.id == id }

    fun count(listId: String) = all().count { it.listId == listId && !it.completed }

    fun upsert(r: Reminder) {
        val list = all().toMutableList()
        val i = list.indexOfFirst { it.id == r.id }
        if (i >= 0) list[i] = r else list.add(r)
        saveItems(list)
    }

    fun delete(id: String) { saveItems(all().filterNot { it.id == id }) }

    private fun saveItems(items: List<Reminder>) {
        val arr = JSONArray(); items.forEach { arr.put(toJson(it)) }
        prefs.edit().putString(KEY_ITEMS, arr.toString()).apply()
    }

    private fun toJson(r: Reminder) = JSONObject().apply {
        put("id", r.id); put("listId", r.listId); put("title", r.title); put("notes", r.notes)
        put("hasTime", r.hasTime); put("due", r.dueMillis)
        put("hasLoc", r.hasLocation); put("lat", r.locLat); put("lng", r.locLng)
        put("rad", r.locRadius.toDouble()); put("locLabel", r.locLabel); put("arrival", r.onArrival)
        put("flag", r.flagged); put("done", r.completed); put("created", r.createdAt)
    }

    private fun fromJson(o: JSONObject) = Reminder(
        id = o.getString("id"), listId = o.optString("listId", "default"),
        title = o.optString("title"), notes = o.optString("notes"),
        hasTime = o.optBoolean("hasTime"), dueMillis = o.optLong("due"),
        hasLocation = o.optBoolean("hasLoc"), locLat = o.optDouble("lat"), locLng = o.optDouble("lng"),
        locRadius = o.optDouble("rad", 150.0).toFloat(), locLabel = o.optString("locLabel"),
        onArrival = o.optBoolean("arrival", true), flagged = o.optBoolean("flag"),
        completed = o.optBoolean("done"), createdAt = o.optLong("created", System.currentTimeMillis()))

    companion object {
        private const val KEY_LISTS = "lists"
        private const val KEY_ITEMS = "items"
    }
}
