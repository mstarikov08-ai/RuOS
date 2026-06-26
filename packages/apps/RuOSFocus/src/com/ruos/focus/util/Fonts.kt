package com.ruos.focus.util

import android.graphics.Typeface

/**
 * Golos Text is the RuOS system font (shipped as a prebuilt). Typeface.create falls
 * back to the system sans-serif if the family isn't registered, so this is safe even
 * before the font is installed.
 */
object Fonts {
    val regular: Typeface = Typeface.create("golos", Typeface.NORMAL)
    val medium: Typeface = Typeface.create("golos-medium", Typeface.NORMAL)
        .let { if (it === Typeface.DEFAULT) Typeface.create("sans-serif-medium", Typeface.NORMAL) else it }
    val bold: Typeface = Typeface.create("golos", Typeface.BOLD)
}
