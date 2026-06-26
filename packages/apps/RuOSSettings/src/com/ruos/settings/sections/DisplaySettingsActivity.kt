package com.ruos.settings.sections

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

class DisplaySettingsActivity : Activity() {

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

        col.addView(sectionLabel("ЯРКОСТЬ"))
        col.addView(brightnessCard())
        col.addView(spacer(20))

        col.addView(sectionLabel("ЧАСТОТА ОБНОВЛЕНИЯ"))
        col.addView(infoSection(listOf(
            "ProMotion (120 Гц)" to "Включено"
        )))
        col.addView(spacer(4))
        col.addView(sectionNote("RuOS поддерживает 120 Гц на всех совместимых устройствах Pixel."))
        col.addView(spacer(20))

        col.addView(sectionLabel("ВНЕШНИЙ ВИД"))
        col.addView(toggleSection(listOf(
            "Тёмный режим" to false,
            "True Tone" to true,
            "Night Shift" to false
        )))
        col.addView(spacer(20))

        col.addView(sectionLabel("МАСШТАБ ТЕКСТА"))
        col.addView(textScaleCard())

        scroll.addView(col)
        root.addView(scroll)
        return root
    }

    private fun brightnessCard(): View {
        val wrapper = FrameLayout(this).apply { setPadding(dp(16), 0, dp(16), 0) }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(Color.WHITE)
            }
        }
        val seek = SeekBar(this).apply {
            min = 0; max = 255
            progress = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
            progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#D94F3D"))
            thumbTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#D94F3D"))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    if (fromUser) Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, p)
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }
        card.addView(seek, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        wrapper.addView(card)
        return wrapper
    }

    private fun textScaleCard(): View {
        val wrapper = FrameLayout(this).apply { setPadding(dp(16), 0, dp(16), 0) }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(Color.WHITE)
            }
        }
        card.addView(SeekBar(this).apply {
            min = 85; max = 135; progress = 100
            progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#D94F3D"))
        }, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        wrapper.addView(card)
        return wrapper
    }

    private fun toggleSection(rows: List<Pair<String, Boolean>>): View {
        val wrapper = FrameLayout(this).apply { setPadding(dp(16), 0, dp(16), 0) }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(Color.WHITE)
            }
        }
        rows.forEachIndexed { i, (label, enabled) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(8), dp(16), dp(8))
            }
            row.addView(TextView(this).apply { text = label; textSize = 17f; setTextColor(Color.BLACK) },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(Switch(this).apply { isChecked = enabled })
            col.addView(row)
            if (i < rows.size - 1) {
                col.addView(View(this).apply { setBackgroundColor(Color.parseColor("#C6C6C8")) },
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).also { it.marginStart = dp(16) })
            }
        }
        wrapper.addView(col)
        return wrapper
    }

    private fun infoSection(rows: List<Pair<String, String>>): View {
        val wrapper = FrameLayout(this).apply { setPadding(dp(16), 0, dp(16), 0) }
        val col = LinearLayout(this).apply {
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
                setPadding(dp(16), dp(12), dp(16), dp(12))
            }
            row.addView(TextView(this).apply { text = label; textSize = 17f; setTextColor(Color.BLACK) },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(TextView(this).apply { text = value; textSize = 17f; setTextColor(Color.GRAY) })
            col.addView(row)
        }
        wrapper.addView(col)
        return wrapper
    }

    private fun sectionLabel(text: String) = TextView(this).apply {
        this.text = text; textSize = 13f; setTextColor(Color.parseColor("#6C6C70"))
        setPadding(dp(32), dp(4), dp(16), dp(4))
    }

    private fun sectionNote(text: String) = TextView(this).apply {
        this.text = text; textSize = 13f; setTextColor(Color.parseColor("#6C6C70"))
        setPadding(dp(32), dp(4), dp(32), dp(4))
    }

    private fun spacer(dp: Int) = View(this).also {
        it.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(dp))
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
