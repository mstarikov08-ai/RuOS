package com.ruos.clock

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.media.RingtoneManager
import android.media.MediaPlayer
import android.view.*
import android.widget.*
import java.util.*
import kotlin.math.*

// ─── Data ────────────────────────────────────────────────────────────────────

data class WorldCity(
    val name: String,
    val tzId: String,
    val utcOffsetHours: Float
)

data class AlarmItem(
    var id: Int,
    var hour: Int,
    var minute: Int,
    var days: BooleanArray = BooleanArray(7) { false },
    var enabled: Boolean = true,
    var label: String = ""
) {
    val daysText: String get() {
        val names = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
        val active = days.mapIndexedNotNull { i, on -> if (on) names[i] else null }
        return when {
            active.size == 7 -> "Каждый день"
            active == names.subList(0, 5) -> "Пн – Пт"
            active == names.subList(5, 7) -> "Сб – Вс"
            active.isEmpty() -> "Однократно"
            else -> active.joinToString(", ")
        }
    }
}

data class LapItem(val lapNumber: Int, val lapTime: Long, val totalTime: Long)

// ─── Circular track view for stopwatch / timer ───────────────────────────────

class CircularTrackView(context: Context) : View(context) {
    var progress: Float = 0f   // 0.0 – 1.0
    var trackColor: Int = Color.parseColor("#2C2C2E")
    var progressColor: Int = Color.parseColor("#D94F3D")

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val oval = RectF()

    override fun onDraw(canvas: Canvas) {
        val sw = width.toFloat() * 0.06f
        trackPaint.strokeWidth = sw
        trackPaint.color = trackColor
        progressPaint.strokeWidth = sw
        progressPaint.color = progressColor

        val inset = sw / 2f + 4f
        oval.set(inset, inset, width - inset, height - inset)
        canvas.drawArc(oval, -90f, 360f, false, trackPaint)
        if (progress > 0f) {
            canvas.drawArc(oval, -90f, 360f * progress, false, progressPaint)
        }
    }
}

// ─── Scroll picker (iOS-style number wheel) ──────────────────────────────────

class NumberPickerView(context: Context, private val values: List<String>) : View(context) {
    var selectedIndex: Int = 0
        set(v) { field = v.coerceIn(0, values.size - 1); invalidate() }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }
    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8E8E93")
        textAlign = Paint.Align.CENTER
    }
    private var itemH = 0f
    private var startY = 0f
    private var lastY = 0f
    private var isDragging = false
    private var scrollOffset = 0f

    var onValueChanged: ((Int) -> Unit)? = null

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        itemH = h / 5f
        textPaint.textSize = itemH * 0.5f
        dimPaint.textSize = itemH * 0.4f
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val centerY = height / 2f

        for (offset in -2..2) {
            val idx = selectedIndex + offset
            val y = centerY + offset * itemH + scrollOffset + textPaint.textSize / 3f
            if (idx < 0 || idx >= values.size) continue
            val paint = if (offset == 0) textPaint else dimPaint
            paint.alpha = when (abs(offset)) {
                0 -> 255; 1 -> 180; else -> 80
            }
            canvas.drawText(values[idx], cx, y, paint)
        }
        // Center line highlight
        val lineY1 = centerY - itemH / 2f
        val lineY2 = centerY + itemH / 2f
        val linePaint = Paint().apply { color = Color.parseColor("#38383A"); strokeWidth = 1f }
        canvas.drawLine(0f, lineY1, width.toFloat(), lineY1, linePaint)
        canvas.drawLine(0f, lineY2, width.toFloat(), lineY2, linePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startY = event.y; lastY = event.y; isDragging = true
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = event.y - lastY
                scrollOffset += dy
                lastY = event.y
                // Snap partial
                val moved = (scrollOffset / itemH).toInt()
                if (moved != 0) {
                    selectedIndex = (selectedIndex - moved).coerceIn(0, values.size - 1)
                    scrollOffset -= moved * itemH
                    onValueChanged?.invoke(selectedIndex)
                }
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                // Snap to nearest
                val snap = (scrollOffset / itemH).roundToInt()
                selectedIndex = (selectedIndex - snap).coerceIn(0, values.size - 1)
                scrollOffset = 0f
                onValueChanged?.invoke(selectedIndex)
                invalidate()
            }
        }
        return true
    }
}

// ─── Main Activity ────────────────────────────────────────────────────────────

class ClockActivity : Activity() {

    // Colors
    private val colorBg = Color.parseColor("#000000")
    private val colorSurface = Color.parseColor("#1C1C1E")
    private val colorSurface2 = Color.parseColor("#2C2C2E")
    private val colorRed = Color.parseColor("#D94F3D")
    private val colorBlue = Color.parseColor("#0A84FF")
    private val colorText = Color.parseColor("#FFFFFF")
    private val colorSecondary = Color.parseColor("#8E8E93")
    private val colorSeparator = Color.parseColor("#38383A")

    // Tab views
    private lateinit var worldClockView: View
    private lateinit var alarmView: View
    private lateinit var stopwatchView: View
    private lateinit var timerView: View
    private val tabViews get() = listOf(worldClockView, alarmView, stopwatchView, timerView)
    private var currentTab = 0
    private val tabButtons = mutableListOf<LinearLayout>()

    // World clock
    private val cities = listOf(
        WorldCity("Москва", "Europe/Moscow", 3f),
        WorldCity("Санкт-Петербург", "Europe/Moscow", 3f),
        WorldCity("Новосибирск", "Asia/Novosibirsk", 7f),
        WorldCity("Владивосток", "Asia/Vladivostok", 10f),
        WorldCity("Лондон", "Europe/London", 0f),
        WorldCity("Нью-Йорк", "America/New_York", -5f),
        WorldCity("Токио", "Asia/Tokyo", 9f),
        WorldCity("Пекин", "Asia/Shanghai", 8f)
    )
    private val worldClockRows = mutableListOf<TextView>()
    private val worldClockHandler = Handler(Looper.getMainLooper())
    private val worldClockRunnable = object : Runnable {
        override fun run() {
            updateWorldClockTimes()
            worldClockHandler.postDelayed(this, 30_000)
        }
    }

    // Alarms
    private val alarms = mutableListOf(
        AlarmItem(1, 7, 0, BooleanArray(7) { it < 5 }, true, ""),
        AlarmItem(2, 9, 0, BooleanArray(7) { it >= 5 }, false, "")
    )
    private var alarmListContainer: LinearLayout? = null
    private var nextAlarmId = 3

    // Stopwatch
    private var swRunning = false
    private var swStartTime = 0L
    private var swElapsed = 0L
    private var swDisplay: TextView? = null
    private var swLapList: LinearLayout? = null
    private var swStartBtn: TextView? = null
    private var swResetBtn: TextView? = null
    private val laps = mutableListOf<LapItem>()
    private var lapStartElapsed = 0L
    private var swTrack: CircularTrackView? = null
    private val swHandler = Handler(Looper.getMainLooper())
    private val swRunnable = object : Runnable {
        override fun run() {
            if (swRunning) {
                val now = System.currentTimeMillis()
                swElapsed = (now - swStartTime)
                updateStopwatchDisplay()
                swHandler.postDelayed(this, 10)
            }
        }
    }

    // Timer
    private var timerTotalMs = 0L
    private var timerRemaining = 0L
    private var timerRunning = false
    private var timerStartTime = 0L
    private var timerPickerContainer: LinearLayout? = null
    private var timerRunningContainer: LinearLayout? = null
    private var timerDisplay: TextView? = null
    private var timerTrack: CircularTrackView? = null
    private var timerHourPicker: NumberPickerView? = null
    private var timerMinPicker: NumberPickerView? = null
    private var timerSecPicker: NumberPickerView? = null
    private var timerStartBtn: TextView? = null
    private var timerPauseBtn: TextView? = null
    private var timerCancelBtn: TextView? = null
    private var timerHours = 0; private var timerMins = 0; private var timerSecs = 0
    private var mediaPlayer: MediaPlayer? = null
    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (timerRunning) {
                val elapsed = System.currentTimeMillis() - timerStartTime
                timerRemaining = maxOf(0, timerTotalMs - elapsed)
                updateTimerDisplay()
                if (timerRemaining <= 0) {
                    timerRunning = false
                    onTimerFinished()
                } else {
                    timerHandler.postDelayed(this, 100)
                }
            }
        }
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun dp(v: Float): Int = (v * resources.displayMetrics.density).toInt()

    private fun statusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else dp(24)
    }

    private fun navBarHeight(): Int {
        val id = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else dp(34)
    }

    private fun roundRect(color: Int, radius: Float = 12f): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radius.toInt()).toFloat()
            setColor(color)
        }
    }

    // ─── onCreate ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        buildUI()
        switchTab(0)
        worldClockHandler.post(worldClockRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        worldClockHandler.removeCallbacks(worldClockRunnable)
        swHandler.removeCallbacks(swRunnable)
        timerHandler.removeCallbacks(timerRunnable)
        mediaPlayer?.release()
    }

    // ─── Build UI ─────────────────────────────────────────────────────────────

    private fun buildUI() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(colorBg)

        // Top status bar spacer
        val statusSpacer = View(this)
        statusSpacer.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, statusBarHeight()
        )
        root.addView(statusSpacer)

        // Content container
        val contentFrame = FrameLayout(this)
        contentFrame.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        )

        worldClockView = buildWorldClockView()
        alarmView = buildAlarmView()
        stopwatchView = buildStopwatchView()
        timerView = buildTimerView()

        contentFrame.addView(worldClockView)
        contentFrame.addView(alarmView)
        contentFrame.addView(stopwatchView)
        contentFrame.addView(timerView)

        root.addView(contentFrame)

        // Bottom tab bar
        root.addView(buildTabBar())

        setContentView(root)
    }

    private fun buildTabBar(): LinearLayout {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.setBackgroundColor(Color.parseColor("#111111"))
        val navH = navBarHeight()
        bar.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(56) + navH
        )
        bar.setPadding(0, 0, 0, navH)

        val tabDefs = listOf(
            Pair("Мировое", android.R.drawable.ic_menu_mapmode),
            Pair("Будильник", android.R.drawable.ic_lock_idle_alarm),
            Pair("Секундомер", android.R.drawable.ic_menu_recent_history),
            Pair("Таймер", android.R.drawable.ic_menu_rotate)
        )

        tabButtons.clear()
        tabDefs.forEachIndexed { i, (label, icon) ->
            val tab = LinearLayout(this)
            tab.orientation = LinearLayout.VERTICAL
            tab.gravity = Gravity.CENTER
            tab.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            tab.isClickable = true
            tab.isFocusable = true

            val img = ImageView(this)
            img.setImageResource(icon)
            img.layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
            img.scaleType = ImageView.ScaleType.FIT_CENTER
            tab.addView(img)

            val lbl = TextView(this)
            lbl.text = label
            lbl.textSize = 10f
            lbl.gravity = Gravity.CENTER
            lbl.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            tab.addView(lbl)
            tab.tag = Pair(img, lbl)

            tab.setOnClickListener { switchTab(i) }
            bar.addView(tab)
            tabButtons.add(tab)
        }

        return bar
    }

    private fun switchTab(index: Int) {
        currentTab = index
        tabViews.forEachIndexed { i, v -> v.visibility = if (i == index) View.VISIBLE else View.GONE }
        tabButtons.forEachIndexed { i, tab ->
            val (img, lbl) = tab.tag as Pair<*, *>
            val color = if (i == index) colorRed else colorSecondary
            (img as ImageView).setColorFilter(color)
            (lbl as TextView).setTextColor(color)
        }
    }

    // ─── World Clock ──────────────────────────────────────────────────────────

    private fun buildWorldClockView(): View {
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(colorBg)

        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        container.setPadding(dp(16), dp(16), dp(16), dp(16))

        val title = TextView(this)
        title.text = "Мировое время"
        title.textSize = 28f
        title.setTextColor(colorText)
        title.setTypeface(null, android.graphics.Typeface.BOLD)
        title.setPadding(dp(4), 0, 0, dp(16))
        container.addView(title)

        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.background = roundRect(colorSurface)
        card.setPadding(dp(16), 0, dp(16), 0)

        worldClockRows.clear()
        cities.forEachIndexed { i, city ->
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(60)
            )

            val left = LinearLayout(this)
            left.orientation = LinearLayout.VERTICAL
            left.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

            val cityName = TextView(this)
            cityName.text = city.name
            cityName.textSize = 17f
            cityName.setTextColor(colorText)
            left.addView(cityName)

            val diffText = getDiffFromMoscow(city)
            val subText = TextView(this)
            subText.text = diffText
            subText.textSize = 13f
            subText.setTextColor(colorSecondary)
            left.addView(subText)
            row.addView(left)

            val timeText = TextView(this)
            timeText.textSize = 17f
            timeText.setTextColor(colorSecondary)
            timeText.gravity = Gravity.END
            worldClockRows.add(timeText)
            row.addView(timeText)

            card.addView(row)

            if (i < cities.size - 1) {
                val sep = View(this)
                sep.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1
                )
                sep.setBackgroundColor(colorSeparator)
                card.addView(sep)
            }
        }

        container.addView(card)
        updateWorldClockTimes()
        scroll.addView(container)
        return scroll
    }

    private fun getDiffFromMoscow(city: WorldCity): String {
        val moscowOffset = 3f
        val diff = city.utcOffsetHours - moscowOffset
        return when {
            diff == 0f -> "Москва"
            diff > 0 -> "+${diff.toInt()}ч от Москвы"
            else -> "${diff.toInt()}ч от Москвы"
        }
    }

    private fun updateWorldClockTimes() {
        val cal = Calendar.getInstance()
        cities.forEachIndexed { i, city ->
            val tz = TimeZone.getTimeZone(city.tzId)
            val tzCal = Calendar.getInstance(tz)
            val hour = tzCal.get(Calendar.HOUR_OF_DAY)
            val min = tzCal.get(Calendar.MINUTE)
            if (i < worldClockRows.size) {
                worldClockRows[i].text = String.format("%02d:%02d", hour, min)
            }
        }
    }

    // ─── Alarm ────────────────────────────────────────────────────────────────

    private fun buildAlarmView(): View {
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(colorBg)

        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        container.setPadding(dp(16), dp(16), dp(16), dp(16))

        val headerRow = LinearLayout(this)
        headerRow.orientation = LinearLayout.HORIZONTAL
        headerRow.gravity = Gravity.CENTER_VERTICAL
        headerRow.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )

        val title = TextView(this)
        title.text = "Будильник"
        title.textSize = 28f
        title.setTextColor(colorText)
        title.setTypeface(null, android.graphics.Typeface.BOLD)
        title.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        title.setPadding(dp(4), 0, 0, 0)
        headerRow.addView(title)

        val addBtn = TextView(this)
        addBtn.text = "+"
        addBtn.textSize = 28f
        addBtn.setTextColor(colorRed)
        addBtn.setPadding(dp(8), 0, dp(8), 0)
        addBtn.setOnClickListener { showAddAlarmDialog() }
        headerRow.addView(addBtn)

        container.addView(headerRow)

        val spacer = View(this)
        spacer.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(16)
        )
        container.addView(spacer)

        val listContainer = LinearLayout(this)
        listContainer.orientation = LinearLayout.VERTICAL
        alarmListContainer = listContainer
        container.addView(listContainer)
        renderAlarmList()

        scroll.addView(container)
        return scroll
    }

    private fun renderAlarmList() {
        val container = alarmListContainer ?: return
        container.removeAllViews()

        if (alarms.isEmpty()) {
            val empty = TextView(this)
            empty.text = "Нет будильников"
            empty.textSize = 16f
            empty.setTextColor(colorSecondary)
            empty.gravity = Gravity.CENTER
            empty.setPadding(0, dp(48), 0, 0)
            container.addView(empty)
            return
        }

        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.background = roundRect(colorSurface)
        card.setPadding(dp(16), 0, dp(16), 0)

        alarms.forEachIndexed { i, alarm ->
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(80)
            )

            val left = LinearLayout(this)
            left.orientation = LinearLayout.VERTICAL
            left.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

            val timeText = TextView(this)
            timeText.text = String.format("%02d:%02d", alarm.hour, alarm.minute)
            timeText.textSize = 40f
            timeText.setTextColor(if (alarm.enabled) colorText else colorSecondary)
            left.addView(timeText)

            val daysText = TextView(this)
            daysText.text = alarm.daysText
            daysText.textSize = 13f
            daysText.setTextColor(colorSecondary)
            left.addView(daysText)

            row.addView(left)

            // Toggle switch (using a simple TextView as toggle)
            val toggle = createToggle(alarm.enabled) { enabled ->
                alarm.enabled = enabled
                timeText.setTextColor(if (enabled) colorText else colorSecondary)
                if (enabled) scheduleAlarm(alarm) else cancelAlarm(alarm)
            }
            row.addView(toggle)

            row.setOnLongClickListener {
                showDeleteAlarmDialog(alarm)
                true
            }

            card.addView(row)

            if (i < alarms.size - 1) {
                val sep = View(this)
                sep.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1
                )
                sep.setBackgroundColor(colorSeparator)
                card.addView(sep)
            }
        }

        container.addView(card)
    }

    private fun createToggle(initial: Boolean, onChange: (Boolean) -> Unit): View {
        var state = initial
        val toggle = TextView(this)
        toggle.text = if (state) "ВКЛ" else "ВЫКЛ"
        toggle.textSize = 13f
        toggle.setTextColor(if (state) colorRed else colorSecondary)
        toggle.setPadding(dp(8), dp(4), dp(8), dp(4))
        toggle.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(12).toFloat()
            setColor(colorSurface2)
        }
        toggle.setOnClickListener {
            state = !state
            toggle.text = if (state) "ВКЛ" else "ВЫКЛ"
            toggle.setTextColor(if (state) colorRed else colorSecondary)
            onChange(state)
        }
        return toggle
    }

    private fun showAddAlarmDialog() {
        val dialog = android.app.Dialog(this)
        dialog.setContentView(buildAlarmEditor(null) { alarm ->
            alarms.add(alarm)
            if (alarm.enabled) scheduleAlarm(alarm)
            renderAlarmList()
            dialog.dismiss()
        })
        dialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(colorBg))
        dialog.show()
    }

    private fun showDeleteAlarmDialog(alarm: AlarmItem) {
        android.app.AlertDialog.Builder(this)
            .setTitle("Удалить будильник?")
            .setMessage(String.format("%02d:%02d", alarm.hour, alarm.minute))
            .setPositiveButton("Удалить") { _, _ ->
                cancelAlarm(alarm)
                alarms.remove(alarm)
                renderAlarmList()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun buildAlarmEditor(existing: AlarmItem?, onSave: (AlarmItem) -> Unit): View {
        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        container.setBackgroundColor(colorBg)
        container.setPadding(dp(16), statusBarHeight() + dp(16), dp(16), dp(16))

        val topRow = LinearLayout(this)
        topRow.orientation = LinearLayout.HORIZONTAL
        topRow.gravity = Gravity.CENTER_VERTICAL

        val cancelBtn = TextView(this)
        cancelBtn.text = "Отмена"
        cancelBtn.textSize = 17f
        cancelBtn.setTextColor(colorRed)
        cancelBtn.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        topRow.addView(cancelBtn)

        val titleLbl = TextView(this)
        titleLbl.text = if (existing == null) "Новый будильник" else "Изменить"
        titleLbl.textSize = 17f
        titleLbl.setTextColor(colorText)
        titleLbl.setTypeface(null, android.graphics.Typeface.BOLD)
        titleLbl.gravity = Gravity.CENTER
        titleLbl.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        topRow.addView(titleLbl)

        val saveBtn = TextView(this)
        saveBtn.text = "Сохранить"
        saveBtn.textSize = 17f
        saveBtn.setTextColor(colorRed)
        saveBtn.gravity = Gravity.END
        saveBtn.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        topRow.addView(saveBtn)

        container.addView(topRow)

        // Time picker
        val hours = (0..23).map { String.format("%02d", it) }
        val minutes = (0..59).map { String.format("%02d", it) }

        val pickerRow = LinearLayout(this)
        pickerRow.orientation = LinearLayout.HORIZONTAL
        pickerRow.gravity = Gravity.CENTER
        val pickerParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(200)
        )
        pickerParams.topMargin = dp(24)
        pickerRow.layoutParams = pickerParams

        val hourPicker = NumberPickerView(this, hours)
        hourPicker.selectedIndex = existing?.hour ?: 7
        hourPicker.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)

        val colonLbl = TextView(this)
        colonLbl.text = ":"
        colonLbl.textSize = 32f
        colonLbl.setTextColor(colorText)
        colonLbl.gravity = Gravity.CENTER
        colonLbl.layoutParams = LinearLayout.LayoutParams(dp(24), LinearLayout.LayoutParams.MATCH_PARENT)

        val minPicker = NumberPickerView(this, minutes)
        minPicker.selectedIndex = existing?.minute ?: 0
        minPicker.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)

        pickerRow.addView(hourPicker)
        pickerRow.addView(colonLbl)
        pickerRow.addView(minPicker)
        container.addView(pickerRow)

        // Days selector
        val daysRow = LinearLayout(this)
        daysRow.orientation = LinearLayout.HORIZONTAL
        daysRow.gravity = Gravity.CENTER
        val daysParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        daysParams.topMargin = dp(24)
        daysRow.layoutParams = daysParams

        val dayNames = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
        val daySelected = existing?.days?.copyOf() ?: BooleanArray(7) { false }
        val dayBtns = mutableListOf<TextView>()

        dayNames.forEachIndexed { i, name ->
            val btn = TextView(this)
            btn.text = name
            btn.textSize = 13f
            btn.gravity = Gravity.CENTER
            val size = dp(40)
            btn.layoutParams = LinearLayout.LayoutParams(size, size).also {
                if (i > 0) it.leftMargin = dp(4)
            }
            btn.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (daySelected[i]) colorRed else colorSurface2)
            }
            btn.setTextColor(if (daySelected[i]) colorText else colorSecondary)
            btn.setOnClickListener {
                daySelected[i] = !daySelected[i]
                btn.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (daySelected[i]) colorRed else colorSurface2)
                }
                btn.setTextColor(if (daySelected[i]) colorText else colorSecondary)
            }
            dayBtns.add(btn)
            daysRow.addView(btn)
        }
        container.addView(daysRow)

        saveBtn.setOnClickListener {
            val alarm = existing ?: AlarmItem(nextAlarmId++, 0, 0)
            alarm.hour = hourPicker.selectedIndex
            alarm.minute = minPicker.selectedIndex
            alarm.days = daySelected
            alarm.enabled = true
            onSave(alarm)
        }

        return container
    }

    private fun scheduleAlarm(alarm: AlarmItem) {
        val am = getSystemService(ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(this, AlarmReceiver::class.java).apply {
            putExtra("alarm_id", alarm.id)
        }
        val pi = PendingIntent.getBroadcast(
            this, alarm.id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, alarm.hour)
        cal.set(Calendar.MINUTE, alarm.minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
    }

    private fun cancelAlarm(alarm: AlarmItem) {
        val am = getSystemService(ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(this, AlarmReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            this, alarm.id, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pi?.let { am.cancel(it) }
    }

    // ─── Stopwatch ────────────────────────────────────────────────────────────

    private fun buildStopwatchView(): View {
        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        container.setBackgroundColor(colorBg)
        container.setPadding(dp(16), dp(16), dp(16), dp(16))

        val title = TextView(this)
        title.text = "Секундомер"
        title.textSize = 28f
        title.setTextColor(colorText)
        title.setTypeface(null, android.graphics.Typeface.BOLD)
        title.setPadding(dp(4), 0, 0, dp(16))
        container.addView(title)

        // Track + display
        val trackFrame = FrameLayout(this)
        val trackSize = minOf(
            resources.displayMetrics.widthPixels,
            resources.displayMetrics.heightPixels
        ) * 3 / 4
        trackFrame.layoutParams = LinearLayout.LayoutParams(trackSize, trackSize).also {
            it.gravity = Gravity.CENTER_HORIZONTAL
        }

        val track = CircularTrackView(this)
        track.layoutParams = FrameLayout.LayoutParams(trackSize, trackSize)
        swTrack = track
        trackFrame.addView(track)

        val displayContainer = LinearLayout(this)
        displayContainer.orientation = LinearLayout.VERTICAL
        displayContainer.gravity = Gravity.CENTER
        displayContainer.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )

        val display = TextView(this)
        display.text = "00:00.00"
        display.textSize = 52f
        display.setTextColor(colorText)
        display.gravity = Gravity.CENTER
        display.setTypeface(null, android.graphics.Typeface.LIGHT)
        swDisplay = display
        displayContainer.addView(display)

        trackFrame.addView(displayContainer)
        container.addView(trackFrame)

        // Buttons
        val btnRow = LinearLayout(this)
        btnRow.orientation = LinearLayout.HORIZONTAL
        btnRow.gravity = Gravity.CENTER
        val btnParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        btnParams.topMargin = dp(24)
        btnRow.layoutParams = btnParams

        val resetBtn = makeRoundButton("Сброс", colorSurface2)
        swResetBtn = resetBtn as? TextView
        resetBtn.setOnClickListener { onSwReset() }
        btnRow.addView(resetBtn)

        val spacer = View(this)
        spacer.layoutParams = LinearLayout.LayoutParams(dp(48), 1)
        btnRow.addView(spacer)

        val startBtn = makeRoundButton("Старт", colorRed)
        swStartBtn = startBtn as? TextView
        startBtn.setOnClickListener { onSwStartStop() }
        btnRow.addView(startBtn)

        container.addView(btnRow)

        // Laps
        val scroll = ScrollView(this)
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        )
        lp.topMargin = dp(16)
        scroll.layoutParams = lp

        val lapContainer = LinearLayout(this)
        lapContainer.orientation = LinearLayout.VERTICAL
        swLapList = lapContainer
        scroll.addView(lapContainer)
        container.addView(scroll)

        return container
    }

    private fun makeRoundButton(label: String, color: Int): View {
        val btn = TextView(this)
        btn.text = label
        btn.textSize = 18f
        btn.setTextColor(colorText)
        btn.gravity = Gravity.CENTER
        val size = dp(72)
        btn.layoutParams = LinearLayout.LayoutParams(size, size)
        btn.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
        btn.isClickable = true
        btn.isFocusable = true
        return btn
    }

    private fun onSwStartStop() {
        if (swRunning) {
            swRunning = false
            swElapsed += System.currentTimeMillis() - swStartTime
            swStartBtn?.text = "Старт"
            swStartBtn?.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL; setColor(colorRed)
            }
            swResetBtn?.text = "Сброс"
        } else {
            swRunning = true
            swStartTime = System.currentTimeMillis() - swElapsed
            swStartBtn?.text = "Стоп"
            swStartBtn?.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL; setColor(Color.parseColor("#30D158"))
            }
            swResetBtn?.text = "Круг"
            swHandler.post(swRunnable)
        }
    }

    private fun onSwReset() {
        if (swRunning) {
            // Lap
            val total = swElapsed + (System.currentTimeMillis() - swStartTime)
            val lapTime = total - lapStartElapsed
            lapStartElapsed = total
            laps.add(0, LapItem(laps.size + 1, lapTime, total))
            renderLaps()
        } else {
            swRunning = false
            swElapsed = 0
            lapStartElapsed = 0
            laps.clear()
            swDisplay?.text = "00:00.00"
            swTrack?.progress = 0f
            swTrack?.invalidate()
            swStartBtn?.text = "Старт"
            swStartBtn?.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL; setColor(colorRed)
            }
            swResetBtn?.text = "Сброс"
            renderLaps()
        }
    }

    private fun updateStopwatchDisplay() {
        val total = swElapsed
        val cs = (total / 10) % 100
        val secs = (total / 1000) % 60
        val mins = (total / 60000) % 60
        val hrs = total / 3600000
        val text = if (hrs > 0) {
            String.format("%02d:%02d:%02d.%02d", hrs, mins, secs, cs)
        } else {
            String.format("%02d:%02d.%02d", mins, secs, cs)
        }
        swDisplay?.text = text
        // Update arc: use last 60-second cycle
        val progress = ((total % 60000) / 60000f)
        swTrack?.progress = progress
        swTrack?.invalidate()
    }

    private fun renderLaps() {
        val container = swLapList ?: return
        container.removeAllViews()
        laps.forEach { lap ->
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44)
            )
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(dp(8), 0, dp(8), 0)

            val lapLbl = TextView(this)
            lapLbl.text = "Круг ${lap.lapNumber}"
            lapLbl.textSize = 15f
            lapLbl.setTextColor(colorSecondary)
            lapLbl.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            row.addView(lapLbl)

            val lapTimeView = TextView(this)
            lapTimeView.text = formatMs(lap.lapTime)
            lapTimeView.textSize = 15f
            lapTimeView.setTextColor(colorText)
            lapTimeView.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            lapTimeView.gravity = Gravity.CENTER
            row.addView(lapTimeView)

            val totalView = TextView(this)
            totalView.text = formatMs(lap.totalTime)
            totalView.textSize = 15f
            totalView.setTextColor(colorSecondary)
            totalView.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            totalView.gravity = Gravity.END
            row.addView(totalView)

            container.addView(row)

            val sep = View(this)
            sep.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
            sep.setBackgroundColor(colorSeparator)
            container.addView(sep)
        }
    }

    private fun formatMs(ms: Long): String {
        val cs = (ms / 10) % 100
        val secs = (ms / 1000) % 60
        val mins = (ms / 60000) % 60
        return String.format("%02d:%02d.%02d", mins, secs, cs)
    }

    // ─── Timer ────────────────────────────────────────────────────────────────

    private fun buildTimerView(): View {
        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        container.setBackgroundColor(colorBg)
        container.setPadding(dp(16), dp(16), dp(16), dp(16))

        val title = TextView(this)
        title.text = "Таймер"
        title.textSize = 28f
        title.setTextColor(colorText)
        title.setTypeface(null, android.graphics.Typeface.BOLD)
        title.setPadding(dp(4), 0, 0, dp(16))
        container.addView(title)

        // Picker container
        val pickerContainer = LinearLayout(this)
        pickerContainer.orientation = LinearLayout.VERTICAL
        timerPickerContainer = pickerContainer

        val pickerRow = LinearLayout(this)
        pickerRow.orientation = LinearLayout.HORIZONTAL
        pickerRow.gravity = Gravity.CENTER
        pickerRow.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(200)
        )

        val hours = (0..23).map { it.toString() }
        val minutes = (0..59).map { String.format("%02d", it) }
        val seconds = (0..59).map { String.format("%02d", it) }

        val hourPicker = NumberPickerView(this, hours)
        hourPicker.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        hourPicker.onValueChanged = { timerHours = it }
        timerHourPicker = hourPicker
        pickerRow.addView(hourPicker)

        val colonH = TextView(this)
        colonH.text = "ч"
        colonH.textSize = 18f
        colonH.setTextColor(colorSecondary)
        colonH.gravity = Gravity.CENTER
        colonH.layoutParams = LinearLayout.LayoutParams(dp(32), LinearLayout.LayoutParams.MATCH_PARENT)
        pickerRow.addView(colonH)

        val minPicker = NumberPickerView(this, minutes)
        minPicker.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        minPicker.onValueChanged = { timerMins = it }
        timerMinPicker = minPicker
        pickerRow.addView(minPicker)

        val colonM = TextView(this)
        colonM.text = "мин"
        colonM.textSize = 14f
        colonM.setTextColor(colorSecondary)
        colonM.gravity = Gravity.CENTER
        colonM.layoutParams = LinearLayout.LayoutParams(dp(40), LinearLayout.LayoutParams.MATCH_PARENT)
        pickerRow.addView(colonM)

        val secPicker = NumberPickerView(this, seconds)
        secPicker.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        secPicker.onValueChanged = { timerSecs = it }
        timerSecPicker = secPicker
        pickerRow.addView(secPicker)

        val colonS = TextView(this)
        colonS.text = "сек"
        colonS.textSize = 14f
        colonS.setTextColor(colorSecondary)
        colonS.gravity = Gravity.CENTER
        colonS.layoutParams = LinearLayout.LayoutParams(dp(40), LinearLayout.LayoutParams.MATCH_PARENT)
        pickerRow.addView(colonS)

        pickerContainer.addView(pickerRow)

        val startBtn = TextView(this)
        startBtn.text = "Старт"
        startBtn.textSize = 20f
        startBtn.setTextColor(colorText)
        startBtn.gravity = Gravity.CENTER
        val startParams = LinearLayout.LayoutParams(dp(80), dp(80))
        startParams.topMargin = dp(24)
        startParams.gravity = Gravity.CENTER_HORIZONTAL
        startBtn.layoutParams = startParams
        startBtn.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL; setColor(colorRed)
        }
        startBtn.setOnClickListener { startTimer() }
        pickerContainer.addView(startBtn)

        container.addView(pickerContainer)

        // Running container
        val runningContainer = LinearLayout(this)
        runningContainer.orientation = LinearLayout.VERTICAL
        runningContainer.gravity = Gravity.CENTER_HORIZONTAL
        runningContainer.visibility = View.GONE
        timerRunningContainer = runningContainer

        val trackFrame = FrameLayout(this)
        val trackSize = minOf(
            resources.displayMetrics.widthPixels,
            resources.displayMetrics.heightPixels
        ) * 2 / 3
        val trackParams = LinearLayout.LayoutParams(trackSize, trackSize)
        trackParams.gravity = Gravity.CENTER_HORIZONTAL
        trackParams.topMargin = dp(8)
        trackFrame.layoutParams = trackParams

        val track = CircularTrackView(this)
        track.layoutParams = FrameLayout.LayoutParams(trackSize, trackSize)
        timerTrack = track
        trackFrame.addView(track)

        val timerDisplayContainer = LinearLayout(this)
        timerDisplayContainer.gravity = Gravity.CENTER
        timerDisplayContainer.orientation = LinearLayout.VERTICAL
        timerDisplayContainer.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )

        val timerDisp = TextView(this)
        timerDisp.textSize = 52f
        timerDisp.setTextColor(colorText)
        timerDisp.gravity = Gravity.CENTER
        timerDisp.setTypeface(null, android.graphics.Typeface.LIGHT)
        timerDisplay = timerDisp
        timerDisplayContainer.addView(timerDisp)
        trackFrame.addView(timerDisplayContainer)

        runningContainer.addView(trackFrame)

        // Timer buttons
        val timerBtnRow = LinearLayout(this)
        timerBtnRow.orientation = LinearLayout.HORIZONTAL
        timerBtnRow.gravity = Gravity.CENTER
        val timerBtnParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        timerBtnParams.topMargin = dp(24)
        timerBtnRow.layoutParams = timerBtnParams

        val cancelBtn = makeRoundButton("Отмена", colorSurface2)
        cancelBtn.setOnClickListener { cancelTimer() }
        timerCancelBtn = cancelBtn as? TextView
        timerBtnRow.addView(cancelBtn)

        val sp2 = View(this)
        sp2.layoutParams = LinearLayout.LayoutParams(dp(48), 1)
        timerBtnRow.addView(sp2)

        val pauseBtn = makeRoundButton("Пауза", colorSurface2)
        pauseBtn.setOnClickListener { pauseResumeTimer() }
        timerPauseBtn = pauseBtn as? TextView
        timerBtnRow.addView(pauseBtn)

        runningContainer.addView(timerBtnRow)
        container.addView(runningContainer)

        return container
    }

    private fun startTimer() {
        val total = (timerHours * 3600L + timerMins * 60L + timerSecs) * 1000L
        if (total <= 0) return
        timerTotalMs = total
        timerRemaining = total
        timerRunning = true
        timerStartTime = System.currentTimeMillis()
        timerPickerContainer?.visibility = View.GONE
        timerRunningContainer?.visibility = View.VISIBLE
        timerHandler.post(timerRunnable)
    }

    private fun pauseResumeTimer() {
        if (timerRunning) {
            timerRunning = false
            timerRemaining -= System.currentTimeMillis() - timerStartTime
            timerPauseBtn?.text = "Продолжить"
        } else {
            timerRunning = true
            timerStartTime = System.currentTimeMillis()
            timerTotalMs = timerRemaining
            timerStartTime = System.currentTimeMillis()
            timerPauseBtn?.text = "Пауза"
            timerHandler.post(timerRunnable)
        }
    }

    private fun cancelTimer() {
        timerRunning = false
        timerRemaining = 0
        LiveTimer.end(this)
        timerPickerContainer?.visibility = View.VISIBLE
        timerRunningContainer?.visibility = View.GONE
        timerPauseBtn?.text = "Пауза"
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun updateTimerDisplay() {
        val rem = timerRemaining
        val secs = (rem / 1000) % 60
        val mins = (rem / 60000) % 60
        val hrs = rem / 3600000
        val text = if (hrs > 0) {
            String.format("%02d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format("%02d:%02d", mins, secs)
        }
        timerDisplay?.text = text
        val progress = if (timerTotalMs > 0) rem.toFloat() / timerTotalMs.toFloat() else 0f
        timerTrack?.progress = progress
        timerTrack?.invalidate()
        if (timerRunning && rem > 0) LiveTimer.update(this, text, progress)
    }

    private fun onTimerFinished() {
        timerDisplay?.text = "00:00"
        timerTrack?.progress = 0f
        timerTrack?.invalidate()
        timerPauseBtn?.text = "Сброс"
        LiveTimer.end(this)

        // Vibrate
        val vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator
        val pattern = longArrayOf(0, 400, 200, 400, 200, 400)
        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))

        // Play alarm sound
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@ClockActivity, uri)
                setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = false
                prepare()
                start()
            }
        } catch (e: Exception) {
            // ignore
        }
    }
}
