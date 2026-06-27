package com.android.systemui.ruos.power

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

/**
 * iOS-style "plug-in" charging animation. When the charger connects (or the device is
 * plugged in at the lock screen), a large green battery glyph springs up in the centre,
 * its fill sweeps up to the current charge level, the percentage counts up beneath it,
 * a short chime plays and the device gives one soft haptic tick. It auto-dismisses after
 * a couple of seconds — exactly the brief "yes, it's charging" confirmation iOS shows.
 *
 * Everything is canvas-drawn (project rule: no emoji). Reuses the same spring vocabulary
 * (stiffness 400 / damping 0.75) as the rest of RuOS SystemUI.
 *
 * Wire-up: construct once from SystemUI startup and call [start]:
 *   RuOSChargingAnimation(context).start()
 * (alongside RuOSLowBatteryWarning in RuOSSystemUIModule.initDynamicIsland). [stop] on teardown.
 */
class RuOSChargingAnimation(private val context: Context) {

    private val main = Handler(Looper.getMainLooper())
    private val wm = context.getSystemService(WindowManager::class.java)
    private val vibrator = context.getSystemService(Vibrator::class.java)

    private var shown: View? = null
    private var lastConnectAt = 0L

    private val sound = SoundPool.Builder()
        .setMaxStreams(1)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        ).build()
    private var chimeId = 0

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            if (i?.action == Intent.ACTION_POWER_CONNECTED) onConnected()
            else if (i?.action == Intent.ACTION_POWER_DISCONNECTED) dismiss()
        }
    }

    fun start() {
        // A small bundled chime; if it isn't present we just fall back to no sound.
        runCatching {
            val res = context.resources.getIdentifier("ruos_charge", "raw", context.packageName)
            if (res != 0) chimeId = sound.load(context, res, 1)
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        runCatching { context.registerReceiver(receiver, filter) }
    }

    fun stop() {
        runCatching { context.unregisterReceiver(receiver) }
        runCatching { sound.release() }
        dismiss()
    }

    private fun onConnected() {
        // Debounce flaky chargers / quick replugs.
        val now = SystemClock.uptimeMillis()
        if (now - lastConnectAt < 1500L) return
        lastConnectAt = now
        val pct = currentPct()
        main.post {
            dismiss()
            val card = ChargingCard(context, pct)
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.CENTER }
            runCatching {
                wm.addView(card, lp); shown = card
                card.animateIn()
                playChime()
                runCatching {
                    vibrator?.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
                }
            }
            main.postDelayed({ dismiss() }, AUTO_DISMISS_MS)
        }
    }

    private fun playChime() {
        if (chimeId != 0) runCatching { sound.play(chimeId, 0.7f, 0.7f, 1, 0, 1f) }
    }

    private fun currentPct(): Int {
        val bm = context.getSystemService(BatteryManager::class.java)
        val p = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        return if (p in 0..100) p else 50
    }

    private fun dismiss() {
        shown?.let { v -> runCatching { wm.removeView(v) } }
        shown = null
        main.removeCallbacksAndMessages(null)
    }

    companion object { private const val AUTO_DISMISS_MS = 2200L }
}

/** The centred glyph: a big battery that fills to [pct] with a counting number below. */
private class ChargingCard(context: Context, private val pct: Int) : FrameLayout(context) {

    private val d = resources.displayMetrics.density
    private fun dp(v: Float) = v * d
    private val golos = Typeface.create("golos-semibold", Typeface.NORMAL)
        .let { if (it === Typeface.DEFAULT) Typeface.create("sans-serif-medium", Typeface.BOLD) else it }

    private val glyph = BatteryGlyph(context)
    private val label = TextView(context).apply {
        setTextColor(Color.WHITE); textSize = 44f; typeface = golos
        gravity = Gravity.CENTER; text = "0%"
    }

    init {
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        col.addView(glyph, LinearLayout.LayoutParams(dp(110f).toInt(), dp(54f).toInt()))
        col.addView(label, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(14f).toInt() })
        addView(col, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        alpha = 0f
    }

    fun animateIn() {
        scaleX = 0.7f; scaleY = 0.7f
        animate().alpha(1f).setDuration(140).start()
        val spec = SpringForce(1f).setStiffness(400f).setDampingRatio(0.75f)
        SpringAnimation(this, SpringAnimation.SCALE_X).apply { spring = spec }.start()
        SpringAnimation(this, SpringAnimation.SCALE_Y).apply { spring = spec }.start()
        // Sweep the fill 0 → pct and count the number up in step.
        val anim = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 800
            interpolator = android.view.animation.PathInterpolator(0.32f, 0.72f, 0f, 1f)
            addUpdateListener {
                val f = it.animatedValue as Float
                glyph.setFraction(f * pct / 100f)
                label.text = "${(f * pct).toInt()}%"
            }
        }
        anim.start()
    }

    /** Green battery, hand-drawn, with a horizontal fill that animates to [frac]. */
    private inner class BatteryGlyph(context: Context) : View(context) {
        private val green = 0xFF34C759.toInt()
        private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; color = 0x4DFFFFFF; strokeWidth = dp(2.5f)
        }
        private val cap = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x4DFFFFFF }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private var frac = 0f
        fun setFraction(f: Float) { frac = f.coerceIn(0f, 1f); invalidate() }

        override fun onDraw(c: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            val capW = dp(5f); val r = dp(7f)
            val body = RectF(dp(2f), dp(2f), w - capW - dp(4f), h - dp(2f))
            c.drawRoundRect(body, r, r, track)
            // terminal cap on the right
            c.drawRoundRect(RectF(w - capW - dp(2f), h/2 - dp(9f), w - dp(2f), h/2 + dp(9f)),
                dp(3f), dp(3f), cap)
            // fill with a subtle top-light gradient (RuOS design: light from top-left)
            val pad = dp(4f)
            val maxW = body.width() - pad * 2
            val fw = maxW * frac
            if (fw > 0.5f) {
                val fr = RectF(body.left + pad, body.top + pad, body.left + pad + fw, body.bottom - pad)
                fill.shader = LinearGradient(fr.left, fr.top, fr.left, fr.bottom,
                    0xFF5BE584.toInt(), green, Shader.TileMode.CLAMP)
                c.drawRoundRect(fr, dp(4f), dp(4f), fill)
            }
        }
    }
}
