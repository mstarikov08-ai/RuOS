package com.ruos.standby.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.session.MediaSessionManager
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.ruos.standby.util.Fonts
import com.ruos.standby.util.Haptics
import kotlin.math.abs

/** Widget kinds available in the page-2 stack. */
enum class WidgetKind { WEATHER, NOTIFICATIONS, NOWPLAYING }

/** A glowing rounded card base for StandBy widgets. */
open class WidgetCard(context: Context) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        val d = resources.displayMetrics.density
        background = GradientDrawable().apply {
            cornerRadius = 26f * d
            setColor(Color.parseColor("#141416"))
            setStroke((1f * d).toInt(), Color.parseColor("#22FFFFFF"))
        }
        elevation = 18f * d            // soft glow in a dark room
        setPadding((22 * d).toInt(), (18 * d).toInt(), (22 * d).toInt(), (18 * d).toInt())
        gravity = Gravity.CENTER_VERTICAL
    }
}

/** Weather — demo data (no live feed wired yet; clearly a placeholder card). */
class WeatherWidget(context: Context) : WidgetCard(context) {
    init {
        addView(TextView(context).apply {
            text = "Москва"; setTextColor(Color.parseColor("#AEAEB2")); textSize = 14f; typeface = Fonts.medium
        })
        addView(TextView(context).apply {
            text = "18°"; setTextColor(Color.WHITE); textSize = 44f; typeface = Fonts.thin
        })
        addView(TextView(context).apply {
            text = "Облачно с прояснениями"; setTextColor(Color.parseColor("#C8D0E0")); textSize = 13f
        })
    }
}

/** Notifications — real unread total fed by RuOSNotify's badge broadcast. */
class NotificationsWidget(context: Context) : WidgetCard(context) {
    private val count = TextView(context).apply {
        setTextColor(Color.WHITE); textSize = 40f; typeface = Fonts.thin; text = "0"
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            val total = i?.getIntArrayExtra("counts")?.sum() ?: 0
            count.text = total.toString()
        }
    }
    init {
        addView(TextView(context).apply {
            text = "Уведомления"; setTextColor(Color.parseColor("#AEAEB2")); textSize = 14f; typeface = Fonts.medium
        })
        addView(count)
        addView(TextView(context).apply {
            text = "непрочитанных"; setTextColor(Color.parseColor("#C8D0E0")); textSize = 13f
        })
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        runCatching { context.registerReceiver(receiver, IntentFilter("com.ruos.notify.BADGES"), Context.RECEIVER_EXPORTED) }
    }
    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); runCatching { context.unregisterReceiver(receiver) } }
}

/** Now Playing — real, via MediaSessionManager (platform-signed app holds the perm). */
class NowPlayingWidget(context: Context) : WidgetCard(context) {
    private val title = TextView(context).apply { setTextColor(Color.WHITE); textSize = 20f; typeface = Fonts.medium; maxLines = 1 }
    private val artist = TextView(context).apply { setTextColor(Color.parseColor("#C8D0E0")); textSize = 14f; maxLines = 1 }
    private val handler = Handler(Looper.getMainLooper())
    private val poll = object : Runnable { override fun run() { refresh(); handler.postDelayed(this, 4000) } }

    init {
        addView(TextView(context).apply {
            text = "Сейчас играет"; setTextColor(Color.parseColor("#AEAEB2")); textSize = 14f; typeface = Fonts.medium
        })
        addView(title); addView(artist)
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); handler.post(poll) }
    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); handler.removeCallbacks(poll) }

    private fun refresh() {
        runCatching {
            val msm = context.getSystemService(MediaSessionManager::class.java)
            val sessions = msm.getActiveSessions(null)   // needs MEDIA_CONTENT_CONTROL
            val md = sessions.firstOrNull()?.metadata
            if (md != null) {
                title.text = md.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: "—"
                artist.text = md.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST) ?: ""
            } else { title.text = "Ничего не играет"; artist.text = "" }
        }.onFailure { title.text = "Ничего не играет"; artist.text = "" }
    }
}

/**
 * A slot that hosts one widget and cycles to the next/previous kind on a vertical
 * swipe (iOS "swipe up/down to change each widget independently"), with a crossfade.
 */
class WidgetSlot(context: Context, private var kind: WidgetKind,
                 private val onKindChanged: (WidgetKind) -> Unit) : FrameLayout(context) {

    private val order = WidgetKind.values()
    private var downY = 0f
    private val slop = 24f * resources.displayMetrics.density

    init { showWidget(kind, animate = false) }

    private fun build(k: WidgetKind): View = when (k) {
        WidgetKind.WEATHER -> WeatherWidget(context)
        WidgetKind.NOTIFICATIONS -> NotificationsWidget(context)
        WidgetKind.NOWPLAYING -> NowPlayingWidget(context)
    }

    private fun showWidget(k: WidgetKind, animate: Boolean) {
        kind = k
        val v = build(k)
        v.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        if (animate) {
            v.alpha = 0f; v.translationY = 20f
            addView(v)
            v.animate().alpha(1f).translationY(0f).setDuration(220).start()
            val old = if (childCount > 1) getChildAt(0) else null
            old?.animate()?.alpha(0f)?.setDuration(180)?.withEndAction { removeView(old) }?.start()
        } else { removeAllViews(); addView(v) }
        onKindChanged(k)
    }

    private fun cycle(dir: Int) {
        val idx = (order.indexOf(kind) + dir + order.size) % order.size
        Haptics.select(this)
        showWidget(order[idx], animate = true)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> downY = ev.y
            MotionEvent.ACTION_MOVE -> if (abs(ev.y - downY) > slop) return true
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            val dy = event.y - downY
            if (abs(dy) > slop) cycle(if (dy < 0) 1 else -1)
        }
        return true
    }
}
