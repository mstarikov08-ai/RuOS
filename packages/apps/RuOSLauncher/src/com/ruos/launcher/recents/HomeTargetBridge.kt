package com.ruos.launcher.recents

import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import java.lang.ref.WeakReference

/**
 * Process-local link between the bound [RuOSHomeTargetService] (which receives
 * cross-process calls from SystemUI on a binder thread) and the live [HomeView]
 * (which can only be touched on the main thread).
 *
 * The launcher activity registers/unregisters its HomeView here in onResume/onPause.
 */
object HomeTargetBridge {

    /** Implemented by HomeView. */
    interface Host {
        /** progress 0 = icons hidden (app fullscreen) → 1 = home fully revealed. */
        fun setRevealProgress(progress: Float)
        /** Gesture done; toHome true = committed to home. */
        fun onHomeSettled(toHome: Boolean)
        /** Pulse the landing app's icon as it "lands". */
        fun pulseIcon(packageName: String)
        /** On-screen bounds of a package's icon, or null. */
        fun landingBounds(packageName: String): Rect?
    }

    private val main = Handler(Looper.getMainLooper())
    private var hostRef: WeakReference<Host>? = null

    fun register(host: Host) { hostRef = WeakReference(host) }
    fun unregister(host: Host) {
        if (hostRef?.get() === host) hostRef = null
    }

    private val host: Host? get() = hostRef?.get()

    fun postProgress(progress: Float) = main.post { host?.setRevealProgress(progress) }

    fun postSettled(toHome: Boolean) = main.post {
        host?.onHomeSettled(toHome)
    }

    fun postPulse(pkg: String) = main.post { host?.pulseIcon(pkg) }

    /**
     * Synchronous read for getLandingBounds. Binder calls may arrive off the main
     * thread, so block briefly for the main thread to read the view geometry.
     */
    fun landingBoundsBlocking(pkg: String): Rect? {
        val h = host ?: return null
        if (Looper.myLooper() == Looper.getMainLooper()) return h.landingBounds(pkg)
        var result: Rect? = null
        val latch = java.util.concurrent.CountDownLatch(1)
        main.post {
            result = h.landingBounds(pkg)
            latch.countDown()
        }
        latch.await(50, java.util.concurrent.TimeUnit.MILLISECONDS)
        return result
    }
}
