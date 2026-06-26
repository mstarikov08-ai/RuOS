package com.ruos.keyboard

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

class SuggestionBarView(context: Context) : LinearLayout(context) {

    private val cells = Array(3) { makeSuggestionCell(it) }
    private var onSuggestionPicked: ((String) -> Unit)? = null

    init {
        orientation = HORIZONTAL
        setBackgroundColor(Color.parseColor("#1B1B1B"))

        // Thin separator line on bottom
        val sep = View(context).apply {
            setBackgroundColor(Color.parseColor("#3A3A3C"))
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(0.5f))
        }

        for ((i, cell) in cells.withIndex()) {
            if (i > 0) {
                addView(View(context).apply {
                    setBackgroundColor(Color.parseColor("#3A3A3C"))
                    layoutParams = LayoutParams(dp(0.5f), LayoutParams.MATCH_PARENT).also {
                        it.topMargin = dp(8f)
                        it.bottomMargin = dp(8f)
                    }
                })
            }
            addView(cell)
        }
    }

    private fun makeSuggestionCell(index: Int): TextView = TextView(context).apply {
        gravity = Gravity.CENTER
        textSize = 16f
        setTextColor(Color.WHITE)
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
        isClickable = true
        isFocusable = true
        setOnClickListener {
            val word = text.toString().trim('"')
            if (word.isNotEmpty()) onSuggestionPicked?.invoke(word)
        }
    }

    fun setSuggestions(left: String, center: String, right: String) {
        cells[0].apply {
            text = if (left.isNotEmpty()) "\"$left\"" else ""
            setTextColor(Color.parseColor("#AEAEB2"))
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        cells[1].apply {
            text = center
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        cells[2].apply {
            text = if (right.isNotEmpty()) "\"$right\"" else ""
            setTextColor(Color.parseColor("#AEAEB2"))
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
    }

    fun setOnSuggestionPicked(callback: (String) -> Unit) {
        onSuggestionPicked = callback
    }

    private fun dp(v: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()
}
