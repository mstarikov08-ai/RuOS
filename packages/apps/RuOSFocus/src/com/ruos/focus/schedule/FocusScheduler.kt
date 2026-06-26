package com.ruos.focus.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.ruos.focus.model.FocusStore
import java.util.Calendar

/**
 * Drives time-based activation. Rather than scheduling per-mode start/stop alarms (which
 * multiply quickly), it sets a single "re-evaluate" alarm at the next schedule boundary
 * and, on each fire, recomputes which scheduled focus *should* be on right now. This is
 * robust to reboots, missed alarms, and overlapping windows (first match wins).
 */
object FocusScheduler {

    const val ACTION_EVAL = "com.ruos.focus.EVALUATE"
    private const val REQ = 7711

    /** Apply the schedule for "now" and arm the next boundary alarm. */
    fun evaluateAndReschedule(context: Context) {
        val store = FocusStore(context)
        val cal = Calendar.getInstance()
        val nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val todayMz = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7   // Calendar.SUN=1 → Mon=0

        // Which scheduled focus should be active now? (first match in user order)
        val due = store.all().firstOrNull { it.schedule?.contains(nowMin, todayMz) == true }
        val activeNow = store.active()

        when {
            due != null && activeNow?.id != due.id -> FocusController.activate(context, due)
            // a schedule just ended and the active focus was the scheduled one
            due == null && activeNow != null && activeNow.schedule?.enabled == true ->
                FocusController.deactivate(context)
        }
        armNextBoundary(context, store)
    }

    /** Find the soonest upcoming schedule edge and set one exact alarm for it. */
    private fun armNextBoundary(context: Context, store: FocusStore) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val edges = ArrayList<Int>()                   // minutes-of-day boundaries
        store.all().forEach { m ->
            m.schedule?.let { if (it.enabled) { edges.add(it.startMin); edges.add(it.endMin) } }
        }
        if (edges.isEmpty()) { cancel(context, am); return }

        val cal = Calendar.getInstance()
        val nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val nextEdge = edges.filter { it > nowMin }.minOrNull()
        val target = Calendar.getInstance().apply {
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (nextEdge != null) {
                set(Calendar.HOUR_OF_DAY, nextEdge / 60); set(Calendar.MINUTE, nextEdge % 60)
            } else {
                // all edges are earlier today → first edge tomorrow
                add(Calendar.DAY_OF_YEAR, 1)
                val first = edges.min()
                set(Calendar.HOUR_OF_DAY, first / 60); set(Calendar.MINUTE, first % 60)
            }
        }
        val pi = pending(context)
        runCatching { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, pi) }
            .onFailure { am.set(AlarmManager.RTC_WAKEUP, target.timeInMillis, pi) }
    }

    private fun cancel(context: Context, am: AlarmManager) {
        am.cancel(pending(context))
    }

    private fun pending(context: Context): PendingIntent {
        val intent = Intent(context, FocusAlarmReceiver::class.java).setAction(ACTION_EVAL)
        return PendingIntent.getBroadcast(
            context, REQ, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
