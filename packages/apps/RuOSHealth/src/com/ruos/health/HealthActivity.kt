package com.ruos.health

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HealthActivity : AppCompatActivity(), SensorEventListener {

    private val BG = Color.parseColor("#000000")
    private val SURFACE = Color.parseColor("#1C1C1E")
    private val SURFACE2 = Color.parseColor("#2C2C2E")
    private val RED = Color.parseColor("#D94F3D")
    private val BLUE = Color.parseColor("#0A84FF")
    private val GREEN = Color.parseColor("#30D158")
    private val TEXT_PRIMARY = Color.WHITE
    private val TEXT_SECONDARY = Color.parseColor("#8E8E93")
    private val SEPARATOR = Color.parseColor("#38383A")

    private lateinit var rootFrame: FrameLayout
    private lateinit var contentContainer: FrameLayout
    private lateinit var bottomTabBar: LinearLayout

    private var currentTab = 0
    private var dailySteps = 0
    private val stepGoal = 10000

    private var stepRingView: StepRingView? = null
    private var stepsValueView: TextView? = null

    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null
    private lateinit var prefs: SharedPreferences

    // Date navigation
    private var selectedDate: Calendar = Calendar.getInstance()

    private val stepReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == StepCounterService.ACTION_STEP_UPDATE) {
                val steps = intent.getIntExtra(StepCounterService.EXTRA_STEPS, 0)
                updateStepCount(steps)
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results[Manifest.permission.ACTIVITY_RECOGNITION] == true
        if (granted) {
            initStepSensor()
            startStepService()
        } else {
            Toast.makeText(this, "Разрешение на распознавание активности не предоставлено", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.parseColor("#1A1A1A")

        prefs = getSharedPreferences(StepCounterService.PREFS_NAME, MODE_PRIVATE)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        dailySteps = prefs.getInt(StepCounterService.KEY_DAILY_STEPS, 0)

        rootFrame = FrameLayout(this).apply {
            setBackgroundColor(BG)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        contentContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).also { it.bottomMargin = dp(83) }
        }

        bottomTabBar = buildBottomTabBar()
        rootFrame.addView(contentContainer)
        rootFrame.addView(bottomTabBar)
        setContentView(rootFrame)

        showTab(0)
        checkAndRequestPermissions()
    }

    override fun onResume() {
        super.onResume()
        stepSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        registerReceiver(stepReceiver, IntentFilter(StepCounterService.ACTION_STEP_UPDATE),
            RECEIVER_NOT_EXPORTED)
        // Refresh from prefs
        dailySteps = prefs.getInt(StepCounterService.KEY_DAILY_STEPS, 0)
        updateStepCount(dailySteps)
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        try { unregisterReceiver(stepReceiver) } catch (_: Exception) {}
    }

    private fun checkAndRequestPermissions() {
        val perms = mutableListOf(Manifest.permission.ACTIVITY_RECOGNITION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = perms.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            initStepSensor()
            startStepService()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun initStepSensor() {
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (stepSensor == null) {
            Toast.makeText(this, "Датчик шагов недоступен", Toast.LENGTH_LONG).show()
        } else {
            sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    private fun startStepService() {
        val serviceIntent = Intent(this, StepCounterService::class.java)
        try {
            startForegroundService(serviceIntent)
        } catch (_: Exception) {
            startService(serviceIntent)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_STEP_COUNTER) return
        val totalSteps = event.values[0].toLong()

        val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val baselineDate = prefs.getString(StepCounterService.KEY_BASELINE_DATE, "")
        val baseline = prefs.getLong(StepCounterService.KEY_BASELINE, -1L)

        if (baseline == -1L || baselineDate != today) {
            prefs.edit()
                .putLong(StepCounterService.KEY_BASELINE, totalSteps)
                .putString(StepCounterService.KEY_BASELINE_DATE, today)
                .apply()
            updateStepCount(0)
        } else {
            val steps = (totalSteps - baseline).toInt().coerceAtLeast(0)
            prefs.edit().putInt(StepCounterService.KEY_DAILY_STEPS, steps).apply()
            updateStepCount(steps)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

    private fun updateStepCount(steps: Int) {
        dailySteps = steps
        stepRingView?.steps = steps
        stepsValueView?.text = steps.toString()
        // Update stat cards if visible
        if (currentTab == 0) {
            refreshSummaryStats()
        }
    }

    // ── Bottom Tab Bar ───────────────────────────────────────────────────────

    private fun buildBottomTabBar(): LinearLayout {
        val labels = listOf("Резюме", "Обзор")
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#1A1A1A"))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(83)
            ).also { it.gravity = Gravity.BOTTOM }
            setPadding(0, 0, 0, dp(20))
        }
        labels.forEachIndexed { i, label ->
            val tab = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                isClickable = true
                isFocusable = true
                setOnClickListener { showTab(i) }
            }
            val tv = TextView(this).apply {
                text = label
                textSize = 12f
                setTextColor(if (i == 0) RED else TEXT_SECONDARY)
                gravity = Gravity.CENTER
                tag = "tab_label_$i"
            }
            tab.addView(tv)
            bar.addView(tab)
        }
        return bar
    }

    private fun updateTabColors(active: Int) {
        for (i in 0..1) {
            rootFrame.findViewWithTag<TextView>("tab_label_$i")
                ?.setTextColor(if (i == active) RED else TEXT_SECONDARY)
        }
    }

    private fun showTab(index: Int) {
        currentTab = index
        updateTabColors(index)
        contentContainer.removeAllViews()
        when (index) {
            0 -> contentContainer.addView(buildSummaryTab())
            1 -> contentContainer.addView(buildBrowseTab())
        }
    }

    // ── Tab 1: Summary ───────────────────────────────────────────────────────

    // Keep refs to update card values
    private val statCardRefs = mutableMapOf<String, TextView>()

    private fun buildSummaryTab(): ScrollView {
        statCardRefs.clear()
        val scroll = ScrollView(this).apply {
            setBackgroundColor(BG)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(56), dp(16), dp(16))
        }

        // Header
        root.addView(TextView(this).apply {
            text = "Здоровье"
            textSize = 34f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(TEXT_PRIMARY)
            setPadding(0, dp(8), 0, dp(8))
        })

        // Date navigation
        root.addView(buildDateNavRow())

        // Step ring
        val ringContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(220)
            )
        }
        val ring = StepRingView(this).apply {
            steps = dailySteps
            goal = stepGoal
            layoutParams = FrameLayout.LayoutParams(dp(200), dp(200)).also {
                it.gravity = Gravity.CENTER
            }
        }
        stepRingView = ring
        ringContainer.addView(ring)
        root.addView(ringContainer)

        // Sensor unavailable warning
        if (sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) == null) {
            root.addView(TextView(this).apply {
                text = "Датчик шагов недоступен"
                textSize = 14f
                setTextColor(TEXT_SECONDARY)
                gravity = Gravity.CENTER
                setPadding(0, dp(4), 0, dp(8))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })
        }

        // Stats grid (2 columns)
        root.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(8)
            )
        })
        root.addView(buildStatsGrid())

        scroll.addView(root)
        return scroll
    }

    private fun buildDateNavRow(): LinearLayout {
        val dateFormat = SimpleDateFormat("d MMMM yyyy", Locale("ru"))
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, dp(4), 0, dp(12))
        }

        val prevBtn = TextView(this).apply {
            text = "‹"
            textSize = 24f
            setTextColor(BLUE)
            setPadding(0, 0, dp(16), 0)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                selectedDate.add(Calendar.DAY_OF_YEAR, -1)
                (parent as? LinearLayout)?.let { p ->
                    p.findViewWithTag<TextView>("date_label")?.text =
                        dateFormat.format(selectedDate.time)
                }
            }
        }

        val dateLabel = TextView(this).apply {
            text = dateFormat.format(selectedDate.time)
            textSize = 15f
            setTextColor(TEXT_PRIMARY)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            tag = "date_label"
        }

        val nextBtn = TextView(this).apply {
            text = "›"
            textSize = 24f
            setTextColor(BLUE)
            setPadding(dp(16), 0, 0, 0)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                val now = Calendar.getInstance()
                if (selectedDate.before(now) ||
                    selectedDate.get(Calendar.DAY_OF_YEAR) != now.get(Calendar.DAY_OF_YEAR)) {
                    selectedDate.add(Calendar.DAY_OF_YEAR, 1)
                    (parent as? LinearLayout)?.let { p ->
                        p.findViewWithTag<TextView>("date_label")?.text =
                            dateFormat.format(selectedDate.time)
                    }
                }
            }
        }

        row.addView(prevBtn)
        row.addView(dateLabel)
        row.addView(nextBtn)
        return row
    }

    private fun buildStatsGrid(): LinearLayout {
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        data class StatItem(
            val key: String,
            val title: String,
            val iconType: String,
            val iconColor: Int,
            val unit: String,
            val value: () -> String
        )

        val stats = listOf(
            StatItem("activity", "Активность", "flame", Color.parseColor("#FF9F0A"), "ккал") {
                String.format("%.0f", dailySteps * 0.05f)
            },
            StatItem("distance", "Дистанция", "pin", Color.parseColor("#30D158"), "км") {
                String.format("%.2f", dailySteps * 0.0008f)
            },
            StatItem("exercise", "Упражнения", "clock", Color.parseColor("#0A84FF"), "мин") {
                String.format("%.0f", dailySteps * 0.01f)
            },
            StatItem("sleep", "Сон", "moon", Color.parseColor("#BF5AF2"), "") {
                "8ч 20м"
            },
            StatItem("heart", "Пульс", "heart", Color.parseColor("#D94F3D"), "уд/мин") {
                "72"
            },
            StatItem("stand", "Стояние", "person", Color.parseColor("#32ADE6"), "ч") {
                "6"
            }
        )

        var row: LinearLayout? = null
        stats.forEachIndexed { i, stat ->
            if (i % 2 == 0) {
                row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).also { if (i > 0) it.topMargin = dp(12) }
                }
                grid.addView(row)
            }
            val card = buildStatCard(stat.key, stat.title, stat.iconType, stat.iconColor, stat.value(), stat.unit)
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            if (i % 2 == 1) lp.leftMargin = dp(12)
            card.layoutParams = lp
            row?.addView(card)
        }
        // Fill last odd
        if (stats.size % 2 == 1) {
            row?.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
            })
        }
        return grid
    }

    private fun buildStatCard(key: String, title: String, iconType: String, iconColor: Int,
                               value: String, unit: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = GradientDrawable().apply {
                setColor(SURFACE)
                cornerRadius = dp(16).toFloat()
            }

            // Title row
            val titleRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val iconSize = dp(20)
            titleRow.addView(ImageView(context).apply {
                setImageDrawable(iconDrawable(iconType, iconColor))
                layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).also {
                    it.marginEnd = dp(6)
                }
            })
            titleRow.addView(TextView(context).apply {
                text = title
                textSize = 13f
                setTextColor(TEXT_SECONDARY)
            })
            addView(titleRow)

            // Value
            val valueView = TextView(context).apply {
                text = value
                textSize = 28f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(RED)
                setPadding(0, dp(8), 0, 0)
                tag = "stat_value_$key"
            }
            statCardRefs[key] = valueView
            addView(valueView)

            // Unit
            if (unit.isNotEmpty()) {
                addView(TextView(context).apply {
                    text = unit
                    textSize = 12f
                    setTextColor(TEXT_SECONDARY)
                })
            }
        }
    }

    private fun refreshSummaryStats() {
        statCardRefs["activity"]?.text = String.format("%.0f", dailySteps * 0.05f)
        statCardRefs["distance"]?.text = String.format("%.2f", dailySteps * 0.0008f)
        statCardRefs["exercise"]?.text = String.format("%.0f", dailySteps * 0.01f)
    }

    // ── Tab 2: Browse ────────────────────────────────────────────────────────

    private fun buildBrowseTab(): ScrollView {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(BG)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(56), dp(16), dp(16))
        }

        root.addView(TextView(this).apply {
            text = "Обзор"
            textSize = 34f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(TEXT_PRIMARY)
            setPadding(0, dp(8), 0, dp(20))
        })

        // Categories
        data class HealthCategory(
            val name: String,
            val color: Int,
            val iconType: String,
            val items: List<Pair<String, String>>
        )

        val categories = listOf(
            HealthCategory("Тело", Color.parseColor("#30D158"), "scale", listOf(
                "Вес" to "—",
                "ИМТ" to "—",
                "Рост" to "—"
            )),
            HealthCategory("Активность", RED, "runner", listOf(
                "Шаги" to "$dailySteps шаг.",
                "Ходьба + бег" to String.format("%.2f км", dailySteps * 0.0008f),
                "Активные калории" to String.format("%.0f ккал", dailySteps * 0.05f)
            )),
            HealthCategory("Сон", BLUE, "moon", listOf(
                "Время сна" to "8ч 20м",
                "Глубокий сон" to "1ч 45м",
                "Фаза REM" to "2ч 10м"
            )),
            HealthCategory("Питание", Color.parseColor("#FFD60A"), "apple", listOf(
                "Калории" to "—",
                "Белки" to "—",
                "Углеводы" to "—"
            )),
            HealthCategory("Психическое здоровье", Color.parseColor("#BF5AF2"), "brain", listOf(
                "Осознанность" to "—",
                "Уровень стресса" to "—"
            )),
            HealthCategory("Сердце", RED, "heart", listOf(
                "Пульс" to "72 уд/мин",
                "Вариабельность ЧСС" to "—"
            ))
        )

        categories.forEach { cat ->
            root.addView(buildCategorySection(cat.name, cat.color, cat.iconType, cat.items))
            root.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(16)
                )
            })
        }

        scroll.addView(root)
        return scroll
    }

    private fun buildCategorySection(
        name: String,
        color: Int,
        iconType: String,
        items: List<Pair<String, String>>
    ): LinearLayout {
        val section = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(8))
        }
        val hIconSize = dp(22)
        header.addView(ImageView(this).apply {
            setImageDrawable(iconDrawable(iconType, color))
            layoutParams = LinearLayout.LayoutParams(hIconSize, hIconSize).also {
                it.marginEnd = dp(8)
            }
        })
        header.addView(TextView(this).apply {
            text = name
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(color)
        })
        section.addView(header)

        // Items card
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(SURFACE)
                cornerRadius = dp(12).toFloat()
            }
        }
        items.forEachIndexed { i, (label, value) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(12), dp(16), dp(12))
            }
            row.addView(TextView(this).apply {
                text = label
                textSize = 15f
                setTextColor(TEXT_PRIMARY)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(TextView(this).apply {
                text = value
                textSize = 15f
                setTextColor(TEXT_SECONDARY)
            })
            card.addView(row)
            if (i < items.size - 1) {
                card.addView(View(this).apply {
                    setBackgroundColor(SEPARATOR)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
                    ).also { it.leftMargin = dp(16) }
                })
            }
        }
        section.addView(card)
        return section
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(),
            resources.displayMetrics).toInt()

    private fun iconDrawable(type: String, tint: Int): android.graphics.drawable.Drawable {
        return object : android.graphics.drawable.Drawable() {
            override fun draw(canvas: android.graphics.Canvas) {
                val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = tint; style = android.graphics.Paint.Style.FILL
                }
                val b = bounds; val cx = b.exactCenterX(); val cy = b.exactCenterY()
                val r = b.width() * 0.38f
                when (type) {
                    "flame" -> {
                        val path = android.graphics.Path()
                        path.moveTo(cx, b.top.toFloat())
                        path.cubicTo(cx + r * 1.2f, cy - r, cx + r, cy + r * 0.5f, cx, b.bottom.toFloat())
                        path.cubicTo(cx - r, cy + r * 0.5f, cx - r * 1.2f, cy - r, cx, b.top.toFloat())
                        canvas.drawPath(path, p)
                    }
                    "pin" -> {
                        canvas.drawCircle(cx, cy - r * 0.3f, r * 0.8f, p)
                        val path = android.graphics.Path()
                        path.moveTo(cx - r * 0.4f, cy + r * 0.3f)
                        path.lineTo(cx, b.bottom.toFloat())
                        path.lineTo(cx + r * 0.4f, cy + r * 0.3f)
                        canvas.drawPath(path, p)
                    }
                    "clock" -> {
                        p.style = android.graphics.Paint.Style.STROKE
                        p.strokeWidth = b.width() * 0.08f
                        canvas.drawCircle(cx, cy, r, p)
                        p.style = android.graphics.Paint.Style.FILL
                        canvas.drawLine(cx, cy, cx, cy - r * 0.7f, p)
                        canvas.drawLine(cx, cy, cx + r * 0.5f, cy, p)
                    }
                    "moon" -> {
                        canvas.drawCircle(cx, cy, r, p)
                        p.color = android.graphics.Color.parseColor("#1C1C1E")
                        canvas.drawCircle(cx + r * 0.35f, cy - r * 0.2f, r * 0.75f, p)
                    }
                    "heart" -> {
                        val path = android.graphics.Path()
                        path.moveTo(cx, b.bottom.toFloat() - b.height() * 0.1f)
                        path.cubicTo(b.left.toFloat(), cy, b.left.toFloat(), b.top.toFloat(), cx, cy - r * 0.2f)
                        path.cubicTo(b.right.toFloat(), b.top.toFloat(), b.right.toFloat(), cy, cx, b.bottom.toFloat() - b.height() * 0.1f)
                        canvas.drawPath(path, p)
                    }
                    "person" -> {
                        canvas.drawCircle(cx, cy - r * 0.5f, r * 0.55f, p)
                        val rr = android.graphics.RectF(cx - r, cy + r * 0.1f, cx + r, b.bottom.toFloat())
                        canvas.drawArc(rr, 0f, 180f, true, p)
                    }
                    "scale" -> {
                        p.strokeWidth = b.width() * 0.07f
                        p.style = android.graphics.Paint.Style.STROKE
                        canvas.drawLine(b.left.toFloat() + b.width() * 0.1f, cy - r * 0.3f, b.right.toFloat() - b.width() * 0.1f, cy - r * 0.3f, p)
                        canvas.drawLine(cx, cy - r * 0.3f, cx, b.bottom.toFloat() - b.height() * 0.05f, p)
                        p.style = android.graphics.Paint.Style.FILL
                        canvas.drawCircle(b.left.toFloat() + b.width() * 0.25f, cy + r * 0.2f, r * 0.5f, p)
                        canvas.drawCircle(b.right.toFloat() - b.width() * 0.25f, cy + r * 0.2f, r * 0.5f, p)
                    }
                    "runner" -> {
                        canvas.drawCircle(cx + r * 0.2f, b.top.toFloat() + b.height() * 0.15f, r * 0.35f, p)
                        val path = android.graphics.Path()
                        path.moveTo(cx, b.top.toFloat() + b.height() * 0.3f)
                        path.lineTo(cx - r * 0.5f, cy)
                        path.lineTo(cx - r * 0.9f, b.bottom.toFloat())
                        path.moveTo(cx, b.top.toFloat() + b.height() * 0.3f)
                        path.lineTo(cx + r * 0.6f, cy + r * 0.3f)
                        path.lineTo(cx + r * 0.3f, b.bottom.toFloat())
                        p.style = android.graphics.Paint.Style.STROKE
                        p.strokeWidth = b.width() * 0.08f
                        canvas.drawPath(path, p)
                    }
                    "apple" -> {
                        val path = android.graphics.Path()
                        path.addOval(android.graphics.RectF(cx - r, cy - r * 0.3f, cx + r, b.bottom.toFloat() - b.height() * 0.05f), android.graphics.Path.Direction.CW)
                        canvas.drawPath(path, p)
                        p.style = android.graphics.Paint.Style.STROKE
                        p.strokeWidth = b.width() * 0.07f
                        canvas.drawLine(cx, cy - r * 0.3f, cx, b.top.toFloat() + b.height() * 0.05f, p)
                    }
                    "brain" -> {
                        p.style = android.graphics.Paint.Style.STROKE
                        p.strokeWidth = b.width() * 0.08f
                        canvas.drawOval(android.graphics.RectF(b.left.toFloat() + b.width() * 0.05f, b.top.toFloat() + b.height() * 0.1f, cx + b.width() * 0.1f, b.bottom.toFloat() - b.height() * 0.1f), p)
                        canvas.drawOval(android.graphics.RectF(cx - b.width() * 0.1f, b.top.toFloat() + b.height() * 0.1f, b.right.toFloat() - b.width() * 0.05f, b.bottom.toFloat() - b.height() * 0.1f), p)
                        canvas.drawLine(cx, b.top.toFloat() + b.height() * 0.1f, cx, b.bottom.toFloat() - b.height() * 0.1f, p)
                    }
                    else -> canvas.drawCircle(cx, cy, r, p)
                }
            }
            override fun setAlpha(a: Int) {}
            override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
            @Suppress("OVERRIDE_DEPRECATION")
            override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
        }
    }
}
