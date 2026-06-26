package com.ruos.screenshot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

/**
 * The markup canvas: shows the screenshot, lets you draw pen / highlighter strokes in a
 * chosen colour, and crop. Strokes are stored in *bitmap* coordinates so they stay
 * pixel-correct when exported at full resolution, independent of the on-screen fit.
 */
class MarkupView(context: Context) : View(context) {

    enum class Tool { PEN, HIGHLIGHTER, CROP }

    private class Stroke(val path: Path, val color: Int, val width: Float, val highlighter: Boolean)

    private lateinit var base: Bitmap
    private val strokes = ArrayList<Stroke>()
    private val redo = ArrayList<Stroke>()

    private var tool = Tool.PEN
    private var color = Color.parseColor("#FF3B30")
    private val fit = Matrix()
    private val inv = Matrix()

    private var cropRect = RectF()
    private var activeStroke: Stroke? = null
    private var dragCorner = -1   // 0..3 TL TR BR BL, -1 none

    var onHistoryChanged: (() -> Unit)? = null

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val dim = Paint().apply { color = 0xAA000000.toInt() }
    private val cropBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.WHITE; strokeWidth = 4f
    }
    private val handle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    fun setBitmap(b: Bitmap) {
        base = b; cropRect = RectF(0f, 0f, b.width.toFloat(), b.height.toFloat())
        requestLayout(); invalidate()
    }

    fun setTool(t: Tool) { tool = t; invalidate() }
    fun setColor(c: Int) { color = c }
    fun currentTool() = tool

    fun canUndo() = strokes.isNotEmpty()
    fun undo() { if (strokes.isNotEmpty()) { redo.add(strokes.removeAt(strokes.size - 1)); invalidate(); onHistoryChanged?.invoke() } }

    private fun penWidthBmp() = base.width * 0.011f
    private fun hlWidthBmp() = base.width * 0.045f

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { computeFit() }

    private fun computeFit() {
        if (!::base.isInitialized || width == 0 || height == 0) return
        val pad = 0f
        val sx = (width - pad) / base.width
        val sy = (height - pad) / base.height
        val s = minOf(sx, sy)
        val dx = (width - base.width * s) / 2f
        val dy = (height - base.height * s) / 2f
        fit.reset(); fit.postScale(s, s); fit.postTranslate(dx, dy)
        fit.invert(inv)
    }

    override fun onDraw(canvas: Canvas) {
        if (!::base.isInitialized) return
        if (fit.isIdentity) computeFit()
        canvas.save(); canvas.concat(fit)
        canvas.drawBitmap(base, 0f, 0f, null)
        for (s in strokes) drawStroke(canvas, s)
        activeStroke?.let { drawStroke(canvas, it) }
        canvas.restore()

        if (tool == Tool.CROP) drawCropOverlay(canvas)
    }

    private fun drawStroke(canvas: Canvas, s: Stroke) {
        strokePaint.color = if (s.highlighter) (s.color and 0x00FFFFFF) or 0x66000000 else s.color
        strokePaint.strokeWidth = s.width
        canvas.drawPath(s.path, strokePaint)
    }

    private fun drawCropOverlay(canvas: Canvas) {
        val r = mapRect(cropRect)
        // dim outside
        canvas.drawRect(0f, 0f, width.toFloat(), r.top, dim)
        canvas.drawRect(0f, r.bottom, width.toFloat(), height.toFloat(), dim)
        canvas.drawRect(0f, r.top, r.left, r.bottom, dim)
        canvas.drawRect(r.right, r.top, width.toFloat(), r.bottom, dim)
        canvas.drawRect(r, cropBorder)
        val hs = 10f
        for (p in cropCorners(r)) canvas.drawCircle(p.x, p.y, hs, handle)
    }

    private fun cropCorners(r: RectF) = listOf(
        PointF(r.left, r.top), PointF(r.right, r.top), PointF(r.right, r.bottom), PointF(r.left, r.bottom))

    private fun mapRect(b: RectF): RectF { val o = RectF(b); fit.mapRect(o); return o }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!::base.isInitialized) return false
        return if (tool == Tool.CROP) handleCrop(e) else handleDraw(e)
    }

    private fun handleDraw(e: MotionEvent): Boolean {
        val pt = floatArrayOf(e.x, e.y); inv.mapPoints(pt)
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                val hl = tool == Tool.HIGHLIGHTER
                val p = Path().apply { moveTo(pt[0], pt[1]) }
                activeStroke = Stroke(p, color, if (hl) hlWidthBmp() else penWidthBmp(), hl)
            }
            MotionEvent.ACTION_MOVE -> activeStroke?.path?.lineTo(pt[0], pt[1])
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activeStroke?.let { strokes.add(it); redo.clear(); onHistoryChanged?.invoke() }
                activeStroke = null
            }
        }
        invalidate(); return true
    }

    private fun handleCrop(e: MotionEvent): Boolean {
        val r = mapRect(cropRect)
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                dragCorner = -1
                cropCorners(r).forEachIndexed { i, p ->
                    if (Math.hypot((e.x - p.x).toDouble(), (e.y - p.y).toDouble()) < 48) dragCorner = i
                }
            }
            MotionEvent.ACTION_MOVE -> if (dragCorner >= 0) {
                val pt = floatArrayOf(e.x, e.y); inv.mapPoints(pt)
                val x = pt[0].coerceIn(0f, base.width.toFloat())
                val y = pt[1].coerceIn(0f, base.height.toFloat())
                when (dragCorner) {
                    0 -> { cropRect.left = x; cropRect.top = y }
                    1 -> { cropRect.right = x; cropRect.top = y }
                    2 -> { cropRect.right = x; cropRect.bottom = y }
                    3 -> { cropRect.left = x; cropRect.bottom = y }
                }
                cropRect.sort()
                invalidate()
            }
            MotionEvent.ACTION_UP -> dragCorner = -1
        }
        return true
    }

    /** Render the final edited image at full resolution (cropped + strokes). */
    fun export(): Bitmap {
        val r = RectF(cropRect); r.sort()
        val x = r.left.toInt().coerceIn(0, base.width - 1)
        val y = r.top.toInt().coerceIn(0, base.height - 1)
        val w = (r.width().toInt()).coerceIn(1, base.width - x)
        val h = (r.height().toInt()).coerceIn(1, base.height - y)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.save(); c.translate(-x.toFloat(), -y.toFloat())
        c.drawBitmap(base, 0f, 0f, null)
        for (s in strokes) drawStroke(c, s)
        c.restore()
        return out
    }
}
