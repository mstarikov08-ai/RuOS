package com.ruos.alarm.model

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON-backed persistence for alarms + bedtime config in SharedPreferences.
 * Survives reboot; BootReceiver reschedules everything from here.
 */
class AlarmStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("ruos_alarm", Context.MODE_PRIVATE)

    // ── Alarms ──────────────────────────────────────────────────────────────

    fun getAlarms(): MutableList<Alarm> {
        val json = prefs.getString(KEY_ALARMS, null) ?: return mutableListOf()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
                .sortedWith(compareBy({ it.hour }, { it.minute }))
                .toMutableList()
        } catch (_: Exception) { mutableListOf() }
    }

    fun saveAlarms(alarms: List<Alarm>) {
        val arr = JSONArray()
        alarms.forEach { arr.put(toJson(it)) }
        prefs.edit().putString(KEY_ALARMS, arr.toString()).apply()
    }

    fun upsert(alarm: Alarm) {
        val list = getAlarms()
        val idx = list.indexOfFirst { it.id == alarm.id }
        if (idx >= 0) list[idx] = alarm else list.add(alarm)
        saveAlarms(list)
    }

    fun delete(id: Long) = saveAlarms(getAlarms().filterNot { it.id == id })

    fun get(id: Long): Alarm? = getAlarms().firstOrNull { it.id == id }

    fun newId(): Long = System.currentTimeMillis()

    private fun toJson(a: Alarm) = JSONObject().apply {
        put("id", a.id); put("hour", a.hour); put("minute", a.minute)
        put("label", a.label)
        put("repeatDays", JSONArray(a.repeatDays.toList()))
        put("enabled", a.enabled); put("soundId", a.soundId)
        put("snoozeEnabled", a.snoozeEnabled); put("vibrationId", a.vibrationId)
        put("sunrise", a.sunrise); put("skipNext", a.skipNext)
    }

    private fun fromJson(o: JSONObject): Alarm {
        val days = mutableSetOf<Int>()
        o.optJSONArray("repeatDays")?.let { for (i in 0 until it.length()) days.add(it.getInt(i)) }
        return Alarm(
            id = o.getLong("id"),
            hour = o.getInt("hour"),
            minute = o.getInt("minute"),
            label = o.optString("label", ""),
            repeatDays = days,
            enabled = o.optBoolean("enabled", true),
            soundId = o.optString("soundId", AlarmSound.GENTLE.id),
            snoozeEnabled = o.optBoolean("snoozeEnabled", true),
            vibrationId = o.optString("vibrationId", VibrationPattern.BASIC.id),
            sunrise = o.optBoolean("sunrise", false),
            skipNext = o.optBoolean("skipNext", false)
        )
    }

    // ── Bedtime ─────────────────────────────────────────────────────────────

    data class Bedtime(
        var enabled: Boolean = false,
        var sleepHour: Int = 23, var sleepMinute: Int = 0,
        var wakeHour: Int = 7, var wakeMinute: Int = 0,
        var sleepSoundId: String = SleepSound.NONE.id
    )

    fun getBedtime(): Bedtime {
        val json = prefs.getString(KEY_BEDTIME, null) ?: return Bedtime()
        return try {
            val o = JSONObject(json)
            Bedtime(
                enabled = o.optBoolean("enabled", false),
                sleepHour = o.optInt("sleepHour", 23), sleepMinute = o.optInt("sleepMinute", 0),
                wakeHour = o.optInt("wakeHour", 7), wakeMinute = o.optInt("wakeMinute", 0),
                sleepSoundId = o.optString("sleepSoundId", SleepSound.NONE.id)
            )
        } catch (_: Exception) { Bedtime() }
    }

    fun saveBedtime(b: Bedtime) {
        val o = JSONObject().apply {
            put("enabled", b.enabled)
            put("sleepHour", b.sleepHour); put("sleepMinute", b.sleepMinute)
            put("wakeHour", b.wakeHour); put("wakeMinute", b.wakeMinute)
            put("sleepSoundId", b.sleepSoundId)
        }
        prefs.edit().putString(KEY_BEDTIME, o.toString()).apply()
    }

    companion object {
        private const val KEY_ALARMS = "alarms"
        private const val KEY_BEDTIME = "bedtime"
    }
}
