package com.ruos.journal.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.ruos.journal.model.JournalStore
import java.util.Calendar

/** Schedules (or cancels) the daily "time to journal" reminder via AlarmManager. */
object JournalReminder {

    const val ACTION_FIRE = "com.ruos.journal.REMIND"

    fun update(context: Context) {
        val store = JournalStore(context)
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, ReminderReceiver::class.java).setAction(ACTION_FIRE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        am.cancel(pi)
        if (!store.reminderEnabled) return

        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, store.reminderHour); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, next.timeInMillis,
            AlarmManager.INTERVAL_DAY, pi)
    }
}
