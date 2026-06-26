package com.ruos.alarm.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import com.ruos.alarm.alarm.AlarmScheduler
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Surfaces the next alarm:
 *  1. A silent ongoing notification ("Будильник в HH:MM") — like iOS's upcoming chip.
 *  2. A broadcast the SystemUI Dynamic Island listens for to show the next alarm.
 *
 * (The lock-screen next-alarm icon itself is provided automatically by the OS via
 * AlarmManager.setAlarmClock, so nothing extra is needed for that.)
 */
class UpcomingNotifier(private val context: Context) {

    private val nm = context.getSystemService(NotificationManager::class.java)

    fun refresh() {
        ensureChannel()
        val next = AlarmScheduler(context).nextAcrossAll()
        if (next == null) {
            nm.cancel(NOTIF_ID)
            broadcastIsland(-1L, "")
            return
        }
        val (alarm, at) = next
        val timeStr = SimpleDateFormat("HH:mm", Locale("ru")).format(Date(at))
        val whenStr = relativeRu(at)

        val notif = Notification.Builder(context, Constants.NOTIF_CHANNEL_UPCOMING)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Будильник $timeStr")
            .setContentText(if (alarm.label.trim().isNotEmpty()) alarm.label else whenStr)
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .build()
        nm.notify(NOTIF_ID, notif)
        broadcastIsland(at, timeStr)
    }

    private fun broadcastIsland(at: Long, timeStr: String) {
        // SystemUI's DynamicIslandView can register for this to show the next alarm.
        context.sendBroadcast(Intent(ACTION_NEXT_ALARM).apply {
            setPackage("com.android.systemui")
            putExtra("time", timeStr)
            putExtra("at", at)
        })
    }

    private fun relativeRu(at: Long): String {
        val mins = ((at - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0)
        val h = mins / 60; val m = mins % 60
        return when {
            h <= 0 -> "через $m мин."
            m == 0L -> "через $h ч."
            else -> "через $h ч. $m мин."
        }
    }

    private fun ensureChannel() {
        if (nm.getNotificationChannel(Constants.NOTIF_CHANNEL_UPCOMING) == null) {
            nm.createNotificationChannel(NotificationChannel(
                Constants.NOTIF_CHANNEL_UPCOMING, "Предстоящий будильник",
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) })
        }
    }

    companion object {
        private const val NOTIF_ID = 4202
        const val ACTION_NEXT_ALARM = "com.ruos.alarm.NEXT_ALARM"
    }
}
