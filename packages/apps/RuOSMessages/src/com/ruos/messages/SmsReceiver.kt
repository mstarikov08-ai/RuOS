package com.ruos.messages

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import com.ruos.messages.spam.SpamFilter

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        val sender = messages[0].originatingAddress ?: "Неизвестный"
        val body = messages.joinToString("") { it.messageBody ?: "" }

        // Spam filtering: consult the shared rules from RuOS Phone's spam provider. This is
        // cross-process I/O (rules provider + contacts lookup), so it runs off the broadcast's
        // main thread via goAsync(). A blocked SMS is suppressed from notifications and recorded
        // in the shared block log (it also stays hidden from the Messages lists — they filter by
        // the same rules).
        val pending = goAsync()
        Thread {
            try {
                val rules = com.ruos.messages.spam.SpamRules.fetch(context)
                if (rules != null) {
                    val isContact = isContact(context, sender)
                    if (SpamFilter.decide(rules, sender, body, isContact) == SpamFilter.Decision.BLOCK) {
                        com.ruos.messages.spam.SpamRules.logBlocked(context, sender)
                        return@Thread
                    }
                }
                showNotification(context, sender, body)
            } finally {
                pending.finish()
            }
        }.start()
    }

    // Fail-safe: if the contacts lookup errors, treat the sender as a contact so a lookup
    // failure can never cause the short-code rule to block a legitimate message.
    private fun isContact(context: Context, number: String): Boolean = runCatching {
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)?.use {
            it.count > 0
        } ?: false
    }.getOrDefault(true)

    private fun showNotification(context: Context, sender: String, body: String) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return

        val channelId = "sms_incoming"
        if (nm.getNotificationChannel(channelId) == null) {
            val channel = NotificationChannel(
                channelId, "Входящие SMS", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления о входящих сообщениях"
                enableVibration(true)
            }
            nm.createNotificationChannel(channel)
        }

        val tapIntent = Intent(context, MessagesActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("sender", sender)
        }
        val pi = PendingIntent.getActivity(
            context, sender.hashCode(), tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(sender)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        nm.notify(sender.hashCode(), notification)
    }
}
