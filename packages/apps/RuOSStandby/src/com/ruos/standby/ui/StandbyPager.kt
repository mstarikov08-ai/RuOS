package com.ruos.standby.ui

import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import kotlin.math.abs
import kotlin.math.roundToInt

/** Minimal full-width horizontal pager with spring snap, for the 3 StandBy pages. */
class StandbyPager(context: Context) : ViewGroup(context) {

    var onPageChanged: ((Int) -> Unit)? = null
    private var current = 0
    private var scrollX2 = 0f
    private var lastX = 0f; private var downX = 0f; private var downY = 0f
    private var dragging = false
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var vt: VelocityTracker? = null

    private val prop = object : FloatPropertyCompat<StandbyPager>("sx") {
        override fun getValue(o: StandbyPager) = o.scrollX2
        override fun setValue(o: StandbyPager, v: Float) { o.scrollX2 = v; o.requestLayout() }
    }
    private val spring = SpringAnimation(this, prop).apply {
        spring = SpringForce().apply { stiffness = SpringForce.STIFFNESS_MEDIUM; dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY }
    }

    fun setPage(i: Int) { current = i.coerceIn(0, (childCount - 1).coerceAtLeast(0)); scrollX2 = current.toFloat() * width; requestLayout() }

    override fun onMeasure(w: Int, h: Int) {
        super.onMeasure(w, h)
        for (i in 0 until childCount) getChildAt(i).measure(w, h)
    }
    override fun onLayout(c: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val w = r - l
        for (i in 0 until childCount) {
            val left = i * w - scrollX2.toInt()
            getChildAt(i).layout(left, 0, left + w, b - t)
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = ev.x; downY = ev.y; lastX = ev.x; spring.cancel() }
            MotionEvent.ACTION_MOVE -> {
                if (abs(ev.x - downX) > slop && abs(ev.x - downX) > abs(ev.y - downY)) return true
            }
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val tracker = vt ?: VelocityTracker.obtain().also { vt = it }
        tracker.addMovement(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = event.x; lastX = event.x }
            MotionEvent.ACTION_MOVE -> {
                scrollX2 = (scrollX2 - (event.x - lastX)).coerceIn(0f, ((childCount - 1) * width).toFloat())
                lastX = event.x; requestLayout()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                tracker.computeCurrentVelocity(1000)
                val vx = tracker.xVelocity
                vt?.recycle(); vt = null
                val target = when {
                    vx < -800 && current < childCount - 1 -> current + 1
                    vx > 800 && current > 0 -> current - 1
                    else -> (scrollX2 / width).roundToInt().coerceIn(0, childCount - 1)
                }
                current = target
                spring.animateToFinalPosition(target.toFloat() * width)
                onPageChanged?.invoke(current)
            }
        }
        return true
    }
}
