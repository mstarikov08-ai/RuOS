package com.ruos.journal.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Posts the daily journal reminder; also re-arms the schedule after a reboot. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            runCatching { JournalReminder.update(context) }   // exact-alarm op may be revoked
            return
        }

        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(
                CHANNEL, "Напоминания журнала", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val open = PendingIntent.getActivity(
            context, 0,
            Intent().setClassName("com.ruos.journal", "com.ruos.journal.ui.JournalListActivity"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        nm.notify(7701, android.app.Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle("Журнал")
            .setContentText("Время записать мысли за день")
            .setAutoCancel(true)
            .setContentIntent(open)
            .build())
    }
    companion object { private const val CHANNEL = "ruos_journal_remind" }
}
