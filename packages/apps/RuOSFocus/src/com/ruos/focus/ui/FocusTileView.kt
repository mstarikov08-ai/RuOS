package com.ruos.focus.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.ruos.focus.model.FocusMode
import com.ruos.focus.util.Fonts

/**
 * An iOS-style Focus tile: rounded card with the mode's glyph in a tinted circle, its
 * name below, and a soft fill when active. Press gives the standard RuOS spring scale.
 */
class FocusTileView(context: Context, var mode: FocusMode) : View(context) {

    var activeNow = false
        set(value) { field = value; invalidate() }

    var onTap: (() -> Unit)? = null

    private val d = resources.displayMetrics.density
    private fun dp(v: Float) = v * d

    private val card = Paint(Paint.ANTI_ALIAS_FLAG)
    private val circle = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER; typeface = Fonts.medium; textSize = dp(15f)
    }
    private val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF8E8E93.toInt(); textAlign = Paint.Align.CENTER; typeface = Fonts.regular; textSize = dp(12f)
    }

    private val scale = SpringAnimation(this, View.SCALE_X).apply {
        spring = SpringForce(1f).setStiffness(400f).setDampingRatio(0.75f)
    }
    private val scaleY = SpringAnimation(this, View.SCALE_Y).apply {
        spring = SpringForce(1f).setStiffness(400f).setDampingRatio(0.75f)
    }

    init {
        isClickable = true
    }

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(MeasureSpec.getSize(w), dp(112f).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val pad = dp(4f)
        val rect = RectF(pad, pad, width - pad, height - pad)
        card.color = if (activeNow) blend(mode.color, 0.22f) else 0xFF1C1C1E.toInt()
        canvas.drawRoundRect(rect, dp(20f), dp(20f), card)

        if (activeNow) {
            card.style = Paint.Style.STROKE; card.strokeWidth = dp(1.5f); card.color = mode.color
            canvas.drawRoundRect(rect, dp(20f), dp(20f), card)
            card.style = Paint.Style.FILL
        }

        // glyph circle
        val cx = width / 2f
        val cy = dp(40f)
        val rr = dp(24f)
        circle.color = mode.color
        canvas.drawCircle(cx, cy, rr, circle)
        FocusGlyph.draw(canvas, mode.icon, cx, cy, rr * 1.1f, glyph)

        canvas.drawText(mode.name, cx, dp(82f), label)
        canvas.drawText(if (activeNow) "Вкл." else "Выкл.", cx, dp(100f), sub)
    }

    override fun onTouchEvent(e: android.view.MotionEvent): Boolean {
        when (e.action) {
            android.view.MotionEvent.ACTION_DOWN -> springTo(0.94f)
            android.view.MotionEvent.ACTION_UP -> {
                springTo(1f)
                if (isInside(e)) { performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY); onTap?.invoke() }
            }
            android.view.MotionEvent.ACTION_CANCEL -> springTo(1f)
        }
        return true
    }

    private fun isInside(e: android.view.MotionEvent) =
        e.x >= 0 && e.y >= 0 && e.x <= width && e.y <= height

    private fun springTo(v: Float) { scale.animateToFinalPosition(v); scaleY.animateToFinalPosition(v) }

    private fun blend(c: Int, a: Float): Int =
        Color.argb((a * 255).toInt(), Color.red(c), Color.green(c), Color.blue(c))
}
