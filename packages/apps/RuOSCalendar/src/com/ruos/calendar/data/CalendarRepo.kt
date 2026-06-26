package com.ruos.calendar.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import java.util.Calendar
import java.util.TimeZone

/** A user calendar (account-backed; e.g. a synced Yandex calendar). */
data class CalendarInfo(val id: Long, val name: String, val color: Int, val account: String)

/** Editable detail of a single event (from the Events table). */
data class EventDetail(
    val eventId: Long, val title: String, val location: String, val notes: String,
    val begin: Long, val end: Long, val allDay: Boolean, val calendarId: Long)

/** A single event instance (recurrences already expanded by the Instances table). */
data class CalEvent(
    val eventId: Long,
    val title: String,
    val location: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val color: Int
)

/**
 * Thin wrapper over the system CalendarContract. Reading and writing here means RuOS
 * Calendar shares events with every account synced on the device (Google, Yandex, …) —
 * no separate store. Requires READ_CALENDAR / WRITE_CALENDAR.
 */
class CalendarRepo(private val context: Context) {

    fun calendars(): List<CalendarInfo> {
        val out = ArrayList<CalendarInfo>()
        val proj = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.VISIBLE)
        runCatching {
            context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, proj,
                "${CalendarContract.Calendars.VISIBLE}=1", null, null)?.use { c ->
                while (c.moveToNext()) {
                    out.add(CalendarInfo(c.getLong(0), c.getString(1) ?: "Календарь",
                        c.getInt(2), c.getString(3) ?: ""))
                }
            }
        }
        return out
    }

    /** Default calendar to add new events to (first writable one). */
    fun defaultCalendarId(): Long? = calendars().firstOrNull()?.id

    /** Event instances overlapping [from, to). */
    fun instances(from: Long, to: Long): List<CalEvent> {
        val out = ArrayList<CalEvent>()
        val proj = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.DISPLAY_COLOR)
        runCatching {
            CalendarContract.Instances.query(context.contentResolver, proj, from, to)?.use { c ->
                while (c.moveToNext()) {
                    out.add(CalEvent(
                        c.getLong(0), c.getString(1) ?: "(Без названия)",
                        c.getString(4) ?: "", c.getLong(2), c.getLong(3),
                        c.getInt(5) == 1, c.getInt(6)))
                }
            }
        }
        return out.sortedBy { it.begin }
    }

    fun eventsForDay(dayStart: Long): List<CalEvent> {
        val dayEnd = dayStart + 24 * 3600_000L
        return instances(dayStart, dayEnd).filter { it.begin < dayEnd && it.end > dayStart }
    }

    /** Which day-of-month numbers in [monthStart..] have at least one event. */
    fun daysWithEvents(monthStart: Long, monthEnd: Long): Set<Int> {
        val tz = TimeZone.getDefault()
        return instances(monthStart, monthEnd).map {
            Calendar.getInstance(tz).apply { timeInMillis = it.begin }.get(Calendar.DAY_OF_MONTH)
        }.toHashSet()
    }

    fun insert(calendarId: Long, title: String, location: String, notes: String,
               begin: Long, end: Long, allDay: Boolean, reminderMin: Int): Long? {
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.EVENT_LOCATION, location)
            put(CalendarContract.Events.DESCRIPTION, notes)
            put(CalendarContract.Events.DTSTART, begin)
            put(CalendarContract.Events.DTEND, end)
            put(CalendarContract.Events.ALL_DAY, if (allDay) 1 else 0)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
        }
        val uri = runCatching { context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values) }.getOrNull()
            ?: return null
        val id = ContentUris.parseId(uri)
        if (reminderMin >= 0) runCatching {
            context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, id)
                put(CalendarContract.Reminders.MINUTES, reminderMin)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            })
        }
        return id
    }

    fun loadEvent(eventId: Long): EventDetail? = runCatching {
        val proj = arrayOf(
            CalendarContract.Events.TITLE, CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DESCRIPTION, CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND, CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.CALENDAR_ID)
        context.contentResolver.query(
            ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId), proj, null, null, null)?.use { c ->
            if (c.moveToFirst()) EventDetail(
                eventId, c.getString(0) ?: "", c.getString(1) ?: "", c.getString(2) ?: "",
                c.getLong(3), c.getLong(4), c.getInt(5) == 1, c.getLong(6))
            else null
        }
    }.getOrNull()

    fun update(eventId: Long, title: String, location: String, notes: String,
               begin: Long, end: Long, allDay: Boolean): Boolean = runCatching {
        val values = ContentValues().apply {
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.EVENT_LOCATION, location)
            put(CalendarContract.Events.DESCRIPTION, notes)
            put(CalendarContract.Events.DTSTART, begin)
            put(CalendarContract.Events.DTEND, end)
            put(CalendarContract.Events.ALL_DAY, if (allDay) 1 else 0)
        }
        context.contentResolver.update(
            ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId), values, null, null) > 0
    }.getOrDefault(false)

    fun delete(eventId: Long): Boolean = runCatching {
        context.contentResolver.delete(
            ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId), null, null) > 0
    }.getOrDefault(false)

    companion object {
        fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
