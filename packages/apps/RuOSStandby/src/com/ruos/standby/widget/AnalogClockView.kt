package com.ruos.standby.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.view.View
import java.util.Calendar

/** A clean canvas analogue clock for the StandBy clock page. */
class AnalogClockView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density
    private val face = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.parseColor("#33FFFFFF"); strokeWidth = 2f * density
    }
    private val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#66FFFFFF") }
    private val hand = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; strokeCap = Paint.Cap.ROUND
    }
    private val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#D94F3D") }

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable { override fun run() { invalidate(); handler.postDelayed(this, 1000) } }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); handler.post(ticker) }
    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); handler.removeCallbacks(ticker) }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        val r = minOf(width, height) / 2f - 6f * density
        canvas.drawCircle(cx, cy, r, face)

        for (i in 0 until 12) {
            val a = Math.toRadians(i * 30.0)
            val outer = r - 4f * density
            val inner = r - (if (i % 3 == 0) 16f else 10f) * density
            tick.strokeWidth = (if (i % 3 == 0) 3f else 1.5f) * density
            canvas.drawLine(
                cx + (outer * Math.sin(a)).toFloat(), cy - (outer * Math.cos(a)).toFloat(),
                cx + (inner * Math.sin(a)).toFloat(), cy - (inner * Math.cos(a)).toFloat(), tick)
        }

        val c = Calendar.getInstance()
        val hr = c.get(Calendar.HOUR) + c.get(Calendar.MINUTE) / 60f
        val min = c.get(Calendar.MINUTE) + c.get(Calendar.SECOND) / 60f
        val sec = c.get(Calendar.SECOND).toFloat()

        drawHand(canvas, cx, cy, hr / 12f * 360f, r * 0.5f, 5f * density, hand)
        drawHand(canvas, cx, cy, min / 60f * 360f, r * 0.72f, 3.5f * density, hand)
        drawHand(canvas, cx, cy, sec / 60f * 360f, r * 0.82f, 1.6f * density, accent)
        canvas.drawCircle(cx, cy, 4f * density, accent)
    }

    private fun drawHand(canvas: Canvas, cx: Float, cy: Float, deg: Float, len: Float, w: Float, p: Paint) {
        val a = Math.toRadians(deg.toDouble())
        p.strokeWidth = w
        canvas.drawLine(cx, cy, cx + (len * Math.sin(a)).toFloat(), cy - (len * Math.cos(a)).toFloat(), p)
    }
}
