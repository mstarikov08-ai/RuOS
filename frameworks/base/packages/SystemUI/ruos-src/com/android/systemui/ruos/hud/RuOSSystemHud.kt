package com.android.systemui.ruos.hud

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

/**
 * iOS-18-style Volume & Brightness HUD. A slim frosted capsule slides in from the left
 * edge, fills to the new level, and auto-dismisses — replacing the boxy stock Android
 * volume panel. Driven entirely by system state, so it needs no key-event hooks:
 *   • Volume:    android.media.VOLUME_CHANGED_ACTION (+ AudioManager for max/mute)
 *   • Brightness: ContentObserver on Settings.System.SCREEN_BRIGHTNESS
 *
 * Wire-up: construct once at SystemUI startup and call [start] (done from
 * RuOSSystemUIModule.initDynamicIsland). Glyphs are canvas-drawn (no emoji).
 *
 * NOTE: to fully suppress the *stock* volume dialog so only this HUD shows, point
 * SystemUI's VolumeDialog/VolumeDialogComponent at a no-op (see docs/SystemHUD.md).
 * Until then both may appear; this HUD is otherwise complete.
 */
class RuOSSystemHud(private val context: Context) {

    private val main = Handler(Looper.getMainLooper())
    private val wm = context.getSystemService(WindowManager::class.java)
    private val audio = context.getSystemService(AudioManager::class.java)

    private var capsule: HudCapsule? = null
    private var lastBrightness = -1

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            if (i?.action != VOLUME_CHANGED) return
            val stream = i.getIntExtra(EXTRA_STREAM_TYPE, AudioManager.STREAM_MUSIC)
            // ignore streams the user isn't actively setting (e.g. system beeps)
            if (stream != AudioManager.STREAM_MUSIC &&
                stream != AudioManager.STREAM_RING &&
                stream != AudioManager.STREAM_NOTIFICATION &&
                stream != AudioManager.STREAM_VOICE_CALL) return
            val value = i.getIntExtra(EXTRA_STREAM_VALUE, -1)
            if (value < 0) return
            val max = audio.getStreamMaxVolume(stream).coerceAtLeast(1)
            val kind = when (stream) {
                AudioManager.STREAM_RING, AudioManager.STREAM_NOTIFICATION -> HudKind.RING
                AudioManager.STREAM_VOICE_CALL -> HudKind.CALL
                else -> HudKind.MEDIA
            }
            main.post { show(kind, value.toFloat() / max, muted = value == 0) }
        }
    }

    private val brightnessObserver = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) {
            val v = runCatching {
                Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            }.getOrDefault(lastBrightness)
            if (v == lastBrightness) return
            lastBrightness = v
            main.post { show(HudKind.BRIGHTNESS, (v.toFloat() / 255f).coerceIn(0f, 1f), false) }
        }
    }

    fun start() {
        runCatching {
            context.registerReceiver(volumeReceiver, IntentFilter(VOLUME_CHANGED))
        }
        runCatching {
            context.contentResolver.registerContentObserver(
                Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS), false, brightnessObserver)
            lastBrightness = Settings.System.getInt(
                context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, lastBrightness)
        }
    }

    fun stop() {
        runCatching { context.unregisterReceiver(volumeReceiver) }
        runCatching { context.contentResolver.unregisterContentObserver(brightnessObserver) }
        dismiss()
    }

    private fun show(kind: HudKind, fraction: Float, muted: Boolean) {
        val view = capsule ?: HudCapsule(context).also { c ->
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                x = (12 * context.resources.displayMetrics.density).toInt()
            }
            runCatching { wm.addView(c, lp); capsule = c; c.slideIn() }
        }
        view.setLevel(kind, fraction, muted)
        main.removeCallbacks(dismissRunnable)
        main.postDelayed(dismissRunnable, VISIBLE_MS)
    }

    private val dismissRunnable = Runnable { dismiss() }

    private fun dismiss() {
        capsule?.let { v -> v.slideOut { runCatching { wm.removeView(v) } } }
        capsule = null
    }

    companion object {
        private const val VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
        private const val EXTRA_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"
        private const val EXTRA_STREAM_VALUE = "android.media.EXTRA_VOLUME_STREAM_VALUE"
        private const val VISIBLE_MS = 1300L
    }
}

private enum class HudKind { MEDIA, RING, CALL, BRIGHTNESS }

/** The slim frosted capsule with a bottom-anchored fill and a canvas glyph. */
private class HudCapsule(context: Context) : View(context) {

    private val d = resources.displayMetrics.density
    private fun dp(v: Float) = v * d

    private val capW = dp(42f)
    private val capH = dp(150f)
    private var kind = HudKind.MEDIA
    private var fraction = 0.5f
    private var muted = false

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCC1C1C1E.toInt() }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF8E8E93.toInt() }

    private val slide = SpringAnimation(this, TRANSLATION_X).apply {
        spring = SpringForce(0f).setStiffness(500f).setDampingRatio(0.78f)
    }

    fun setLevel(k: HudKind, f: Float, m: Boolean) {
        kind = k; fraction = f.coerceIn(0f, 1f); muted = m; invalidate()
        runCatching {
            performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    fun slideIn() { translationX = -capW - dp(20f); alpha = 1f; slide.animateToFinalPosition(0f) }

    fun slideOut(end: () -> Unit) {
        animate().translationX(-capW - dp(20f)).alpha(0f).setDuration(180)
            .withEndAction(end).start()
    }

    override fun onMeasure(w: Int, h: Int) =
        setMeasuredDimension(capW.toInt(), capH.toInt())

    override fun onDraw(canvas: Canvas) {
        val r = capW / 2f
        // track
        canvas.drawRoundRect(RectF(0f, 0f, capW, capH), r, r, track)
        // fill from the bottom
        val fillTop = capH * (1f - fraction)
        if (fraction > 0.001f) {
            val save = canvas.save()
            canvas.clipRect(0f, fillTop, capW, capH)
            canvas.drawRoundRect(RectF(0f, 0f, capW, capH), r, r, fill)
            canvas.restoreToCount(save)
        }
        // glyph near the bottom: dark when over the white fill, light otherwise
        val gy = capH - dp(22f)
        glyph.color = if (fraction > 0.16f) 0xFF1C1C1E.toInt() else 0xFFBFBFC4.toInt()
        drawGlyph(canvas, capW / 2f, gy, dp(11f))
    }

    private fun drawGlyph(c: Canvas, cx: Float, cy: Float, s: Float) {
        when (kind) {
            HudKind.BRIGHTNESS -> sun(c, cx, cy, s)
            HudKind.RING, HudKind.CALL -> if (muted) bell(c, cx, cy, s, true) else bell(c, cx, cy, s, false)
            HudKind.MEDIA -> speaker(c, cx, cy, s, muted)
        }
    }

    private fun speaker(c: Canvas, cx: Float, cy: Float, s: Float, mute: Boolean) {
        val p = Paint(glyph).apply { style = Paint.Style.FILL }
        // body: small rect + triangle cone
        c.drawRect(cx - s * 0.9f, cy - s * 0.35f, cx - s * 0.4f, cy + s * 0.35f, p)
        val path = android.graphics.Path().apply {
            moveTo(cx - s * 0.4f, cy - s * 0.35f)
            lineTo(cx + s * 0.15f, cy - s * 0.75f)
            lineTo(cx + s * 0.15f, cy + s * 0.75f)
            lineTo(cx - s * 0.4f, cy + s * 0.35f); close()
        }
        c.drawPath(path, p)
        val stroke = Paint(glyph).apply { style = Paint.Style.STROKE; strokeWidth = s * 0.18f; strokeCap = Paint.Cap.ROUND }
        if (mute) {
            c.drawLine(cx + s * 0.45f, cy - s * 0.5f, cx + s * 0.95f, cy + s * 0.5f, stroke)
            c.drawLine(cx + s * 0.95f, cy - s * 0.5f, cx + s * 0.45f, cy + s * 0.5f, stroke)
        } else {
            c.drawArc(RectF(cx + s * 0.1f, cy - s * 0.6f, cx + s * 0.8f, cy + s * 0.6f), -45f, 90f, false, stroke)
        }
    }

    private fun bell(c: Canvas, cx: Float, cy: Float, s: Float, mute: Boolean) {
        val p = Paint(glyph).apply { style = Paint.Style.FILL }
        val path = android.graphics.Path().apply {
            moveTo(cx - s * 0.7f, cy + s * 0.4f)
            quadTo(cx - s * 0.7f, cy - s * 0.7f, cx, cy - s * 0.8f)
            quadTo(cx + s * 0.7f, cy - s * 0.7f, cx + s * 0.7f, cy + s * 0.4f); close()
        }
        c.drawPath(path, p)
        c.drawCircle(cx, cy + s * 0.7f, s * 0.18f, p)
        if (mute) {
            val stroke = Paint(glyph).apply { style = Paint.Style.STROKE; strokeWidth = s * 0.18f; strokeCap = Paint.Cap.ROUND }
            c.drawLine(cx - s * 0.85f, cy - s * 0.85f, cx + s * 0.85f, cy + s * 0.85f, stroke)
        }
    }

    private fun sun(c: Canvas, cx: Float, cy: Float, s: Float) {
        val p = Paint(glyph).apply { style = Paint.Style.FILL }
        c.drawCircle(cx, cy, s * 0.45f, p)
        val stroke = Paint(glyph).apply { style = Paint.Style.STROKE; strokeWidth = s * 0.16f; strokeCap = Paint.Cap.ROUND }
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0)
            val x1 = cx + (s * 0.7f) * Math.cos(a).toFloat()
            val y1 = cy + (s * 0.7f) * Math.sin(a).toFloat()
            val x2 = cx + (s * 0.95f) * Math.cos(a).toFloat()
            val y2 = cy + (s * 0.95f) * Math.sin(a).toFloat()
            c.drawLine(x1, y1, x2, y2, stroke)
        }
    }
}
