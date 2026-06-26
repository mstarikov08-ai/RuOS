package com.ruos.focus.schedule

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import com.ruos.focus.model.FocusMode
import com.ruos.focus.model.FocusStore

/**
 * The brain of Focus: activates/deactivates a mode, mirrors the state to the system
 * Do-Not-Disturb policy (best effort), and broadcasts the active filter so RuOSNotify
 * can suppress banners/sounds for non-allowed apps.
 *
 * Cross-app contract (RuOSNotify listens for this):
 *   action  com.ruos.focus.CHANGED
 *   extras  active(boolean), name(String), allowed_packages(StringArrayList),
 *           allow_calls(boolean), suppress_all(boolean), hide(boolean), color(int)
 */
object FocusController {

    const val ACTION_CHANGED = "com.ruos.focus.CHANGED"
    const val EXTRA_ACTIVE = "active"
    const val EXTRA_NAME = "name"
    const val EXTRA_ALLOWED = "allowed_packages"
    const val EXTRA_ALLOW_CALLS = "allow_calls"
    const val EXTRA_SUPPRESS_ALL = "suppress_all"
    const val EXTRA_HIDE = "hide"
    const val EXTRA_COLOR = "color"

    private const val NOTIFY_PKG = "com.ruos.notify"

    /** Turn [mode] on (replacing any other active focus). */
    fun activate(context: Context, mode: FocusMode) {
        val store = FocusStore(context)
        store.setActive(mode.id)
        applyDnd(context, mode)
        broadcast(context, mode)
    }

    /** Turn off whatever focus is active. */
    fun deactivate(context: Context) {
        val store = FocusStore(context)
        store.setActive(null)
        clearDnd(context)
        broadcastOff(context)
    }

    /** Toggle a mode by id; returns the new active state. */
    fun toggle(context: Context, id: String): Boolean {
        val store = FocusStore(context)
        return if (store.activeId() == id) {
            deactivate(context); false
        } else {
            store.byId(id)?.let { activate(context, it) }; true
        }
    }

    /** Re-emit current state (e.g. on boot / when RuOSNotify reconnects). */
    fun resend(context: Context) {
        val active = FocusStore(context).active()
        if (active != null) broadcast(context, active) else broadcastOff(context)
    }

    private fun broadcast(context: Context, mode: FocusMode) {
        send(context, Intent(ACTION_CHANGED).apply {
            putExtra(EXTRA_ACTIVE, true)
            putExtra(EXTRA_NAME, mode.name)
            putStringArrayListExtra(EXTRA_ALLOWED, ArrayList(mode.allowedPackages))
            putExtra(EXTRA_ALLOW_CALLS, mode.allowCalls)
            putExtra(EXTRA_SUPPRESS_ALL, mode.suppressAll)
            putExtra(EXTRA_HIDE, mode.hideNotifications)
            putExtra(EXTRA_COLOR, mode.color)
        })
    }

    private fun broadcastOff(context: Context) {
        send(context, Intent(ACTION_CHANGED).putExtra(EXTRA_ACTIVE, false))
    }

    /** Send to both RuOSNotify (filtering) and SystemUI (status pill / lock dim). */
    private fun send(context: Context, base: Intent) {
        runCatching {
            context.sendBroadcast(Intent(base).setPackage(NOTIFY_PKG))
        }
        runCatching {
            context.sendBroadcast(Intent(base).setPackage("com.android.systemui"))
        }
    }

    /**
     * Mirror to the platform DND so apps and the framework also respect quiet hours.
     * Requires ACCESS_NOTIFICATION_POLICY (granted to platform-signed apps); guarded so
     * it degrades gracefully if policy access isn't held.
     */
    private fun applyDnd(context: Context, mode: FocusMode) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (!nm.isNotificationPolicyAccessGranted) return
        val filter = if (mode.suppressAll) {
            NotificationManager.INTERRUPTION_FILTER_NONE
        } else {
            NotificationManager.INTERRUPTION_FILTER_PRIORITY
        }
        runCatching { nm.setInterruptionFilter(filter) }
    }

    private fun clearDnd(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (!nm.isNotificationPolicyAccessGranted) return
        runCatching { nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL) }
    }
}
