package com.ruos.settings.sections

import android.app.Activity
import android.app.usage.StorageStatsManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.storage.StorageManager
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Хранилище — real storage usage: total / used / free (StorageStatsManager), a usage bar,
 * the biggest apps by size (queryStatsForPackage), and an "offload" suggestion listing
 * large apps not opened recently. Sizes need PACKAGE_USAGE_STATS (held by this privileged
 * app); guarded so it degrades to a note if unavailable.
 */
class StorageSettingsActivity : Activity() {

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
        root.addView(title("Хранилище"))

        val (total, free) = totals()
        val used = (total - free).coerceAtLeast(0)
        val appSizes = appSizes()
        val appsTotal = appSizes.sumOf { it.second }
        val other = (used - appsTotal).coerceAtLeast(0)

        root.addView(usageBar(used, total))
        root.addView(legend(used, free, total))

        if (appSizes.isEmpty()) {
            root.addView(label("ПРИЛОЖЕНИЯ"))
            root.addView(card(listOf(kv("Размеры недоступны", "нет доступа"))))
        } else {
            // category split
            root.addView(label("КАТЕГОРИИ"))
            root.addView(card(listOf(
                kv("Приложения", fmt(appsTotal)),
                kv("Система и прочее", fmt(other)),
                kv("Свободно", fmt(free))
            )))

            root.addView(label("ЗАНИМАЮТ БОЛЬШЕ ВСЕГО"))
            root.addView(card(appSizes.sortedByDescending { it.second }.take(10).map { kv(it.first, fmt(it.second)) }))

            val offload = offloadCandidates(appSizes)
            if (offload.isNotEmpty()) {
                root.addView(label("МОЖНО ВЫГРУЗИТЬ (давно не использовались)"))
                root.addView(card(offload.map { kv(it.first, fmt(it.second)) }))
                root.addView(note("Эти приложения давно не открывались. Их можно удалить, " +
                    "чтобы освободить место — документы и данные при переустановке вернутся."))
            }
        }

        return ScrollView(this).apply { addView(root) }
    }

    private fun totals(): Pair<Long, Long> = runCatching {
        val ssm = getSystemService(StorageStatsManager::class.java)
        val uuid = StorageManager.UUID_DEFAULT
        ssm.getTotalBytes(uuid) to ssm.getFreeBytes(uuid)
    }.getOrElse {
        // fallback to StatFs on the data directory
        val sf = android.os.StatFs(filesDir.absolutePath)
        (sf.blockCountLong * sf.blockSizeLong) to (sf.availableBlocksLong * sf.blockSizeLong)
    }

    private fun appSizes(): List<Pair<String, Long>> = runCatching {
        val ssm = getSystemService(StorageStatsManager::class.java)
        val uuid = StorageManager.UUID_DEFAULT
        val user = android.os.Process.myUserHandle()
        val pm = packageManager
        pm.getInstalledApplications(0).mapNotNull { ai ->
            val stats = runCatching { ssm.queryStatsForPackage(uuid, ai.packageName, user) }.getOrNull() ?: return@mapNotNull null
            val size = stats.appBytes + stats.dataBytes + stats.cacheBytes
            val label = runCatching { pm.getApplicationLabel(ai).toString() }.getOrDefault(ai.packageName)
            label to size
        }.filter { it.second > 0 }
    }.getOrDefault(emptyList())

    private fun offloadCandidates(appSizes: List<Pair<String, Long>>): List<Pair<String, Long>> = runCatching {
        val usm = getSystemService(android.app.usage.UsageStatsManager::class.java) ?: return emptyList()
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_MONTHLY, now - 30L * 24 * 3600_000, now)
        val recent = stats.filter { it.lastTimeUsed > now - 30L * 24 * 3600_000 }
            .map { it.packageName }.toHashSet()
        val pm = packageManager
        val recentLabels = recent.mapNotNull { runCatching { pm.getApplicationLabel(pm.getApplicationInfo(it, 0)).toString() }.getOrNull() }.toHashSet()
        appSizes.filter { it.second > 30_000_000 && it.first !in recentLabels }
            .sortedByDescending { it.second }.take(5)
    }.getOrDefault(emptyList())

    // ── UI ────────────────────────────────────────────────────────────────────────

    private fun usageBar(used: Long, total: Long): View {
        val frac = if (total > 0) used.toFloat() / total else 0f
        return object : View(this) {
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#D9D9DE") }
            val fg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0A84FF") }
            override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(MeasureSpec.getSize(w), dp(16))
            override fun onDraw(c: Canvas) {
                val r = height / 2f
                c.drawRoundRect(RectF(dp(16).toFloat(), 0f, (width - dp(16)).toFloat(), height.toFloat()), r, r, bg)
                val left = dp(16).toFloat(); val right = left + (width - dp(32)) * frac
                c.drawRoundRect(RectF(left, 0f, right.coerceAtLeast(left + r * 2), height.toFloat()), r, r, fg)
            }
        }.also { it.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(16)).also { p -> p.topMargin = dp(8) } }
    }

    private fun legend(used: Long, free: Long, total: Long): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; setPadding(dp(20), dp(8), dp(20), dp(8))
        addView(TextView(this@StorageSettingsActivity).apply {
            text = "Занято ${fmt(used)}"; textSize = 14f; setTextColor(Color.parseColor("#3A3A3C")); typeface = golosM
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(TextView(this@StorageSettingsActivity).apply {
            text = "Свободно ${fmt(free)}"; textSize = 14f; setTextColor(Color.parseColor("#8E8E93")); typeface = golos
            gravity = Gravity.END
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun kv(k: String, v: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(12))
        addView(TextView(this@StorageSettingsActivity).apply { text = k; textSize = 16f; setTextColor(Color.BLACK); typeface = golos; maxLines = 1 },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(TextView(this@StorageSettingsActivity).apply { text = v; textSize = 16f; setTextColor(Color.parseColor("#8E8E93")); typeface = golos })
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

    private fun fmt(bytes: Long): String {
        if (bytes < 1024) return "$bytes Б"
        val units = arrayOf("КБ", "МБ", "ГБ", "ТБ")
        var v = bytes.toDouble() / 1024; var i = 0
        while (v >= 1024 && i < units.size - 1) { v /= 1024; i++ }
        return "%.1f %s".format(v, units[i])
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
