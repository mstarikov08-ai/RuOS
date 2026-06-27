package com.ruos.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Prewarm Russian apps 15 seconds after boot
            // (system has settled by then)
            val appCtx = context.applicationContext
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                runCatching { PrewarmManager(appCtx).prewarmRussianApps() }
            }, 15_000)
        }
    }
}
