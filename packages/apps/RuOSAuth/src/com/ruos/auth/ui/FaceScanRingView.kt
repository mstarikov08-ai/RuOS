package com.ruos.auth.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/**
 * The iOS Face ID enrolment ring: dots arranged in a circle that light up green as
 * the scan "progresses". Purely cosmetic onboarding — real face data is captured by
 * the system enrolment flow that follows; RuOS never sees camera frames.
 */
class FaceScanRingView(context: Context) : View(context) {

    private val dotCount = 36
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density

    /** 0..1 across the current pass. */
    var progress = 0f
        set(v) { field = v; invalidate() }
    /** Tints later passes slightly differently, like iOS. */
    var pass = 0

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        val r = minOf(width, height) * 0.36f
        val lit = (progress * dotCount).toInt()
        for (i in 0 until dotCount) {
            val a = Math.toRadians((i * 360.0 / dotCount) - 90.0)
            val x = cx + r * Math.cos(a).toFloat()
            val y = cy + r * Math.sin(a).toFloat()
            val on = i < lit
            paint.color = if (on) Color.parseColor(if (pass == 0) "#30D158" else "#34C7C0")
                          else Color.parseColor("#2A2A2E")
            val dotR = if (on) 4.5f * density else 3f * density
            canvas.drawCircle(x, y, dotR, paint)
        }
    }
}
