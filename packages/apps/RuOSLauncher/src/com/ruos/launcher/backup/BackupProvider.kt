package com.ruos.launcher.backup

import android.content.ContentProvider
import android.content.ContentValues
import android.content.SharedPreferences
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import org.json.JSONArray
import org.json.JSONObject

/**
 * RuOS backup provider — exposes this app's SharedPreferences to RuOSBackup, guarded by the
 * signature permission com.ruos.permission.BACKUP (only platform-signed RuOS can reach it).
 * Values are type-tagged so restore is byte-exact. Same template across participating apps;
 * only [prefsFiles] differs.
 */
class BackupProvider : ContentProvider() {
    private val prefsFiles = arrayOf("ruos_widgets", "ruos_launcher")

    override fun onCreate() = true

    override fun query(uri: Uri, proj: Array<String>?, sel: String?, args: Array<String>?, sort: String?): Cursor {
        val root = JSONObject()
        for (name in prefsFiles) {
            val sp = context!!.getSharedPreferences(name, 0)
            val obj = JSONObject()
            for ((k, v) in sp.all) obj.put(k, encode(v))
            root.put(name, obj)
        }
        return MatrixCursor(arrayOf("json")).apply { addRow(arrayOf(root.toString())) }
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method == "import" && arg != null) runCatching {
            val root = JSONObject(arg)
            for (name in prefsFiles) {
                val obj = root.optJSONObject(name) ?: continue
                val e = context!!.getSharedPreferences(name, 0).edit().clear()
                val it = obj.keys()
                while (it.hasNext()) { val k = it.next(); decode(e, k, obj.getJSONObject(k)) }
                e.apply()
            }
        }
        return null
    }

    private fun encode(v: Any?): JSONObject = when (v) {
        is String -> tag("s", v)
        is Boolean -> tag("b", v)
        is Int -> tag("i", v)
        is Long -> tag("l", v)
        is Float -> tag("f", v.toDouble())
        is Set<*> -> JSONObject().put("t", "set").put("v", JSONArray(v.map { it.toString() }))
        else -> tag("s", v.toString())
    }
    private fun tag(t: String, v: Any) = JSONObject().put("t", t).put("v", v)

    private fun decode(e: SharedPreferences.Editor, k: String, o: JSONObject) {
        when (o.optString("t")) {
            "s" -> e.putString(k, o.optString("v"))
            "b" -> e.putBoolean(k, o.optBoolean("v"))
            "i" -> e.putInt(k, o.optInt("v"))
            "l" -> e.putLong(k, o.optLong("v"))
            "f" -> e.putFloat(k, o.optDouble("v").toFloat())
            "set" -> {
                val arr = o.optJSONArray("v"); val set = HashSet<String>()
                if (arr != null) for (i in 0 until arr.length()) set.add(arr.getString(i))
                e.putStringSet(k, set)
            }
        }
    }

    override fun getType(uri: Uri) = "vnd.ruos/backup"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, sel: String?, args: Array<String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, sel: String?, args: Array<String>?) = 0
}
