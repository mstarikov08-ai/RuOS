package com.android.systemui.ruos

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.view.WindowManager
import dagger.Module

/**
 * RuOS SystemUI module — injected by dagger into SystemUI's component graph.
 *
 * Responsibilities:
 *  1. Inflate DynamicIslandView into the status bar window
 *  2. Add ControlCenterView / NotificationCenterView as overlay windows
 *  3. Register GestureNavigationController with InputManagerService
 *  4. Listen to MediaSession for Dynamic Island music updates
 *  5. Register LockScreenView for keyguard replacement
 *
 * Integration point:
 *   frameworks/base/packages/SystemUI/src/com/android/systemui/SystemUIFactory.java
 *   → override createSystemUI() to return RuOSSystemUIFactory
 */
@Module
class RuOSSystemUIModule {

    companion object {
        private val handler = Handler(Looper.getMainLooper())
    }

    fun initDynamicIsland(context: Context, statusBarWindow: ViewGroup) {
        val island = DynamicIslandView(context)
        statusBarWindow.addView(island, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        subscribeToMediaSession(context, island)
    }

    /**
     * Stand up the gesture navigation engine: orchestrator + raw input monitor,
     * and bind the launcher's home-target so the home swipe can fade the launcher
     * icon grid + pulse/fly into the landing icon.
     *
     * Call once from SystemUI startup (e.g. CoreStartable.start() of a RuOS
     * startable, or RuOSStatusBar init).
     */
    fun initGestureNavigation(context: Context): RuOSGestureInputMonitor {
        val provider = SharedRecentsLeashProvider(context)
        val controller = GestureNavigationController(context, provider)

        // ── Bind the launcher's IRuOSHomeTarget ────────────────────────────────
        val targetHolder = arrayOfNulls<com.ruos.launcher.recents.IRuOSHomeTarget>(1)
        val conn = object : android.content.ServiceConnection {
            override fun onServiceConnected(name: android.content.ComponentName?, service: android.os.IBinder?) {
                targetHolder[0] = com.ruos.launcher.recents.IRuOSHomeTarget.Stub.asInterface(service)
            }
            override fun onServiceDisconnected(name: android.content.ComponentName?) {
                targetHolder[0] = null
            }
        }
        runCatching {
            val intent = android.content.Intent().setClassName(
                "com.ruos.launcher",
                "com.ruos.launcher.recents.RuOSHomeTargetService"
            )
            context.bindService(intent, conn, Context.BIND_AUTO_CREATE)
        }

        // Forward home-swipe progress + settle to the launcher (oneway, cheap).
        controller.homeIconProgressSink = { p ->
            runCatching { targetHolder[0]?.onHomeProgress(p) }
        }
        controller.homeSettledSink = { toHome ->
            runCatching { targetHolder[0]?.onHomeSettled(toHome) }
        }
        // Resolve the landing icon's centre: taskId → package (from the recents
        // handover) → launcher icon bounds (sync AIDL).
        controller.landingSlotProvider = { taskId ->
            val pkg = provider.packageForTask(taskId)
            val bounds = pkg?.let { p -> runCatching { targetHolder[0]?.getLandingBounds(p) }.getOrNull() }
            bounds?.let { Pair(it.exactCenterX(), it.exactCenterY()) }
        }

        val monitor = RuOSGestureInputMonitor(context, controller)
        monitor.start()
        return monitor
    }

    fun initControlCenter(context: Context, windowManager: WindowManager) {
        val cc = ControlCenterView(context)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_STATUS_BAR_SUB_PANEL,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.END
        }
        // ControlCenter is added and shown/hidden by the swipe gesture handler
    }

    fun initNotificationCenter(context: Context, windowManager: WindowManager) {
        val nc = NotificationCenterView(context)
        // Similar to ControlCenter — added as an overlay window
    }

    private fun subscribeToMediaSession(context: Context, island: DynamicIslandView) {
        val msm = context.getSystemService(MediaSessionManager::class.java)
        msm.addOnActiveSessionsChangedListener({ controllers ->
            val active = controllers?.firstOrNull()
            active?.registerCallback(object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    handler.post { island.updateMusicMetadata(metadata) }
                }
                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    handler.post { island.updatePlaybackState(state) }
                }
            }, handler)
        }, null)
    }
}
