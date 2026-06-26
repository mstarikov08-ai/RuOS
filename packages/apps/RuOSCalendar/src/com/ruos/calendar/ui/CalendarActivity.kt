package com.ruos.calendar.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.ruos.calendar.data.CalEvent
import com.ruos.calendar.data.CalendarRepo
import java.util.Calendar

/**
 * iOS-style calendar: month header with prev/next + «Сегодня», a Monday-first month grid
 * with event dots, and the selected day's events listed below. «+» creates an event. All
 * data comes from the system CalendarContract, so synced (Yandex/Google) events appear.
 */
class CalendarActivity : Activity() {

    private lateinit var repo: CalendarRepo
    private lateinit var header: TextView
    private lateinit var grid: MonthGridView
    private lateinit var dayList: LinearLayout
    private val cal = Calendar.getInstance()
    private var selectedMillis = CalendarRepo.startOfDay(System.currentTimeMillis())
    private val accent = 0xFFFF3B30.toInt()
    private val d get() = resources.displayMetrics.density
    private fun dp(v: Float) = (v * d).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = CalendarRepo(this)
        if (checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR), 1)
        }
        setContentView(build())
        refreshMonth(); refreshDay()
    }

    override fun onResume() { super.onResume(); if (::grid.isInitialized) { refreshMonth(); refreshDay() } }

    override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, r: IntArray) {
        super.onRequestPermissionsResult(rc, p, r); refreshMonth(); refreshDay()
    }

    private fun build(): View {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK) }

        // header
        val hr = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f), dp(48f), dp(16f), dp(8f))
        }
        header = TextView(this).apply { setTextColor(accent); textSize = 22f; typeface = Fonts.bold }
        hr.addView(header, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        hr.addView(navBtn("Сегодня") { cal.timeInMillis = System.currentTimeMillis(); selectedMillis = CalendarRepo.startOfDay(System.currentTimeMillis()); refreshMonth(); refreshDay() })
        hr.addView(navBtn("‹") { shiftMonth(-1) })
        hr.addView(navBtn("›") { shiftMonth(1) })
        root.addView(hr)

        // weekday header
        val wd = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(8f), 0, dp(8f), dp(4f)) }
        listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEachIndexed { i, name ->
            wd.addView(TextView(this).apply {
                text = name; gravity = Gravity.CENTER; textSize = 12f; typeface = Fonts.medium
                setTextColor(if (i >= 5) 0xFF8E8E93.toInt() else 0xFF6C6C70.toInt())
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        root.addView(wd)

        grid = MonthGridView(this).apply {
            onDaySelected = { _, millis -> selectedMillis = millis; refreshDay() }
        }
        root.addView(grid, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        root.addView(View(this).apply { setBackgroundColor(0xFF2C2C2E.toInt()) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).also { it.topMargin = dp(8f) })

        dayList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(4f), 0, dp(4f)) }
        root.addView(ScrollView(this).apply { addView(dayList) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        root.addView(TextView(this).apply {
            text = "+  Новое событие"; setTextColor(accent); textSize = 17f; typeface = Fonts.medium
            setPadding(dp(20f), dp(14f), dp(16f), dp(18f)); isClickable = true
            setOnClickListener {
                startActivity(Intent(this@CalendarActivity, EventEditActivity::class.java)
                    .putExtra("day", selectedMillis))
            }
        })
        return root
    }

    private fun navBtn(label: String, onTap: () -> Unit) = TextView(this).apply {
        text = label; setTextColor(accent); textSize = if (label.length == 1) 26f else 16f; typeface = Fonts.medium
        setPadding(dp(10f), 0, dp(10f), 0); isClickable = true; setOnClickListener { onTap() }
    }

    private fun shiftMonth(delta: Int) { cal.add(Calendar.MONTH, delta); refreshMonth(); }

    private fun refreshMonth() {
        val y = cal.get(Calendar.YEAR); val m = cal.get(Calendar.MONTH)
        header.text = "${monthName(m)} $y"
        // keep selected day within the month
        val selCal = Calendar.getInstance().apply { timeInMillis = selectedMillis }
        val selDay = if (selCal.get(Calendar.YEAR) == y && selCal.get(Calendar.MONTH) == m) selCal.get(Calendar.DAY_OF_MONTH) else 1
        grid.setMonth(y, m, selDay)
        val monthStart = Calendar.getInstance().apply { set(y, m, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val monthEnd = Calendar.getInstance().apply { timeInMillis = monthStart; add(Calendar.MONTH, 1) }.timeInMillis
        grid.setEventDays(repo.daysWithEvents(monthStart, monthEnd))
    }

    private fun refreshDay() {
        dayList.removeAllViews()
        val events = repo.eventsForDay(selectedMillis)
        dayList.addView(TextView(this).apply {
            text = dayHeading(selectedMillis)
            setTextColor(0xFF8E8E93.toInt()); textSize = 13f; typeface = Fonts.medium
            setPadding(dp(16f), dp(10f), dp(16f), dp(6f))
        })
        if (events.isEmpty()) dayList.addView(TextView(this).apply {
            text = "Нет событий"; setTextColor(0xFF6C6C70.toInt()); textSize = 15f; typeface = Fonts.regular
            setPadding(dp(16f), dp(20f), dp(16f), 0); gravity = Gravity.CENTER
        })
        events.forEach { dayList.addView(eventRow(it)) }
    }

    private fun eventRow(e: CalEvent): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; setPadding(dp(16f), dp(10f), dp(16f), dp(10f)); isClickable = true
        setOnClickListener {
            startActivity(Intent(this@CalendarActivity, EventEditActivity::class.java).putExtra("eventId", e.eventId))
        }
        addView(View(this@CalendarActivity).apply {
            background = GradientDrawable().apply { cornerRadius = dp(2f).toFloat(); setColor(if (e.color != 0) e.color else accent) }
        }, LinearLayout.LayoutParams(dp(4f), dp(40f)).also { it.marginEnd = dp(12f) })
        val col = LinearLayout(this@CalendarActivity).apply { orientation = LinearLayout.VERTICAL }
        col.addView(TextView(this@CalendarActivity).apply { text = e.title; setTextColor(Color.WHITE); textSize = 16f; typeface = Fonts.medium })
        col.addView(TextView(this@CalendarActivity).apply {
            text = timeLabel(e) + (if (e.location.isNotEmpty()) " · ${e.location}" else "")
            setTextColor(0xFF8E8E93.toInt()); textSize = 13f; typeface = Fonts.regular
        })
        addView(col)
    }

    private fun timeLabel(e: CalEvent): String {
        if (e.allDay) return "Весь день"
        val f = java.text.SimpleDateFormat("HH:mm", java.util.Locale("ru"))
        return "${f.format(java.util.Date(e.begin))} – ${f.format(java.util.Date(e.end))}"
    }

    private fun dayHeading(millis: Long) =
        java.text.SimpleDateFormat("EEEE, d MMMM", java.util.Locale("ru")).format(java.util.Date(millis)).replaceFirstChar { it.uppercase() }

    private fun monthName(m: Int) = listOf("Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
        "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь")[m]
}
