package com.ruos.screenrecord

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import android.view.View

/** iOS-style red recording pill: pulsing dot + elapsed mm:ss. Tap to stop. */
@SuppressLint("ViewConstructor")
class RedIndicatorView(context: Context, private val onStop: () -> Unit) : View(context) {

    private val d = resources.displayMetrics.density
    private fun dp(v: Float) = v * d

    private var elapsedMs = 0L
    private var pulse = 1f
    private var growing = false

    private val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE6FF3B30.toInt() }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = dp(13f); typeface = Typeface.create("golos-medium", Typeface.NORMAL)
        textAlign = Paint.Align.LEFT
    }

    init {
        isClickable = true
        setOnClickListener { performHapticFeedback(HapticFeedbackConstants.CONFIRM); onStop() }
    }

    fun setElapsed(ms: Long) { elapsedMs = ms; invalidate() }

    override fun onMeasure(w: Int, h: Int) =
        setMeasuredDimension(dp(96f).toInt(), dp(30f).toInt())

    override fun onDraw(canvas: Canvas) {
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()),
            height / 2f, height / 2f, pill)
        // pulsing dot
        pulse += if (growing) 0.04f else -0.04f
        if (pulse > 1f) { pulse = 1f; growing = false }
        if (pulse < 0.55f) { pulse = 0.55f; growing = true }
        dot.alpha = (255 * pulse).toInt()
        canvas.drawCircle(dp(16f), height / 2f, dp(5f), dot)
        canvas.drawText(fmt(elapsedMs), dp(28f), height / 2f + dp(5f), text)
        postInvalidateDelayed(40)
    }

    private fun fmt(ms: Long): String {
        val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60)
    }
}
