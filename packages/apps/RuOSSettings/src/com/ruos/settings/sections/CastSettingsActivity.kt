package com.ruos.settings.sections

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaRouter
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Трансляция экрана — discovers wireless-display (Miracast) routes via the AOSP MediaRouter
 * and connects to a TV. Falls back to the system cast settings. Live video + remote-display
 * route types; tap a route to select it.
 */
class CastSettingsActivity : Activity() {

    private val golos = Typeface.create("golos", Typeface.NORMAL)
    private val golosM = Typeface.create("golos-medium", Typeface.NORMAL)
    private val routeTypes = MediaRouter.ROUTE_TYPE_LIVE_VIDEO or MediaRouter.ROUTE_TYPE_REMOTE_DISPLAY
    private lateinit var router: MediaRouter
    private lateinit var listHost: LinearLayout

    private val callback = object : MediaRouter.SimpleCallback() {
        override fun onRouteAdded(r: MediaRouter?, route: MediaRouter.RouteInfo?) = rebuildRoutes()
        override fun onRouteRemoved(r: MediaRouter?, route: MediaRouter.RouteInfo?) = rebuildRoutes()
        override fun onRouteChanged(r: MediaRouter?, route: MediaRouter.RouteInfo?) = rebuildRoutes()
        override fun onRouteSelected(r: MediaRouter?, type: Int, route: MediaRouter.RouteInfo?) = rebuildRoutes()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        router = getSystemService(Context.MEDIA_ROUTER_SERVICE) as MediaRouter
        setContentView(build())
    }

    override fun onResume() {
        super.onResume()
        runCatching { router.addCallback(routeTypes, callback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY) }
        rebuildRoutes()
    }

    override fun onPause() { super.onPause(); runCatching { router.removeCallback(callback) } }

    private fun build(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#F2F2F7")); setPadding(0, dp(100), 0, dp(40))
        }
        col.addView(title("Трансляция экрана"))
        col.addView(note("Транслируйте экран RuOS на телевизор или приставку по Wi-Fi " +
            "(Miracast). Убедитесь, что устройства в одной сети."))
        col.addView(label("УСТРОЙСТВА"))
        listHost = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(listHost)
        col.addView(actionButton("Открыть системные настройки трансляции") {
            runCatching { startActivity(Intent("android.settings.CAST_SETTINGS")) }
                .onFailure { Toast.makeText(this, "Недоступно", Toast.LENGTH_SHORT).show() }
        })
        return ScrollView(this).apply { addView(col) }
    }

    private fun rebuildRoutes() {
        if (!::listHost.isInitialized) return
        listHost.removeAllViews()
        val routes = ArrayList<MediaRouter.RouteInfo>()
        for (i in 0 until router.routeCount) {
            val r = router.getRouteAt(i)
            if (r == router.defaultRoute) continue
            if (r.supportedTypes and routeTypes != 0) routes.add(r)
        }
        if (routes.isEmpty()) {
            listHost.addView(card(listOf(row("Поиск устройств…", "", false) {})))
            return
        }
        val cardCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(Color.WHITE) }
        }
        routes.forEachIndexed { i, r ->
            val selected = router.selectedRoute == r
            cardCol.addView(row(r.name?.toString() ?: "Дисплей", if (selected) "Подключено" else "", selected) {
                runCatching { router.selectRoute(routeTypes, r) }
            })
            if (i < routes.size - 1) cardCol.addView(divider())
        }
        listHost.addView(LinearLayout(this).apply { setPadding(dp(16), 0, dp(16), 0); addView(cardCol,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)) })
    }

    private fun row(name: String, sub: String, selected: Boolean, onTap: () -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(12))
        isClickable = true; setOnClickListener { onTap() }
        addView(TextView(context).apply { text = name; setTextColor(Color.BLACK); textSize = 17f; typeface = golos },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(TextView(context).apply { text = sub; setTextColor(Color.parseColor("#0A84FF")); textSize = 14f; typeface = golosM })
    }

    private fun card(rows: List<View>): View {
        val cardCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(Color.WHITE) }
        }
        rows.forEach { cardCol.addView(it) }
        return LinearLayout(this).apply { setPadding(dp(16), 0, dp(16), 0); addView(cardCol,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)) }
    }
    private fun divider() = View(this).apply { setBackgroundColor(Color.parseColor("#E5E5EA")) }
        .also { it.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).also { p -> p.marginStart = dp(16) } }
    private fun actionButton(label: String, onTap: () -> Unit) = TextView(this).apply {
        text = label; setTextColor(Color.parseColor("#0A84FF")); textSize = 16f; typeface = golosM; gravity = Gravity.CENTER
        setPadding(0, dp(16), 0, dp(16)); isClickable = true; setOnClickListener { onTap() }
    }
    private fun title(t: String) = TextView(this).apply {
        text = t; textSize = 28f; setTextColor(Color.BLACK); typeface = Typeface.create(golos, Typeface.BOLD); setPadding(dp(20), dp(4), dp(20), dp(12))
    }
    private fun label(t: String) = TextView(this).apply {
        text = t; textSize = 13f; setTextColor(Color.parseColor("#6C6C70")); typeface = golosM; setPadding(dp(32), dp(14), dp(16), dp(6))
    }
    private fun note(t: String) = TextView(this).apply {
        text = t; textSize = 13f; setTextColor(Color.parseColor("#6C6C70")); typeface = golos; setPadding(dp(32), dp(6), dp(32), dp(8))
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
