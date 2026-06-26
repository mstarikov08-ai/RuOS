package com.ruos.keychain.ui

import android.graphics.Typeface

object Fonts {
    val regular: Typeface = Typeface.create("golos", Typeface.NORMAL)
    val medium: Typeface = Typeface.create("golos-medium", Typeface.NORMAL)
        .let { if (it === Typeface.DEFAULT) Typeface.create("sans-serif-medium", Typeface.NORMAL) else it }
    val bold: Typeface = Typeface.create("golos", Typeface.BOLD)
    val mono: Typeface = Typeface.create("monospace", Typeface.NORMAL)
}
