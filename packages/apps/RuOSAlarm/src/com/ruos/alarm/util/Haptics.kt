package com.ruos.alarm.util

import android.view.HapticFeedbackConstants
import android.view.View

/** Consistent haptic vocabulary for the alarm UI (matches the rest of RuOS). */
object Haptics {
    /** Tiny tick — wheel-picker detents, toggles. */
    fun tick(v: View) = v.performHapticFeedback(
        HapticFeedbackConstants.CLOCK_TICK,
        HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
    )
    /** Selection — day chips, sound rows. */
    fun select(v: View) = v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    /** Confirm — save, snooze, stop. */
    fun confirm(v: View) = v.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
}
