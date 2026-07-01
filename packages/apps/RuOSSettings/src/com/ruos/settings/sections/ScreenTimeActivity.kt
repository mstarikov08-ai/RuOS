package com.ruos.settings.sections

import android.app.Activity
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Calendar

/**
 * «Время использования» — a Digital-Wellbeing / Screen-Time dashboard: today's total foreground
 * time, a 7-day daily bar chart, and the top apps by time today. Data comes from
 * [UsageStatsManager] (RuOS holds PACKAGE_USAGE_STATS). If usage access isn't granted the screen
 * offers a shortcut to the system settings page and shows an empty state rather than crashing.
 */
class ScreenTimeActivity : Activity() {

    private val golos = Typeface.create("golos", Typeface.NORMAL)
    private val golosM = Typeface.create("golos-medium", Typeface.NORMAL)
    private val accent = 0xFF5E5CE6.toInt()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#F2F2F7"))
            setPadding(0, dp(100), 0, dp(40))
        }
        col.addView(title("Время использования"))

        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        val perApp = todayPerApp(usm)
        if (perApp == null) {
            col.addView(note("Нет доступа к статистике использования."))
            col.addView(card(listOf(clickRow("Открыть настройки доступа") {
                runCatching { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            })))
            setContentView(ScrollView(this).apply { addView(col) })
            return
        }

        val todayTotal = perApp.sumOf { it.second }
        col.addView(bigTime(todayTotal))
        col.addView(label("ПОСЛЕДНИЕ 7 ДНЕЙ"))
        col.addView(weekChart(usm))

        col.addView(label("ЧАЩЕ ВСЕГО"))
        if (perApp.isEmpty()) col.addView(card(listOf(kv("Нет данных за сегодня", ""))))
        else {
            val max = perApp.first().second.coerceAtLeast(1)
            col.addView(card(perApp.take(10).map { (name, ms) -> appBar(name, ms, max) }))
        }
        col.addView(note("Данные о времени использования хранятся только на устройстве."))

        setContentView(ScrollView(this).apply { addView(col) })
    }

    /** Foreground time per app for today (midnight→now). null = no usage-access permission. */
    private fun todayPerApp(usm: UsageStatsManager?): List<Pair<String, Long>>? {
        usm ?: return null
        val start = startOfToday()
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, now)
        if (stats == null || stats.isEmpty()) {
            // Empty can mean either a fresh day or no access. Treat a totally empty result as
            // "no access" only when even the aggregate is empty; otherwise it's a real empty day.
            return if (stats == null) null else emptyList()
        }
        val pm = packageManager
        // A package can appear in multiple buckets; sum foreground time.
        val byPkg = HashMap<String, Long>()
        for (u in stats) if (u.totalTimeInForeground > 0) byPkg[u.packageName] = (byPkg[u.packageName] ?: 0L) + u.totalTimeInForeground
        return byPkg.entries
            .filter { it.value >= 1000 }
            .mapNotNull { (pkg, ms) ->
                val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrNull() ?: return@mapNotNull null
                label to ms
            }
            .sortedByDescending { it.second }
    }

    /** Total foreground time per day for the last 7 days (oldest→newest). */
    private fun weekTotals(usm: UsageStatsManager): List<Pair<String, Long>> {
        val days = ArrayList<Pair<String, Long>>()
        val ruDow = arrayOf("Вс", "Пн", "Вт", "Ср", "Чт", "Пт", "Сб")
        for (back in 6 downTo 0) {
            val c = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -back)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val dayStart = c.timeInMillis
            val dayEnd = dayStart + 86_400_000L
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, dayStart, minOf(dayEnd, System.currentTimeMillis()))
            val total = stats?.sumOf { it.totalTimeInForeground } ?: 0L
            days.add(ruDow[c.get(Calendar.DAY_OF_WEEK) - 1] to total)
        }
        return days
    }

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    // ── UI ────────────────────────────────────────────────────────────────────────
    private fun fmt(ms: Long): String {
        val min = ms / 60_000
        return if (min >= 60) "${min / 60} ч ${min % 60} мин" else "$min мин"
    }

    private fun bigTime(ms: Long): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(0, dp(8), 0, dp(16))
        addView(TextView(this@ScreenTimeActivity).apply {
            text = fmt(ms); setTextColor(accent); textSize = 44f; typeface = golosM
        })
        addView(TextView(this@ScreenTimeActivity).apply {
            text = "сегодня"; setTextColor(Color.GRAY); textSize = 15f; typeface = golos
        })
    }

    private fun weekChart(usm: UsageStatsManager): View {
        val totals = weekTotals(usm)
        val max = (totals.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(1)
        val bars = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM; setPadding(dp(16), dp(12), dp(16), dp(8))
        }
        totals.forEach { (day, ms) ->
            val h = (dp(90) * ms / max).toInt().coerceAtLeast(dp(2))
            bars.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                addView(View(this@ScreenTimeActivity).apply {
                    background = GradientDrawable().apply { cornerRadius = dp(4).toFloat(); setColor(accent) }
                }, LinearLayout.LayoutParams(dp(18), h).also { it.bottomMargin = dp(6) })
                addView(TextView(this@ScreenTimeActivity).apply {
                    text = day; setTextColor(Color.parseColor("#8E8E93")); textSize = 11f; typeface = golos
                })
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        return LinearLayout(this).apply { setPadding(dp(16), 0, dp(16), 0)
            addView(LinearLayout(this@ScreenTimeActivity).apply {
                background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(Color.WHITE) }
                addView(bars, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun appBar(name: String, ms: Long, max: Long): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(10), dp(16), dp(10))
        addView(LinearLayout(this@ScreenTimeActivity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@ScreenTimeActivity).apply { text = name; setTextColor(Color.BLACK); textSize = 16f; typeface = golos },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(this@ScreenTimeActivity).apply { text = fmt(ms); setTextColor(Color.parseColor("#8E8E93")); textSize = 14f; typeface = golos })
        })
        // Proportional bar: an accent fill over a light track, weighted by ms/max.
        val fillWeight = (ms.toFloat() / max.toFloat()).coerceIn(0.02f, 1f)
        addView(LinearLayout(this@ScreenTimeActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply { cornerRadius = dp(3).toFloat(); setColor(Color.parseColor("#E5E5EA")) }
            addView(View(this@ScreenTimeActivity).apply {
                background = GradientDrawable().apply { cornerRadius = dp(3).toFloat(); setColor(accent) }
            }, LinearLayout.LayoutParams(0, dp(6), fillWeight))
            addView(View(this@ScreenTimeActivity), LinearLayout.LayoutParams(0, dp(6), 1f - fillWeight))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(6)).also { it.topMargin = dp(6) })
    }

    private fun kv(k: String, v: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(12))
        addView(TextView(this@ScreenTimeActivity).apply { text = k; textSize = 16f; setTextColor(Color.BLACK); typeface = golos },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(TextView(this@ScreenTimeActivity).apply { text = v; textSize = 16f; setTextColor(Color.parseColor("#8E8E93")); typeface = golos })
    }

    private fun clickRow(text: String, onTap: () -> Unit): View = TextView(this).apply {
        this.text = text; setTextColor(accent); textSize = 16f; typeface = golosM
        gravity = Gravity.CENTER; setPadding(0, dp(14), 0, dp(14)); isClickable = true; setOnClickListener { onTap() }
    }

    private fun card(rows: List<View>): View {
        val cardCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(Color.WHITE) }
        }
        rows.forEachIndexed { i, r ->
            cardCol.addView(r)
            if (i < rows.size - 1) cardCol.addView(View(this).apply { setBackgroundColor(Color.parseColor("#E5E5EA")) }
                .also { it.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).also { p -> p.marginStart = dp(16) } })
        }
        return LinearLayout(this).apply { setPadding(dp(16), 0, dp(16), 0); addView(cardCol,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)) }
    }

    private fun title(t: String) = TextView(this).apply {
        text = t; textSize = 28f; setTextColor(Color.BLACK); typeface = Typeface.create(golos, Typeface.BOLD); setPadding(dp(20), dp(4), dp(20), dp(12))
    }
    private fun label(t: String) = TextView(this).apply {
        text = t; textSize = 13f; setTextColor(Color.parseColor("#6C6C70")); typeface = golosM; setPadding(dp(32), dp(16), dp(16), dp(6))
    }
    private fun note(t: String) = TextView(this).apply {
        text = t; textSize = 13f; setTextColor(Color.parseColor("#6C6C70")); typeface = golos; setPadding(dp(32), dp(10), dp(32), dp(8))
    }
}
