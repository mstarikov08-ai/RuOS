package com.ruos.settings.sections

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Bundle
import android.os.PowerManager
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import java.io.File

/**
 * Аккумулятор — real battery data: level, status, temperature, voltage, technology;
 * **battery health** (max-capacity % and cycle count from the power-supply sysfs, like a
 * battery-info app); a **low-power-mode** toggle; and **per-app drain** via
 * BatteryStatsManager (falls back to recent screen-time when the privileged API is
 * unavailable). Read-only system data, guarded so missing values degrade gracefully.
 */
class BatterySettingsActivity : Activity() {

    private val accent = Color.parseColor("#34C759")
    private val golos = Typeface.create("golos", Typeface.NORMAL)
    private val golosM = Typeface.create("golos-medium", Typeface.NORMAL)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(build())
    }

    private fun build(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#F2F2F7"))
            setPadding(0, dp(100), 0, dp(40))
        }
        root.addView(title("Аккумулятор"))

        val b = batteryIntent()
        val level = pct(b)
        root.addView(bigLevel(level, charging(b)))

        root.addView(label("СОСТОЯНИЕ"))
        root.addView(card(listOf(
            kv("Уровень", "$level%"),
            kv("Состояние", statusText(b)),
            kv("Температура", tempText(b)),
            kv("Напряжение", voltageText(b)),
            kv("Технология", b?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "—")
        )))

        root.addView(label("СОСТОЯНИЕ АККУМУЛЯТОРА"))
        val (health, cycles) = healthFromSysfs()
        root.addView(card(listOf(
            kv("Максимальная ёмкость", health),
            kv("Циклы зарядки", cycles),
            kv("Состояние", healthText(b))
        )))
        root.addView(note("Максимальная ёмкость — текущая полная ёмкость относительно заводской. " +
            "Со временем она снижается."))

        root.addView(label("РЕЖИМ ЭНЕРГОСБЕРЕЖЕНИЯ"))
        val pm = getSystemService(PowerManager::class.java)
        root.addView(card(listOf(switchRow("Режим энергосбережения", pm?.isPowerSaveMode == true) { on ->
            setPowerSave(on)
        })))
        root.addView(note("Снижает фоновую активность и анимацию, чтобы продлить работу."))

        root.addView(label("ЗАРЯДКА"))
        root.addView(card(listOf(switchRow("Ограничение заряда 80 %", chargeLimitEnabled()) { on ->
            setChargeLimit(on)
        })))
        root.addView(note("Останавливает зарядку на 80 %, чтобы замедлить износ аккумулятора. " +
            "Работает только на устройствах, где ядро поддерживает ограничение заряда."))

        root.addView(label("РАСХОД ПО ПРИЛОЖЕНИЯМ"))
        val apps = perAppDrain()
        if (apps.isEmpty()) root.addView(card(listOf(kv("Данные недоступны", "—"))))
        else root.addView(card(apps.map { kv(it.first, it.second) }))

        return ScrollView(this).apply { addView(root) }
    }

    // ── battery facts ─────────────────────────────────────────────────────────────

    private fun batteryIntent(): Intent? =
        registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

    private fun pct(b: Intent?): Int {
        val l = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val s = b?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        return if (l < 0) 0 else l * 100 / s
    }

    private fun charging(b: Intent?): Boolean {
        val st = b?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun statusText(b: Intent?) = when (b?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "Зарядка"
        BatteryManager.BATTERY_STATUS_FULL -> "Заряжен"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "Разрядка"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Не заряжается"
        else -> "—"
    }

    private fun tempText(b: Intent?): String {
        val t = b?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        return if (t < 0) "—" else "%.1f °C".format(t / 10f)
    }

    private fun voltageText(b: Intent?): String {
        val v = b?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
        return if (v < 0) "—" else "${v} мВ"
    }

    private fun healthText(b: Intent?) = when (b?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "Хорошее"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Перегрев"
        BatteryManager.BATTERY_HEALTH_DEAD -> "Изношен"
        BatteryManager.BATTERY_HEALTH_COLD -> "Холодный"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Перенапряжение"
        else -> "Норма"
    }

    /** Max capacity % = charge_full / charge_full_design, plus cycle_count, from sysfs. */
    private fun healthFromSysfs(): Pair<String, String> {
        val base = "/sys/class/power_supply/battery"
        val full = readLong("$base/charge_full")
        val design = readLong("$base/charge_full_design")
        val cycles = readLong("$base/cycle_count")
        val health = if (full != null && design != null && design > 0)
            "${(full * 100 / design)}%" else "недоступно"
        val cyclesText = cycles?.toString() ?: "недоступно"
        return health to cyclesText
    }

    private fun readLong(path: String): Long? =
        runCatching { File(path).readText().trim().toLong() }.getOrNull()

    private fun setPowerSave(on: Boolean) {
        val ok = runCatching {
            getSystemService(PowerManager::class.java)?.let { pm ->
                PowerManager::class.java.getMethod("setPowerSaveModeEnabled", Boolean::class.javaPrimitiveType)
                    .invoke(pm, on)
            }
        }.isSuccess
        if (!ok) runCatching {
            android.provider.Settings.Global.putInt(contentResolver, "low_power", if (on) 1 else 0)
        }
    }

    // ── charge limit (80 %) — device/kernel-dependent sysfs node ────────────────────
    // Pixel/Tensor kernels expose a charge-stop level; the node name varies by kernel, so we
    // probe a small set of known candidates. Guarded so it degrades to a no-op (and a remembered
    // preference) where the node is absent or SELinux blocks the write.
    private val chargeLimitNodes = listOf(
        "/sys/class/power_supply/battery/charge_control_limit",
        "/sys/devices/platform/google,charger/charge_stop_level",
        "/sys/class/power_supply/battery/charge_stop_level"
    )

    private fun chargeLimitPref() = getSharedPreferences("ruos_battery", Context.MODE_PRIVATE)

    /** True if the limit is currently on — from the live sysfs value if readable, else the pref. */
    private fun chargeLimitEnabled(): Boolean {
        for (n in chargeLimitNodes) {
            val v = readLong(n) ?: continue
            return v in 1..99          // any sub-100 stop level means limiting is active
        }
        return chargeLimitPref().getBoolean("charge_limit", false)
    }

    private fun setChargeLimit(on: Boolean) {
        val value = if (on) "80" else "100"
        var wrote = false
        for (n in chargeLimitNodes) {
            val f = File(n)
            if (!f.exists()) continue
            if (runCatching { f.writeText(value) }.isSuccess) { wrote = true; break }
        }
        chargeLimitPref().edit().putBoolean("charge_limit", on).apply()
        if (!wrote) toast("Устройство не поддерживает ограничение заряда")
    }

    private fun toast(s: String) =
        android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_SHORT).show()

    /** Top apps by consumed power via BatteryStatsManager (reflection); else recent usage time. */
    private fun perAppDrain(): List<Pair<String, String>> {
        reflectBatteryStats()?.let { if (it.isNotEmpty()) return it }
        return usageFallback()
    }

    private fun reflectBatteryStats(): List<Pair<String, String>>? = runCatching {
        val mgr = getSystemService("batterystats") ?: return null
        val stats = mgr.javaClass.getMethod("getBatteryUsageStats").invoke(mgr) ?: return null
        @Suppress("UNCHECKED_CAST")
        val consumers = stats.javaClass.getMethod("getUidBatteryConsumers").invoke(stats) as? List<Any> ?: return null
        val pm = packageManager
        consumers.mapNotNull { c ->
            val uid = c.javaClass.getMethod("getUid").invoke(c) as Int
            val power = c.javaClass.getMethod("getConsumedPower").invoke(c) as Double
            if (power <= 0.0) return@mapNotNull null
            val name = pm.getNameForUid(uid) ?: return@mapNotNull null
            val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(name, 0)).toString() }.getOrDefault(name)
            label to "%.1f мА·ч".format(power)
        }.sortedByDescending { it.second.takeWhile { ch -> ch.isDigit() || ch == '.' }.toDoubleOrNull() ?: 0.0 }.take(8)
    }.getOrNull()

    private fun usageFallback(): List<Pair<String, String>> = runCatching {
        val usm = getSystemService(android.app.usage.UsageStatsManager::class.java) ?: return emptyList()
        val end = System.currentTimeMillis(); val start = end - 24 * 3600_000L
        val stats = usm.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY, start, end) ?: return emptyList()
        val pm = packageManager
        stats.filter { it.totalTimeInForeground > 60_000 }
            .sortedByDescending { it.totalTimeInForeground }
            .take(8)
            .mapNotNull { u ->
                val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(u.packageName, 0)).toString() }.getOrNull() ?: return@mapNotNull null
                val min = u.totalTimeInForeground / 60_000
                label to (if (min >= 60) "${min / 60} ч ${min % 60} мин" else "$min мин")
            }
    }.getOrDefault(emptyList())

    // ── UI helpers ────────────────────────────────────────────────────────────────

    private fun bigLevel(level: Int, charging: Boolean): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(0, dp(8), 0, dp(16))
        addView(TextView(this@BatterySettingsActivity).apply {
            text = "$level%"; setTextColor(if (level <= 20 && !charging) Color.parseColor("#FF3B30") else accent)
            textSize = 56f; typeface = golosM
        })
        addView(TextView(this@BatterySettingsActivity).apply {
            text = if (charging) "Идёт зарядка" else "Аккумулятор"
            setTextColor(Color.GRAY); textSize = 15f; typeface = golos
        })
    }

    private fun kv(k: String, v: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(12))
        addView(TextView(this@BatterySettingsActivity).apply { text = k; textSize = 16f; setTextColor(Color.BLACK); typeface = golos },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(TextView(this@BatterySettingsActivity).apply { text = v; textSize = 16f; setTextColor(Color.parseColor("#8E8E93")); typeface = golos })
    }

    private fun switchRow(label: String, initial: Boolean, onChange: (Boolean) -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(8))
        addView(TextView(this@BatterySettingsActivity).apply { text = label; textSize = 16f; setTextColor(Color.BLACK); typeface = golos },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(Switch(this@BatterySettingsActivity).apply { isChecked = initial; setOnCheckedChangeListener { _, v -> onChange(v) } })
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
        text = t; textSize = 28f; setTextColor(Color.BLACK); typeface = Typeface.create(golos, Typeface.BOLD)
        setPadding(dp(20), dp(4), dp(20), dp(12))
    }
    private fun label(t: String) = TextView(this).apply {
        text = t; textSize = 13f; setTextColor(Color.parseColor("#6C6C70")); typeface = golosM; setPadding(dp(32), dp(14), dp(16), dp(6))
    }
    private fun note(t: String) = TextView(this).apply {
        text = t; textSize = 13f; setTextColor(Color.parseColor("#6C6C70")); typeface = golos; setPadding(dp(32), dp(6), dp(32), dp(4))
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
