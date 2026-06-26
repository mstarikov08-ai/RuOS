package com.ruos.notify.ui

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.ruos.notify.model.NotifSettings
import com.ruos.notify.util.Fonts

/** Per-app notification toggles, matching iOS's per-app notification screen. */
class AppNotifDetailActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra("pkg") ?: run { finish(); return }
        val settings = NotifSettings(this)
        val prefs = settings.forApp(pkg)
        val appName = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)

        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK) }
        root.addView(TextView(this).apply {
            text = appName; setTextColor(Color.WHITE); textSize = 28f; typeface = Fonts.bold
            setPadding(dp(16), dp(48), dp(16), dp(12))
        })

        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(col); root.addView(scroll)
        setContentView(root)

        col.addView(switchRow("Разрешить уведомления", prefs.allow) { prefs.allow = it; settings.save(pkg, prefs) })
        col.addView(switchRow("Постоянный баннер", prefs.persistent) { prefs.persistent = it; settings.save(pkg, prefs) })
        col.addView(switchRow("Звуки", prefs.sounds) { prefs.sounds = it; settings.save(pkg, prefs) })
        col.addView(switchRow("Наклейки (бейджи)", prefs.badges) { prefs.badges = it; settings.save(pkg, prefs) })
        col.addView(switchRow("Показывать на экране блокировки", prefs.lockScreen) { prefs.lockScreen = it; settings.save(pkg, prefs) })
        col.addView(switchRow("Группировать", prefs.grouping) { prefs.grouping = it; settings.save(pkg, prefs) })
    }

    private fun switchRow(title: String, initial: Boolean, onChange: (Boolean) -> Unit): View {
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
            addView(TextView(context).apply {
                text = title; setTextColor(Color.WHITE); textSize = 16f; typeface = Fonts.regular
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(Switch(context).apply {
                isChecked = initial
                setOnCheckedChangeListener { b, c -> b.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK); onChange(c) }
            })
        }
    }
}
