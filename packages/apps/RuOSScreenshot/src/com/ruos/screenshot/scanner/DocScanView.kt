package com.ruos.screenshot.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.view.MotionEvent
import android.view.View

/**
 * Shows the source photo with four draggable corner handles bounding the document. [warp] returns
 * the region inside those corners rectified to a straight-on rectangle via [Matrix.setPolyToPoly]
 * (the standard four-point perspective transform). The corners start as a slight inset so the user
 * only nudges them to the page edges.
 */
class DocScanView(context: Context) : View(context) {

    private var bitmap: Bitmap? = null
    private val corners = Array(4) { PointF() }   // TL, TR, BR, BL in view coordinates
    private var activeCorner = -1
    private val d = resources.displayMetrics.density
    private fun dp(v: Int) = v * d

    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = dp(2); color = Color.parseColor("#0A84FF") }
    private val handle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0A84FF") }
    private val handleCore = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    fun setBitmap(b: Bitmap) { bitmap = b; requestLayout(); post { resetCorners() } }

    private fun drawRect(): FloatArray {
        // The area the bitmap occupies inside this view (fit-center).
        val b = bitmap ?: return floatArrayOf(0f, 0f, width.toFloat(), height.toFloat())
        val scale = minOf(width.toFloat() / b.width, height.toFloat() / b.height)
        val w = b.width * scale; val h = b.height * scale
        val left = (width - w) / 2f; val top = (height - h) / 2f
        return floatArrayOf(left, top, left + w, top + h)
    }

    private fun resetCorners() {
        val r = drawRect()
        val insetX = (r[2] - r[0]) * 0.08f; val insetY = (r[3] - r[1]) * 0.08f
        corners[0].set(r[0] + insetX, r[1] + insetY)
        corners[1].set(r[2] - insetX, r[1] + insetY)
        corners[2].set(r[2] - insetX, r[3] - insetY)
        corners[3].set(r[0] + insetX, r[3] - insetY)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val b = bitmap ?: return
        val r = drawRect()
        canvas.drawBitmap(b, null, android.graphics.RectF(r[0], r[1], r[2], r[3]), null)
        for (i in 0 until 4) {
            val a = corners[i]; val c = corners[(i + 1) % 4]
            canvas.drawLine(a.x, a.y, c.x, c.y, edge)
        }
        for (p in corners) {
            canvas.drawCircle(p.x, p.y, dp(11), handle)
            canvas.drawCircle(p.x, p.y, dp(5), handleCore)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                activeCorner = (0 until 4).minByOrNull { dist(corners[it], event.x, event.y) }
                    ?.takeIf { dist(corners[it], event.x, event.y) < dp(44) } ?: -1
                return activeCorner >= 0
            }
            MotionEvent.ACTION_MOVE -> if (activeCorner >= 0) {
                val r = drawRect()
                corners[activeCorner].set(event.x.coerceIn(r[0], r[2]), event.y.coerceIn(r[1], r[3]))
                invalidate(); return true
            }
            MotionEvent.ACTION_UP -> activeCorner = -1
        }
        return super.onTouchEvent(event)
    }

    private fun dist(p: PointF, x: Float, y: Float) =
        Math.hypot((p.x - x).toDouble(), (p.y - y).toDouble()).toFloat()

    /** Rectify the quadrilateral into a straight rectangle, in source-bitmap pixels. */
    fun warp(): Bitmap? {
        val b = bitmap ?: return null
        val r = drawRect()
        val scale = (b.width.toFloat()) / (r[2] - r[0])   // view→bitmap scale (uniform)
        // Corners in bitmap-pixel space.
        val src = FloatArray(8)
        for (i in 0 until 4) {
            src[i * 2] = (corners[i].x - r[0]) * scale
            src[i * 2 + 1] = (corners[i].y - r[1]) * scale
        }
        // Output size from the average of opposite edges.
        val wTop = edgeLen(src, 0, 1); val wBot = edgeLen(src, 3, 2)
        val hL = edgeLen(src, 0, 3); val hR = edgeLen(src, 1, 2)
        val outW = ((wTop + wBot) / 2f).toInt().coerceAtLeast(1)
        val outH = ((hL + hR) / 2f).toInt().coerceAtLeast(1)
        val dst = floatArrayOf(0f, 0f, outW.toFloat(), 0f, outW.toFloat(), outH.toFloat(), 0f, outH.toFloat())
        val m = Matrix()
        if (!m.setPolyToPoly(src, 0, dst, 0, 4)) return null
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(b, m, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    private fun edgeLen(p: FloatArray, a: Int, c: Int): Float =
        Math.hypot((p[a * 2] - p[c * 2]).toDouble(), (p[a * 2 + 1] - p[c * 2 + 1]).toDouble()).toFloat()
}
