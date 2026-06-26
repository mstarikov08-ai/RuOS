package com.ruos.alarm.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/** Shared colours + canvas-drawn icons (no emoji, matching the rest of RuOS). */
object Ui {
    const val BG = "#000000"
    const val CARD = "#1C1C1E"
    const val TEXT = "#FFFFFF"
    const val TEXT_DIM = "#8E8E93"
    const val ACCENT = "#D94F3D"
    const val RED = "#FF453A"
    const val GREEN = "#30D158"
    const val SEP = "#2C2C2E"

    fun dp(ctx: Context, v: Float) = v * ctx.resources.displayMetrics.density

    /** iOS-style red "−" delete badge for edit mode. */
    fun deleteCircle(ctx: Context): Drawable = object : Drawable() {
        override fun draw(canvas: Canvas) {
            val b = bounds
            val cx = b.exactCenterX(); val cy = b.exactCenterY()
            val r = b.width() * 0.42f
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(RED) }
            canvas.drawCircle(cx, cy, r, fill)
            val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE; strokeWidth = b.width() * 0.10f; strokeCap = Paint.Cap.ROUND
            }
            canvas.drawLine(cx - r * 0.5f, cy, cx + r * 0.5f, cy, bar)
        }
        override fun setAlpha(a: Int) {}
        override fun setColorFilter(cf: ColorFilter?) {}
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    /** A "+" glyph for the add-alarm button. */
    fun plusIcon(ctx: Context, color: Int): Drawable = object : Drawable() {
        override fun draw(canvas: Canvas) {
            val b = bounds
            val cx = b.exactCenterX(); val cy = b.exactCenterY()
            val r = b.width() * 0.26f
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color; strokeWidth = b.width() * 0.07f; strokeCap = Paint.Cap.ROUND
            }
            canvas.drawLine(cx - r, cy, cx + r, cy, p)
            canvas.drawLine(cx, cy - r, cx, cy + r, p)
        }
        override fun setAlpha(a: Int) {}
        override fun setColorFilter(cf: ColorFilter?) {}
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    /** A right-chevron for navigation rows. */
    fun chevron(ctx: Context, color: Int): Drawable = object : Drawable() {
        override fun draw(canvas: Canvas) {
            val b = bounds
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color; style = Paint.Style.STROKE
                strokeWidth = b.width() * 0.12f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
            }
            val w = b.width().toFloat(); val h = b.height().toFloat()
            canvas.drawLine(w * 0.4f, h * 0.3f, w * 0.62f, h * 0.5f, p)
            canvas.drawLine(w * 0.62f, h * 0.5f, w * 0.4f, h * 0.7f, p)
        }
        override fun setAlpha(a: Int) {}
        override fun setColorFilter(cf: ColorFilter?) {}
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}
