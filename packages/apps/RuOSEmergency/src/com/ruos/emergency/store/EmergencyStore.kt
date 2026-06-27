package com.ruos.emergency.store

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Medical ID — shown on the lock screen in an emergency (so it is intentionally readable
 *  without unlocking, exactly like iOS). */
data class MedicalId(
    val name: String = "",
    val dob: String = "",
    val bloodType: String = "",
    val height: String = "",
    val weight: String = "",
    val allergies: String = "",
    val conditions: String = "",
    val medications: String = "",
    val notes: String = "",
    val organDonor: Boolean = false
) {
    fun isEmpty() = name.isBlank() && allergies.isBlank() && conditions.isBlank() &&
        medications.isBlank() && bloodType.isBlank() && notes.isBlank()
}

data class EmergencyContact(val name: String, val phone: String, val relation: String)

/** Plain persistence — Medical ID must be reachable from the lock screen, so it is not
 *  encrypted; it holds only what the user chooses to expose in an emergency. */
class EmergencyStore(context: Context) {

    private val prefs = context.getSharedPreferences("ruos_emergency", Context.MODE_PRIVATE)

    fun medicalId(): MedicalId {
        val o = runCatching { JSONObject(prefs.getString(KEY_ID, "{}") ?: "{}") }.getOrDefault(JSONObject())
        return MedicalId(
            o.optString("name"), o.optString("dob"), o.optString("blood"), o.optString("height"),
            o.optString("weight"), o.optString("allergies"), o.optString("conditions"),
            o.optString("medications"), o.optString("notes"), o.optBoolean("donor"))
    }

    fun saveMedicalId(m: MedicalId) {
        val o = JSONObject().apply {
            put("name", m.name); put("dob", m.dob); put("blood", m.bloodType); put("height", m.height)
            put("weight", m.weight); put("allergies", m.allergies); put("conditions", m.conditions)
            put("medications", m.medications); put("notes", m.notes); put("donor", m.organDonor)
        }
        prefs.edit().putString(KEY_ID, o.toString()).apply()
    }

    fun contacts(): List<EmergencyContact> {
        val arr = runCatching { JSONArray(prefs.getString(KEY_CONTACTS, "[]") ?: "[]") }.getOrDefault(JSONArray())
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it); EmergencyContact(o.optString("name"), o.optString("phone"), o.optString("relation"))
        }
    }

    fun saveContacts(list: List<EmergencyContact>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().apply { put("name", it.name); put("phone", it.phone); put("relation", it.relation) }) }
        prefs.edit().putString(KEY_CONTACTS, arr.toString()).apply()
    }

    companion object {
        private const val KEY_ID = "medical_id"
        private const val KEY_CONTACTS = "contacts"
    }
}
