package com.ruos.notify.ui

import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.ruos.notify.util.Fonts

/**
 * Settings → Уведомления: list of apps; tap one to edit its per-app preferences.
 * Launched from RuOSSettings (or directly).
 */
class NotificationSettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK)
        }
        root.addView(TextView(this).apply {
            text = "Уведомления"; setTextColor(Color.WHITE); textSize = 30f; typeface = Fonts.bold
            setPadding(dp(16), dp(48), dp(16), dp(12))
        })

        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(col)
        root.addView(scroll)
        setContentView(root)

        val pm = packageManager
        val apps = pm.getInstalledApplications(0)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
        apps.forEach { col.addView(appRow(it)) }
    }

    private fun appRow(ai: ApplicationInfo): View {
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val pm = packageManager
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(10))
            isClickable = true
            setOnClickListener {
                startActivity(Intent(this@NotificationSettingsActivity, AppNotifDetailActivity::class.java)
                    .putExtra("pkg", ai.packageName))
            }
            addView(ImageView(context).apply { setImageDrawable(pm.getApplicationIcon(ai)) },
                LinearLayout.LayoutParams(dp(36), dp(36)).also { it.marginEnd = dp(14) })
            addView(TextView(context).apply {
                text = pm.getApplicationLabel(ai); setTextColor(Color.WHITE); textSize = 16f; typeface = Fonts.regular
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
    }
}
