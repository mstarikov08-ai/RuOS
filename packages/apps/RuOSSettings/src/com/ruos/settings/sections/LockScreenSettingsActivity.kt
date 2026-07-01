package com.ruos.settings.sections

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/**
 * «Экран блокировки» — pick the lock-screen clock style, choose which widgets appear under the
 * clock (battery, next alarm, weather) and toggle the always-on display. Writes the same
 * Settings.Secure keys the keyguard's LockScreenView reads (see SystemUI LockScreenStyle), so
 * changes apply live. Requires WRITE_SECURE_SETTINGS, which platform-signed RuOSSettings holds.
 */
class LockScreenSettingsActivity : Activity() {

    private val golos = Typeface.create("golos", Typeface.NORMAL)
    private val golosM = Typeface.create("golos-medium", Typeface.NORMAL)
    private val accent = Color.parseColor("#0A84FF")
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // Must match SystemUI LockScreenStyle.
    private val KEY_CLOCK = "ruos_clock_style"
    private val KEY_WIDGETS = "ruos_lock_widgets"
    private val KEY_AOD = "doze_always_on"

    private val clockNames = listOf("Тонкий", "Жирный", "С засечками", "Компактный")
    private val widgetTokens = listOf("battery" to "Заряд аккумулятора", "alarm" to "Следующий будильник", "weather" to "Погода")

    private lateinit var clockCol: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#F2F2F7"))
            setPadding(0, dp(60), 0, dp(40))
        }
        col.addView(title("Экран блокировки"))
        col.addView(note("Настройте вид часов, виджеты под часами и постоянный дисплей."))

        col.addView(sectionLabel("СТИЛЬ ЧАСОВ"))
        clockCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(clockCol)
        buildClockChoices()

        col.addView(sectionLabel("ВИДЖЕТЫ"))
        val active = currentWidgets().toMutableSet()
        col.addView(card(widgetTokens.map { (token, label) ->
            switchRow(label, active.contains(token)) { on ->
                if (on) active.add(token) else active.remove(token)
                writeSecureString(KEY_WIDGETS, active.joinToString(","))
            }
        }))

        col.addView(sectionLabel("ПОСТОЯННЫЙ ДИСПЛЕЙ"))
        col.addView(card(listOf(switchRow("Always-On Display", readInt(KEY_AOD, 0) == 1) { on ->
            writeSecureInt(KEY_AOD, if (on) 1 else 0)
        })))
        col.addView(note("Показывает часы и уведомления на выключенном экране. " +
            "Может увеличить расход аккумулятора."))

        setContentView(ScrollView(this).apply { addView(col) })
    }

    private fun buildClockChoices() {
        clockCol.removeAllViews()
        val current = readInt(KEY_CLOCK, 0)
        clockCol.addView(card(clockNames.mapIndexed { i, name ->
            checkRow(name, i == current) { writeSecureInt(KEY_CLOCK, i); buildClockChoices() }
        }))
    }

    private fun currentWidgets(): List<String> {
        val raw = runCatching { Settings.Secure.getString(contentResolver, KEY_WIDGETS) }.getOrNull()
        if (raw.isNullOrBlank()) return listOf("date")
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    // ── secure settings I/O ─────────────────────────────────────────────────────
    private fun readInt(key: String, def: Int) =
        runCatching { Settings.Secure.getInt(contentResolver, key, def) }.getOrDefault(def)

    private fun writeSecureInt(key: String, value: Int) {
        val ok = runCatching { Settings.Secure.putInt(contentResolver, key, value) }.isSuccess
        if (!ok) toast("Нет прав на изменение настроек")
    }

    private fun writeSecureString(key: String, value: String) {
        val ok = runCatching { Settings.Secure.putString(contentResolver, key, value) }.isSuccess
        if (!ok) toast("Нет прав на изменение настроек")
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    // ── UI helpers ──────────────────────────────────────────────────────────────
    private fun checkRow(label: String, selected: Boolean, onTap: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; isClickable = true
        setOnClickListener { onTap() }
        addView(TextView(this@LockScreenSettingsActivity).apply { text = label; setTextColor(Color.BLACK); textSize = 16f; typeface = golos },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(TextView(this@LockScreenSettingsActivity).apply {
            text = if (selected) "✓" else ""; setTextColor(accent); textSize = 18f; typeface = golosM
        })
    }

    private fun switchRow(label: String, initial: Boolean, onChange: (Boolean) -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        addView(TextView(this@LockScreenSettingsActivity).apply { text = label; setTextColor(Color.BLACK); textSize = 16f; typeface = golos },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(Switch(this@LockScreenSettingsActivity).apply { isChecked = initial; setOnCheckedChangeListener { _, v -> onChange(v) } })
    }

    private fun card(rows: List<View>): View {
        val cardCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(Color.WHITE) }
            setPadding(dp(14), dp(6), dp(14), dp(6))
        }
        rows.forEachIndexed { i, r ->
            cardCol.addView(r, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)))
            if (i < rows.size - 1) cardCol.addView(View(this).apply { setBackgroundColor(Color.parseColor("#E5E5EA")) }
                .also { it.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1) })
        }
        return LinearLayout(this).apply { setPadding(dp(16), 0, dp(16), 0); addView(cardCol,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)) }
    }

    private fun title(t: String) = TextView(this).apply {
        text = t; textSize = 28f; setTextColor(Color.BLACK); typeface = Typeface.create(golos, Typeface.BOLD); setPadding(dp(20), dp(4), dp(20), dp(12))
    }
    private fun sectionLabel(t: String) = TextView(this).apply {
        text = t; textSize = 13f; setTextColor(Color.parseColor("#6C6C70")); typeface = golosM; setPadding(dp(32), dp(16), dp(16), dp(6))
    }
    private fun note(t: String) = TextView(this).apply {
        text = t; textSize = 13f; setTextColor(Color.parseColor("#6C6C70")); typeface = golos; setPadding(dp(32), dp(6), dp(32), dp(8))
    }
}
