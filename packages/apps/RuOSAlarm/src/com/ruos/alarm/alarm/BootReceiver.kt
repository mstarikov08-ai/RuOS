package com.ruos.alarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-arms every alarm after a reboot or app update. OS alarms are cleared on
 * reboot, so the stored alarms (which DO persist) must be rescheduled.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                // Guard: exact-alarm scheduling can throw SecurityException if the user
                // revoked the alarms-and-reminders op — must not crash at boot.
                runCatching { AlarmScheduler(context).rescheduleAll() }
                runCatching { com.ruos.alarm.util.UpcomingNotifier(context).refresh() }
            }
        }
    }
}
