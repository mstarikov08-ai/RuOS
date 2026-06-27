package com.ruos.emergency.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.ruos.emergency.core.EmergencyActions

/**
 * Emergency SOS: a 5-second countdown that auto-calls 112 (loud alarm + vibration), then
 * texts the user's location to their emergency contacts. «Позвонить сейчас» fires
 * immediately; «Отмена» aborts. Works over the lock screen (showWhenLocked).
 */
class SosActivity : Activity() {

    private val main = Handler(Looper.getMainLooper())
    private var seconds = 5
    private var fired = false
    private var ringtone: android.media.Ringtone? = null
    private val d get() = resources.displayMetrics.density
    private fun dp(v: Float) = (v * d).toInt()

    private lateinit var countText: TextView
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true); setTurnScreenOn(true)
        setContentView(build())
        startAlarm()
        main.post(tick)
    }

    override fun onDestroy() { stopAlarm(); main.removeCallbacksAndMessages(null); super.onDestroy() }

    private fun build(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setBackgroundColor(0xFFB3140E.toInt()); setPadding(dp(28f), 0, dp(28f), 0)
        }
        root.addView(TextView(this).apply {
            text = "Экстренный вызов"; setTextColor(Color.WHITE); textSize = 26f; typeface = Fonts.bold
        })
        countText = TextView(this).apply {
            text = seconds.toString(); setTextColor(Color.WHITE); textSize = 110f; typeface = Fonts.bold; gravity = Gravity.CENTER
        }
        root.addView(countText)
        root.addView(TextView(this).apply {
            text = "Вызов службы 112 начнётся автоматически"; setTextColor(0xFFFFD7D5.toInt()); textSize = 15f; typeface = Fonts.regular
            gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(24f))
        })
        statusText = TextView(this).apply { setTextColor(Color.WHITE); textSize = 14f; typeface = Fonts.medium; gravity = Gravity.CENTER }
        root.addView(statusText)

        root.addView(button("Позвонить сейчас", Color.WHITE, 0xFFB3140E.toInt()) { fire() })
        root.addView(button("Медкарта", 0x33FFFFFF, Color.WHITE) {
            startActivity(Intent(this, MedicalIdViewActivity::class.java))
        })
        root.addView(button("Отмена", 0x00000000, Color.WHITE) { finish() })
        return root
    }

    private val tick = object : Runnable {
        override fun run() {
            if (fired) return
            countText.text = seconds.toString()
            if (seconds <= 0) { fire(); return }
            seconds--
            main.postDelayed(this, 1000)
        }
    }

    private fun fire() {
        if (fired) return
        fired = true
        stopAlarm()
        statusText.text = "Вызываем 112…"
        EmergencyActions.call112(this)
        val n = EmergencyActions.alertContacts(this)
        statusText.postDelayed({
            statusText.text = if (n > 0) "Контакты уведомлены ($n)" else "Экстренные контакты не заданы"
        }, 800)
    }

    private fun startAlarm() {
        runCatching {
            val am = getSystemService(AudioManager::class.java)
            am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
            val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(this, uri)?.apply {
                if (android.os.Build.VERSION.SDK_INT >= 28) isLooping = true; play()
            }
        }
        getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 300), 0))
    }

    private fun stopAlarm() {
        runCatching { ringtone?.stop() }; ringtone = null
        getSystemService(Vibrator::class.java)?.cancel()
    }

    private fun button(label: String, bg: Int, fg: Int, onTap: () -> Unit) = TextView(this).apply {
        text = label; setTextColor(fg); textSize = 17f; typeface = Fonts.medium; gravity = Gravity.CENTER
        background = if (bg == 0) null else GradientDrawable().apply { cornerRadius = dp(14f).toFloat(); setColor(bg) }
        setPadding(0, dp(13f), 0, dp(13f)); isClickable = true; setOnClickListener { onTap() }
        val lp = LinearLayout.LayoutParams(dp(280f), LinearLayout.LayoutParams.WRAP_CONTENT); lp.topMargin = dp(10f); layoutParams = lp
    }
}
