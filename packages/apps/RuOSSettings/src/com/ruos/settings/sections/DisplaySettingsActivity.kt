package com.ruos.settings.sections

import android.app.Activity
import android.app.TimePickerDialog
import android.app.UiModeManager
import android.content.res.ColorStateList
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
import android.widget.Toast

/**
 * Экран и яркость — real controls wired to system settings:
 *   • brightness + adaptive brightness (SCREEN_BRIGHTNESS / _MODE)
 *   • appearance Light / Dark / Auto (UiModeManager night mode)
 *   • Night Shift on/off + schedule + warmth (Night Display secure settings)
 *   • auto-rotate (ACCELEROMETER_ROTATION)
 *   • text size (FONT_SCALE)
 *
 * Secure / system writes need WRITE_SETTINGS + WRITE_SECURE_SETTINGS, held by this
 * platform-signed app. Each write is guarded so it degrades gracefully if not granted.
 */
class DisplaySettingsActivity : Activity() {

    private val accent = Color.parseColor("#D94F3D")
    private val uiMode by lazy { getSystemService(UiModeManager::class.java) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUI())
    }

    private fun buildUI(): View {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#F2F2F7")) }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(0, dp(100), 0, dp(40))
        }

        col.addView(title("Экран и яркость"))

        col.addView(sectionLabel("ЯРКОСТЬ"))
        col.addView(card(listOf(
            brightnessRow(),
            switchRow("Автояркость", isAutoBrightness()) { on ->
                putSystemInt(Settings.System.SCREEN_BRIGHTNESS_MODE, if (on) 1 else 0)
            }
        )))
        col.addView(spacer(20))

        col.addView(sectionLabel("ВНЕШНИЙ ВИД"))
        col.addView(appearanceCard())
        col.addView(sectionNote("«Авто» переключает светлую и тёмную тему по закату и рассвету."))
        col.addView(spacer(20))

        col.addView(sectionLabel("НОЧНОЙ РЕЖИМ (NIGHT SHIFT)"))
        col.addView(nightShiftCard())
        col.addView(sectionNote("Тёплые тона вечером снижают нагрузку на глаза."))
        col.addView(spacer(20))

        col.addView(sectionLabel("ПОВОРОТ"))
        col.addView(card(listOf(
            switchRow("Автоповорот экрана", isAutoRotate()) { on ->
                putSystemInt(Settings.System.ACCELEROMETER_ROTATION, if (on) 1 else 0)
            }
        )))
        col.addView(spacer(20))

        col.addView(sectionLabel("РАЗМЕР ТЕКСТА"))
        col.addView(card(listOf(textScaleRow())))
        col.addView(spacer(20))

        col.addView(sectionLabel("ЧАСТОТА ОБНОВЛЕНИЯ"))
        col.addView(card(listOf(infoRow("ProMotion (120 Гц)", "Включено"))))

        val scroll = ScrollView(this).apply { addView(col) }
        root.addView(scroll)
        return root
    }

    // ── appearance (light / dark / auto) ──────────────────────────────────────────

    private fun appearanceCard(): View {
        val options = listOf("Светлая", "Тёмная", "Авто")
        var selected = when (runCatching { uiMode.nightMode }.getOrDefault(UiModeManager.MODE_NIGHT_NO)) {
            UiModeManager.MODE_NIGHT_YES -> 1
            UiModeManager.MODE_NIGHT_CUSTOM, UiModeManager.MODE_NIGHT_AUTO -> 2
            else -> 0
        }
        val pills = ArrayList<TextView>()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        fun paint() = pills.forEachIndexed { i, p ->
            p.setBackgroundColor(0); p.background = GradientDrawable().apply {
                cornerRadius = dp(9).toFloat(); setColor(if (i == selected) accent else Color.parseColor("#EFEFF4"))
            }
            p.setTextColor(if (i == selected) Color.WHITE else Color.BLACK)
        }
        options.forEachIndexed { i, label ->
            val pill = TextView(this).apply {
                text = label; textSize = 15f; gravity = Gravity.CENTER
                setPadding(0, dp(10), 0, dp(10)); isClickable = true
                setOnClickListener { selected = i; paint(); applyAppearance(i) }
            }
            pills.add(pill)
            row.addView(pill, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also {
                it.setMargins(dp(4), 0, dp(4), 0)
            })
        }
        paint()
        return cardWrap(row)
    }

    private fun applyAppearance(index: Int) {
        runCatching {
            when (index) {
                0 -> uiMode.setNightMode(UiModeManager.MODE_NIGHT_NO)
                1 -> uiMode.setNightMode(UiModeManager.MODE_NIGHT_YES)
                2 -> {
                    uiMode.setNightMode(UiModeManager.MODE_NIGHT_CUSTOM)
                    uiMode.setNightModeCustomType(UiModeManager.MODE_NIGHT_CUSTOM_TYPE_SCHEDULE)
                }
            }
        }.onFailure { toast("Нет прав на смену темы") }
    }

    // ── Night Shift (Night Display) ────────────────────────────────────────────────

    private fun nightShiftCard(): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(rowSwitch("Включить", getSecureInt(NIGHT_ACTIVATED, 0) == 1) { on ->
            putSecureInt(NIGHT_ACTIVATED, if (on) 1 else 0)
        })
        col.addView(divider())

        // schedule: Выкл / закат-рассвет / свой
        val schedLabels = listOf("Выкл.", "От заката до рассвета", "Свой график")
        val schedModes = listOf(0, 2, 1)
        var sel = schedModes.indexOf(getSecureInt(NIGHT_AUTO_MODE, 0)).coerceAtLeast(0)
        val schedValue = TextView(this).apply { text = schedLabels[sel]; setTextColor(Color.GRAY); textSize = 16f }
        val customTimes = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = if (sel == 2) View.VISIBLE else View.GONE }
        val schedRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12)); isClickable = true
            setOnClickListener {
                sel = (sel + 1) % 3
                schedValue.text = schedLabels[sel]
                putSecureInt(NIGHT_AUTO_MODE, schedModes[sel])
                customTimes.visibility = if (sel == 2) View.VISIBLE else View.GONE
            }
            addView(TextView(this@DisplaySettingsActivity).apply { text = "Расписание"; textSize = 17f; setTextColor(Color.BLACK) },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(schedValue)
        }
        col.addView(schedRow); col.addView(divider())

        customTimes.addView(timeRow("Включать в", NIGHT_START, 22 * 60))
        customTimes.addView(timeRow("Выключать в", NIGHT_END, 7 * 60))
        col.addView(customTimes)

        // warmth slider
        col.addView(divider())
        val warmth = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(10), dp(16), dp(10))
        }
        warmth.addView(TextView(this).apply { text = "Тёплость"; textSize = 14f; setTextColor(Color.GRAY) })
        val curK = getSecureInt(NIGHT_TEMP, 3500).coerceIn(2700, 4800)
        warmth.addView(SeekBar(this).apply {
            max = 100; progress = 100 - ((curK - 2700) * 100 / (4800 - 2700))  // right = warmer
            progressTintList = ColorStateList.valueOf(accent)
            thumbTintList = ColorStateList.valueOf(accent)
            setOnSeekBarChangeListener(simpleSeek { p ->
                val k = 4800 - (p * (4800 - 2700) / 100)
                putSecureInt(NIGHT_TEMP, k)
            })
        }, LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        col.addView(warmth)

        return cardWrap(col)
    }

    private fun timeRow(label: String, key: String, defMin: Int): View {
        val value = TextView(this).apply {
            text = fmtTime(getSecureInt(key, defMin * 60_000) / 60_000)
            setTextColor(Color.GRAY); textSize = 16f
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12)); isClickable = true
            setOnClickListener {
                val cur = getSecureInt(key, defMin * 60_000) / 60_000
                TimePickerDialog(this@DisplaySettingsActivity, { _, h, m ->
                    val min = h * 60 + m
                    putSecureInt(key, min * 60_000)
                    value.text = fmtTime(min)
                }, cur / 60, cur % 60, true).show()
            }
            addView(TextView(this@DisplaySettingsActivity).apply { text = label; textSize = 17f; setTextColor(Color.BLACK) },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(value)
        }
    }

    // ── rows / cards ──────────────────────────────────────────────────────────────

    private fun brightnessRow(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(8), dp(16), dp(8))
        addView(SeekBar(this@DisplaySettingsActivity).apply {
            max = 255
            progress = getSystemInt(Settings.System.SCREEN_BRIGHTNESS, 128)
            progressTintList = ColorStateList.valueOf(accent); thumbTintList = ColorStateList.valueOf(accent)
            setOnSeekBarChangeListener(simpleSeek { p -> putSystemInt(Settings.System.SCREEN_BRIGHTNESS, p) })
        }, LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun textScaleRow(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(8))
        val cur = runCatching { Settings.System.getFloat(contentResolver, Settings.System.FONT_SCALE) }.getOrDefault(1f)
        addView(SeekBar(this@DisplaySettingsActivity).apply {
            max = 50; progress = ((cur - 0.85f) * 100).toInt().coerceIn(0, 50)
            progressTintList = ColorStateList.valueOf(accent); thumbTintList = ColorStateList.valueOf(accent)
            setOnSeekBarChangeListener(simpleSeek { p ->
                val scale = 0.85f + p / 100f
                runCatching { Settings.System.putFloat(contentResolver, Settings.System.FONT_SCALE, scale) }
                    .onFailure { toast("Нет прав на изменение размера текста") }
            })
        }, LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun switchRow(label: String, initial: Boolean, onChange: (Boolean) -> Unit) = rowSwitch(label, initial, onChange)

    private fun rowSwitch(label: String, initial: Boolean, onChange: (Boolean) -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
            addView(TextView(this@DisplaySettingsActivity).apply { text = label; textSize = 17f; setTextColor(Color.BLACK) },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(Switch(this@DisplaySettingsActivity).apply {
                isChecked = initial
                setOnCheckedChangeListener { _, v -> onChange(v) }
            })
        }

    private fun infoRow(label: String, value: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(12))
        addView(TextView(this@DisplaySettingsActivity).apply { text = label; textSize = 17f; setTextColor(Color.BLACK) },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(TextView(this@DisplaySettingsActivity).apply { text = value; textSize = 17f; setTextColor(Color.GRAY) })
    }

    private fun card(rows: List<View>): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        rows.forEachIndexed { i, r -> col.addView(r); if (i < rows.size - 1) col.addView(divider()) }
        return cardWrap(col)
    }

    /** Inset white rounded card (the white background sits on [inner], not the full frame). */
    private fun cardWrap(inner: View): View {
        inner.background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(Color.WHITE) }
        return FrameLayout(this).apply {
            setPadding(dp(16), 0, dp(16), 0)
            addView(inner, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun divider() = View(this).apply { setBackgroundColor(Color.parseColor("#E5E5EA")) }
        .also { it.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).also { p -> p.marginStart = dp(16) } }

    // ── settings helpers ──────────────────────────────────────────────────────────

    private fun isAutoBrightness() = getSystemInt(Settings.System.SCREEN_BRIGHTNESS_MODE, 0) == 1
    private fun isAutoRotate() = getSystemInt(Settings.System.ACCELEROMETER_ROTATION, 1) == 1

    private fun getSystemInt(key: String, def: Int) =
        runCatching { Settings.System.getInt(contentResolver, key, def) }.getOrDefault(def)
    private fun putSystemInt(key: String, v: Int) =
        runCatching { Settings.System.putInt(contentResolver, key, v) }.onFailure { toast("Нет прав на изменение") }.let {}
    private fun getSecureInt(key: String, def: Int) =
        runCatching { Settings.Secure.getInt(contentResolver, key, def) }.getOrDefault(def)
    private fun putSecureInt(key: String, v: Int) =
        runCatching { Settings.Secure.putInt(contentResolver, key, v) }.onFailure { toast("Нет прав (WRITE_SECURE_SETTINGS)") }.let {}

    private fun simpleSeek(onProgress: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) { if (fromUser) onProgress(p) }
        override fun onStartTrackingTouch(sb: SeekBar) {}
        override fun onStopTrackingTouch(sb: SeekBar) {}
    }

    private fun fmtTime(min: Int) = "%02d:%02d".format(min / 60, min % 60)
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    // ── small UI helpers ────────────────────────────────────────────────────────────

    private fun title(t: String) = TextView(this).apply {
        text = t; textSize = 28f; setTextColor(Color.BLACK)
        setTypeface(null, android.graphics.Typeface.BOLD); setPadding(dp(20), dp(4), dp(20), dp(12))
    }
    private fun sectionLabel(text: String) = TextView(this).apply {
        this.text = text; textSize = 13f; setTextColor(Color.parseColor("#6C6C70")); setPadding(dp(32), dp(4), dp(16), dp(4))
    }
    private fun sectionNote(text: String) = TextView(this).apply {
        this.text = text; textSize = 13f; setTextColor(Color.parseColor("#6C6C70")); setPadding(dp(32), dp(4), dp(32), dp(4))
    }
    private fun spacer(v: Int) = View(this).also { it.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(v)) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        // @hide Night Display secure keys (stable AOSP names; written with WRITE_SECURE_SETTINGS)
        private const val NIGHT_ACTIVATED = "night_display_activated"
        private const val NIGHT_AUTO_MODE = "night_display_auto_mode"          // 0 none, 1 custom, 2 twilight
        private const val NIGHT_START = "night_display_custom_start_time"       // ms from midnight
        private const val NIGHT_END = "night_display_custom_end_time"
        private const val NIGHT_TEMP = "night_display_color_temperature"        // Kelvin
    }
}
