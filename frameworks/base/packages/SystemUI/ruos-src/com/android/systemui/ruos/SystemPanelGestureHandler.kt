package com.android.systemui.ruos

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.input.InputManager
import android.os.Looper
import android.util.Log
import android.view.Choreographer
import android.view.Gravity
import android.view.InputEvent
import android.view.InputMonitor
import android.view.MotionEvent
import android.view.WindowManager

/**
 * Opens RuOS Control Centre / Notification Centre by swiping down from the top
 * screen corners, and dismisses them by dragging the open panel up. Reuses each
 * panel's existing animateIn()/animateOut() springs.
 *
 *   top-RIGHT corner, swipe down → Control Centre
 *   top-LEFT  corner, swipe down → Notification Centre
 *
 * The panels are hosted as blur-behind overlay windows (added lazily on first show)
 * so the content behind them is really blurred — not a flat scrim.
 *
 * [device] confidence: uses InputManager.monitorGestureInput() (@hide) + cross-
 * window blur; needs the systemui domain + on-device validation.
 */
class SystemPanelGestureHandler(
    private val context: Context,
    private val windowManager: WindowManager
) {
    private val density = context.resources.displayMetrics.density
    private val topZone = 48f * density
    private val cornerFraction = 0.40f      // how wide each corner trigger zone is
    private val openThreshold = 28f * density

    private val controlCenter = ControlCenterView(context)
    private val notificationCenter = NotificationCenterView(context)
    private var ccAdded = false
    private var ncAdded = false

    private enum class Target { NONE, CONTROL, NOTIFICATION }
    private var armed = Target.NONE
    private var shown = Target.NONE
    private var downX = 0f
    private var downY = 0f

    private var monitor: InputMonitor? = null
    private var receiver: Receiver? = null

    fun start() {
        if (monitor != null) return
        val im = context.getSystemService(InputManager::class.java)
        try {
            val m = InputManager::class.java
                .getMethod("monitorGestureInput", String::class.java, Int::class.javaPrimitiveType)
                .invoke(im, "RuOSPanels", android.view.Display.DEFAULT_DISPLAY) as InputMonitor
            monitor = m
            receiver = Receiver(m, Looper.myLooper() ?: Looper.getMainLooper())
            Log.i(TAG, "Panel gesture monitor acquired")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to acquire panel gesture monitor", t)
        }
    }

    fun stop() {
        receiver?.dispose(); receiver = null
        monitor?.dispose(); monitor = null
    }

    private fun handle(event: MotionEvent, w: Int, h: Int): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x; downY = event.y
                armed = if (shown != Target.NONE) Target.NONE
                    else if (event.y <= topZone) {
                        if (event.x > w * (1f - cornerFraction)) Target.CONTROL
                        else if (event.x < w * cornerFraction) Target.NOTIFICATION
                        else Target.NONE
                    } else Target.NONE
                return armed != Target.NONE || shown != Target.NONE
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = event.y - downY
                if (armed != Target.NONE && dy > openThreshold) {
                    open(armed); armed = Target.NONE; return true
                }
                // Drag the open panel up to dismiss.
                if (shown != Target.NONE && (downY - event.y) > openThreshold) {
                    close(); return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { armed = Target.NONE }
        }
        return shown != Target.NONE
    }

    private fun open(t: Target) {
        when (t) {
            Target.CONTROL -> { ensureAdded(controlCenter, Gravity.TOP or Gravity.END) { ccAdded = true }; controlCenter.animateIn() }
            Target.NOTIFICATION -> { ensureAdded(notificationCenter, Gravity.TOP or Gravity.START) { ncAdded = true }; notificationCenter.animateIn() }
            else -> {}
        }
        shown = t
    }

    private fun close() {
        when (shown) {
            Target.CONTROL -> controlCenter.animateOut { }
            Target.NOTIFICATION -> notificationCenter.animateOut { }
            else -> {}
        }
        shown = Target.NONE
    }

    private fun ensureAdded(view: android.view.View, gravity: Int, mark: () -> Unit) {
        if ((view === controlCenter && ccAdded) || (view === notificationCenter && ncAdded)) return
        val w = (context.resources.displayMetrics.widthPixels * 0.92f).toInt()
        val lp = WindowManager.LayoutParams(
            w,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_STATUS_BAR_SUB_PANEL,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            this.gravity = gravity
            applyBlurBehind(this)
        }
        runCatching { windowManager.addView(view, lp); mark() }
    }

    /** Real cross-window blur behind the panel, when the platform supports it. */
    private fun applyBlurBehind(lp: WindowManager.LayoutParams) {
        try {
            val crossBlur = WindowManager::class.java
                .getMethod("isCrossWindowBlurEnabled").invoke(windowManager) as? Boolean ?: false
            if (!crossBlur) return
            // FLAG_BLUR_BEHIND + radius; field/method names are @hide so set reflectively.
            val flagBlur = WindowManager.LayoutParams::class.java
                .getField("FLAG_BLUR_BEHIND").getInt(null)
            lp.flags = lp.flags or flagBlur
            WindowManager.LayoutParams::class.java
                .getMethod("setBlurBehindRadius", Int::class.javaPrimitiveType)
                .invoke(lp, (40 * density).toInt())
        } catch (_: Throwable) { /* blur unsupported — flat glass remains */ }
    }

    private inner class Receiver(monitor: InputMonitor, looper: Looper) :
        android.view.BatchedInputEventReceiver(monitor.inputChannel, looper, Choreographer.getInstance()) {
        private val m = context.resources.displayMetrics
        override fun onInputEvent(event: InputEvent) {
            var handled = false
            try {
                if (event is MotionEvent) {
                    handled = handle(event, m.widthPixels, m.heightPixels)
                    if (handled) monitor?.pilferPointers()
                }
            } finally { finishInputEvent(event, handled) }
        }
    }

    companion object { private const val TAG = "RuOSPanels" }
}
