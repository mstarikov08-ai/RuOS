package com.ruos.auth.util

import android.view.HapticFeedbackConstants
import android.view.View

/** Auth haptic vocabulary — every interaction has feedback, iOS-style. */
object Haptics {
    fun tap(v: View) = v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    fun success(v: View) = v.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    fun reject(v: View) = v.performHapticFeedback(HapticFeedbackConstants.REJECT)
    fun tick(v: View) = v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
}
