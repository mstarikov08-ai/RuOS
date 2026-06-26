package com.android.systemui.ruos

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.telephony.SignalStrength
import android.telephony.TelephonyManager
import android.util.AttributeSet
import android.widget.FrameLayout
import java.util.Calendar
import java.util.Timer
import java.util.TimerTask

/**
 * RuOS status bar — iOS 18 layout, replacing the AOSP status bar entirely.
 *
 *   LEFT  : time only (Golos semibold). No carrier, nothing else.
 *   RIGHT : [signal bars] [network type] [battery]   (in that order)
 *           When Wi-Fi is connected, the Wi-Fi glyph REPLACES the bars + type.
 *
 * Nothing else is ever drawn — no notification dots, no alarm/sync/location/Bluetooth
 * icons. The centre is left empty so nothing overlaps the Dynamic Island pill. All
 * glyphs are canvas-drawn. Content is white by default and can flip to dark on light
 * backgrounds via [setDarkContent] (SystemUI drives this from the appearance flags).
 */
class RuOSStatusBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val density = resources.displayMetrics.density

    // Content colour (white on dark wallpaper; dark on light backgrounds).
    private var contentColor = Color.WHITE
    private val dimColor get() = (contentColor and 0x00FFFFFF) or 0x55000000

    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val typePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var timeText = ""
    private var batteryPercent = 100
    private var batteryCharging = false
    private var powerSave = false
    private var wifiConnected = false
    private var wifiStrength = 0     // 0–3 (iOS arcs)
    private var cellStrength = 0     // 0–4
    private var networkType = ""     // "5G" / "LTE" / "3G" / "2G" / ""

    private val wifiManager = context.applicationContext.getSystemService(WifiManager::class.java)
    private val telephonyManager = context.getSystemService(TelephonyManager::class.java)
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    private val powerManager = context.getSystemService(PowerManager::class.java)

    private val telephonyCallback = object : android.telephony.TelephonyCallback(),
        android.telephony.TelephonyCallback.SignalStrengthsListener {
        override fun onSignalStrengthsChanged(ss: SignalStrength) {
            cellStrength = ss.level.coerceIn(0, 4); postInvalidate()
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val clockTimer = Timer()

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            batteryPercent = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 100)
            batteryCharging = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                .let { it == BatteryManager.BATTERY_STATUS_CHARGING || it == BatteryManager.BATTERY_STATUS_FULL }
            powerSave = powerManager?.isPowerSaveMode == true
            postInvalidate()
        }
    }

    init {
        setWillNotDraw(false)
        setBackgroundColor(Color.TRANSPARENT)
        context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        runCatching { telephonyManager?.registerTelephonyCallback(context.mainExecutor, telephonyCallback) }
        clockTimer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() = mainHandler.post { updateTime(); refreshConnectivity(); postInvalidate() }
        }, 0, 1_000)
        updateTime(); refreshConnectivity()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        runCatching { context.unregisterReceiver(batteryReceiver) }
        runCatching { telephonyManager?.unregisterTelephonyCallback(telephonyCallback) }
        clockTimer.cancel()
    }

    /** White content on dark backgrounds (default) → dark content on light ones. */
    fun setDarkContent(dark: Boolean) {
        contentColor = if (dark) Color.parseColor("#FF111111") else Color.WHITE
        postInvalidate()
    }

    private fun refreshConnectivity() {
        powerSave = powerManager?.isPowerSaveMode == true
        runCatching {
            val caps = connectivityManager?.getNetworkCapabilities(connectivityManager?.activeNetwork)
            wifiConnected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            if (wifiConnected && wifiManager != null) {
                val rssi = wifiManager.connectionInfo?.rssi ?: -127
                // Map RSSI → 0..3 arcs (iOS shows up to 3).
                wifiStrength = WifiManager.calculateSignalLevel(rssi, 4).coerceIn(0, 3)
            }
        }
        // Network type text (only relevant when NOT on Wi-Fi).
        if (!wifiConnected) {
            networkType = runCatching { mapNetworkType(telephonyManager?.dataNetworkType ?: 0) }
                .getOrDefault("")
        }
    }

    private fun mapNetworkType(t: Int): String = when (t) {
        TelephonyManager.NETWORK_TYPE_NR -> "5G"
        TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
        TelephonyManager.NETWORK_TYPE_UMTS, TelephonyManager.NETWORK_TYPE_HSPA,
        TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_HSDPA,
        TelephonyManager.NETWORK_TYPE_HSUPA, TelephonyManager.NETWORK_TYPE_EVDO_0,
        TelephonyManager.NETWORK_TYPE_EVDO_A, TelephonyManager.NETWORK_TYPE_EVDO_B -> "3G"
        TelephonyManager.NETWORK_TYPE_GPRS, TelephonyManager.NETWORK_TYPE_EDGE,
        TelephonyManager.NETWORK_TYPE_CDMA, TelephonyManager.NETWORK_TYPE_1xRTT,
        TelephonyManager.NETWORK_TYPE_GSM -> "2G"
        else -> ""
    }

    private fun updateTime() {
        val cal = Calendar.getInstance()
        timeText = String.format("%d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
    }

    override fun onDraw(canvas: Canvas) {
        // Landscape shrinks the bar contents slightly, like iOS.
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val s = if (landscape) 0.9f else 1f
        val h = height.toFloat()
        val pad = 18f * density

        // ── Time (left, Golos semibold) ───────────────────────────────────────
        timePaint.color = contentColor
        timePaint.textSize = 16f * density * s
        timePaint.typeface = Typeface.create("golos", Typeface.BOLD)
        val baseline = h / 2f - (timePaint.descent() + timePaint.ascent()) / 2f
        canvas.drawText(timeText, pad, baseline, timePaint)

        // ── Right cluster (battery is rightmost) ──────────────────────────────
        var rx = width.toFloat() - pad

        // Battery glyph.
        val batW = 25f * density * s; val batH = 12f * density * s
        rx -= batW
        drawBattery(canvas, rx, h / 2f - batH / 2f, batW, batH)
        rx -= 5f * density

        // Battery percentage (left of the glyph).
        typePaint.color = contentColor
        typePaint.textSize = 13f * density * s
        typePaint.typeface = Typeface.create("golos-medium", Typeface.NORMAL)
        typePaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("$batteryPercent", rx, baseline, typePaint)
        rx -= typePaint.measureText("$batteryPercent") + 8f * density
        typePaint.textAlign = Paint.Align.LEFT

        if (wifiConnected) {
            // Wi-Fi REPLACES the bars + network type entirely.
            val wifiW = 18f * density * s
            rx -= wifiW
            drawWifi(canvas, rx, h / 2f - 8f * density * s, wifiW, 16f * density * s)
        } else {
            // Network type text, then signal bars to its left.
            if (networkType.isNotEmpty()) {
                typePaint.textAlign = Paint.Align.RIGHT
                canvas.drawText(networkType, rx, baseline, typePaint)
                rx -= typePaint.measureText(networkType) + 6f * density
                typePaint.textAlign = Paint.Align.LEFT
            }
            val barsW = 18f * density * s
            rx -= barsW
            drawSignalBars(canvas, rx, h / 2f - 8f * density * s, barsW, 16f * density * s)
        }
    }

    // ── Glyphs ──────────────────────────────────────────────────────────────

    private fun drawSignalBars(canvas: Canvas, x: Float, y: Float, w: Float, h: Float) {
        val gap = 1.6f * density
        val barW = (w - 3 * gap) / 4f
        for (i in 0 until 4) {
            val barH = h * (0.42f + i * 0.19f)
            val bx = x + i * (barW + gap)
            iconPaint.color = if (i < cellStrength) contentColor else dimColor
            iconPaint.style = Paint.Style.FILL
            canvas.drawRoundRect(bx, y + h - barH, bx + barW, y + h, 1.5f * density, 1.5f * density, iconPaint)
        }
    }

    private fun drawWifi(canvas: Canvas, x: Float, y: Float, w: Float, h: Float) {
        iconPaint.style = Paint.Style.STROKE
        iconPaint.strokeCap = Paint.Cap.ROUND
        iconPaint.strokeWidth = 1.7f * density
        val cx = x + w / 2f; val by = y + h * 0.92f
        for (i in 0 until 3) {
            iconPaint.color = if (i < wifiStrength) contentColor else dimColor
            val r = (i + 1) * w / 3.4f
            canvas.drawArc(RectF(cx - r, by - r, cx + r, by + r), 215f, 110f, false, iconPaint)
        }
        iconPaint.style = Paint.Style.FILL
        iconPaint.color = if (wifiStrength > 0) contentColor else dimColor
        canvas.drawCircle(cx, by, 1.8f * density, iconPaint)
    }

    private fun drawBattery(canvas: Canvas, x: Float, y: Float, w: Float, h: Float) {
        val bodyW = w * 0.9f
        // Shell (rounded rect) at 35% opacity, iOS-style.
        iconPaint.style = Paint.Style.STROKE
        iconPaint.strokeWidth = 1f * density
        iconPaint.color = (contentColor and 0x00FFFFFF) or 0x66000000
        val r = 3f * density
        canvas.drawRoundRect(x, y, x + bodyW, y + h, r, r, iconPaint)
        // Positive terminal nub.
        iconPaint.style = Paint.Style.FILL
        canvas.drawRoundRect(x + bodyW + 0.5f * density, y + h * 0.3f,
            x + w, y + h * 0.7f, 1f * density, 1f * density, iconPaint)
        // Fill.
        val inset = 1.6f * density
        val fillW = (bodyW - 2 * inset) * (batteryPercent / 100f)
        iconPaint.color = when {
            batteryCharging -> Color.parseColor("#30D158")   // green
            powerSave -> Color.parseColor("#FFD60A")          // yellow (low power)
            batteryPercent <= 20 -> Color.parseColor("#FF453A")
            else -> contentColor
        }
        canvas.drawRoundRect(x + inset, y + inset, x + inset + fillW, y + h - inset,
            1.5f * density, 1.5f * density, iconPaint)
        // Charging bolt overlay.
        if (batteryCharging) drawBolt(canvas, x + bodyW / 2f, y + h / 2f, h * 0.62f)
    }

    private fun drawBolt(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        val p = Path()
        val hw = size * 0.28f; val hh = size / 2f
        p.moveTo(cx + hw * 0.2f, cy - hh)
        p.lineTo(cx - hw, cy + hh * 0.1f)
        p.lineTo(cx - hw * 0.05f, cy + hh * 0.1f)
        p.lineTo(cx - hw * 0.2f, cy + hh)
        p.lineTo(cx + hw, cy - hh * 0.1f)
        p.lineTo(cx + hw * 0.05f, cy - hh * 0.1f)
        p.close()
        iconPaint.style = Paint.Style.FILL
        iconPaint.color = Color.parseColor("#FF111111")   // dark bolt over green fill
        canvas.drawPath(p, iconPaint)
    }
}
