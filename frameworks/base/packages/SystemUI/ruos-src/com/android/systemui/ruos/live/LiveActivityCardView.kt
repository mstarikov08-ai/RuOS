package com.android.systemui.ruos.live

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import kotlin.math.abs

/**
 * The expanded Live Activity card — shown on the lock screen below notifications and
 * when a Dynamic Island activity is tapped. Frosted dark card: accent dot + title,
 * subtitle, optional progress, body. Tap opens the app; swipe sideways dismisses.
 */
class LiveActivityCardView(context: Context, private val activity: LiveActivity) : FrameLayout(context) {

    var onDismiss: (() -> Unit)? = null
    var onOpen: (() -> Unit)? = null

    private val d = resources.displayMetrics.density
    private fun dp(v: Float) = v * d
    private var downX = 0f
    private var dragging = false

    init {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(22f); setColor(Color.parseColor("#CC1C1C1E"))
                setStroke(dp(0.5f).toInt(), Color.parseColor("#33FFFFFF"))
            }
            setPadding(dp(16f).toInt(), dp(14f).toInt(), dp(16f).toInt(), dp(14f).toInt())
            elevation = dp(8f)
        }

        val head = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(View(context).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(activity.color) }
        }, LinearLayout.LayoutParams(dp(10f).toInt(), dp(10f).toInt()).also { it.marginEnd = dp(8f).toInt() })
        head.addView(TextView(context).apply {
            text = activity.title.ifBlank { activity.type }
            setTextColor(Color.WHITE); textSize = 16f
            typeface = Typeface.create("golos-medium", Typeface.NORMAL)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (activity.compactTrailing.isNotBlank()) head.addView(TextView(context).apply {
            text = activity.compactTrailing; setTextColor(activity.color); textSize = 16f
            typeface = Typeface.create("golos-medium", Typeface.NORMAL)
        })
        card.addView(head)

        if (activity.subtitle.isNotBlank()) card.addView(TextView(context).apply {
            text = activity.subtitle; setTextColor(Color.parseColor("#C8D0E0")); textSize = 14f
            setPadding(0, dp(4f).toInt(), 0, 0)
        })

        if (activity.progress in 0f..1f && activity.progress > 0f) {
            card.addView(ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100; progress = (activity.progress * 100).toInt()
                progressTintList = android.content.res.ColorStateList.valueOf(activity.color)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also {
                it.topMargin = dp(10f).toInt()
            })
        }

        if (activity.body.isNotBlank()) card.addView(TextView(context).apply {
            text = activity.body; setTextColor(Color.parseColor("#AEAEB2")); textSize = 13f
            setPadding(0, dp(8f).toInt(), 0, 0)
        })

        addView(card, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = event.x; dragging = false }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX
                if (abs(dx) > dp(8f)) { dragging = true; translationX = dx }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!dragging) { onOpen?.invoke(); runCatching { activity.contentIntent?.send() } }
                else if (abs(translationX) > width * 0.35f) {
                    animate().translationX(if (translationX < 0) -width.toFloat() else width.toFloat())
                        .alpha(0f).setDuration(160).withEndAction { onDismiss?.invoke() }.start()
                } else animate().translationX(0f).setDuration(160).start()
            }
        }
        return true
    }
}
