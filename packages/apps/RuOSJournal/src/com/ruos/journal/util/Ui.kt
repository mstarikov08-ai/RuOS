package com.ruos.journal.util

import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import android.view.View

object Fonts {
    val regular: Typeface = Typeface.create("golos", Typeface.NORMAL)
    val medium: Typeface = Typeface.create("golos-medium", Typeface.NORMAL)
        .let { if (it === Typeface.DEFAULT) Typeface.create("sans-serif-medium", Typeface.NORMAL) else it }
    val bold: Typeface = Typeface.create("golos", Typeface.BOLD)
}

object Haptics {
    fun tick(v: View) = v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    fun select(v: View) = v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    fun confirm(v: View) = v.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
}
