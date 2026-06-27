package com.ruos.launcher

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * iOS-style App Library: every installed app, auto-organised into category cards
 * (Игры, Общение, Музыка…) using ApplicationInfo.category plus Russian-app heuristics.
 * Search filters to a flat list. Revealed by over-scrolling left of the last home page.
 */
class AppLibraryView(context: Context) : LinearLayout(context) {

    private val d = resources.displayMetrics.density
    private fun dp(v: Float) = (v * d).toInt()
    private val golos = Typeface.create("golos", Typeface.NORMAL)
    private val golosM = Typeface.create("golos-medium", Typeface.NORMAL)

    private val content = LinearLayout(context).apply { orientation = VERTICAL }
    private val searchField: EditText
    var onDismiss: (() -> Unit)? = null

    init {
        orientation = VERTICAL
        setBackgroundColor(0xF20E0E10.toInt())
        setPadding(dp(14f), dp(48f), dp(14f), 0)
        visibility = View.GONE

        addView(TextView(context).apply {
            text = "Библиотека"; setTextColor(Color.WHITE); textSize = 28f; typeface = Typeface.create(golos, Typeface.BOLD)
            setPadding(dp(4f), 0, 0, dp(10f))
        })
        searchField = EditText(context).apply {
            hint = "Поиск"; setHintTextColor(0xFF8E8E93.toInt()); setTextColor(Color.WHITE); textSize = 17f; typeface = golos
            background = GradientDrawable().apply { cornerRadius = dp(10f).toFloat(); setColor(0xFF1C1C1E.toInt()) }
            setPadding(dp(12f), dp(9f), dp(12f), dp(9f)); isSingleLine = true
        }
        addView(searchField)
        addView(ScrollView(context).apply { addView(content); isVerticalScrollBarEnabled = false },
            LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        searchField.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { rebuild(s?.toString().orEmpty()) }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
    }

    fun show() {
        searchField.setText("")
        rebuild("")
        visibility = View.VISIBLE
        translationX = width.toFloat().coerceAtLeast(1000f)
        animate().translationX(0f).setDuration(260).start()
    }

    fun hide() {
        animate().translationX(width.toFloat()).setDuration(220).withEndAction {
            visibility = View.GONE; onDismiss?.invoke()
        }.start()
    }

    fun isShown2() = visibility == View.VISIBLE

    private fun rebuild(query: String) {
        content.removeAllViews()
        val pm = context.packageManager
        val apps = AppRepository(context).getInstalledApps()
        val q = query.trim().lowercase()
        if (q.isNotEmpty()) {
            val matches = apps.filter { it.label.lowercase().contains(q) }
            content.addView(iconGrid(matches))
            return
        }
        val groups = LinkedHashMap<String, MutableList<AppInfo>>()
        apps.forEach { app ->
            val cat = categoryOf(pm, app.packageName)
            groups.getOrPut(cat) { ArrayList() }.add(app)
        }
        // stable, sensible order
        val order = listOf("Недавно добавленные", "Общение", "Соцсети", "Музыка и аудио", "Видео",
            "Фото", "Игры", "Карты", "Финансы и госуслуги", "Магазины", "Продуктивность", "Новости", "Прочее")
        order.filter { groups.containsKey(it) }.forEach { name ->
            content.addView(categoryCard(name, groups[name]!!))
        }
        groups.keys.filter { it !in order }.forEach { content.addView(categoryCard(it, groups[it]!!)) }
    }

    private fun categoryCard(title: String, apps: List<AppInfo>): View {
        val card = LinearLayout(context).apply {
            orientation = VERTICAL
            background = GradientDrawable().apply { cornerRadius = dp(20f).toFloat(); setColor(0xFF1C1C1E.toInt()) }
            setPadding(dp(12f), dp(12f), dp(12f), dp(12f))
            val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT); lp.topMargin = dp(10f); layoutParams = lp
        }
        card.addView(TextView(context).apply {
            text = title; setTextColor(Color.WHITE); textSize = 15f; typeface = golosM; setPadding(dp(4f), 0, 0, dp(8f))
        })
        card.addView(iconGrid(apps.sortedBy { it.label.lowercase() }))
        return card
    }

    private fun iconGrid(apps: List<AppInfo>): View {
        val cols = 4
        val grid = LinearLayout(context).apply { orientation = VERTICAL }
        var row: LinearLayout? = null
        apps.forEachIndexed { i, app ->
            if (i % cols == 0) {
                row = LinearLayout(context).apply { orientation = HORIZONTAL }
                grid.addView(row)
            }
            row!!.addView(iconCell(app), LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        }
        // pad last row
        row?.let { r -> while (r.childCount < cols) r.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f)) }
        return grid
    }

    private fun iconCell(app: AppInfo): View = LinearLayout(context).apply {
        orientation = VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(0, dp(6f), 0, dp(6f))
        isClickable = true
        setOnClickListener {
            context.packageManager.getLaunchIntentForPackage(app.packageName)?.let {
                it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(it) }; hide()
            }
        }
        addView(ImageView(context).apply { setImageDrawable(app.icon) }, LayoutParams(dp(52f), dp(52f)))
        addView(TextView(context).apply {
            text = app.label; setTextColor(0xFFC7C7CC.toInt()); textSize = 11f; typeface = golos
            gravity = Gravity.CENTER; maxLines = 1; setPadding(dp(2f), dp(4f), dp(2f), 0)
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    private fun categoryOf(pm: android.content.pm.PackageManager, pkg: String): String {
        keywordCategory(pkg)?.let { return it }
        val cat = runCatching { pm.getApplicationInfo(pkg, 0).category }.getOrDefault(ApplicationInfo.CATEGORY_UNDEFINED)
        return when (cat) {
            ApplicationInfo.CATEGORY_GAME -> "Игры"
            ApplicationInfo.CATEGORY_AUDIO -> "Музыка и аудио"
            ApplicationInfo.CATEGORY_VIDEO -> "Видео"
            ApplicationInfo.CATEGORY_IMAGE -> "Фото"
            ApplicationInfo.CATEGORY_SOCIAL -> "Общение"
            ApplicationInfo.CATEGORY_NEWS -> "Новости"
            ApplicationInfo.CATEGORY_MAPS -> "Карты"
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Продуктивность"
            else -> "Прочее"
        }
    }

    /** Russian-market app hints take priority over the manifest category. */
    private fun keywordCategory(pkg: String): String? {
        val p = pkg.lowercase()
        return when {
            listOf("telegram", "whatsapp", "viber", "ruos.messages", "ruos.phone", "icq").any { p.contains(it) } -> "Общение"
            listOf("vkontakte", "com.vk", "ok.android", "ruos.mail", "instagram").any { p.contains(it) } -> "Соцсети"
            listOf("yandex.music", "zvuk", "spotify", "ruos.music", "soundcloud").any { p.contains(it) } -> "Музыка и аудио"
            listOf("rutube", "youtube", "kinopoisk", "ivi", "okko", "wink").any { p.contains(it) } -> "Видео"
            listOf("yandex.maps", "yandexnavi", "2gis", "ruos.maps").any { p.contains(it) } -> "Карты"
            listOf("sberbank", "tinkoff", "vtb", "alfabank", "mirpay", "nspk", "gosuslugi").any { p.contains(it) } -> "Финансы и госуслуги"
            listOf("rustore", "appgallery", "market").any { p.contains(it) } -> "Магазины"
            else -> null
        }
    }
}
