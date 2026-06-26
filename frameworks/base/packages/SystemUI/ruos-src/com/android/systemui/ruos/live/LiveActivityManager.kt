package com.android.systemui.ruos.live

import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import com.android.systemui.ruos.DynamicIslandView

/**
 * Receives Live Activity broadcasts ([LiveActivityApi]), keeps at most two active
 * (newest two), and renders them:
 *   - Dynamic Island: 1 → compact, 2 → split into two bubbles, tap → expand.
 *   - Lock screen: a stack of expanded [LiveActivityCardView]s shown while locked
 *     (and briefly when the island is tapped).
 *
 * [device] confidence: drives SystemUI views + an overlay window; needs on-device
 * validation. The broadcast API itself is plain and stable.
 */
class LiveActivityManager(
    private val context: Context,
    private val island: DynamicIslandView
) {
    private val main = Handler(Looper.getMainLooper())
    private val wm = context.getSystemService(WindowManager::class.java)
    private val keyguard = context.getSystemService(KeyguardManager::class.java)

    private val activities = LinkedHashMap<String, LiveActivity>()  // insertion-ordered
    private var cardHost: LinearLayout? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            when (i?.action) {
                LiveActivityApi.ACTION_UPDATE -> parse(i)?.let { upsert(it) }
                LiveActivityApi.ACTION_END -> i.getStringExtra(LiveActivityApi.EXTRA_ID)?.let { remove(it) }
            }
        }
    }

    fun start() {
        val f = IntentFilter().apply {
            addAction(LiveActivityApi.ACTION_UPDATE); addAction(LiveActivityApi.ACTION_END)
        }
        runCatching { context.registerReceiver(receiver, f, Context.RECEIVER_EXPORTED) }
        island.onLiveTap = { showCards(force = true); main.postDelayed({ refreshCards() }, 6000) }
    }

    fun stop() { runCatching { context.unregisterReceiver(receiver) }; teardownCards() }

    private fun parse(i: Intent): LiveActivity? {
        val id = i.getStringExtra(LiveActivityApi.EXTRA_ID) ?: return null
        return LiveActivity(
            id = id,
            type = i.getStringExtra(LiveActivityApi.EXTRA_TYPE) ?: "generic",
            color = i.getIntExtra(LiveActivityApi.EXTRA_COLOR, Color.parseColor("#D94F3D")),
            compactLeading = i.getStringExtra(LiveActivityApi.EXTRA_COMPACT_LEADING) ?: "",
            compactTrailing = i.getStringExtra(LiveActivityApi.EXTRA_COMPACT_TRAILING) ?: "",
            minimal = i.getStringExtra(LiveActivityApi.EXTRA_MINIMAL) ?: "",
            title = i.getStringExtra(LiveActivityApi.EXTRA_TITLE) ?: "",
            subtitle = i.getStringExtra(LiveActivityApi.EXTRA_SUBTITLE) ?: "",
            body = i.getStringExtra(LiveActivityApi.EXTRA_BODY) ?: "",
            progress = i.getFloatExtra(LiveActivityApi.EXTRA_PROGRESS, -1f),
            contentIntent = runCatching {
                i.getParcelableExtra(LiveActivityApi.EXTRA_CONTENT_INTENT, PendingIntent::class.java)
            }.getOrNull(),
            postedAt = System.currentTimeMillis()
        )
    }

    private fun upsert(a: LiveActivity) = main.post {
        activities[a.id] = a
        // Keep only the two most recent.
        while (activities.size > 2) {
            val oldest = activities.keys.first(); activities.remove(oldest)
        }
        render()
    }

    private fun remove(id: String) = main.post { activities.remove(id); render() }

    private fun render() {
        val list = activities.values.toList()
        when (list.size) {
            0 -> island.clearLive()
            1 -> island.showLive(list[0].compactLeading, list[0].compactTrailing, list[0].color)
            else -> island.showSplit(
                list[0].minimal.ifBlank { list[0].compactLeading }, list[0].color,
                list[1].minimal.ifBlank { list[1].compactLeading }, list[1].color)
        }
        refreshCards()
    }

    // ── Lock-screen card overlay ───────────────────────────────────────────────

    private fun refreshCards() {
        val locked = keyguard?.isKeyguardLocked == true
        if (activities.isEmpty() || !locked) { teardownCards(); return }
        showCards(force = false)
    }

    private fun showCards(force: Boolean) {
        if (activities.isEmpty()) return
        ensureHost()
        val host = cardHost ?: return
        host.removeAllViews()
        val d = context.resources.displayMetrics.density
        activities.values.toList().forEach { a ->
            val card = LiveActivityCardView(context, a).apply {
                onDismiss = { remove(a.id) }
            }
            host.addView(card, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also {
                it.topMargin = (10 * d).toInt()
            })
        }
    }

    private fun ensureHost() {
        if (cardHost != null) return
        val d = context.resources.displayMetrics.density
        val h = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_STATUS_BAR_SUB_PANEL,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            y = (220 * d).toInt()      // below notifications, like iOS
            width = (context.resources.displayMetrics.widthPixels * 0.92f).toInt()
        }
        runCatching { wm.addView(h, lp); cardHost = h }
    }

    private fun teardownCards() {
        cardHost?.let { runCatching { wm.removeView(it) } }
        cardHost = null
    }
}
