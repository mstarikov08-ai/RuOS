package com.ruos.notify.service

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.ruos.notify.model.NotifItem

/**
 * Applies the active RuOS Focus to incoming notifications. RuOSFocus broadcasts its
 * filter (com.ruos.focus.CHANGED); when a focus is active, only allow-listed apps (and,
 * optionally, calls) may produce a heads-up banner + sound. Everything else is still
 * recorded — it lands in Notification Centre and is badged — it just doesn't break
 * through. This mirrors iOS Focus exactly.
 *
 * Owned by [RuOSNotificationListener]; call [register] in onCreate, [unregister] in
 * onDestroy, and consult [shouldBreakThrough] before showing a banner / playing a sound.
 */
class FocusGate(private val context: Context) {

    @Volatile private var active = false
    @Volatile private var allowed: Set<String> = emptySet()
    @Volatile private var allowCalls = true
    @Volatile private var suppressAll = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            if (i?.action != ACTION_CHANGED) return
            active = i.getBooleanExtra(EXTRA_ACTIVE, false)
            if (active) {
                allowed = i.getStringArrayListExtra(EXTRA_ALLOWED)?.toSet() ?: emptySet()
                allowCalls = i.getBooleanExtra(EXTRA_ALLOW_CALLS, true)
                suppressAll = i.getBooleanExtra(EXTRA_SUPPRESS_ALL, false)
            } else {
                allowed = emptySet(); allowCalls = true; suppressAll = false
            }
        }
    }

    fun register() {
        runCatching {
            context.registerReceiver(receiver, IntentFilter(ACTION_CHANGED), Context.RECEIVER_EXPORTED)
        }
    }

    fun unregister() {
        runCatching { context.unregisterReceiver(receiver) }
    }

    val isActive: Boolean get() = active

    /**
     * True if [item] may produce a banner + sound under the current focus. When no focus
     * is active, everything breaks through. Time-critical alarms always break through.
     */
    fun shouldBreakThrough(item: NotifItem): Boolean {
        if (!active) return true
        if (item.category == Notification.CATEGORY_ALARM) return true   // alarms always ring
        val isCall = item.category == Notification.CATEGORY_CALL
        if (isCall && allowCalls) return true
        if (suppressAll) return false
        return allowed.contains(item.pkg)
    }

    companion object {
        const val ACTION_CHANGED = "com.ruos.focus.CHANGED"
        const val EXTRA_ACTIVE = "active"
        const val EXTRA_ALLOWED = "allowed_packages"
        const val EXTRA_ALLOW_CALLS = "allow_calls"
        const val EXTRA_SUPPRESS_ALL = "suppress_all"
    }
}
