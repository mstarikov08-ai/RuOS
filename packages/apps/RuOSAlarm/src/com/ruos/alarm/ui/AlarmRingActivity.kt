package com.ruos.alarm.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.ruos.alarm.alarm.AlarmRingService
import com.ruos.alarm.model.Alarm
import com.ruos.alarm.model.AlarmStore
import com.ruos.alarm.util.Constants
import com.ruos.alarm.util.Haptics
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full-screen alarm takeover. Shows over the lock screen, turns the screen on, shows
 * the time large + the label, and offers Snooze / Stop. In "sunrise" mode it has no
 * buttons and gradually brightens the screen over the 5-minute lead-in.
 */
class AlarmRingActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var root: FrameLayout
    private lateinit var timeView: TextView
    private lateinit var labelView: TextView
    private lateinit var buttonRow: LinearLayout
    private var alarm: Alarm? = null
    private var sunriseMode = false
    private var sunriseStart = 0L

    private val clockTick = object : Runnable {
        override fun run() { updateTime(); handler.postDelayed(this, 1000) }
    }
    private val sunriseTick = object : Runnable {
        override fun run() {
            val elapsed = System.currentTimeMillis() - sunriseStart
            val frac = (elapsed.toFloat() / (Constants.SUNRISE_LEAD_MINUTES * 60_000f)).coerceIn(0f, 1f)
            applyBrightness(0.02f + frac * 0.98f)
            applySunriseTint(frac)
            if (frac < 1f) handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )

        root = FrameLayout(this)
        setContentView(root)
        buildUi()
        applyMode(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyMode(intent)   // sunrise → ring transition arrives here
    }

    private fun applyMode(intent: Intent) {
        val id = intent.getLongExtra(Constants.EXTRA_ALARM_ID, -1L)
        alarm = AlarmStore(this).get(id)
        labelView.text = alarm?.label?.takeIf { it.isNotBlank() } ?: "Будильник"
        sunriseMode = intent.getStringExtra("mode") == "sunrise"

        handler.removeCallbacks(sunriseTick)
        if (sunriseMode) {
            buttonRow.visibility = View.GONE
            sunriseStart = System.currentTimeMillis()
            applyBrightness(0.02f)
            handler.post(sunriseTick)
        } else {
            buttonRow.visibility = View.VISIBLE
            applyBrightness(1f)
            applySunriseTint(1f)
            // Hide snooze if disabled for this alarm.
            snoozeButton.visibility = if (alarm?.snoozeEnabled != false) View.VISIBLE else View.GONE
        }
        updateTime()
    }

    private lateinit var snoozeButton: TextView

    private fun buildUi() {
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        root.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#05070F"), Color.parseColor("#0A1026"))
        )

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        root.addView(col, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ).also { it.topMargin = dp(140) })

        labelView = TextView(this).apply {
            setTextColor(Color.parseColor("#C8D0E0")); textSize = 18f
            gravity = Gravity.CENTER
        }
        col.addView(labelView)

        timeView = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 88f
            typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
            gravity = Gravity.CENTER
        }
        col.addView(timeView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.topMargin = dp(8) })

        // Buttons pinned to the bottom.
        buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        root.addView(buttonRow, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM
        ).also { it.bottomMargin = dp(64) })

        snoozeButton = pill("Отложить", Color.parseColor("#2C2C2E"), Color.WHITE) {
            Haptics.confirm(it)
            send(Constants.ACTION_SNOOZE)
        }
        buttonRow.addView(snoozeButton, LinearLayout.LayoutParams(dp(260), dp(64)).also { it.bottomMargin = dp(18) })

        buttonRow.addView(pill("Стоп", Color.parseColor("#D94F3D"), Color.WHITE) {
            Haptics.confirm(it)
            send(Constants.ACTION_STOP)
        }, LinearLayout.LayoutParams(dp(260), dp(64)))
    }

    private fun pill(text: String, bg: Int, fg: Int, onClick: (View) -> Unit): TextView {
        val d = resources.displayMetrics.density
        return TextView(this).apply {
            this.text = text
            setTextColor(fg); textSize = 20f
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            background = GradientDrawable().apply { cornerRadius = 32f * d; setColor(bg) }
            isClickable = true
            setOnClickListener { onClick(it) }
        }
    }

    private fun send(action: String) {
        startService(Intent(this, AlarmRingService::class.java).setAction(action))
        finish()
    }

    private fun updateTime() {
        timeView.text = SimpleDateFormat("HH:mm", Locale("ru")).format(Date())
    }

    private fun applyBrightness(level: Float) {
        window.attributes = window.attributes.apply { screenBrightness = level.coerceIn(0.01f, 1f) }
    }

    private fun applySunriseTint(frac: Float) {
        // Cool night → warm sunrise as it brightens.
        val top = blend(Color.parseColor("#05070F"), Color.parseColor("#FF8A3D"), frac * 0.7f)
        val bot = blend(Color.parseColor("#0A1026"), Color.parseColor("#FFD27A"), frac * 0.7f)
        root.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bot))
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        val tt = t.coerceIn(0f, 1f)
        fun mix(s: Int, e: Int) = (s + (e - s) * tt).toInt()
        return Color.rgb(
            mix(Color.red(a), Color.red(b)),
            mix(Color.green(a), Color.green(b)),
            mix(Color.blue(a), Color.blue(b))
        )
    }

    override fun onResume() { super.onResume(); handler.post(clockTick) }
    override fun onPause() { super.onPause(); handler.removeCallbacks(clockTick) }
    override fun onDestroy() { super.onDestroy(); handler.removeCallbacks(sunriseTick) }

    @Deprecated("Alarm screen must not be back-dismissable")
    override fun onBackPressed() { /* swallow — must use Snooze/Stop */ }
}
