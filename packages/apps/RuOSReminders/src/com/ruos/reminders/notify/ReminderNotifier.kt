package com.ruos.reminders.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.ruos.reminders.model.Reminder
import com.ruos.reminders.schedule.CompleteReceiver
import com.ruos.reminders.schedule.ReminderScheduler

/** Builds and shows the reminder notification with a «Выполнено» action. */
object ReminderNotifier {

    private const val CHANNEL = "ruos_reminders"

    fun show(context: Context, r: Reminder) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(
            CHANNEL, "Напоминания", NotificationManager.IMPORTANCE_HIGH))

        val open = PendingIntent.getActivity(context, r.id.hashCode(),
            Intent().setClassName("com.ruos.reminders", "com.ruos.reminders.ui.RemindersActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val done = PendingIntent.getBroadcast(context, r.id.hashCode() + 1,
            Intent(context, CompleteReceiver::class.java).putExtra(ReminderScheduler.EXTRA_ID, r.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val text = listOf(r.notes, r.subtitle()).filter { it.isNotEmpty() }.joinToString(" — ")
        val n = Notification.Builder(context, CHANNEL)
            .setContentTitle(r.title.ifEmpty { "Напоминание" })
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(Notification.Action.Builder(null, "Выполнено", done).build())
            .build()
        nm.notify(r.id.hashCode(), n)
    }

    fun dismiss(context: Context, id: String) {
        context.getSystemService(NotificationManager::class.java).cancel(id.hashCode())
    }
}
