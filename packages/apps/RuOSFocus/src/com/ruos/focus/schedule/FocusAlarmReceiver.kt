package com.ruos.focus.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Wakes on a scheduled boundary (or boot / time change) and re-evaluates which focus
 * should be active, then re-arms the next alarm. Also re-emits the current focus state
 * so RuOSNotify picks it up after a reboot.
 */
class FocusAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                FocusController.resend(context)
                FocusScheduler.evaluateAndReschedule(context)
            }
            else -> FocusScheduler.evaluateAndReschedule(context)
        }
    }
}
