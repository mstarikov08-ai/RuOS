package com.ruos.alarm.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.OverScroller
import com.ruos.alarm.util.Haptics
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * iOS-style spinning wheel picker. Momentum fling via [OverScroller], snaps to the
 * nearest item on settle, dims + shrinks items by distance from the centre to fake
 * the curved 3D drum, and ticks a haptic each time a new item passes the centre.
 */
class WheelPicker(context: Context) : View(context) {

    private var items: List<String> = emptyList()
    var selectedIndex: Int = 0
        private set
    var onSelected: ((Int) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val itemHeight = 46f * density
    private val visibleRows = 5

    private var scrollOffset = 0f          // px; selectedIndex = offset / itemHeight
    private var lastReportedIndex = 0

    private val scroller = OverScroller(context)
    private var velocityTracker: VelocityTracker? = null
    private var lastY = 0f
    private var dragging = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }

    fun setItems(list: List<String>, initial: Int = 0) {
        items = list
        selectedIndex = initial.coerceIn(0, (list.size - 1).coerceAtLeast(0))
        lastReportedIndex = selectedIndex
        scrollOffset = selectedIndex * itemHeight
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = (itemHeight * visibleRows).toInt()
        setMeasuredDimension(w, h)
    }

    private fun maxOffset() = ((items.size - 1).coerceAtLeast(0)) * itemHeight

    override fun onDraw(canvas: Canvas) {
        if (items.isEmpty()) return
        val cx = width / 2f
        val cy = height / 2f
        val center = scrollOffset / itemHeight

        // Draw a couple of rows beyond the viewport for smooth entry/exit.
        val first = (center - visibleRows / 2 - 1).toInt()
        val last = (center + visibleRows / 2 + 1).toInt()
        for (i in first..last) {
            if (i < 0 || i >= items.size) continue
            val y = cy + (i * itemHeight - scrollOffset)
            val dist = abs(y - cy) / (itemHeight * (visibleRows / 2f))
            val clamped = dist.coerceIn(0f, 1f)
            textPaint.alpha = (255 * (1f - 0.72f * clamped)).toInt()
            textPaint.textSize = (26f - 7f * clamped) * density
            textPaint.typeface = if (i == selectedIndex)
                Typeface.create("sans-serif", Typeface.NORMAL)
            else Typeface.create("sans-serif-light", Typeface.NORMAL)
            val baseline = y - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(items[i], cx, baseline, textPaint)
        }

        // Subtle selection lines top/bottom of the centre row.
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(40, 255, 255, 255); strokeWidth = 1f * density
        }
        canvas.drawLine(width * 0.16f, cy - itemHeight / 2, width * 0.84f, cy - itemHeight / 2, linePaint)
        canvas.drawLine(width * 0.16f, cy + itemHeight / 2, width * 0.84f, cy + itemHeight / 2, linePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val vt = velocityTracker ?: VelocityTracker.obtain().also { velocityTracker = it }
        vt.addMovement(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scroller.forceFinished(true)
                lastY = event.y
                dragging = false
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = event.y - lastY
                if (!dragging && abs(dy) > touchSlop) dragging = true
                if (dragging) {
                    scrollOffset = (scrollOffset - dy).coerceIn(0f, maxOffset())
                    lastY = event.y
                    reportIfChanged()
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                vt.computeCurrentVelocity(1000)
                val vy = vt.yVelocity
                velocityTracker?.recycle(); velocityTracker = null
                if (abs(vy) > 300) {
                    scroller.fling(0, scrollOffset.toInt(), 0, -vy.toInt(),
                        0, 0, 0, maxOffset().toInt())
                    postInvalidateOnAnimation()
                } else {
                    snap()
                }
            }
        }
        return true
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollOffset = scroller.currY.toFloat().coerceIn(0f, maxOffset())
            reportIfChanged()
            if (scroller.isFinished) snap() else postInvalidateOnAnimation()
            invalidate()
        }
    }

    private fun snap() {
        val target = (scrollOffset / itemHeight).roundToInt()
            .coerceIn(0, (items.size - 1).coerceAtLeast(0)) * itemHeight
        if (abs(target - scrollOffset) < 0.5f) {
            scrollOffset = target; reportIfChanged(); invalidate(); return
        }
        scroller.startScroll(0, scrollOffset.toInt(), 0, (target - scrollOffset).toInt(), 240)
        postInvalidateOnAnimation()
    }

    private fun reportIfChanged() {
        val idx = (scrollOffset / itemHeight).roundToInt().coerceIn(0, (items.size - 1).coerceAtLeast(0))
        if (idx != lastReportedIndex) {
            lastReportedIndex = idx
            selectedIndex = idx
            Haptics.tick(this)
            onSelected?.invoke(idx)
        }
    }
}
