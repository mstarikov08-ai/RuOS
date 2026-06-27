package com.ruos.assist.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/**
 * The iOS AssistiveTouch puck: a soft translucent rounded-square with a concentric ring,
 * draggable anywhere, fades to semi-transparent when idle. A tap (without dragging) opens
 * the action menu; dragging repositions it (the service owns the window, so movement is
 * reported through [onMove], and a release through [onMoveEnd]).
 */
@SuppressLint("ViewConstructor")
class FloatingButton(context: Context) : View(context) {

    private val d = resources.displayMetrics.density
    private fun dp(v: Float) = v * d
    private val main = Handler(Looper.getMainLooper())

    var onTap: (() -> Unit)? = null
    var onMove: ((dx: Float, dy: Float) -> Unit)? = null
    var onMoveEnd: (() -> Unit)? = null

    private val outer = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x80FFFFFF.toInt() }
    private val mid = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(2f); color = 0xCCFFFFFF.toInt() }
    private val inner = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }

    private var downX = 0f; private var downY = 0f; private var moved = false

    private val fade = Runnable { animate().alpha(0.4f).setDuration(400).start() }

    init { isClickable = true; scheduleFade() }

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(dp(52f).toInt(), dp(52f).toInt())

    override fun onDraw(c: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        c.drawRoundRect(RectF(dp(4f), dp(4f), width - dp(4f), height - dp(4f)), dp(13f), dp(13f), outer)
        c.drawRoundRect(RectF(dp(13f), dp(13f), width - dp(13f), height - dp(13f)), dp(7f), dp(7f), mid)
        c.drawCircle(cx, cy, dp(5f), inner)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                wake(); downX = e.rawX; downY = e.rawY; moved = false; return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.rawX - downX; val dy = e.rawY - downY
                if (abs(dx) > dp(6f) || abs(dy) > dp(6f)) moved = true
                if (moved) { onMove?.invoke(dx, dy); downX = e.rawX; downY = e.rawY }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (moved) onMoveEnd?.invoke() else onTap?.invoke()
                scheduleFade(); return true
            }
        }
        return false
    }

    private fun wake() { main.removeCallbacks(fade); animate().alpha(1f).setDuration(120).start() }
    private fun scheduleFade() { main.removeCallbacks(fade); main.postDelayed(fade, 4000) }
}
