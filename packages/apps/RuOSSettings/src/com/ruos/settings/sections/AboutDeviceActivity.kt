package com.ruos.settings.sections

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * About Device screen.
 * Tapping Build Number 7 times unlocks Developer Mode — exactly as AOSP,
 * but we gate it here and show a RuOS-branded toast sequence.
 */
class AboutDeviceActivity : Activity() {

    private var buildTaps = 0
    private var devModeUnlocked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUI())
    }

    private fun buildUI(): View {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#F2F2F7")) }
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(100), 0, dp(40))
        }

        // RuOS branding at top
        val brandCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, dp(20), 0, dp(30))
        }
        brandCol.addView(TextView(this).apply {
            text = "RuOS"
            textSize = 48f
            setTextColor(Color.parseColor("#D94F3D"))
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.create("sans-serif-thin", android.graphics.Typeface.NORMAL)
        })
        brandCol.addView(TextView(this).apply {
            text = "версия 1.0"
            textSize = 16f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
        })
        col.addView(brandCol)

        // Info rows
        col.addView(infoSection(listOf(
            Pair("Имя устройства", Build.MODEL),
            Pair("Версия RuOS", "1.0 (RuOS-1.0-DEV)"),
            Pair("Версия Android", Build.VERSION.RELEASE),
            Pair("Уровень патча", Build.VERSION.SECURITY_PATCH),
            Pair("Модель", Build.MODEL),
            Pair("Производитель", "Google × RuOS"),
            Pair("Серийный номер", Build.SERIAL.take(4) + "●●●●●●●●")
        )))
        col.addView(spacer(20))

        // Build number — tappable for dev mode
        col.addView(buildNumberRow())

        scroll.addView(col)
        root.addView(scroll)
        return root
    }

    private fun buildNumberRow(): View {
        val wrapper = FrameLayout(this).apply { setPadding(dp(16), 0, dp(16), 0) }
        val section = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(Color.WHITE)
            }
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            isFocusable = true
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setOnClickListener { onBuildTap() }
        }
        row.addView(TextView(this).apply {
            text = "Номер сборки"
            textSize = 15f
            setTextColor(Color.GRAY)
        })
        row.addView(TextView(this).apply {
            text = "RuOS-1.0-DEV"
            textSize = 17f
            setTextColor(Color.BLACK)
        })
        section.addView(row)
        wrapper.addView(section)
        return wrapper
    }

    private fun onBuildTap() {
        if (devModeUnlocked) return
        buildTaps++
        val remaining = 7 - buildTaps
        when {
            remaining > 0 -> {
                android.widget.Toast.makeText(this,
                    "До включения режима разработчика осталось $remaining нажатий",
                    android.widget.Toast.LENGTH_SHORT).show()
            }
            else -> {
                devModeUnlocked = true
                android.provider.Settings.Global.putInt(contentResolver,
                    android.provider.Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1)
                android.widget.Toast.makeText(this,
                    "Режим разработчика включён",
                    android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun infoSection(rows: List<Pair<String, String>>): View {
        val wrapper = FrameLayout(this).apply { setPadding(dp(16), 0, dp(16), 0) }
        val section = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(Color.WHITE)
            }
        }
        rows.forEachIndexed { i, (label, value) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(11), dp(16), dp(11))
            }
            row.addView(TextView(this).apply {
                text = label; textSize = 17f; setTextColor(Color.BLACK)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(TextView(this).apply {
                text = value; textSize = 17f; setTextColor(Color.GRAY)
            })
            section.addView(row)
            if (i < rows.size - 1) {
                section.addView(View(this).apply {
                    setBackgroundColor(Color.parseColor("#C6C6C8"))
                }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).also {
                    it.marginStart = dp(16)
                })
            }
        }
        wrapper.addView(section)
        return wrapper
    }

    private fun spacer(dp: Int) = View(this).also {
        it.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(dp))
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
