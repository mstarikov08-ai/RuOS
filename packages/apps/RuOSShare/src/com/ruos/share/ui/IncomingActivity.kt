package com.ruos.share.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.ruos.share.service.ReceiverService

/** AirDrop-style incoming prompt: «Имя устройства» хочет отправить N файлов — Принять? */
class IncomingActivity : Activity() {

    private val d get() = resources.displayMetrics.density
    private fun dp(v: Float) = (v * d).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sender = intent.getStringExtra("sender") ?: "Устройство"
        val summary = intent.getStringExtra("summary") ?: ""

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setBackgroundColor(0xCC000000.toInt()); setPadding(dp(28f), 0, dp(28f), 0)
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            background = GradientDrawable().apply { cornerRadius = dp(24f).toFloat(); setColor(0xF21C1C1E.toInt()) }
            setPadding(dp(24f), dp(26f), dp(24f), dp(18f))
        }
        card.addView(TextView(this).apply {
            text = "RuOS Share"; setTextColor(0xFF8E8E93.toInt()); textSize = 13f; typeface = Fonts.medium
        })
        card.addView(TextView(this).apply {
            text = "«$sender» хочет отправить файлы"; setTextColor(Color.WHITE); textSize = 19f; typeface = Fonts.medium
            gravity = Gravity.CENTER; setPadding(0, dp(12f), 0, dp(4f))
        })
        card.addView(TextView(this).apply {
            text = summary; setTextColor(0xFF8E8E93.toInt()); textSize = 14f; typeface = Fonts.regular
            setPadding(0, 0, 0, dp(18f))
        })
        card.addView(button("Принять", 0xFF34C759.toInt(), Color.WHITE) { ReceiverService.resolve(true); finish() })
        card.addView(button("Отклонить", 0x00000000, 0xFFFF453A.toInt()) { ReceiverService.resolve(false); finish() })

        root.addView(card, LinearLayout.LayoutParams(dp(300f), LinearLayout.LayoutParams.WRAP_CONTENT))
        setContentView(root)
    }

    override fun onBackPressed() { ReceiverService.resolve(false); super.onBackPressed() }

    private fun button(label: String, bg: Int, fg: Int, onTap: () -> Unit) = Button(this).apply {
        text = label; setTextColor(fg); typeface = Fonts.medium; isAllCaps = false; textSize = 16f
        background = if (bg == 0) null else GradientDrawable().apply { cornerRadius = dp(14f).toFloat(); setColor(bg) }
        setOnClickListener { onTap() }
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48f)); lp.topMargin = dp(8f); layoutParams = lp
    }
}
