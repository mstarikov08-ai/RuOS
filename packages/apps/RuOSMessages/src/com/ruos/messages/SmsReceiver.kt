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

        // Spam filtering: consult the shared rules from RuOS Phone's spam provider. A blocked SMS is
        // silently dropped from notifications and recorded in the shared block log.
        val rules = fetchSpamRules(context)
        if (rules != null) {
            val isContact = isContact(context, sender)
            if (SpamFilter.decide(rules, sender, body, isContact) == SpamFilter.Decision.BLOCK) {
                logBlocked(context, sender)
                return
            }
        }

        showNotification(context, sender, body)
    }

    private fun fetchSpamRules(context: Context): SpamFilter.Rules? = runCatching {
        val uri = Uri.parse("content://com.ruos.phone.spam")
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) SpamFilter.fromJson(c.getString(0)) else null
        }
    }.getOrNull()

    private fun isContact(context: Context, number: String): Boolean = runCatching {
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)?.use {
            it.count > 0
        } ?: false
    }.getOrDefault(false)

    private fun logBlocked(context: Context, sender: String) {
        runCatching { context.contentResolver.call(Uri.parse("content://com.ruos.phone.spam"), "logBlocked", sender, null) }
    }

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
