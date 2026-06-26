package com.ruos.notify.model

import android.graphics.drawable.Drawable

/** A simplified, UI-ready snapshot of a posted notification. */
data class NotifAction(val label: CharSequence, val intent: android.app.PendingIntent?)

data class NotifItem(
    val key: String,
    val pkg: String,
    val appName: String,
    val title: CharSequence,
    val text: CharSequence,
    val whenMs: Long,
    val icon: Drawable?,
    val isGroupSummary: Boolean,
    val groupKey: String?,
    val category: String?,           // e.g. Notification.CATEGORY_CALL / MESSAGE
    val timeSensitive: Boolean,
    val critical: Boolean,
    val actions: List<NotifAction>,
    val contentIntent: android.app.PendingIntent?
) {
    /** "сейчас", "5 мин", "14:32" — iOS-style relative time. */
    fun timeText(): String {
        val diff = System.currentTimeMillis() - whenMs
        return when {
            diff < 60_000 -> "сейчас"
            diff < 3_600_000 -> "${diff / 60_000} мин"
            else -> java.text.SimpleDateFormat("HH:mm", java.util.Locale("ru")).format(java.util.Date(whenMs))
        }
    }
}
