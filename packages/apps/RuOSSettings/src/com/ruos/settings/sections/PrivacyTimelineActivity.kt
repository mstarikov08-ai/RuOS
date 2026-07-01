package com.ruos.settings.sections

import android.app.Activity
import android.app.AppOpsManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * «Отчёт о конфиденциальности» — an iOS-style App Privacy Report: a timeline of which apps recently
 * used the camera, microphone or location. Data comes from [AppOpsManager] (last-access times per
 * op), which platform-signed RuOS can read via GET_APP_OPS_STATS. Read-only, resilient: every
 * reflective call is guarded so the screen degrades to "no data" rather than crashing on a device
 * where the op history is empty or the API shape differs.
 */
class PrivacyTimelineActivity : Activity() {

    private val golos = Typeface.create("golos", Typeface.NORMAL)
    private val golosM = Typeface.create("golos-medium", Typeface.NORMAL)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    /** op string → (label, dot colour). */
    private val trackedOps = linkedMapOf(
        "android:camera" to ("Камера" to 0xFF34C759.toInt()),
        "android:record_audio" to ("Микрофон" to 0xFFFF9500.toInt()),
        "android:fine_location" to ("Геолокация" to 0xFF0A84FF.toInt()),
        "android:coarse_location" to ("Геолокация" to 0xFF0A84FF.toInt())
    )

    private data class Access(val app: String, val op: String, val color: Int, val time: Long)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#F2F2F7"))
            setPadding(0, dp(100), 0, dp(40))
        }
        col.addView(title("Отчёт о конфиденциальности"))
        col.addView(note("Когда приложения недавно обращались к камере, микрофону и геолокации."))

        val accesses = collectAccesses()
        if (accesses.isEmpty()) {
            col.addView(card(listOf(row("#8E8E93".toColor(), "Нет данных о недавнем доступе", ""))))
            col.addView(note("Данные появятся после того, как приложения начнут использовать датчики. " +
                "Требуется системный доступ к статистике разрешений."))
        } else {
            col.addView(card(accesses.map { row(it.color, "${it.app} · ${it.op}", ago(it.time)) }))
        }

        setContentView(ScrollView(this).apply { addView(col) })
    }

    private fun collectAccesses(): List<Access> = runCatching {
        val aom = getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return emptyList()
        val ops = trackedOps.keys.toTypedArray()
        // getPackagesForOps(String[]) is @SystemApi; reachable via reflection on the platform build.
        val method = AppOpsManager::class.java.getMethod("getPackagesForOps", Array<String>::class.java)
        @Suppress("UNCHECKED_CAST")
        val packageOps = method.invoke(aom, ops) as? List<Any> ?: return emptyList()
        val pm = packageManager
        val out = ArrayList<Access>()
        for (po in packageOps) {
            val pkg = runCatching { po.javaClass.getMethod("getPackageName").invoke(po) as String }.getOrNull() ?: continue
            @Suppress("UNCHECKED_CAST")
            val entries = runCatching { po.javaClass.getMethod("getOps").invoke(po) as List<Any> }.getOrNull() ?: continue
            val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
            for (e in entries) {
                val opStr = runCatching { e.javaClass.getMethod("getOpStr").invoke(e) as String }.getOrNull() ?: continue
                val meta = trackedOps[opStr] ?: continue
                val t = lastAccessTime(e)
                if (t > 0) out.add(Access(label, meta.first, meta.second, t))
            }
        }
        out.sortedByDescending { it.time }.take(50)
    }.getOrDefault(emptyList())

    /** Newest access time across API variants: getLastAccessTime(int) (API 31+) or getTime(). */
    private fun lastAccessTime(entry: Any): Long {
        runCatching {
            val m = entry.javaClass.getMethod("getLastAccessTime", Int::class.javaPrimitiveType)
            val flags = 0x1 or 0x2 or 0x4   // SELF | TRUSTED | UNTRUSTED proxy flags
            return m.invoke(entry, flags) as Long
        }
        return runCatching {
            @Suppress("DEPRECATION")
            entry.javaClass.getMethod("getTime").invoke(entry) as Long
        }.getOrDefault(0L)
    }

    private fun ago(time: Long): String {
        val d = System.currentTimeMillis() - time
        return when {
            d < 60_000 -> "только что"
            d < 3_600_000 -> "${d / 60_000} мин назад"
            d < 86_400_000 -> "${d / 3_600_000} ч назад"
            else -> "${d / 86_400_000} дн назад"
        }
    }

    private fun String.toColor() = Color.parseColor(this)

    private fun row(color: Int, text: String, trailing: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(12))
        addView(View(this@PrivacyTimelineActivity).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
        }, LinearLayout.LayoutParams(dp(10), dp(10)).also { it.marginEnd = dp(12) })
        addView(TextView(this@PrivacyTimelineActivity).apply { this.text = text; setTextColor(Color.BLACK); textSize = 16f; typeface = golos },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(TextView(this@PrivacyTimelineActivity).apply { this.text = trailing; setTextColor(Color.parseColor("#8E8E93")); textSize = 13f; typeface = golos })
    }

    private fun card(rows: List<View>): View {
        val cardCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(Color.WHITE) }
        }
        rows.forEachIndexed { i, r ->
            cardCol.addView(r)
            if (i < rows.size - 1) cardCol.addView(View(this).apply { setBackgroundColor(Color.parseColor("#E5E5EA")) }
                .also { it.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).also { p -> p.marginStart = dp(38) } })
        }
        return LinearLayout(this).apply { setPadding(dp(16), 0, dp(16), 0); addView(cardCol,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)) }
    }

    private fun title(t: String) = TextView(this).apply {
        text = t; textSize = 26f; setTextColor(Color.BLACK); typeface = Typeface.create(golos, Typeface.BOLD); setPadding(dp(20), dp(4), dp(20), dp(12))
    }
    private fun note(t: String) = TextView(this).apply {
        text = t; textSize = 13f; setTextColor(Color.parseColor("#6C6C70")); typeface = golos; setPadding(dp(32), dp(10), dp(32), dp(8))
    }
}
