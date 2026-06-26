package com.ruos.standby.util

import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import android.view.View

object Fonts {
    val thin: Typeface = Typeface.create("golos", Typeface.NORMAL)
        .let { Typeface.create("sans-serif-thin", Typeface.NORMAL) }
    val light: Typeface = Typeface.create("golos", Typeface.NORMAL)
        .let { Typeface.create("sans-serif-light", Typeface.NORMAL) }
    val medium: Typeface = Typeface.create("golos-medium", Typeface.NORMAL)
}

object Haptics {
    fun tick(v: View) = v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    fun select(v: View) = v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
}
