package com.ruos.standby.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ruos.standby.model.StandbySettings

/**
 * Starts/stops the StandBy manager when the charger is connected/disconnected.
 * ACTION_POWER_CONNECTED/DISCONNECTED are exempt from manifest-broadcast limits.
 */
class PowerConnectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_POWER_CONNECTED -> {
                if (!StandbySettings(context).enabled) return
                context.startForegroundService(
                    Intent(context, StandbyManagerService::class.java).setAction(StandbyManagerService.ACTION_START))
            }
            Intent.ACTION_POWER_DISCONNECTED -> {
                context.startService(
                    Intent(context, StandbyManagerService::class.java).setAction(StandbyManagerService.ACTION_STOP))
            }
        }
    }
}
