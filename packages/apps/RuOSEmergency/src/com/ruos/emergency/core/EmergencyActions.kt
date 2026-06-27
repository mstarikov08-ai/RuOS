package com.ruos.emergency.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.telephony.SmsManager
import com.ruos.emergency.store.EmergencyStore

/** The real emergency actions: place the 112 call and text the user's location to their
 *  emergency contacts. */
object EmergencyActions {

    /** Dial 112. Uses ACTION_CALL (auto-dial) when CALL_PHONE is granted, else ACTION_DIAL. */
    fun call112(context: Context) {
        val uri = Uri.parse("tel:112")
        val canCall = context.checkSelfPermission(android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val intent = Intent(if (canCall) Intent.ACTION_CALL else Intent.ACTION_DIAL, uri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** Text each emergency contact "I need help" + a maps link to the last known location. */
    fun alertContacts(context: Context): Int {
        val contacts = EmergencyStore(context).contacts()
        if (contacts.isEmpty()) return 0
        val loc = lastLocation(context)
        val text = buildString {
            append("Мне нужна помощь. ")
            if (loc != null) append("Я здесь: https://yandex.ru/maps/?ll=${loc.longitude},${loc.latitude}&z=16")
            else append("Местоположение недоступно.")
            append(" — Экстренное сообщение RuOS.")
        }
        var sent = 0
        val sms = smsManager(context)
        contacts.forEach { c ->
            runCatching { sms.sendTextMessage(c.phone, null, text, null, null); sent++ }
        }
        return sent
    }

    private fun lastLocation(context: Context): android.location.Location? {
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        return runCatching {
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        }.getOrNull()
    }

    private fun smsManager(context: Context): SmsManager =
        if (android.os.Build.VERSION.SDK_INT >= 31) context.getSystemService(SmsManager::class.java)
        else @Suppress("DEPRECATION") SmsManager.getDefault()
}
