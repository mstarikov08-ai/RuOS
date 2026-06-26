package com.ruos.launcher.recents

import android.app.Service
import android.content.Intent
import android.graphics.Rect
import android.os.IBinder

/**
 * Bound service exposing [IRuOSHomeTarget] to SystemUI's gesture engine. All work
 * is forwarded through [HomeTargetBridge] to the live HomeView on the main thread.
 */
class RuOSHomeTargetService : Service() {

    private val binder = object : IRuOSHomeTarget.Stub() {
        override fun onHomeProgress(progress: Float) {
            HomeTargetBridge.postProgress(progress)
        }

        override fun onHomeSettled(toHome: Boolean) {
            HomeTargetBridge.postSettled(toHome)
        }

        override fun getLandingBounds(packageName: String?): Rect? {
            if (packageName == null) return null
            return HomeTargetBridge.landingBoundsBlocking(packageName)
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder
}
