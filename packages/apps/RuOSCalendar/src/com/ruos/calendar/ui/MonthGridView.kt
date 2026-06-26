package com.ruos.calendar.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import java.util.Calendar

/**
 * iOS-style month grid (Monday-first). Draws day numbers, a filled accent circle for
 * today, a ring for the selected day, and a dot under days that have events. Tapping a
 * cell selects that day.
 */
@SuppressLint("ViewConstructor")
class MonthGridView(context: Context) : View(context) {

    private val d = resources.displayMetrics.density
    private val accent = 0xFFFF3B30.toInt()

    var year = 0; private set
    var month = 0; private set     // 0-based
    private var selectedDay = 1
    private var daysWithEvents: Set<Int> = emptySet()

    private var firstColOffset = 0
    private var daysInMonth = 30
    private var todayDay = -1

    var onDaySelected: ((day: Int, millis: Long) -> Unit)? = null

    private val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER; textSize = 16f * d; typeface = Fonts.regular
    }
    private val todayFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
    private val selRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1.5f * d; color = 0xFF5A5A5E.toInt() }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }

    fun setMonth(y: Int, m: Int, selectDay: Int) {
        year = y; month = m
        val cal = Calendar.getInstance().apply { set(y, m, 1) }
        // Monday-first offset
        firstColOffset = (cal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
        daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        selectedDay = selectDay.coerceIn(1, daysInMonth)
        val now = Calendar.getInstance()
        todayDay = if (now.get(Calendar.YEAR) == y && now.get(Calendar.MONTH) == m) now.get(Calendar.DAY_OF_MONTH) else -1
        requestLayout(); invalidate()
    }

    fun setEventDays(days: Set<Int>) { daysWithEvents = days; invalidate() }
    fun selectedDay() = selectedDay

    private fun rows(): Int = Math.ceil((firstColOffset + daysInMonth) / 7.0).toInt().coerceAtLeast(1)

    override fun onMeasure(w: Int, h: Int) {
        val width = MeasureSpec.getSize(w)
        setMeasuredDimension(width, (rows() * 48 * d).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val cellW = width / 7f
        val cellH = 48 * d
        for (day in 1..daysInMonth) {
            val idx = firstColOffset + day - 1
            val col = idx % 7; val row = idx / 7
            val cx = col * cellW + cellW / 2
            val cy = row * cellH + cellH / 2
            if (day == todayDay) canvas.drawCircle(cx, cy - 4 * d, 16 * d, todayFill)
            else if (day == selectedDay) canvas.drawCircle(cx, cy - 4 * d, 16 * d, selRing)
            numPaint.color = if (day == todayDay) Color.WHITE else Color.WHITE
            canvas.drawText(day.toString(), cx, cy + 2 * d, numPaint)
            if (daysWithEvents.contains(day) && day != todayDay)
                canvas.drawCircle(cx, cy + 14 * d, 2.5f * d, dot)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action != MotionEvent.ACTION_UP) return true
        val cellW = width / 7f; val cellH = 48 * d
        val col = (e.x / cellW).toInt().coerceIn(0, 6)
        val row = (e.y / cellH).toInt()
        val day = row * 7 + col - firstColOffset + 1
        if (day in 1..daysInMonth) {
            selectedDay = day; invalidate()
            val millis = Calendar.getInstance().apply {
                set(year, month, day, 0, 0, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            onDaySelected?.invoke(day, millis)
        }
        return true
    }
}
