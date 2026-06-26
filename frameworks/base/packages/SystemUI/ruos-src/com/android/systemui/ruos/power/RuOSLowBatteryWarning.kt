package com.android.systemui.ruos.power

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

/**
 * iOS-style low-battery warning. At 20% and again at 10% (while discharging) it shows a
 * small centred card: a yellow battery glyph (canvas-drawn — no emoji), «N% заряда
 * осталось», and a «Режим энергосбережения» button that turns on power save. The card
 * springs in, auto-dismisses after a few seconds, and is suppressed entirely while
 * charging. Each threshold fires once per discharge cycle.
 *
 * Wire-up: construct once from SystemUI startup and call [start]:
 *   RuOSLowBatteryWarning(context).start()
 * (e.g. alongside initDynamicIsland / initSystemPanels in RuOSSystemUIModule, or from a
 * CoreStartable). Call [stop] on teardown.
 */
class RuOSLowBatteryWarning(private val context: Context) {

    private val main = Handler(Looper.getMainLooper())
    private val wm = context.getSystemService(WindowManager::class.java)
    private val pm = context.getSystemService(PowerManager::class.java)

    private val thresholds = intArrayOf(20, 10)
    private val warned = HashSet<Int>()
    private var shownCard: View? = null
    private var lastLevel = 100

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            when (i?.action) {
                Intent.ACTION_POWER_CONNECTED -> { warned.clear(); dismiss() }
                Intent.ACTION_BATTERY_CHANGED -> onBattery(i)
            }
        }
    }

    fun start() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        runCatching { context.registerReceiver(receiver, filter) }
    }

    fun stop() {
        runCatching { context.unregisterReceiver(receiver) }
        dismiss()
    }

    private fun onBattery(i: Intent) {
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        if (level < 0 || scale <= 0) return
        val pct = level * 100 / scale
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        // reset a threshold once we climb back above it (e.g. after a top-up)
        thresholds.forEach { if (pct > it + 3) warned.remove(it) }
        if (charging) return
        // fire when we cross down through a threshold
        if (pct < lastLevel || lastLevel == 100) {
            for (t in thresholds) {
                if (pct <= t && !warned.contains(t)) { warned.add(t); showWarning(pct); break }
            }
        }
        lastLevel = pct
    }

    private fun showWarning(pct: Int) {
        main.post {
            dismiss()
            val card = LowBatteryCard(context, pct,
                onPowerSave = { enablePowerSave(); dismiss() },
                onDismiss = { dismiss() })
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_DIM_BEHIND or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
            ).apply { dimAmount = 0.45f; gravity = Gravity.CENTER }
            runCatching { wm.addView(card, lp); shownCard = card; card.animateIn() }
            main.postDelayed({ dismiss() }, AUTO_DISMISS_MS)
        }
    }

    private fun dismiss() {
        shownCard?.let { v -> runCatching { wm.removeView(v) } }
        shownCard = null
        main.removeCallbacksAndMessages(null)
    }

    /** Turn on battery saver. Prefer the platform API; fall back to the secure setting. */
    private fun enablePowerSave() {
        val ok = runCatching {
            PowerManager::class.java
                .getMethod("setPowerSaveModeEnabled", Boolean::class.javaPrimitiveType)
                .invoke(pm, true)
        }.isSuccess
        if (!ok) runCatching {
            Settings.Global.putInt(context.contentResolver, "low_power", 1)
        }
    }

    companion object { private const val AUTO_DISMISS_MS = 6500L }
}

/** The iOS-style alert card: yellow battery glyph + message + power-save button. */
private class LowBatteryCard(
    context: Context,
    private val pct: Int,
    private val onPowerSave: () -> Unit,
    private val onDismiss: () -> Unit
) : FrameLayout(context) {

    private val d = resources.displayMetrics.density
    private fun dp(v: Float) = v * d
    private val golos = Typeface.create("golos-medium", Typeface.NORMAL)
        .let { if (it === Typeface.DEFAULT) Typeface.create("sans-serif-medium", Typeface.NORMAL) else it }
    private val card: LinearLayout

    init {
        card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24f).toInt(), dp(26f).toInt(), dp(24f).toInt(), dp(16f).toInt())
            background = object : android.graphics.drawable.Drawable() {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xF21C1C1E.toInt() }
                override fun draw(c: Canvas) {
                    val r = RectF(bounds); c.drawRoundRect(r, dp(28f), dp(28f), p)
                }
                override fun setAlpha(a: Int) {}
                override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
                override fun getOpacity() = PixelFormat.TRANSLUCENT
            }
        }
        card.addView(BatteryGlyph(context), LinearLayout.LayoutParams(dp(56f).toInt(), dp(86f).toInt()))
        card.addView(TextView(context).apply {
            text = "$pct% заряда осталось"
            setTextColor(Color.WHITE); textSize = 18f; typeface = golos
            setPadding(0, dp(16f).toInt(), 0, dp(2f).toInt())
        })
        card.addView(TextView(context).apply {
            text = "Включите режим энергосбережения, чтобы продлить работу."
            setTextColor(0xFF9E9EA3.toInt()); textSize = 13f
            gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(18f).toInt())
        })
        card.addView(button("Режим энергосбережения", 0xFFFFCC00.toInt(), Color.BLACK) {
            performHapticFeedback(HapticFeedbackConstants.CONFIRM); onPowerSave()
        })
        card.addView(button("Не сейчас", 0x00000000, 0xFF0A84FF.toInt()) { onDismiss() })

        val lp = LayoutParams(dp(300f).toInt(), LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        addView(card, lp)
        setOnClickListener { onDismiss() }   // tap outside the card
        card.isClickable = true
        alpha = 0f
    }

    private fun button(label: String, bg: Int, fg: Int, onTap: () -> Unit): View =
        TextView(context).apply {
            text = label; setTextColor(fg); textSize = 16f; typeface = golos
            gravity = Gravity.CENTER
            setPadding(0, dp(12f).toInt(), 0, dp(12f).toInt())
            if (bg != 0) background = object : android.graphics.drawable.Drawable() {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg }
                override fun draw(c: Canvas) { c.drawRoundRect(RectF(bounds), dp(14f), dp(14f), p) }
                override fun setAlpha(a: Int) {}
                override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
                override fun getOpacity() = PixelFormat.TRANSLUCENT
            }
            isClickable = true
            setOnClickListener { onTap() }
        }.also {
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(6f).toInt(); it.layoutParams = lp
        }

    fun animateIn() {
        scaleX = 0.9f; scaleY = 0.9f
        animate().alpha(1f).setDuration(120).start()
        val spec = SpringForce(1f).setStiffness(400f).setDampingRatio(0.7f)
        SpringAnimation(this, SpringAnimation.SCALE_X).apply { spring = spec }.start()
        SpringAnimation(this, SpringAnimation.SCALE_Y).apply { spring = spec }.start()
    }

    /** Yellow battery glyph, drawn by hand (project rule: no emoji). */
    private inner class BatteryGlyph(context: Context) : View(context) {
        private val yellow = 0xFFFFCC00.toInt()
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; color = yellow; strokeWidth = dp(3f)
        }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = yellow }
        override fun onDraw(c: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            val capH = dp(7f); val r = dp(9f)
            val body = RectF(dp(4f), capH, w - dp(4f), h - dp(4f))
            c.drawRoundRect(body, r, r, stroke)
            // terminal cap
            c.drawRoundRect(RectF(w/2 - dp(11f), 0f, w/2 + dp(11f), capH + dp(1f)),
                dp(3f), dp(3f), fill)
            // low fill at the bottom (critically low)
            val pad = dp(7f)
            val fh = (body.height() - pad*2) * 0.18f
            c.drawRoundRect(RectF(body.left + pad, body.bottom - pad - fh,
                body.right - pad, body.bottom - pad), dp(4f), dp(4f), fill)
        }
    }
}
