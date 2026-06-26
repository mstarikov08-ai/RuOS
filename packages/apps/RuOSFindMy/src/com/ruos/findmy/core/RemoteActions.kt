package com.ruos.findmy.core

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.telephony.SmsManager
import com.ruos.findmy.admin.FindMyAdminReceiver

/**
 * The real device actions behind Find My RuOS. Lock/wipe use DevicePolicyManager (require
 * the active device admin); locate uses the last known fix and replies with a maps link;
 * sound plays a loud alarm even when silent. These run locally (from the app's buttons)
 * and remotely (from a trusted SMS command).
 */
object RemoteActions {

    fun lock(context: Context): Boolean {
        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return false
        if (!dpm.isAdminActive(FindMyAdminReceiver.component(context))) return false
        return runCatching { dpm.lockNow(); true }.getOrDefault(false)
    }

    fun wipe(context: Context): Boolean {
        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return false
        if (!dpm.isAdminActive(FindMyAdminReceiver.component(context))) return false
        return runCatching { dpm.wipeData(0); true }.getOrDefault(false)
    }

    fun playSound(context: Context) = AlarmPlayer.play(context)
    fun stopSound() = AlarmPlayer.stop()

    /** Best-effort last known location across providers. */
    fun lastLocation(context: Context): Location? {
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.FUSED_PROVIDER)
        var best: Location? = null
        for (p in providers) {
            val loc = runCatching { lm.getLastKnownLocation(p) }.getOrNull() ?: continue
            if (best == null || loc.time > best!!.time) best = loc
        }
        return best
    }

    fun mapsLink(loc: Location): String =
        "https://yandex.ru/maps/?ll=${loc.longitude},${loc.latitude}&z=16&pt=${loc.longitude},${loc.latitude}"

    /** Reply to a LOCATE command with a maps link (or a "no fix" note). */
    fun replyLocation(context: Context, to: String) {
        val loc = lastLocation(context)
        val text = if (loc != null)
            "RuOS «Найти»: ${mapsLink(loc)} (±${loc.accuracy.toInt()} м)"
        else "RuOS «Найти»: местоположение пока недоступно. Попробуйте позже."
        runCatching {
            smsManager(context).sendTextMessage(to, null, text, null, null)
        }
    }

    private fun smsManager(context: Context): SmsManager =
        if (android.os.Build.VERSION.SDK_INT >= 31)
            context.getSystemService(SmsManager::class.java)
        else @Suppress("DEPRECATION") SmsManager.getDefault()
}
