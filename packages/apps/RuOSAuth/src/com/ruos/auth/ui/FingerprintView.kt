package com.ruos.auth.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View

/**
 * A fingerprint glyph (concentric ridge arcs) that fills with the accent colour from
 * the bottom up as enrolment "progresses" — the iOS Touch ID fill. Cosmetic; the real
 * print is captured by the system enrolment that follows.
 */
class FingerprintView(context: Context) : View(context) {

    var progress = 0f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }

    private val density = resources.displayMetrics.density
    private val base = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = Color.parseColor("#3A3A3E")
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = Color.parseColor("#FF3B30")
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        base.strokeWidth = 3f * density; fill.strokeWidth = 3f * density
        // Draw the ridge arcs twice: full in base colour, then clipped fill bottom-up.
        drawRidges(canvas, cx, cy, base)
        canvas.save()
        canvas.clipRect(0f, height * (1f - progress), width.toFloat(), height.toFloat())
        drawRidges(canvas, cx, cy, fill)
        canvas.restore()
    }

    private fun drawRidges(canvas: Canvas, cx: Float, cy: Float, p: Paint) {
        val maxR = minOf(width, height) * 0.34f
        for (i in 0 until 6) {
            val r = maxR * (0.4f + i * 0.12f)
            val sweep = 180f + i * 12f
            val start = 180f - (sweep - 180f) / 2f
            canvas.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), start, sweep, false, p)
        }
        // Central ridges (small vertical loops).
        val path = Path()
        path.moveTo(cx, cy - maxR * 0.25f)
        path.quadTo(cx + maxR * 0.18f, cy, cx, cy + maxR * 0.3f)
        canvas.drawPath(path, p)
    }
}
