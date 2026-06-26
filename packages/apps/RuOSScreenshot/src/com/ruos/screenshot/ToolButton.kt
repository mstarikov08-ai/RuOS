package com.ruos.screenshot

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View

enum class ToolGlyph { PEN, HIGHLIGHTER, CROP, UNDO, SHARE }

/** A markup tool button with a hand-drawn glyph (no emoji) and an iOS selected pill. */
class ToolButton(context: Context, private val glyph: ToolGlyph) : View(context) {

    var selected = false
        set(v) { field = v; invalidate() }

    private val d = resources.displayMetrics.density
    private fun dp(v: Float) = v * d

    private val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2C2C2E.toInt() }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = dp(2f); strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND; color = Color.WHITE
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    init { isClickable = true }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f; val cy = height / 2f; val s = dp(11f)
        if (selected) {
            val r = dp(20f)
            canvas.drawRoundRect(RectF(cx - r, cy - r, cx + r, cy + r), dp(12f), dp(12f), pill)
        }
        when (glyph) {
            ToolGlyph.PEN -> {
                canvas.drawLine(cx - s, cy + s, cx + s * 0.4f, cy - s * 0.6f, stroke)
                val nib = Path().apply {
                    moveTo(cx + s * 0.4f, cy - s * 0.6f); lineTo(cx + s, cy - s)
                    lineTo(cx + s * 0.6f, cy - s * 0.1f); close()
                }
                canvas.drawPath(nib, fill)
            }
            ToolGlyph.HIGHLIGHTER -> {
                val p = Paint(stroke).apply { strokeWidth = dp(7f); color = 0xAAFFD60A.toInt() }
                canvas.drawLine(cx - s, cy + s, cx + s, cy - s, p)
            }
            ToolGlyph.CROP -> {
                // two L brackets
                canvas.drawLine(cx - s, cy - s * 0.4f, cx - s, cy + s, stroke)
                canvas.drawLine(cx - s, cy + s, cx + s * 0.4f, cy + s, stroke)
                canvas.drawLine(cx + s, cy + s * 0.4f, cx + s, cy - s, stroke)
                canvas.drawLine(cx + s, cy - s, cx - s * 0.4f, cy - s, stroke)
            }
            ToolGlyph.UNDO -> {
                canvas.drawArc(RectF(cx - s, cy - s, cx + s, cy + s), 120f, 200f, false, stroke)
                val ah = Path().apply {
                    val ax = cx - s * 0.5f; val ay = cy - s * 0.6f
                    moveTo(ax, ay); lineTo(ax - dp(6f), ay); lineTo(ax, ay + dp(6f)); close()
                }
                canvas.drawPath(ah, fill)
            }
            ToolGlyph.SHARE -> {
                canvas.drawRoundRect(RectF(cx - s, cy - s * 0.2f, cx + s, cy + s), dp(3f), dp(3f), stroke)
                canvas.drawLine(cx, cy - s, cx, cy + s * 0.3f, stroke)
                canvas.drawLine(cx, cy - s, cx - s * 0.5f, cy - s * 0.45f, stroke)
                canvas.drawLine(cx, cy - s, cx + s * 0.5f, cy - s * 0.45f, stroke)
            }
        }
    }
}
