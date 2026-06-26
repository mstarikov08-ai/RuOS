package com.ruos.focus.model

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists the user's focuses (built-ins, seeded once, plus any custom ones the user
 * adds) and remembers which one is currently active. Plain JSON in SharedPreferences —
 * no secrets here, so no encryption needed (unlike Journal/Auth).
 */
class FocusStore(context: Context) {

    private val prefs = context.getSharedPreferences("ruos_focus", Context.MODE_PRIVATE)

    init {
        if (!prefs.contains(KEY_MODES)) {
            // first launch: seed the six iOS-style presets
            save(FocusMode.builtIns())
        }
    }

    fun all(): List<FocusMode> {
        val raw = prefs.getString(KEY_MODES, null) ?: return FocusMode.builtIns()
        return runCatching { parse(raw) }.getOrDefault(FocusMode.builtIns())
    }

    fun byId(id: String): FocusMode? = all().firstOrNull { it.id == id }

    fun save(modes: List<FocusMode>) {
        prefs.edit().putString(KEY_MODES, serialize(modes)).apply()
    }

    fun upsert(mode: FocusMode) {
        val list = all().toMutableList()
        val i = list.indexOfFirst { it.id == mode.id }
        if (i >= 0) list[i] = mode else list.add(mode)
        save(list)
    }

    fun delete(id: String) {
        val mode = byId(id) ?: return
        if (mode.builtIn) return            // built-ins can be edited but not removed
        save(all().filterNot { it.id == id })
        if (activeId() == id) setActive(null)
    }

    /** The currently active focus id, or null. */
    fun activeId(): String? = prefs.getString(KEY_ACTIVE, null)

    fun active(): FocusMode? = activeId()?.let { byId(it) }

    fun setActive(id: String?) {
        prefs.edit().apply { if (id == null) remove(KEY_ACTIVE) else putString(KEY_ACTIVE, id) }.apply()
    }

    // ── JSON ──────────────────────────────────────────────────────────────────

    private fun serialize(modes: List<FocusMode>): String {
        val arr = JSONArray()
        modes.forEach { m ->
            val o = JSONObject()
            o.put("id", m.id); o.put("name", m.name); o.put("color", m.colorHex)
            o.put("icon", m.icon.name)
            o.put("apps", JSONArray(m.allowedPackages.toList()))
            o.put("allowCalls", m.allowCalls); o.put("allowRepeat", m.allowRepeatCalls)
            o.put("suppressAll", m.suppressAll); o.put("dim", m.dimLockScreen)
            o.put("hide", m.hideNotifications); o.put("reply", m.autoReply)
            o.put("builtIn", m.builtIn)
            m.schedule?.let { s ->
                o.put("sch", JSONObject().apply {
                    put("on", s.enabled); put("start", s.startMin)
                    put("end", s.endMin); put("days", s.days)
                })
            }
            arr.put(o)
        }
        return arr.toString()
    }

    private fun parse(raw: String): List<FocusMode> {
        val arr = JSONArray(raw)
        val out = ArrayList<FocusMode>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val apps = linkedSetOf<String>()
            o.optJSONArray("apps")?.let { a -> for (j in 0 until a.length()) apps.add(a.getString(j)) }
            val sch = o.optJSONObject("sch")?.let {
                FocusSchedule(it.optBoolean("on"), it.optInt("start", 22 * 60),
                    it.optInt("end", 7 * 60), it.optInt("days", 0x7F))
            }
            out.add(FocusMode(
                id = o.getString("id"), name = o.getString("name"),
                colorHex = o.optLong("color", 0xFF5E5CE6),
                icon = runCatching { FocusIcon.valueOf(o.optString("icon", "MOON")) }.getOrDefault(FocusIcon.MOON),
                allowedPackages = apps,
                allowCalls = o.optBoolean("allowCalls", true),
                allowRepeatCalls = o.optBoolean("allowRepeat", true),
                suppressAll = o.optBoolean("suppressAll", false),
                dimLockScreen = o.optBoolean("dim", true),
                hideNotifications = o.optBoolean("hide", false),
                autoReply = o.optString("reply", ""),
                schedule = sch,
                builtIn = o.optBoolean("builtIn", false)
            ))
        }
        return out
    }

    companion object {
        private const val KEY_MODES = "modes_json"
        private const val KEY_ACTIVE = "active_id"
    }
}
