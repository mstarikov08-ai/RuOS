package com.ruos.journal.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import com.ruos.journal.model.Mood

/** Canvas-drawn mood face (no emoji): coloured disc + eyes + a mouth curve that goes
 *  from a smile (great) to a frown (awful) based on the mood value. */
class MoodFaceView(context: Context) : View(context) {

    private var mood: Mood? = null
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1C1C1E"); style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val eye = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1C1C1E") }
    var selected = false
        set(v) { field = v; invalidate() }

    fun setMood(m: Mood) { mood = m; fill.color = m.color; invalidate() }

    override fun onDraw(canvas: Canvas) {
        val m = mood ?: return
        val w = width.toFloat(); val h = height.toFloat()
        val cx = w / 2f; val cy = h / 2f; val r = minOf(w, h) / 2f - 3f
        if (selected) {
            val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 3f }
            canvas.drawCircle(cx, cy, r, ring)
        }
        canvas.drawCircle(cx, cy, r - 4f, fill)

        val eyeR = r * 0.11f
        canvas.drawCircle(cx - r * 0.32f, cy - r * 0.18f, eyeR, eye)
        canvas.drawCircle(cx + r * 0.32f, cy - r * 0.18f, eyeR, eye)

        // Mouth: curvature from +1 (smile) to -1 (frown) via mood value (-2..2).
        ink.strokeWidth = r * 0.12f
        val curve = m.id / 2f                        // 1.0 .. -1.0
        val mouthY = cy + r * 0.28f
        val span = r * 0.4f
        val bend = r * 0.34f * curve
        val rect = RectF(cx - span, mouthY - bend, cx + span, mouthY + bend)
        if (kotlin.math.abs(curve) < 0.05f) {
            canvas.drawLine(cx - span, mouthY, cx + span, mouthY, ink)
        } else if (curve > 0) {
            canvas.drawArc(RectF(cx - span, mouthY - bend, cx + span, mouthY + bend), 20f, 140f, false, ink)
        } else {
            canvas.drawArc(RectF(cx - span, mouthY + bend * 2 - bend, cx + span, mouthY - bend * 2 - bend), 200f, 140f, false, ink)
        }
    }
}
