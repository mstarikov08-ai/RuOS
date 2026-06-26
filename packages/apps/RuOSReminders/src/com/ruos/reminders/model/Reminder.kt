package com.ruos.reminders.model

/** A reminder list (iOS-style), e.g. «Напоминания», «Покупки», with an accent colour. */
data class ReminderList(val id: String, val name: String, val colorHex: Long) {
    val color: Int get() = colorHex.toInt()
}

/**
 * A single reminder. May fire on a time ([hasTime]/[dueMillis]) and/or on arriving at or
 * leaving a place ([hasLocation] + lat/lng/radius/[onArrival]). Both triggers are real:
 * time via AlarmManager, location via LocationManager proximity alerts (no Play Services).
 */
data class Reminder(
    val id: String,
    val listId: String,
    val title: String,
    val notes: String = "",
    val hasTime: Boolean = false,
    val dueMillis: Long = 0L,
    val hasLocation: Boolean = false,
    val locLat: Double = 0.0,
    val locLng: Double = 0.0,
    val locRadius: Float = 150f,
    val locLabel: String = "",
    val onArrival: Boolean = true,        // true = when arriving, false = when leaving
    val flagged: Boolean = false,
    val completed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun subtitle(): String {
        val parts = ArrayList<String>()
        if (hasTime && dueMillis > 0) parts.add(
            java.text.SimpleDateFormat("d MMM, HH:mm", java.util.Locale("ru")).format(java.util.Date(dueMillis)))
        if (hasLocation) parts.add((if (onArrival) "по прибытии: " else "при уходе: ") + locLabel.ifEmpty { "место" })
        return parts.joinToString(" · ")
    }
}
