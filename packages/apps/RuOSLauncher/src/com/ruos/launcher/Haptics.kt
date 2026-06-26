package com.ruos.launcher

import android.view.HapticFeedbackConstants
import android.view.View

/**
 * One consistent haptic vocabulary across the launcher so every interaction has the
 * same physical "voice" as iOS. Uses HapticFeedbackConstants (routed through the
 * system's haptic config) rather than raw VibrationEffect so it honours user
 * settings and per-device actuator tuning.
 */
object Haptics {

    /** Light tick — page snap, search open, suggestion-style taps. */
    fun light(v: View) {
        v.performHapticFeedback(
            HapticFeedbackConstants.CLOCK_TICK,
            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
        )
    }

    /** Medium — pickup / enter edit mode. */
    fun medium(v: View) {
        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    /** Confirm — folder open, drop committed. */
    fun confirm(v: View) {
        v.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    }
}
