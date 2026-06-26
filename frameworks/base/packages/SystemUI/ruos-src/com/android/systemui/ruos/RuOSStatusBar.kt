package com.android.systemui.ruos

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.telephony.TelephonyManager
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Calendar
import java.util.Timer
import java.util.TimerTask
import android.os.Handler
import android.os.Looper

/**
 * RuOS custom status bar row.
 *
 * Left:  time (HH:MM or H:MM AM/PM)
 * Right: signal strength icon, WiFi icon, battery % + bar
 *
 * All icons drawn via Canvas (SVG path → Path objects) — no bitmaps.
 * Font: Golos Text (if loaded) or system thin sans-serif fallback.
 */
class RuOSStatusBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val density = resources.displayMetrics.density

    // Left: time
    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 15f * density
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private var timeText = ""

    // Right: signal, wifi, battery
    private val rightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 13f * density
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private var batteryPercent = 100
    private var batteryCharging = false
    private var wifiStrength = 3     // 0–4
    private var cellStrength = 2     // 0–4

    private val mainHandler = Handler(Looper.getMainLooper())
    private val clockTimer = Timer()

    // Receivers
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            batteryPercent = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 100)
            batteryCharging = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ==
                BatteryManager.BATTERY_STATUS_CHARGING
            postInvalidate()
        }
    }

    init {
        setWillNotDraw(false)
        setBackgroundColor(Color.TRANSPARENT)

        context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        clockTimer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() { mainHandler.post { updateTime(); postInvalidate() } }
        }, 0, 10_000)
        updateTime()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        try { context.unregisterReceiver(batteryReceiver) } catch (_: Exception) {}
        clockTimer.cancel()
    }

    private fun updateTime() {
        val cal = Calendar.getInstance()
        val h = cal.get(Calendar.HOUR_OF_DAY)
        val m = cal.get(Calendar.MINUTE)
        timeText = String.format("%d:%02d", h, m)
    }

    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat()

        // Time — left
        val ty = h / 2f - (timePaint.descent() + timePaint.ascent()) / 2f
        canvas.drawText(timeText, 16f * density, ty, timePaint)

        // Right cluster: battery% → battery bar → wifi → signal
        var rx = width.toFloat() - 12f * density

        // Battery %
        val bStr = "$batteryPercent%"
        rx -= rightPaint.measureText(bStr)
        canvas.drawText(bStr, rx, ty, rightPaint)
        rx -= 6f * density

        // Battery bar
        drawBatteryIcon(canvas, rx - 24f * density, h / 2f - 7f * density, 24f * density, 14f * density)
        rx -= 30f * density

        // WiFi dots
        drawWifiIcon(canvas, rx - 18f * density, h / 2f - 8f * density, 18f * density, 16f * density, wifiStrength)
        rx -= 24f * density

        // Cellular bars
        drawCellIcon(canvas, rx - 18f * density, h / 2f - 8f * density, 18f * density, 16f * density, cellStrength)
    }

    private fun drawBatteryIcon(canvas: Canvas, x: Float, y: Float, w: Float, h: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * density
        }
        // Outer shell
        canvas.drawRoundRect(x, y, x + w * 0.88f, y + h, 2f, 2f, p)
        // Nub
        p.style = Paint.Style.FILL
        canvas.drawRect(x + w * 0.88f, y + h * 0.3f, x + w, y + h * 0.7f, p)
        // Fill
        val fillW = w * 0.86f * (batteryPercent / 100f)
        p.color = when {
            batteryCharging -> Color.parseColor("#30D158")
            batteryPercent < 20 -> Color.parseColor("#FF453A")
            else -> Color.WHITE
        }
        canvas.drawRoundRect(x + 2f, y + 2f, x + 2f + fillW, y + h - 2f, 1f, 1f, p)
    }

    private fun drawWifiIcon(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, strength: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * density
            strokeCap = Paint.Cap.ROUND
        }
        val cx = x + w / 2f
        val by = y + h
        for (i in 0 until 4) {
            if (i < strength) p.color = Color.WHITE else p.color = Color.argb(80, 255, 255, 255)
            val r = (i + 1) * w / 4.5f
            val sweepAngle = 120f
            canvas.drawArc(cx - r, by - r, cx + r, by + r, 210f, sweepAngle, false, p)
        }
        // Dot
        p.style = Paint.Style.FILL
        p.color = Color.WHITE
        canvas.drawCircle(cx, by, 2f * density, p)
    }

    private fun drawCellIcon(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, strength: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val barW = w / 5f
        for (i in 0 until 4) {
            val barH = h * (i + 1) / 4.5f
            val bx = x + i * (barW + 1f)
            val by = y + h - barH
            p.color = if (i < strength) Color.WHITE else Color.argb(80, 255, 255, 255)
            canvas.drawRoundRect(bx, by, bx + barW, y + h, 1f, 1f, p)
        }
    }
}
