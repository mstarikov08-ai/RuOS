package com.ruos.standby.ui

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.ruos.standby.model.NightMode
import com.ruos.standby.model.StandbySettings
import com.ruos.standby.service.StandbyManagerService
import com.ruos.standby.util.Fonts
import com.ruos.standby.util.Haptics
import com.ruos.standby.widget.AnalogClockView
import com.ruos.standby.widget.PhotoShuffleView
import com.ruos.standby.widget.WidgetKind
import com.ruos.standby.widget.WidgetSlot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The StandBy takeover: pure-black, three swipeable pages (clock styles · widget
 * stack · photo shuffle). The ambient-light sensor dims the screen in the dark and
 * switches to a red night-vision tint; touching shows full brightness for 30s;
 * unplugging exits immediately.
 */
class StandbyActivity : Activity() {

    private lateinit var settings: StandbySettings
    private lateinit var root: FrameLayout
    private lateinit var pager: StandbyPager
    private val handler = Handler(Looper.getMainLooper())

    private var sensorManager: SensorManager? = null
    private var currentLux = 50f
    private var touchBrightUntil = 0L

    private var clockStyle = 0
    private val clockTick = object : Runnable { override fun run() { refreshClock(); handler.postDelayed(this, 1000) } }
    private lateinit var clockHolder: FrameLayout

    private val exitReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { finish() }
    }
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            val plugged = (i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
            if (!plugged) finish()
        }
    }
    private val lightListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) { currentLux = e.values[0]; applyAmbient() }
        override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = StandbySettings(this)
        clockStyle = settings.clockStyle
        setShowWhenLocked(true); setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)

        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        pager = StandbyPager(this)
        pager.addView(buildClockPage())
        pager.addView(buildWidgetPage())
        pager.addView(PhotoShuffleView(this))
        pager.onPageChanged = { settings.lastPage = it }
        root.addView(pager, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        setContentView(root)
        pager.post { pager.setPage(settings.lastPage.coerceIn(0, 2)) }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(exitReceiver, IntentFilter(StandbyManagerService.ACTION_EXIT), RECEIVER_EXPORTED)
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        sensorManager = getSystemService(SensorManager::class.java)
        sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)?.let {
            sensorManager?.registerListener(lightListener, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        handler.post(clockTick)
        applyAmbient()
    }

    override fun onPause() {
        super.onPause()
        runCatching { unregisterReceiver(exitReceiver) }
        runCatching { unregisterReceiver(batteryReceiver) }
        sensorManager?.unregisterListener(lightListener)
        handler.removeCallbacks(clockTick)
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        touchBrightUntil = System.currentTimeMillis() + 30_000
        applyAmbient()
    }

    // ── Ambient brightness + night-vision tint ────────────────────────────────

    private fun applyAmbient() {
        val touchActive = System.currentTimeMillis() < touchBrightUntil
        // Map lux → brightness; very dim in a dark room, bright when touched.
        val target = when {
            touchActive -> 1f
            currentLux < 3f -> 0.03f
            currentLux < 15f -> 0.12f
            currentLux < 60f -> 0.35f
            else -> 0.6f
        }
        window.attributes = window.attributes.apply { screenBrightness = target }

        val night = when (settings.nightMode) {
            NightMode.ALWAYS -> true
            NightMode.NEVER -> false
            NightMode.AUTOMATIC -> currentLux < 3f
        }
        applyNight(night)
    }

    private fun applyNight(on: Boolean) {
        if (on) {
            // Map everything to red — night-vision, like iOS StandBy in the dark.
            val m = ColorMatrix(floatArrayOf(
                0.5f, 0.4f, 0.1f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f))
            val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(m) }
            root.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
        } else {
            root.setLayerType(View.LAYER_TYPE_NONE, null)
        }
    }

    // ── Page 1: clock styles ──────────────────────────────────────────────────

    private fun buildClockPage(): View {
        val d = resources.displayMetrics.density
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setPadding((36 * d).toInt(), 0, (36 * d).toInt(), 0)
        }
        clockHolder = FrameLayout(this).apply {
            isClickable = true
            setOnClickListener {
                clockStyle = (clockStyle + 1) % 3; settings.clockStyle = clockStyle
                Haptics.select(it); refreshClock()
            }
        }
        page.addView(clockHolder, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 2f))

        // Right: date / calendar card.
        val dateCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
        dateCol.addView(TextView(this).apply {
            text = SimpleDateFormat("EEEE", Locale("ru")).format(Date()).replaceFirstChar { it.uppercase() }
            setTextColor(Color.parseColor("#D94F3D")); textSize = 18f; typeface = Fonts.medium
        })
        dateCol.addView(TextView(this).apply {
            text = SimpleDateFormat("d", Locale("ru")).format(Date())
            setTextColor(Color.WHITE); textSize = 64f; typeface = Fonts.thin
        })
        dateCol.addView(TextView(this).apply {
            text = SimpleDateFormat("MMMM", Locale("ru")).format(Date()).replaceFirstChar { it.uppercase() }
            setTextColor(Color.parseColor("#C8D0E0")); textSize = 16f
        })
        page.addView(dateCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        refreshClock()
        return page
    }

    private fun refreshClock() {
        if (!::clockHolder.isInitialized) return
        clockHolder.removeAllViews()
        when (clockStyle) {
            1 -> clockHolder.addView(AnalogClockView(this), FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT).also { it.gravity = Gravity.CENTER })
            2 -> clockHolder.addView(worldClock())
            else -> clockHolder.addView(digitalClock())
        }
    }

    private fun digitalClock(): View = TextView(this).apply {
        text = SimpleDateFormat("HH:mm", Locale("ru")).format(Date())
        setTextColor(Color.WHITE); textSize = 96f; typeface = Fonts.thin; gravity = Gravity.CENTER
        layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            .also { it.gravity = Gravity.CENTER }
    }

    private fun worldClock(): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        listOf("Москва" to "Europe/Moscow", "Лондон" to "Europe/London", "Нью-Йорк" to "America/New_York").forEach { (name, tz) ->
            val fmt = SimpleDateFormat("HH:mm", Locale("ru")).apply { timeZone = TimeZone.getTimeZone(tz) }
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(TextView(this@StandbyActivity).apply {
                text = name; setTextColor(Color.parseColor("#C8D0E0")); textSize = 18f
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(TextView(this@StandbyActivity).apply {
                text = fmt.format(Date()); setTextColor(Color.WHITE); textSize = 34f; typeface = Fonts.thin
            })
            col.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .also { it.bottomMargin = (12 * resources.displayMetrics.density).toInt() })
        }
        return col
    }

    // ── Page 2: widget stack ──────────────────────────────────────────────────

    private fun buildWidgetPage(): View {
        val d = resources.displayMetrics.density
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding((24 * d).toInt(), (24 * d).toInt(), (24 * d).toInt(), (24 * d).toInt())
        }
        val kinds = WidgetKind.values()
        val top = WidgetSlot(this, kinds[settings.topWidget.coerceIn(0, kinds.size - 1)]) {
            settings.topWidget = kinds.indexOf(it)
        }
        val bottom = WidgetSlot(this, kinds[settings.bottomWidget.coerceIn(0, kinds.size - 1)]) {
            settings.bottomWidget = kinds.indexOf(it)
        }
        col.addView(top, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).also { it.bottomMargin = (16 * d).toInt() })
        col.addView(bottom, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        return col
    }
}
