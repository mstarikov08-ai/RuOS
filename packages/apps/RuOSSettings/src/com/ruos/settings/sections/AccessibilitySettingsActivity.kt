package com.ruos.settings.sections

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/**
 * Универсальный доступ — real accessibility toggles wired to the system settings:
 * bold text, increased contrast, invert colours, colour filters (daltonizer), text size,
 * reduce motion (animation scales), mono audio. Plus shortcuts to enable the system
 * screen reader / voice control (those live in the secure system UI).
 *
 * Secure/Global writes need WRITE_SECURE_SETTINGS + WRITE_SETTINGS (held by this
 * platform-signed app); each write is guarded so it degrades gracefully.
 */
class AccessibilitySettingsActivity : Activity() {

    private val accent = Color.parseColor("#0A84FF")
    private val golos = Typeface.create("golos", Typeface.NORMAL)
    private val golosM = Typeface.create("golos-medium", Typeface.NORMAL)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(build())
    }

    private fun build(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#F2F2F7"))
            setPadding(0, dp(100), 0, dp(40))
        }
        col.addView(title("Универсальный доступ"))

        col.addView(label("ЗРЕНИЕ"))
        col.addView(card(listOf(
            switchRow("Жирный шрифт", getSecure(FONT_WEIGHT, 0) >= 300) { on -> putSecure(FONT_WEIGHT, if (on) 300 else 0) },
            switchRow("Увеличенный контраст", getSecure(HIGH_CONTRAST, 0) == 1) { on -> putSecure(HIGH_CONTRAST, if (on) 1 else 0) },
            switchRow("Инверсия цветов", getSecure(INVERSION, 0) == 1) { on -> putSecure(INVERSION, if (on) 1 else 0) },
            colorFilterRow()
        )))

        col.addView(label("РАЗМЕР ТЕКСТА"))
        col.addView(card(listOf(textScaleRow())))
        col.addView(note("Можно увеличить системный шрифт для удобства чтения."))

        col.addView(label("ДВИЖЕНИЕ"))
        col.addView(card(listOf(
            switchRow("Уменьшить движение", reduceMotionOn()) { on -> setReduceMotion(on) }
        )))
        col.addView(note("Отключает анимации перехода между экранами."))

        col.addView(label("СЛУХ"))
        col.addView(card(listOf(
            switchRow("Моно-аудио", getSystem(MASTER_MONO, 0) == 1) { on -> putSystem(MASTER_MONO, if (on) 1 else 0) },
            linkRow("Слуховые аппараты") { openSystem(Settings.ACTION_ACCESSIBILITY_SETTINGS) }
        )))

        col.addView(label("ОЗВУЧИВАНИЕ И УПРАВЛЕНИЕ"))
        col.addView(card(listOf(
            linkRow("Экранный диктор (озвучивание)") { openSystem(Settings.ACTION_ACCESSIBILITY_SETTINGS) },
            linkRow("Голосовое управление") { openSystem(Settings.ACTION_ACCESSIBILITY_SETTINGS) }
        )))
        col.addView(note("Экранный диктор и голосовое управление включаются в системном " +
            "разделе универсального доступа."))

        return ScrollView(this).apply { addView(col) }
    }

    // ── colour filter (daltonizer) ──────────────────────────────────────────────────

    private val filterModes = listOf(-1, 0, 12, 11, 13)   // off / mono / protan / deuter / tritan
    private val filterLabels = listOf("Выкл.", "Оттенки серого", "Протанопия (красный)",
        "Дейтеранопия (зелёный)", "Тританопия (синий)")

    private fun colorFilterRow(): View {
        val on = getSecure(DALTONIZER_ON, 0) == 1
        val mode = if (on) getSecure(DALTONIZER, 0) else -1
        var idx = filterModes.indexOf(mode).coerceAtLeast(0)
        val value = TextView(this).apply { text = filterLabels[idx]; setTextColor(Color.parseColor("#8E8E93")); textSize = 16f; typeface = golos }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(12))
            isClickable = true
            setOnClickListener {
                idx = (idx + 1) % filterModes.size
                value.text = filterLabels[idx]
                val m = filterModes[idx]
                if (m < 0) putSecure(DALTONIZER_ON, 0)
                else { putSecure(DALTONIZER, m); putSecure(DALTONIZER_ON, 1) }
            }
            addView(TextView(this@AccessibilitySettingsActivity).apply { text = "Светофильтры"; textSize = 16f; setTextColor(Color.BLACK); typeface = golos },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(value)
        }
    }

    // ── reduce motion = animation scales 0 ────────────────────────────────────────────

    private fun reduceMotionOn(): Boolean =
        runCatching { Settings.Global.getFloat(contentResolver, Settings.Global.WINDOW_ANIMATION_SCALE) == 0f }.getOrDefault(false)

    private fun setReduceMotion(on: Boolean) {
        val v = if (on) 0f else 1f
        listOf(Settings.Global.WINDOW_ANIMATION_SCALE, Settings.Global.TRANSITION_ANIMATION_SCALE,
            Settings.Global.ANIMATOR_DURATION_SCALE).forEach { key ->
            runCatching { Settings.Global.putFloat(contentResolver, key, v) }
                .onFailure { toast("Нет прав (WRITE_SECURE_SETTINGS)") }
        }
    }

    private fun textScaleRow(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(8))
        val cur = runCatching { Settings.System.getFloat(contentResolver, Settings.System.FONT_SCALE) }.getOrDefault(1f)
        addView(SeekBar(this@AccessibilitySettingsActivity).apply {
            max = 60; progress = ((cur - 0.85f) * 100).toInt().coerceIn(0, 60)
            progressTintList = ColorStateList.valueOf(accent); thumbTintList = ColorStateList.valueOf(accent)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    if (fromUser) runCatching { Settings.System.putFloat(contentResolver, Settings.System.FONT_SCALE, 0.85f + p / 100f) }
                        .onFailure { toast("Нет прав на изменение размера текста") }
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }, LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    // ── settings helpers ──────────────────────────────────────────────────────────────

    private fun getSecure(k: String, def: Int) = runCatching { Settings.Secure.getInt(contentResolver, k, def) }.getOrDefault(def)
    private fun putSecure(k: String, v: Int) { runCatching { Settings.Secure.putInt(contentResolver, k, v) }.onFailure { toast("Нет прав (WRITE_SECURE_SETTINGS)") } }
    private fun getSystem(k: String, def: Int) = runCatching { Settings.System.getInt(contentResolver, k, def) }.getOrDefault(def)
    private fun putSystem(k: String, v: Int) { runCatching { Settings.System.putInt(contentResolver, k, v) }.onFailure { toast("Нет прав") } }

    private fun openSystem(action: String) { runCatching { startActivity(Intent(action)) } }
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    // ── UI ─────────────────────────────────────────────────────────────────────────────

    private fun switchRow(label: String, initial: Boolean, onChange: (Boolean) -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(8))
        addView(TextView(this@AccessibilitySettingsActivity).apply { text = label; textSize = 16f; setTextColor(Color.BLACK); typeface = golos },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(Switch(this@AccessibilitySettingsActivity).apply { isChecked = initial; setOnCheckedChangeListener { _, v -> onChange(v) } })
    }

    private fun linkRow(label: String, onTap: () -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(12))
        isClickable = true; setOnClickListener { onTap() }
        addView(TextView(this@AccessibilitySettingsActivity).apply { text = label; textSize = 16f; setTextColor(Color.BLACK); typeface = golos },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(TextView(this@AccessibilitySettingsActivity).apply { text = "›"; textSize = 20f; setTextColor(Color.parseColor("#C7C7CC")) })
    }

    private fun card(rows: List<View>): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(Color.WHITE) }
        }
        rows.forEachIndexed { i, r ->
            col.addView(r)
            if (i < rows.size - 1) col.addView(View(this).apply { setBackgroundColor(Color.parseColor("#E5E5EA")) }
                .also { it.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).also { p -> p.marginStart = dp(16) } })
        }
        return LinearLayout(this).apply { setPadding(dp(16), 0, dp(16), 0); addView(col,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)) }
    }

    private fun title(t: String) = TextView(this).apply {
        text = t; textSize = 28f; setTextColor(Color.BLACK); typeface = Typeface.create(golos, Typeface.BOLD); setPadding(dp(20), dp(4), dp(20), dp(12))
    }
    private fun label(t: String) = TextView(this).apply {
        text = t; textSize = 13f; setTextColor(Color.parseColor("#6C6C70")); typeface = golosM; setPadding(dp(32), dp(14), dp(16), dp(6))
    }
    private fun note(t: String) = TextView(this).apply {
        text = t; textSize = 13f; setTextColor(Color.parseColor("#6C6C70")); typeface = golos; setPadding(dp(32), dp(6), dp(32), dp(4))
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val FONT_WEIGHT = "font_weight_adjustment"
        private const val HIGH_CONTRAST = "high_text_contrast_enabled"
        private const val INVERSION = "accessibility_display_inversion_enabled"
        private const val DALTONIZER_ON = "accessibility_display_daltonizer_enabled"
        private const val DALTONIZER = "accessibility_display_daltonizer"
        private const val MASTER_MONO = "master_mono"
    }
}
