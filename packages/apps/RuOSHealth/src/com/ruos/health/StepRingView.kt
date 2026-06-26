package com.ruos.health

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View

class StepRingView(context: Context) : View(context) {

    private val RED = Color.parseColor("#D94F3D")
    private val SURFACE = Color.parseColor("#1C1C1E")
    private val TEXT_PRIMARY = Color.WHITE
    private val TEXT_SECONDARY = Color.parseColor("#8E8E93")

    var steps: Int = 0
        set(value) {
            field = value
            invalidate()
        }

    var goal: Int = 10000
        set(value) {
            field = value
            invalidate()
        }

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(12).toFloat()
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#2C2C2E")
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(12).toFloat()
        strokeCap = Paint.Cap.ROUND
        color = RED
    }

    private val stepNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TEXT_PRIMARY
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val stepLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TEXT_SECONDARY
        textAlign = Paint.Align.CENTER
    }

    private val goalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TEXT_SECONDARY
        textAlign = Paint.Align.CENTER
    }

    private val oval = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val padding = dp(18).toFloat()
        oval.set(padding, padding, w - padding, h - padding)

        // Track ring
        canvas.drawOval(oval, trackPaint)

        // Progress arc
        val fraction = if (goal > 0) (steps.toFloat() / goal).coerceIn(0f, 1f) else 0f
        val sweepAngle = 360f * fraction
        canvas.drawArc(oval, -90f, sweepAngle, false, progressPaint)

        // Center: step count
        val cx = w / 2f
        val cy = h / 2f

        stepNumberPaint.textSize = dp(38).toFloat()
        canvas.drawText(steps.toString(), cx, cy - dp(8).toFloat(), stepNumberPaint)

        stepLabelPaint.textSize = dp(12).toFloat()
        canvas.drawText("ШАГ", cx, cy + dp(12).toFloat(), stepLabelPaint)

        goalPaint.textSize = dp(11).toFloat()
        canvas.drawText("из ${goal.formatWithSpaces()}", cx, cy + dp(26).toFloat(), goalPaint)
    }

    private fun Int.formatWithSpaces(): String {
        return String.format("%,d", this).replace(',', ' ')
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(),
            resources.displayMetrics).toInt()
}
