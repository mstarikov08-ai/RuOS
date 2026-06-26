package com.ruos.focus.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import com.ruos.focus.model.FocusIcon

/**
 * Draws each Focus glyph by hand onto a canvas (project rule: no emoji, no font icons —
 * every symbol is vector-drawn). All glyphs are sized to a unit box [0,size]; the caller
 * positions/colours via [paint].
 */
object FocusGlyph {

    fun draw(canvas: Canvas, icon: FocusIcon, cx: Float, cy: Float, size: Float, paint: Paint) {
        val r = size / 2f
        when (icon) {
            FocusIcon.MOON -> moon(canvas, cx, cy, r, paint)
            FocusIcon.BRIEFCASE -> briefcase(canvas, cx, cy, r, paint)
            FocusIcon.PERSON -> person(canvas, cx, cy, r, paint)
            FocusIcon.BED -> bed(canvas, cx, cy, r, paint)
            FocusIcon.CAR -> car(canvas, cx, cy, r, paint)
            FocusIcon.BOOK -> book(canvas, cx, cy, r, paint)
            FocusIcon.STAR -> star(canvas, cx, cy, r, paint)
            FocusIcon.HEART -> heart(canvas, cx, cy, r, paint)
            FocusIcon.GAME -> game(canvas, cx, cy, r, paint)
            FocusIcon.DUMBBELL -> dumbbell(canvas, cx, cy, r, paint)
        }
    }

    private fun fill(p: Paint) = Paint(p).apply { style = Paint.Style.FILL; isAntiAlias = true }
    private fun stroke(p: Paint, w: Float) = Paint(p).apply {
        style = Paint.Style.STROKE; strokeWidth = w; strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND; isAntiAlias = true
    }

    private fun moon(c: Canvas, cx: Float, cy: Float, r: Float, p: Paint) {
        // crescent = full disc minus an offset disc (DST_OUT)
        val layer = c.saveLayer(cx - r, cy - r, cx + r, cy + r, null)
        val f = fill(p)
        c.drawCircle(cx, cy, r * 0.9f, f)
        val cut = Paint(f).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
        c.drawCircle(cx + r * 0.45f, cy - r * 0.35f, r * 0.82f, cut)
        c.restoreToCount(layer)
    }

    private fun briefcase(c: Canvas, cx: Float, cy: Float, r: Float, p: Paint) {
        val s = stroke(p, r * 0.22f)
        val body = RectF(cx - r * 0.8f, cy - r * 0.35f, cx + r * 0.8f, cy + r * 0.75f)
        c.drawRoundRect(body, r * 0.2f, r * 0.2f, s)
        // handle
        val handle = RectF(cx - r * 0.4f, cy - r * 0.75f, cx + r * 0.4f, cy - r * 0.1f)
        c.drawRoundRect(handle, r * 0.18f, r * 0.18f, s)
        c.drawLine(cx - r * 0.8f, cy + r * 0.2f, cx + r * 0.8f, cy + r * 0.2f, s)
    }

    private fun person(c: Canvas, cx: Float, cy: Float, r: Float, p: Paint) {
        val f = fill(p)
        c.drawCircle(cx, cy - r * 0.42f, r * 0.34f, f)
        val torso = Path().apply {
            addArc(RectF(cx - r * 0.62f, cy + r * 0.0f, cx + r * 0.62f, cy + r * 1.1f), 180f, 180f)
        }
        c.drawPath(torso, f)
    }

    private fun bed(c: Canvas, cx: Float, cy: Float, r: Float, p: Paint) {
        val s = stroke(p, r * 0.2f)
        // frame
        c.drawLine(cx - r * 0.85f, cy - r * 0.4f, cx - r * 0.85f, cy + r * 0.6f, s)
        c.drawLine(cx - r * 0.85f, cy + r * 0.2f, cx + r * 0.85f, cy + r * 0.2f, s)
        c.drawLine(cx + r * 0.85f, cy + r * 0.2f, cx + r * 0.85f, cy + r * 0.6f, s)
        // mattress + pillow
        val mattress = RectF(cx - r * 0.85f, cy - r * 0.15f, cx + r * 0.85f, cy + r * 0.2f)
        c.drawRoundRect(mattress, r * 0.12f, r * 0.12f, s)
        val pillow = RectF(cx - r * 0.7f, cy - r * 0.1f, cx - r * 0.25f, cy + r * 0.05f)
        c.drawRoundRect(pillow, r * 0.08f, r * 0.08f, s)
    }

    private fun car(c: Canvas, cx: Float, cy: Float, r: Float, p: Paint) {
        val s = stroke(p, r * 0.2f)
        val body = Path().apply {
            moveTo(cx - r * 0.85f, cy + r * 0.25f)
            lineTo(cx - r * 0.6f, cy - r * 0.1f)
            quadTo(cx - r * 0.45f, cy - r * 0.4f, cx - r * 0.15f, cy - r * 0.4f)
            lineTo(cx + r * 0.3f, cy - r * 0.4f)
            quadTo(cx + r * 0.55f, cy - r * 0.4f, cx + r * 0.7f, cy - r * 0.05f)
            lineTo(cx + r * 0.85f, cy + r * 0.25f)
        }
        c.drawPath(body, s)
        c.drawLine(cx - r * 0.85f, cy + r * 0.25f, cx + r * 0.85f, cy + r * 0.25f, s)
        val f = fill(p)
        c.drawCircle(cx - r * 0.5f, cy + r * 0.3f, r * 0.16f, f)
        c.drawCircle(cx + r * 0.5f, cy + r * 0.3f, r * 0.16f, f)
    }

    private fun book(c: Canvas, cx: Float, cy: Float, r: Float, p: Paint) {
        val s = stroke(p, r * 0.18f)
        // open book: two pages meeting at the spine
        val left = Path().apply {
            moveTo(cx, cy - r * 0.5f)
            quadTo(cx - r * 0.5f, cy - r * 0.65f, cx - r * 0.85f, cy - r * 0.4f)
            lineTo(cx - r * 0.85f, cy + r * 0.55f)
            quadTo(cx - r * 0.5f, cy + r * 0.3f, cx, cy + r * 0.5f)
        }
        val right = Path().apply {
            moveTo(cx, cy - r * 0.5f)
            quadTo(cx + r * 0.5f, cy - r * 0.65f, cx + r * 0.85f, cy - r * 0.4f)
            lineTo(cx + r * 0.85f, cy + r * 0.55f)
            quadTo(cx + r * 0.5f, cy + r * 0.3f, cx, cy + r * 0.5f)
        }
        c.drawPath(left, s); c.drawPath(right, s)
        c.drawLine(cx, cy - r * 0.5f, cx, cy + r * 0.5f, s)
    }

    private fun star(c: Canvas, cx: Float, cy: Float, r: Float, p: Paint) {
        val f = fill(p)
        val path = Path()
        for (i in 0 until 10) {
            val rad = if (i % 2 == 0) r * 0.95f else r * 0.42f
            val a = Math.toRadians((i * 36 - 90).toDouble())
            val x = cx + (rad * Math.cos(a)).toFloat()
            val y = cy + (rad * Math.sin(a)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close(); c.drawPath(path, f)
    }

    private fun heart(c: Canvas, cx: Float, cy: Float, r: Float, p: Paint) {
        val f = fill(p)
        val path = Path().apply {
            moveTo(cx, cy + r * 0.7f)
            cubicTo(cx - r * 1.2f, cy - r * 0.2f, cx - r * 0.5f, cy - r * 0.85f, cx, cy - r * 0.25f)
            cubicTo(cx + r * 0.5f, cy - r * 0.85f, cx + r * 1.2f, cy - r * 0.2f, cx, cy + r * 0.7f)
        }
        c.drawPath(path, f)
    }

    private fun game(c: Canvas, cx: Float, cy: Float, r: Float, p: Paint) {
        val s = stroke(p, r * 0.2f)
        val body = RectF(cx - r * 0.85f, cy - r * 0.3f, cx + r * 0.85f, cy + r * 0.45f)
        c.drawRoundRect(body, r * 0.35f, r * 0.35f, s)
        // d-pad + buttons
        c.drawLine(cx - r * 0.55f, cy, cx - r * 0.25f, cy, s)
        c.drawLine(cx - r * 0.4f, cy - r * 0.15f, cx - r * 0.4f, cy + r * 0.15f, s)
        val f = fill(p)
        c.drawCircle(cx + r * 0.35f, cy - r * 0.05f, r * 0.1f, f)
        c.drawCircle(cx + r * 0.55f, cy + r * 0.12f, r * 0.1f, f)
    }

    private fun dumbbell(c: Canvas, cx: Float, cy: Float, r: Float, p: Paint) {
        val s = stroke(p, r * 0.22f)
        c.drawLine(cx - r * 0.4f, cy, cx + r * 0.4f, cy, s)
        c.drawLine(cx - r * 0.6f, cy - r * 0.4f, cx - r * 0.6f, cy + r * 0.4f, s)
        c.drawLine(cx + r * 0.6f, cy - r * 0.4f, cx + r * 0.6f, cy + r * 0.4f, s)
        c.drawLine(cx - r * 0.85f, cy - r * 0.22f, cx - r * 0.85f, cy + r * 0.22f, s)
        c.drawLine(cx + r * 0.85f, cy - r * 0.22f, cx + r * 0.85f, cy + r * 0.22f, s)
    }
}
