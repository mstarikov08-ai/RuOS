package com.android.systemui.ruos.live

import android.app.PendingIntent
import android.content.Intent

/**
 * RuOS Live Activity — the public broadcast API.
 *
 * Any app posts/updates/ends a Live Activity by sending an explicit broadcast to
 * SystemUI. No SDK dependency is required — the protocol is just an Intent with
 * string extras (see docs/LiveActivities.md). [LiveActivityManager] consumes them and
 * renders the Dynamic Island (minimal / compact / split) and a lock-screen card.
 *
 * Contract (stable):
 *   action  = ACTION_UPDATE  → post or update    | ACTION_END → dismiss
 *   package = "com.android.systemui"             (so it reaches SystemUI)
 *   extras  = EXTRA_* below
 */
object LiveActivityApi {
    const val ACTION_UPDATE = "com.ruos.liveactivity.UPDATE"
    const val ACTION_END = "com.ruos.liveactivity.END"

    const val EXTRA_ID = "id"                 // String, unique & stable per activity
    const val EXTRA_TYPE = "type"             // String, e.g. taxi/delivery/timer/...
    const val EXTRA_COLOR = "color"           // Int ARGB accent
    const val EXTRA_COMPACT_LEADING = "compact_leading"   // short text shown left in pill
    const val EXTRA_COMPACT_TRAILING = "compact_trailing" // short text shown right in pill
    const val EXTRA_MINIMAL = "minimal"       // tiny text when sharing the pill (split)
    const val EXTRA_TITLE = "title"           // expanded card title
    const val EXTRA_SUBTITLE = "subtitle"     // expanded card subtitle
    const val EXTRA_BODY = "body"             // expanded card detail (multiline)
    const val EXTRA_PROGRESS = "progress"     // Float 0..1, optional
    const val EXTRA_CONTENT_INTENT = "content_intent"  // PendingIntent, tap to open

    /** Convenience sender (usable from in-process / examples). */
    fun build(action: String, id: String): Intent =
        Intent(action).setPackage("com.android.systemui").putExtra(EXTRA_ID, id)
}

/** Parsed Live Activity state held by the manager. */
data class LiveActivity(
    val id: String,
    val type: String,
    val color: Int,
    val compactLeading: String,
    val compactTrailing: String,
    val minimal: String,
    val title: String,
    val subtitle: String,
    val body: String,
    val progress: Float,
    val contentIntent: PendingIntent?,
    val postedAt: Long
)
