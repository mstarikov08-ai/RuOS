package com.ruos.launcher.widget

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.ruos.launcher.R

/**
 * Weather widget for the first home screen page.
 * Displays current temperature, condition, city name.
 * Data is fetched from Yandex.Weather API (requires API key in build config).
 *
 * Visual: frosted glass card, identical to iOS weather widget
 */
class WeatherWidgetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val density = resources.displayMetrics.density

    private val cityLabel = TextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = 14f
        setShadowLayer(4f, 0f, 1f, Color.argb(80, 0, 0, 0))
    }
    private val tempLabel = TextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = 52f
        setTypeface(android.graphics.Typeface.create("sans-serif-thin", android.graphics.Typeface.NORMAL))
        setShadowLayer(4f, 0f, 1f, Color.argb(60, 0, 0, 0))
    }
    private val conditionLabel = TextView(context).apply {
        setTextColor(Color.argb(220, 255, 255, 255))
        textSize = 13f
    }
    private val hiLoLabel = TextView(context).apply {
        setTextColor(Color.argb(200, 255, 255, 255))
        textSize = 13f
    }

    init {
        background = GradientDrawable().apply {
            cornerRadius = 20f * density
            setColor(Color.argb(120, 44, 112, 208))  // weather blue tint
        }
        clipToOutline = true
        outlineProvider = android.view.ViewOutlineProvider.BACKGROUND

        val p = (14 * density).toInt()
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(p, p, p, p)
        }
        col.addView(cityLabel)
        col.addView(tempLabel)
        col.addView(conditionLabel)
        col.addView(hiLoLabel)
        addView(col, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

        // Placeholder data — replace with Yandex.Weather API call
        cityLabel.text = "Москва"
        tempLabel.text = "–3°"
        conditionLabel.text = "Переменная облачность"
        hiLoLabel.text = "В: –1°  Н: –7°"
    }
}
