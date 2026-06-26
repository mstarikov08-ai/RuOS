package com.ruos.keyboard

import android.content.Context
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.LinearLayout

// Root view returned by InputMethodService.onCreateInputView().
// Contains the suggestion bar on top and the canvas keyboard below.
class RuOSKeyboardLayout(context: Context) : LinearLayout(context) {

    val suggestionBar = SuggestionBarView(context)
    val keyboardView  = MainKeyboardView(context)

    init {
        orientation = VERTICAL

        // Suggestion bar: fixed 44dp height
        suggestionBar.layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT, dp(44f)
        )
        addView(suggestionBar)

        // Keyboard fills remaining height (wrap_content drives its own onMeasure)
        keyboardView.layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT
        )
        addView(keyboardView)
    }

    private fun dp(v: Float) =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, v, context.resources.displayMetrics
        ).toInt()
}
