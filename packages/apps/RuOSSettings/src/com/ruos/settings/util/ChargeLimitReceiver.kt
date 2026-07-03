package com.ruos.settings.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-applies the user's charge-limit choice after every boot: the kernel's charge-stop sysfs node
 * resets to its default (100) on reboot, so without this the 80% limit set in Battery settings
 * would silently stop working. No-op when the user never enabled the limit.
 */
class ChargeLimitReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (ChargeLimit.saved(context)) ChargeLimit.apply(true)
    }
}
