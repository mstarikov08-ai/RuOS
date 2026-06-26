package com.ruos.demo

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import java.util.Calendar
import java.util.Timer
import java.util.TimerTask

class LockScreenDemoActivity : Activity(), SensorEventListener {

    private val handler = Handler(Looper.getMainLooper())
    private val clockTimer = Timer()
    private lateinit var timeLabel: TextView
    private lateinit var dateLabel: TextView
    private lateinit var rootView: View
    private lateinit var sensorManager: SensorManager

    private var velocityTracker: VelocityTracker? = null
    private var downY = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setDecorFitsSystemWindows(false)
        window.insetsController?.apply {
            hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        sensorManager = getSystemService(SensorManager::class.java)
        setContentView(buildUI())

        clockTimer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() { handler.post { updateClock() } }
        }, 0, 1000)
    }

    override fun onResume() {
        super.onResume()
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        clockTimer.cancel()
    }

    private fun buildUI(): View {
        rootView = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#050510"))
        }
        val root = rootView as FrameLayout

        // Deep space stars (simulated with random dots painted)
        root.addView(StarFieldView(this), FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)

        // Clock col
        val clockCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        timeLabel = TextView(this).apply {
            textSize = 80f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL))
            setShadowLayer(12f, 0f, 2f, Color.argb(60, 0, 0, 0))
        }
        dateLabel = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.argb(220, 255, 255, 255))
            gravity = Gravity.CENTER
            letterSpacing = 0.04f
        }

        clockCol.addView(timeLabel, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        clockCol.addView(dateLabel, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        updateClock()

        root.addView(clockCol, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).also {
            it.gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
            it.topMargin = dp(160)
        })

        // Bottom: camera | unlock hint | flashlight
        val bottomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(28), 0, dp(28), dp(48))
        }
        bottomRow.addView(circleButton("📷"), LinearLayout.LayoutParams(dp(56), dp(56)).also { it.weight = 0f })
        bottomRow.addView(TextView(this).apply {
            text = "Смахните вверх, чтобы разблокировать"
            setTextColor(Color.argb(140, 255, 255, 255))
            textSize = 12f
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bottomRow.addView(circleButton("🔦"), LinearLayout.LayoutParams(dp(56), dp(56)).also { it.weight = 0f })

        root.addView(bottomRow, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).also { it.gravity = Gravity.BOTTOM })

        // Touch to unlock
        root.setOnTouchListener { _, event ->
            handleSwipeUp(event)
            true
        }

        return root
    }

    private fun handleSwipeUp(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain()
                velocityTracker?.addMovement(event)
                downY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                val dy = event.y - downY
                if (dy < 0) {
                    val progress = (-dy / (rootView.height * 0.3f)).coerceIn(0f, 1f)
                    rootView.translationY = dy * 0.25f
                    rootView.alpha = 1f - progress * 0.5f
                }
            }
            MotionEvent.ACTION_UP -> {
                velocityTracker?.addMovement(event)
                velocityTracker?.computeCurrentVelocity(1000)
                val vy = velocityTracker?.yVelocity ?: 0f
                velocityTracker?.recycle(); velocityTracker = null
                val dy = event.y - downY
                if (vy < -600f || dy < -rootView.height * 0.25f) {
                    rootView.animate().translationY(-rootView.height.toFloat()).alpha(0f)
                        .setDuration(300).withEndAction { finish() }.start()
                } else {
                    SpringAnimation(rootView, SpringAnimation.TRANSLATION_Y, 0f).apply {
                        spring.stiffness = SpringForce.STIFFNESS_MEDIUM
                        spring.dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
                        start()
                    }
                    rootView.animate().alpha(1f).setDuration(200).start()
                }
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val ax = event.values[0]
        val ay = event.values[1]
        val px = (-ax * 4f).coerceIn(-18f, 18f) * resources.displayMetrics.density
        val py = (-ay * 4f).coerceIn(-18f, 18f) * resources.displayMetrics.density
        handler.post {
            timeLabel.translationX = px * 0.3f
            dateLabel.translationX = px * 0.2f
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

    private fun updateClock() {
        val cal = Calendar.getInstance()
        val h = cal.get(Calendar.HOUR_OF_DAY)
        val m = String.format("%02d", cal.get(Calendar.MINUTE))
        timeLabel.text = "$h:$m"
        val days = arrayOf("Вс", "Пн", "Вт", "Ср", "Чт", "Пт", "Сб")
        val months = arrayOf("января", "февраля", "марта", "апреля", "мая", "июня",
            "июля", "августа", "сентября", "октября", "ноября", "декабря")
        val dow = days[cal.get(Calendar.DAY_OF_WEEK) - 1]
        val dom = cal.get(Calendar.DAY_OF_MONTH)
        val mon = months[cal.get(Calendar.MONTH)]
        dateLabel.text = "$dow, $dom $mon"
    }

    private fun circleButton(emoji: String) = TextView(this).apply {
        text = emoji
        textSize = 22f
        gravity = Gravity.CENTER
        background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(Color.argb(60, 255, 255, 255))
        }
        isClickable = true
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

// Minimal star field painted on canvas
class StarFieldView(context: android.content.Context) : View(context) {
    private val stars = mutableListOf<Triple<Float, Float, Float>>()
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        stars.clear()
        repeat(120) {
            stars.add(Triple(
                (Math.random() * w).toFloat(),
                (Math.random() * h).toFloat(),
                (Math.random() * 2 + 0.5).toFloat()
            ))
        }
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        stars.forEach { (x, y, r) ->
            paint.alpha = (80 + Math.random() * 120).toInt()
            canvas.drawCircle(x, y, r, paint)
        }
    }
}
