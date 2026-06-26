package com.ruos.alarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.ruos.alarm.model.Alarm
import com.ruos.alarm.model.AlarmStore
import com.ruos.alarm.ui.AlarmListActivity
import com.ruos.alarm.util.Constants
import java.util.Calendar

/**
 * Turns stored [Alarm]s into exact OS alarms via [AlarmManager.setAlarmClock] — the
 * same API the AOSP clock uses, so the next alarm shows on the lock screen, fires
 * through Doze, and rings even at low priority. Recurring alarms reschedule
 * themselves after firing; one-shots disable. Sunrise alarms get a second, earlier
 * pre-alarm that starts the screen-brightening.
 */
class AlarmScheduler(private val context: Context) {

    private val am = context.getSystemService(AlarmManager::class.java)
    private val store = AlarmStore(context)

    fun rescheduleAll() {
        store.getAlarms().forEach { schedule(it) }
    }

    fun schedule(alarm: Alarm) {
        cancel(alarm)
        if (!alarm.enabled) return
        val triggerAt = nextTrigger(alarm, skipOnce = alarm.skipNext) ?: return

        // Main fire intent → AlarmReceiver.
        val fire = PendingIntent.getBroadcast(
            context, Constants.RC_MAIN + alarm.id.toInt(),
            Intent(context, AlarmReceiver::class.java).apply {
                action = Constants.ACTION_ALARM_FIRE
                putExtra(Constants.EXTRA_ALARM_ID, alarm.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Show intent (tapping the lock-screen alarm chip opens the app).
        val show = PendingIntent.getActivity(
            context, Constants.RC_SHOW + alarm.id.toInt(),
            Intent(context, AlarmListActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, show), fire)

        // Sunrise: brighten the screen SUNRISE_LEAD_MINUTES before.
        if (alarm.sunrise) {
            val sunriseAt = triggerAt - Constants.SUNRISE_LEAD_MINUTES * 60_000L
            if (sunriseAt > System.currentTimeMillis()) {
                val sunrise = PendingIntent.getBroadcast(
                    context, Constants.RC_SUNRISE + alarm.id.toInt(),
                    Intent(context, AlarmReceiver::class.java).apply {
                        action = Constants.ACTION_SUNRISE
                        putExtra(Constants.EXTRA_ALARM_ID, alarm.id)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, sunriseAt, sunrise)
            }
        }
    }

    /** Schedule a one-off snooze in [minutes]. */
    fun scheduleSnooze(alarm: Alarm, minutes: Int) {
        val at = System.currentTimeMillis() + minutes * 60_000L
        val fire = PendingIntent.getBroadcast(
            context, Constants.RC_MAIN + alarm.id.toInt(),
            Intent(context, AlarmReceiver::class.java).apply {
                action = Constants.ACTION_ALARM_FIRE
                putExtra(Constants.EXTRA_ALARM_ID, alarm.id)
                putExtra(Constants.EXTRA_SNOOZED, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val show = PendingIntent.getActivity(
            context, Constants.RC_SHOW + alarm.id.toInt(),
            Intent(context, AlarmListActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), fire)
    }

    fun cancel(alarm: Alarm) {
        listOf(Constants.RC_MAIN, Constants.RC_SUNRISE).forEach { rc ->
            val action = if (rc == Constants.RC_SUNRISE) Constants.ACTION_SUNRISE else Constants.ACTION_ALARM_FIRE
            PendingIntent.getBroadcast(
                context, rc + alarm.id.toInt(),
                Intent(context, AlarmReceiver::class.java).apply { this.action = action },
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )?.let { am.cancel(it); it.cancel() }
        }
    }

    /**
     * After a recurring alarm fires, advance it to the next day; a one-shot is
     * disabled. A pending skipNext is consumed here.
     */
    fun onFired(alarm: Alarm) {
        if (alarm.isOneShot) {
            alarm.enabled = false
            store.upsert(alarm)
            cancel(alarm)
        } else {
            if (alarm.skipNext) { alarm.skipNext = false; store.upsert(alarm) }
            schedule(alarm)
        }
    }

    /** The soonest upcoming trigger across all enabled alarms, or null. */
    fun nextAcrossAll(): Pair<Alarm, Long>? =
        store.getAlarms().filter { it.enabled }
            .mapNotNull { a -> nextTrigger(a, skipOnce = a.skipNext)?.let { a to it } }
            .minByOrNull { it.second }

    /**
     * Compute the next trigger time in epoch millis. [skipOnce] skips the first
     * matching occurrence (used for skip-next).
     */
    fun nextTrigger(alarm: Alarm, skipOnce: Boolean = false): Long? {
        val now = Calendar.getInstance()
        val cand = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (alarm.isOneShot) {
            if (cand.timeInMillis <= now.timeInMillis) cand.add(Calendar.DAY_OF_YEAR, 1)
            return cand.timeInMillis
        }
        // Recurring: scan up to 14 days ahead for the first enabled weekday.
        var skips = if (skipOnce) 1 else 0
        for (offset in 0..14) {
            val c = cand.clone() as Calendar
            c.add(Calendar.DAY_OF_YEAR, offset)
            if (c.timeInMillis <= now.timeInMillis) continue
            val dow = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7   // 0=Mon..6=Sun
            if (alarm.repeatDays.contains(dow)) {
                if (skips > 0) { skips--; continue }
                return c.timeInMillis
            }
        }
        return null
    }
}
