package com.ruos.launcher.widget

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists the user's chosen widgets and their sizes (the iOS "edit widgets" state). Plain
 * JSON in SharedPreferences — no secrets. Seeds a sensible default the first time so Today
 * View isn't empty. The serialize/parse is pure and round-trip-verified.
 */
class WidgetStore(context: Context) {

    private val prefs = context.getSharedPreferences("ruos_widgets", Context.MODE_PRIVATE)

    fun widgets(): List<WidgetSpec> {
        val raw = prefs.getString(KEY, null) ?: return DEFAULTS
        return runCatching { parse(raw) }.getOrDefault(DEFAULTS)
    }

    fun save(specs: List<WidgetSpec>) {
        prefs.edit().putString(KEY, serialize(specs)).apply()
    }

    fun add(spec: WidgetSpec) = save(widgets() + spec)

    fun removeAt(i: Int) = save(widgets().toMutableList().also { if (i in it.indices) it.removeAt(i) })

    fun setSizeAt(i: Int, size: WidgetSize) =
        save(widgets().mapIndexed { idx, s -> if (idx == i) s.copy(size = size) else s })

    companion object {
        private const val KEY = "specs"
        val DEFAULTS = listOf(
            WidgetSpec("weather", WidgetSize.MEDIUM),
            WidgetSpec("music", WidgetSize.MEDIUM))

        /** type|size pairs → JSON array. Pure; mirrored in verify_stores.py. */
        fun serialize(specs: List<WidgetSpec>): String {
            val arr = JSONArray()
            specs.forEach { arr.put(JSONObject().apply { put("type", it.type); put("size", it.size.name) }) }
            return arr.toString()
        }

        fun parse(raw: String): List<WidgetSpec> {
            val arr = JSONArray(raw)
            val out = ArrayList<WidgetSpec>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val type = o.optString("type")
                if (type.isNullOrEmpty()) continue
                out.add(WidgetSpec(type, WidgetSize.from(o.optString("size"))))
            }
            return out
        }
    }
}
