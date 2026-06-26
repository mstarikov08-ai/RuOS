package com.ruos.findmy.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.ruos.findmy.core.FindMySettings
import com.ruos.findmy.core.RemoteActions

/**
 * Receives the remote Find-My commands by SMS (no backend needed). A message authorises
 * an action only if it begins with the user's secret passphrase and — if a trusted number
 * is set — comes from that number. Supported commands (Russian or English):
 *
 *   <passphrase> ЗВУК   | SOUND    → play the locating alarm
 *   <passphrase> БЛОК   | LOCK     → lock the device now (device admin)
 *   <passphrase> ГДЕ    | LOCATE   → reply with a maps link to the last location
 *   <passphrase> СТЕРЕТЬ| WIPE     → factory-reset the device (device admin)
 */
class CommandSmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val settings = FindMySettings(context)
        if (!settings.isConfigured()) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val from = messages.firstOrNull()?.originatingAddress
        val body = messages.joinToString("") { it.messageBody ?: "" }.trim()

        if (!settings.numberMatches(from)) return
        val pass = settings.passphrase
        if (pass.isEmpty() || !body.startsWith(pass, ignoreCase = true)) return

        val command = body.removePrefix(pass).removePrefix(pass.uppercase()).trim().uppercase()
        when {
            command.startsWith("ЗВУК") || command.startsWith("SOUND") -> RemoteActions.playSound(context)
            command.startsWith("БЛОК") || command.startsWith("LOCK") -> RemoteActions.lock(context)
            command.startsWith("ГДЕ") || command.startsWith("LOCATE") ->
                from?.let { RemoteActions.replyLocation(context, it) }
            command.startsWith("СТЕР") || command.startsWith("WIPE") -> RemoteActions.wipe(context)
        }
    }
}
