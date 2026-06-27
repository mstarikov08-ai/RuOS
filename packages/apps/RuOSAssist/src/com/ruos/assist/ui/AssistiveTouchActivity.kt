package com.ruos.assist.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * AssistiveTouch control panel: explains the floating button and enables/disables it by
 * opening the system accessibility settings for the RuOS service. Shows live on/off state.
 */
class AssistiveTouchActivity : Activity() {

    private val service = "com.ruos.assist/com.ruos.assist.service.AssistiveTouchService"
    private val d get() = resources.displayMetrics.density
    private fun dp(v: Float) = (v * d).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(build())
    }

    override fun onResume() { super.onResume(); setContentView(build()) }

    private fun build(): View {
        val on = isEnabled()
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK); setPadding(dp(16f), dp(48f), dp(16f), dp(40f))
        }
        col.addView(TextView(this).apply {
            text = "AssistiveTouch"; setTextColor(Color.WHITE); textSize = 30f; typeface = Fonts.bold; setPadding(0, 0, 0, dp(4f))
        })
        col.addView(note("Плавающая кнопка для управления одной рукой: «Домой», «Назад», " +
            "многозадачность, Пункт управления, снимок экрана и блокировка — без аппаратных кнопок."))

        // status card
        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply { cornerRadius = dp(12f).toFloat(); setColor(0xFF1C1C1E.toInt()) }
            setPadding(dp(14f), dp(14f), dp(14f), dp(14f))
            addView(TextView(this@AssistiveTouchActivity).apply {
                text = if (on) "Включено" else "Выключено"
                setTextColor(if (on) 0xFF34C759.toInt() else 0xFF8E8E93.toInt()); textSize = 17f; typeface = Fonts.medium
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(10f) })

        col.addView(actionButton(if (on) "Открыть настройки доступа" else "Включить AssistiveTouch") {
            runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        col.addView(note("Кнопка работает как служба специальных возможностей. Включите «RuOS " +
            "AssistiveTouch» в системном разделе. Перетаскивайте кнопку в любое место экрана."))

        return ScrollView(this).apply { addView(col) }
    }

    private fun isEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        for (s in splitter) if (s.equals(service, ignoreCase = true)) return true
        return false
    }

    private fun actionButton(label: String, onTap: () -> Unit) = TextView(this).apply {
        text = label; setTextColor(Color.WHITE); textSize = 16f; typeface = Fonts.medium; gravity = Gravity.CENTER
        background = GradientDrawable().apply { cornerRadius = dp(12f).toFloat(); setColor(0xFF0A84FF.toInt()) }
        setPadding(0, dp(14f), 0, dp(14f)); isClickable = true; setOnClickListener { onTap() }
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); lp.topMargin = dp(12f); layoutParams = lp
    }
    private fun note(t: String) = TextView(this).apply {
        text = t; setTextColor(0xFF8E8E93.toInt()); textSize = 13f; typeface = Fonts.regular; setPadding(dp(2f), dp(8f), dp(2f), 0)
    }
}
