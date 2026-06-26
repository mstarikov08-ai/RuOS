package com.android.systemui.ruos

import android.content.Context
import android.hardware.input.InputManager
import android.os.Looper
import android.util.Log
import android.view.Choreographer
import android.view.InputEvent
import android.view.InputMonitor
import android.view.MotionEvent

/**
 * Acquires a system-level gesture [InputMonitor] so RuOS sees raw pointer events on
 * the navigation regions BEFORE the focused app does — the same mechanism SystemUI's
 * own EdgeBackGestureHandler uses for the back gesture.
 *
 * Why a monitor and not a window: a touch-listener on an overlay window competes with
 * the app for the event stream and adds a hop through the app's input pipeline. A
 * gesture monitor receives a private copy of the events on a dedicated channel with a
 * [BatchedInputEventReceiver] clocked to the [Choreographer], so sampling tracks the
 * panel refresh (120 Hz on panther) with no UI-thread involvement. That is what makes
 * "app follows thumb with zero lag" actually true.
 *
 * Requires the holder to be the system UID / signature-privileged (SystemUI is), and
 * the SELinux domain to allow `monitorGestureInput`. See the policy note in the
 * integration script.
 */
class RuOSGestureInputMonitor(
    private val context: Context,
    private val controller: GestureNavigationController,
    private val displayId: Int = android.view.Display.DEFAULT_DISPLAY
) {
    private var monitor: InputMonitor? = null
    private var receiver: GestureReceiver? = null

    fun start() {
        if (monitor != null) return
        val im = context.getSystemService(InputManager::class.java)
        try {
            // InputManager.monitorGestureInput(String name, int displayId) is @hide.
            val m = InputManager::class.java
                .getMethod("monitorGestureInput", String::class.java, Int::class.javaPrimitiveType)
                .invoke(im, "RuOSGestures", displayId) as InputMonitor
            monitor = m
            receiver = GestureReceiver(m, Looper.myLooper() ?: Looper.getMainLooper())
            Log.i(TAG, "Gesture input monitor acquired on display $displayId")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to acquire gesture input monitor — gestures inactive. " +
                "Check signature/privilege and SELinux monitorGestureInput allowance.", t)
        }
    }

    fun stop() {
        receiver?.dispose()
        receiver = null
        monitor?.dispose()
        monitor = null
    }

    private inner class GestureReceiver(monitor: InputMonitor, looper: Looper) :
        android.view.BatchedInputEventReceiver(
            monitor.inputChannel, looper, Choreographer.getInstance()
        ) {

        private val metrics = context.resources.displayMetrics

        override fun onInputEvent(event: InputEvent) {
            var handled = false
            try {
                if (event is MotionEvent) {
                    handled = controller.onMotionEvent(
                        event, metrics.heightPixels, metrics.widthPixels
                    )
                    // While we're driving a gesture, pilfer the pointers so the app
                    // underneath stops receiving them (prevents double-handling).
                    if (handled) monitor?.pilferPointers()
                }
            } finally {
                finishInputEvent(event, handled)
            }
        }
    }

    companion object { private const val TAG = "RuOSInputMonitor" }
}
