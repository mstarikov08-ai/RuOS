package com.ruos.assist.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView

/** One AssistiveTouch action: a label, a hand-drawn glyph, and the global-action code. */
data class AssistAction(val label: String, val glyph: AssistGlyph, val code: Int)

enum class AssistGlyph { HOME, BACK, RECENTS, NOTIFICATIONS, CONTROL, SCREENSHOT, LOCK }

/**
 * The expanded AssistiveTouch panel: a frosted rounded card with a grid of actions. Tap an
 * action to run it; tap outside to dismiss. Springs in. Glyphs are canvas-drawn (no emoji).
 */
@SuppressLint("ViewConstructor")
class AssistiveMenuView(
    context: Context,
    actions: List<AssistAction>,
    private val onAction: (AssistAction) -> Unit,
    private val onDismiss: () -> Unit
) : LinearLayout(context) {

    private val d = resources.displayMetrics.density
    private fun dp(v: Float) = (v * d).toInt()

    init {
        orientation = VERTICAL; gravity = Gravity.CENTER
        setBackgroundColor(0x66000000)
        isClickable = true
        setOnClickListener { onDismiss() }

        val card = GridLayout(context).apply {
            columnCount = 3
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(28f).toFloat(); setColor(0xF21C1C1E.toInt())
            }
            setPadding(dp(10f), dp(10f), dp(10f), dp(10f))
            isClickable = true   // swallow taps so they don't dismiss
        }
        actions.forEach { action -> card.addView(cell(action)) }
        addView(card)
        alpha = 0f; scaleX = 0.9f; scaleY = 0.9f
        animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(160).start()
    }

    private fun cell(action: AssistAction): View = LinearLayout(context).apply {
        orientation = VERTICAL; gravity = Gravity.CENTER; setPadding(dp(8f), dp(12f), dp(8f), dp(12f))
        isClickable = true
        setOnClickListener { onAction(action) }
        addView(GlyphView(context, action.glyph), LinearLayout.LayoutParams(dp(34f), dp(34f)))
        addView(TextView(context).apply {
            text = action.label; setTextColor(Color.WHITE); textSize = 12f; typeface = Fonts.regular
            gravity = Gravity.CENTER; setPadding(0, dp(6f), 0, 0)
        })
        layoutParams = GridLayout.LayoutParams().apply { width = dp(86f); setMargins(dp(2f), dp(2f), dp(2f), dp(2f)) }
    }

    /** Draws each glyph by hand. */
    private class GlyphView(context: Context, val glyph: AssistGlyph) : View(context) {
        private val d = resources.displayMetrics.density
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 2f * d; color = Color.WHITE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        override fun onDraw(c: Canvas) {
            val w = width.toFloat(); val h = height.toFloat(); val s = w * 0.3f
            val cx = w / 2; val cy = h / 2
            when (glyph) {
                AssistGlyph.HOME -> {
                    val p = Path().apply { moveTo(cx - s, cy); lineTo(cx, cy - s); lineTo(cx + s, cy); close() }
                    c.drawPath(p, stroke)
                    c.drawRect(cx - s * 0.7f, cy, cx + s * 0.7f, cy + s, stroke)
                }
                AssistGlyph.BACK -> {
                    c.drawCircle(cx, cy, s, stroke)
                    c.drawLine(cx + s * 0.3f, cy - s * 0.4f, cx - s * 0.3f, cy, stroke)
                    c.drawLine(cx - s * 0.3f, cy, cx + s * 0.3f, cy + s * 0.4f, stroke)
                }
                AssistGlyph.RECENTS -> {
                    c.drawRoundRect(RectF(cx - s, cy - s * 0.6f, cx + s * 0.4f, cy + s), 4f * d, 4f * d, stroke)
                    c.drawRoundRect(RectF(cx - s * 0.4f, cy - s, cx + s, cy + s * 0.6f), 4f * d, 4f * d, stroke)
                }
                AssistGlyph.NOTIFICATIONS -> {
                    val p = Path().apply {
                        moveTo(cx - s * 0.8f, cy + s * 0.4f)
                        quadTo(cx - s * 0.8f, cy - s * 0.8f, cx, cy - s)
                        quadTo(cx + s * 0.8f, cy - s * 0.8f, cx + s * 0.8f, cy + s * 0.4f); close()
                    }
                    c.drawPath(p, stroke); c.drawCircle(cx, cy + s * 0.8f, s * 0.2f, fill)
                }
                AssistGlyph.CONTROL -> {
                    c.drawLine(cx - s, cy - s * 0.5f, cx + s, cy - s * 0.5f, stroke)
                    c.drawCircle(cx + s * 0.3f, cy - s * 0.5f, s * 0.28f, fill)
                    c.drawLine(cx - s, cy + s * 0.5f, cx + s, cy + s * 0.5f, stroke)
                    c.drawCircle(cx - s * 0.3f, cy + s * 0.5f, s * 0.28f, fill)
                }
                AssistGlyph.SCREENSHOT -> {
                    // corner brackets
                    val o = s
                    c.drawLine(cx - o, cy - o * 0.4f, cx - o, cy - o, stroke); c.drawLine(cx - o, cy - o, cx - o * 0.4f, cy - o, stroke)
                    c.drawLine(cx + o, cy - o * 0.4f, cx + o, cy - o, stroke); c.drawLine(cx + o, cy - o, cx + o * 0.4f, cy - o, stroke)
                    c.drawLine(cx - o, cy + o * 0.4f, cx - o, cy + o, stroke); c.drawLine(cx - o, cy + o, cx - o * 0.4f, cy + o, stroke)
                    c.drawLine(cx + o, cy + o * 0.4f, cx + o, cy + o, stroke); c.drawLine(cx + o, cy + o, cx + o * 0.4f, cy + o, stroke)
                }
                AssistGlyph.LOCK -> {
                    c.drawRoundRect(RectF(cx - s * 0.7f, cy - s * 0.1f, cx + s * 0.7f, cy + s), 3f * d, 3f * d, stroke)
                    val arc = Path().apply { addArc(RectF(cx - s * 0.45f, cy - s * 0.8f, cx + s * 0.45f, cy + s * 0.1f), 180f, 180f) }
                    c.drawPath(arc, stroke)
                }
            }
        }
    }
}
