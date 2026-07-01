package com.android.systemui.ruos

import android.app.KeyguardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.format.DateFormat
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import java.util.Calendar
import java.util.Timer
import java.util.TimerTask

/**
 * RuOS Lock Screen.
 *
 * Visual layers (back to front):
 *   1. Deep space wallpaper with parallax (driven by accelerometer)
 *   2. Time: font weight 100 (thin), 80sp, centred
 *   3. Date: capitalised, below time
 *   4. Notification cards (frosted glass groups)
 *   5. Camera + Flashlight buttons (bottom corners)
 *   6. Swipe-up-to-unlock affordance
 *
 * Unlock: swipe up from anywhere → spring animation → KeyguardManager.requestDismissKeyguard()
 * Face unlock: handled by BiometricManager callback (call onFaceUnlocked() from BiometricService)
 */
class LockScreenView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs), SensorEventListener {

    private val density = resources.displayMetrics.density
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    // Parallax offset applied to wallpaper
    private var parallaxX = 0f
    private var parallaxY = 0f

    // Time + date
    private val timeLabel = TextView(context).apply {
        textSize = 80f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL))
        setShadowLayer(8f, 0f, 2f, Color.argb(80, 0, 0, 0))
    }
    private val dateLabel = TextView(context).apply {
        textSize = 16f
        setTextColor(Color.argb(220, 255, 255, 255))
        gravity = Gravity.CENTER
        letterSpacing = 0.04f
    }

    // Extra lock-screen widgets (battery / next alarm / weather), shown per user config.
    private val widgetLabel = TextView(context).apply {
        textSize = 14f
        setTextColor(Color.argb(200, 255, 255, 255))
        gravity = Gravity.CENTER
        letterSpacing = 0.02f
    }

    // Live-updates when the user changes the clock style / widgets in Settings.
    private val settingsObserver = object : android.database.ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { applyClockStyle(); updateClock() }
    }

    // Bottom buttons
    private val cameraBtn = lockActionButton(context, android.R.drawable.ic_menu_camera)
    private val flashBtn = lockActionButton(context, android.R.drawable.ic_menu_view)

    // Unlock swipe
    private val unlockHint = TextView(context).apply {
        text = "Смахните вверх, чтобы разблокировать"
        setTextColor(Color.argb(160, 255, 255, 255))
        textSize = 13f
        gravity = Gravity.CENTER
    }

    private var velocityTracker: VelocityTracker? = null
    private var downY = 0f
    private var isUnlocking = false

    private val clockTimer = Timer()
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        setBackgroundColor(Color.BLACK)
        setWillNotDraw(false)
        clipChildren = false

        // Time + date centred in upper third
        val clockCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        clockCol.addView(timeLabel, LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        clockCol.addView(dateLabel, LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        clockCol.addView(widgetLabel, LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        addView(clockCol, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).also {
            it.gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
            it.topMargin = (120 * density).toInt()
        })

        // Bottom row
        val bottomRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            setPadding((24 * density).toInt(), 0, (24 * density).toInt(), (48 * density).toInt())
        }
        bottomRow.addView(cameraBtn, LinearLayout.LayoutParams((56 * density).toInt(), (56 * density).toInt()).also {
            it.weight = 1f
            it.gravity = Gravity.START
        })
        bottomRow.addView(unlockHint, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f).also {
            it.gravity = Gravity.CENTER
        })
        bottomRow.addView(flashBtn, LinearLayout.LayoutParams((56 * density).toInt(), (56 * density).toInt()).also {
            it.weight = 1f
            it.gravity = Gravity.END
        })
        addView(bottomRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).also {
            it.gravity = Gravity.BOTTOM
        })

        clockTimer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() { mainHandler.post { updateClock() } }
        }, 0, 1000)

        applyClockStyle()
        updateClock()
    }

    /** Apply the user's chosen clock style (font weight + size) from Settings.Secure. */
    private fun applyClockStyle() {
        val style = LockScreenStyle.clockStyle(context)
        timeLabel.typeface = LockScreenStyle.clockTypeface(style)
        timeLabel.textSize = LockScreenStyle.clockSizeSp(style)
    }

    private fun updateClock() {
        val cal = Calendar.getInstance()
        val use24 = DateFormat.is24HourFormat(context)
        val hour = if (use24) cal.get(Calendar.HOUR_OF_DAY) else {
            val h = cal.get(Calendar.HOUR)
            if (h == 0) 12 else h
        }
        val min = String.format("%02d", cal.get(Calendar.MINUTE))
        timeLabel.text = "$hour:$min"

        val dayName = DateFormat.format("EEEE", cal).toString().uppercase()
        val dayDate = DateFormat.format("d MMMM", cal).toString()
        dateLabel.text = "$dayName, $dayDate"

        renderWidgets()
    }

    /** Render the user-selected lock-screen widgets (battery, next alarm, weather) as a subtitle. */
    private fun renderWidgets() {
        val widgets = LockScreenStyle.widgets(context)
        val parts = ArrayList<String>()
        for (w in widgets) when (w) {
            LockScreenStyle.WIDGET_BATTERY -> batteryText()?.let { parts.add(it) }
            LockScreenStyle.WIDGET_ALARM -> nextAlarmText()?.let { parts.add(it) }
            LockScreenStyle.WIDGET_WEATHER -> weatherText()?.let { parts.add(it) }
            // WIDGET_DATE is already the dateLabel; nothing extra here.
        }
        if (parts.isEmpty()) {
            widgetLabel.visibility = GONE
        } else {
            widgetLabel.visibility = VISIBLE
            widgetLabel.text = parts.joinToString("   ·   ")
        }
    }

    private fun batteryText(): String? = runCatching {
        val bm = context.getSystemService(android.os.BatteryManager::class.java) ?: return null
        val level = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (level in 0..100) "$level %" else null
    }.getOrNull()

    private fun nextAlarmText(): String? = runCatching {
        val am = context.getSystemService(android.app.AlarmManager::class.java) ?: return null
        val next = am.nextAlarmClock ?: return null
        val t = DateFormat.format("HH:mm", next.triggerTime).toString()
        "⏰ $t"
    }.getOrNull()

    private fun weatherText(): String? = runCatching {
        // Read the last cached temperature RuOSWeather stored in Settings.Secure (if present).
        Settings.Secure.getString(context.contentResolver, "ruos_weather_now")?.takeIf { it.isNotBlank() }
    }.getOrNull()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME)
        runCatching {
            val cr = context.contentResolver
            cr.registerContentObserver(Settings.Secure.getUriFor(LockScreenStyle.KEY_CLOCK_STYLE), false, settingsObserver)
            cr.registerContentObserver(Settings.Secure.getUriFor(LockScreenStyle.KEY_WIDGETS), false, settingsObserver)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        sensorManager.unregisterListener(this)
        clockTimer.cancel()
        runCatching { context.contentResolver.unregisterContentObserver(settingsObserver) }
    }

    // Parallax from accelerometer
    override fun onSensorChanged(event: SensorEvent) {
        val ax = event.values[0]
        val ay = event.values[1]
        parallaxX = (-ax * 6f * density).coerceIn(-24f * density, 24f * density)
        parallaxY = (-ay * 6f * density).coerceIn(-24f * density, 24f * density)
        // Apply to wallpaper layer — in production this moves the WallpaperEngine offset
        // translationX / Y on this view gives a cheap approximation
        translationX = parallaxX * 0.04f
        translationY = parallaxY * 0.04f
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain()
                velocityTracker?.addMovement(event)
                downY = event.y
                isUnlocking = false
            }
            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                val dy = event.y - downY
                if (dy < -20f) {
                    isUnlocking = true
                    // Drag the unlock UI up
                    val progress = (-dy / (height * 0.4f)).coerceIn(0f, 1f)
                    translationY = dy * 0.3f
                    alpha = 1f - progress * 0.4f
                }
            }
            MotionEvent.ACTION_UP -> {
                velocityTracker?.addMovement(event)
                velocityTracker?.computeCurrentVelocity(1000)
                val vy = velocityTracker?.yVelocity ?: 0f
                velocityTracker?.recycle()
                velocityTracker = null

                val dy = event.y - downY
                if (isUnlocking && (vy < -800f || dy < -height * 0.3f)) {
                    performUnlock()
                } else {
                    SpringAnimation(this, SpringAnimation.TRANSLATION_Y, 0f).apply {
                        spring.stiffness = SpringForce.STIFFNESS_MEDIUM
                        spring.dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
                        start()
                    }
                    animate().alpha(1f).setDuration(200).start()
                }
                isUnlocking = false
            }
        }
        return true
    }

    private fun performUnlock() {
        performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        animate()
            .translationY(-height.toFloat())
            .alpha(0f)
            .setDuration(320)
            .withEndAction {
                val km = context.getSystemService(KeyguardManager::class.java)
                // In SystemUI, dismissal is done through KeyguardDismissCallback
                // This is the hook point; actual dismiss is via IStatusBarService
            }
            .start()
    }

    fun onFaceUnlocked() {
        // Called by BiometricManager when face recognised
        performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        performUnlock()
    }

    private fun lockActionButton(context: Context, iconRes: Int) = ImageButton(context).apply {
        setImageResource(iconRes)
        setBackgroundColor(Color.argb(80, 255, 255, 255))
        setColorFilter(Color.WHITE)
        clipToOutline = true
        outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(Color.argb(80, 255, 255, 255))
        }
    }
}
