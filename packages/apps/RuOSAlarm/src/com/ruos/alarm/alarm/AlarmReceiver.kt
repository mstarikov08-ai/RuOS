package com.ruos.alarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ruos.alarm.model.AlarmStore
import com.ruos.alarm.util.Constants

/**
 * Fires for the exact alarm time and the sunrise pre-alarm. It never launches an
 * Activity directly (background-activity-launch is restricted on Android 14);
 * instead it starts the foreground [AlarmRingService], which posts a full-screen-
 * intent notification that the OS turns into the full-screen ring/sunrise screen.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(Constants.EXTRA_ALARM_ID, -1L)
        if (id < 0) return
        val store = AlarmStore(context)
        val alarm = store.get(id) ?: return

        when (intent.action) {
            Constants.ACTION_SUNRISE -> {
                start(context, id, mode = "sunrise", snoozed = false)
            }
            Constants.ACTION_ALARM_FIRE -> {
                val snoozed = intent.getBooleanExtra(Constants.EXTRA_SNOOZED, false)
                // Advance recurring / disable one-shot for the NEXT cycle (snooze
                // re-fires don't advance the schedule).
                if (!snoozed) AlarmScheduler(context).onFired(alarm)
                start(context, id, mode = "ring", snoozed = snoozed)
            }
        }
    }

    private fun start(context: Context, id: Long, mode: String, snoozed: Boolean) {
        val svc = Intent(context, AlarmRingService::class.java).apply {
            putExtra(Constants.EXTRA_ALARM_ID, id)
            putExtra("mode", mode)
            putExtra(Constants.EXTRA_SNOOZED, snoozed)
        }
        context.startForegroundService(svc)
    }
}
