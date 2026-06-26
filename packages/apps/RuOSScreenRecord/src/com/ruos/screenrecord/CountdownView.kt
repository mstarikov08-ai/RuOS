package com.ruos.screenrecord

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.View

/** Full-screen 3-2-1 countdown shown before recording starts. */
@SuppressLint("ViewConstructor")
class CountdownView(context: Context) : View(context) {

    private val d = resources.displayMetrics.density
    private val main = Handler(Looper.getMainLooper())
    private var value = 3
    private var scale = 1f

    private val scrim = Paint().apply { color = 0x66000000 }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 6f * d; color = 0xFFFF3B30.toInt() }
    private val num = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER; textSize = 96f * d
        typeface = Typeface.create("golos", Typeface.NORMAL)
    }

    fun run(from: Int, onDone: () -> Unit) {
        value = from
        val step = object : Runnable {
            override fun run() {
                if (value <= 0) { onDone(); return }
                scale = 1.25f; invalidate()
                animateShrink()
                value--
                main.postDelayed(this, 700)
            }
        }
        main.post(step)
    }

    private fun animateShrink() {
        val a = object : Runnable {
            override fun run() {
                scale -= 0.03f
                if (scale > 1f) { invalidate(); main.postDelayed(this, 16) } else { scale = 1f; invalidate() }
            }
        }
        main.postDelayed(a, 16)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrim)
        if (value <= 0) return
        val cx = width / 2f; val cy = height / 2f; val r = 70f * d * scale
        canvas.drawCircle(cx, cy, r, ring)
        canvas.drawText(value.toString(), cx, cy + 34f * d, num)
    }
}
