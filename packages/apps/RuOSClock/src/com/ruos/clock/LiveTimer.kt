package com.ruos.clock

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color

/**
 * Posts the running countdown as a RuOS Live Activity (Dynamic Island compact pill +
 * lock-screen card). This is a real in-house example of the broadcast API — it mirrors
 * com.android.systemui.ruos.live.LiveActivityApi (no SDK dependency; just an Intent).
 */
object LiveTimer {

    private const val ACTION_UPDATE = "com.ruos.liveactivity.UPDATE"
    private const val ACTION_END = "com.ruos.liveactivity.END"
    private const val ID = "ruos.clock.timer"

    private var lastText = ""

    fun update(context: Context, text: String, progress: Float) {
        if (text == lastText) return       // throttle: only on visible change (1/s)
        lastText = text
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, ClockActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        context.sendBroadcast(Intent(ACTION_UPDATE).apply {
            setPackage("com.android.systemui")
            putExtra("id", ID)
            putExtra("type", "timer")
            putExtra("color", Color.parseColor("#FF9F0A"))
            putExtra("compact_leading", "Таймер")
            putExtra("compact_trailing", text)
            putExtra("minimal", text)
            putExtra("title", "Таймер")
            putExtra("subtitle", "Осталось $text")
            putExtra("progress", progress)
            putExtra("content_intent", open)
        })
    }

    fun end(context: Context) {
        lastText = ""
        context.sendBroadcast(Intent(ACTION_END).apply {
            setPackage("com.android.systemui"); putExtra("id", ID)
        })
    }
}
