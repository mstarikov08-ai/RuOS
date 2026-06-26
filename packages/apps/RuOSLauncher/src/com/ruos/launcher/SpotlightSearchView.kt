package com.ruos.launcher

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

/**
 * iOS Spotlight-style search overlay. Slides down from the top with a spring over a
 * dark scrim (real RenderEffect blur is layered on in Group 3), a search field, and
 * a live-filtered results list of apps. Tapping a result launches it; tapping the
 * scrim or pressing back dismisses.
 */
class SpotlightSearchView(context: Context) : FrameLayout(context) {

    private val panel = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val field = EditText(context)
    private val resultsContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    private var shown = false
    var onDismiss: (() -> Unit)? = null

    init {
        setBackgroundColor(Color.parseColor("#CC000000"))
        visibility = View.GONE
        isClickable = true
        setOnClickListener { hide() }   // tap scrim dismisses

        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        panel.setPadding(dp(16), dp(48), dp(16), dp(16))

        // Search field — rounded translucent pill.
        field.apply {
            hint = context.getString(R.string.search_hint)
            setHintTextColor(Color.parseColor("#99FFFFFF"))
            setTextColor(Color.WHITE)
            textSize = 17f
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            inputType = InputType.TYPE_CLASS_TEXT
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor("#33FFFFFF"))
            }
            setPadding(dp(14), dp(12), dp(14), dp(12))
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) = updateResults(s?.toString().orEmpty())
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) { launchWebSearch(text.toString()); true }
                else false
            }
        }
        panel.addView(field, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val scroll = ScrollView(context).apply { isVerticalScrollBarEnabled = false }
        scroll.addView(resultsContainer, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        panel.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also {
            it.topMargin = dp(12)
        })

        addView(panel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun isShown(): Boolean = shown

    fun show() {
        if (shown) return
        shown = true
        visibility = View.VISIBLE
        alpha = 0f
        panel.translationY = -40f * resources.displayMetrics.density
        animate().alpha(1f).setDuration(160).start()
        SpringAnimation(panel, SpringAnimation.TRANSLATION_Y, 0f).apply {
            spring.stiffness = SpringForce.STIFFNESS_MEDIUM
            spring.dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
            start()
        }
        field.setText("")
        updateResults("")
        field.requestFocus()
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
    }

    fun hide() {
        if (!shown) return
        shown = false
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(field.windowToken, 0)
        animate().alpha(0f).setDuration(160).withEndAction { visibility = View.GONE }.start()
        onDismiss?.invoke()
    }

    private fun updateResults(query: String) {
        resultsContainer.removeAllViews()
        val q = query.trim().lowercase()
        val apps = RuOSApp.instance.appRepository.getInstalledApps()
        val matches = if (q.isEmpty()) emptyList()
            else apps.filter { it.label.lowercase().contains(q) }.take(8)

        matches.forEach { resultsContainer.addView(appRow(it)) }

        if (q.isNotEmpty()) {
            resultsContainer.addView(webRow(query))
        }
    }

    private fun appRow(info: AppInfo): View {
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(10), dp(8), dp(10))
            isClickable = true
            addView(ImageView(context).apply {
                setImageDrawable(info.icon)
            }, LinearLayout.LayoutParams(dp(40), dp(40)))
            addView(TextView(context).apply {
                text = info.label
                setTextColor(Color.WHITE)
                textSize = 16f
                setPadding(dp(14), 0, 0, 0)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            setOnClickListener {
                val intent = context.packageManager.getLaunchIntentForPackage(info.packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    hide()
                }
            }
        }
    }

    private fun webRow(query: String): View {
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        return TextView(context).apply {
            text = context.getString(R.string.search_web_prefix, query)
            setTextColor(Color.parseColor("#4F9DFF"))
            textSize = 16f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(dp(8), dp(14), dp(8), dp(14))
            isClickable = true
            setOnClickListener { launchWebSearch(query) }
        }
    }

    private fun launchWebSearch(query: String) {
        if (query.isBlank()) return
        val url = "https://yandex.ru/search/?text=" + Uri.encode(query)
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            hide()
        } catch (_: Exception) {}
    }
}
