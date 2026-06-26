package com.ruos.standby.ui

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import com.ruos.standby.model.NightMode
import com.ruos.standby.model.StandbySettings

/** Settings → Режим ожидания. */
class StandbySettingsActivity : Activity() {

    private lateinit var s: StandbySettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        s = StandbySettings(this)
        render()
    }

    private fun render() {
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK) }
        root.addView(TextView(this).apply {
            text = "Режим ожидания"; setTextColor(Color.WHITE); textSize = 28f
            typeface = Typeface.create("sans-serif-bold", Typeface.NORMAL)
            setPadding(dp(16), dp(48), dp(16), dp(12))
        })
        root.addView(switchRow("Включить режим ожидания", s.enabled) { s.enabled = it })
        root.addView(navRow("Ночной режим", s.nightMode.ruName) { pickNight() })
        root.addView(switchRow("Показывать уведомления", s.showNotifications) { s.showNotifications = it })
        root.addView(switchRow("Пробуждение движением", s.motionToWake) { s.motionToWake = it })
        setContentView(root)
    }

    private fun pickNight() {
        val opts = NightMode.values()
        AlertDialog.Builder(this).setTitle("Ночной режим")
            .setSingleChoiceItems(opts.map { it.ruName }.toTypedArray(), opts.indexOf(s.nightMode)) { dlg, w ->
                s.nightMode = opts[w]; dlg.dismiss(); render()
            }.show()
    }

    private fun switchRow(title: String, initial: Boolean, onChange: (Boolean) -> Unit): View {
        val d = resources.displayMetrics.density
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply { setColor(Color.parseColor("#1C1C1E")) }
            setPadding((16 * d).toInt(), (8 * d).toInt(), (16 * d).toInt(), (8 * d).toInt())
            addView(TextView(context).apply { text = title; setTextColor(Color.WHITE); textSize = 16f },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(Switch(context).apply {
                isChecked = initial
                setOnCheckedChangeListener { b, c -> b.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK); onChange(c) }
            })
        }
    }

    private fun navRow(title: String, value: String, onClick: () -> Unit): View {
        val d = resources.displayMetrics.density
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply { setColor(Color.parseColor("#1C1C1E")) }
            setPadding((16 * d).toInt(), (14 * d).toInt(), (16 * d).toInt(), (14 * d).toInt())
            isClickable = true; setOnClickListener { performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK); onClick() }
            addView(TextView(context).apply { text = title; setTextColor(Color.WHITE); textSize = 16f },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(context).apply { text = value; setTextColor(Color.parseColor("#8E8E93")); textSize = 16f })
        }
    }
}
