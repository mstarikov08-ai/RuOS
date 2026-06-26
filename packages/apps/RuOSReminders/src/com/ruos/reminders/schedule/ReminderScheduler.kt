package com.ruos.reminders.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.net.Uri
import com.ruos.reminders.model.Reminder
import com.ruos.reminders.store.ReminderStore

/**
 * Arms a reminder's triggers: a time alarm via AlarmManager and/or a location proximity
 * alert via LocationManager.addProximityAlert (pure AOSP geofencing — no Play Services).
 */
object ReminderScheduler {

    const val ACTION_TIME = "com.ruos.reminders.TIME"
    const val ACTION_PROX = "com.ruos.reminders.PROX"
    const val EXTRA_ID = "id"

    fun scheduleAll(context: Context) {
        ReminderStore(context).all().forEach { schedule(context, it) }
    }

    fun schedule(context: Context, r: Reminder) {
        cancel(context, r)
        if (r.completed) return
        val am = context.getSystemService(AlarmManager::class.java)
        if (r.hasTime && r.dueMillis > System.currentTimeMillis()) {
            runCatching {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.dueMillis, timePi(context, r.id))
            }.onFailure { am.set(AlarmManager.RTC_WAKEUP, r.dueMillis, timePi(context, r.id)) }
        }
        if (r.hasLocation) {
            val lm = context.getSystemService(LocationManager::class.java)
            runCatching {
                lm.addProximityAlert(r.locLat, r.locLng, r.locRadius, -1L, proxPi(context, r.id))
            }
        }
    }

    fun cancel(context: Context, r: Reminder) {
        context.getSystemService(AlarmManager::class.java).cancel(timePi(context, r.id))
        runCatching {
            context.getSystemService(LocationManager::class.java).removeProximityAlert(proxPi(context, r.id))
        }
    }

    private fun timePi(context: Context, id: String): PendingIntent {
        val i = Intent(context, ReminderAlarmReceiver::class.java)
            .setAction(ACTION_TIME).setData(Uri.parse("ruos-rem://time/$id")).putExtra(EXTRA_ID, id)
        return PendingIntent.getBroadcast(context, id.hashCode(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun proxPi(context: Context, id: String): PendingIntent {
        val i = Intent(context, ProximityReceiver::class.java)
            .setAction(ACTION_PROX).setData(Uri.parse("ruos-rem://prox/$id")).putExtra(EXTRA_ID, id)
        // mutable: the framework adds KEY_PROXIMITY_ENTERING to the delivered intent
        return PendingIntent.getBroadcast(context, id.hashCode(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
    }
}
