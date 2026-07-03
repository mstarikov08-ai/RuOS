package com.ruos.settings.util

import android.content.Context
import java.io.File

/**
 * The 80% charge-limit mechanism, shared by the Battery settings toggle and [ChargeLimitReceiver]
 * (which re-applies the choice after boot — the sysfs node resets to its kernel default on every
 * reboot, so without the receiver the limit would silently vanish). The node name varies by
 * kernel, so a small candidate list is probed; everything is guarded to degrade to a no-op where
 * the node is absent or SELinux blocks the write.
 */
object ChargeLimit {

    const val LIMIT = 80
    private const val PREFS = "ruos_battery"
    private const val KEY = "charge_limit"

    private val NODES = listOf(
        "/sys/class/power_supply/battery/charge_control_limit",
        "/sys/devices/platform/google,charger/charge_stop_level",
        "/sys/class/power_supply/battery/charge_stop_level"
    )

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The user's saved choice (survives reboot, unlike the sysfs node). */
    fun saved(context: Context): Boolean = prefs(context).getBoolean(KEY, false)

    /**
     * Whether limiting is on: the saved pref is authoritative (the live node reads its default
     * after boot until the receiver re-applies); the live node only upgrades a "false" answer,
     * e.g. when something else enabled limiting out-of-band.
     */
    fun enabled(context: Context): Boolean {
        if (saved(context)) return true
        for (n in NODES) {
            val v = readLong(n) ?: continue
            return v in 1..99          // any sub-100 stop level means limiting is active
        }
        return false
    }

    /** Persist the choice and try to apply it to the kernel node. True if a node accepted it. */
    fun set(context: Context, on: Boolean): Boolean {
        prefs(context).edit().putBoolean(KEY, on).apply()
        return apply(on)
    }

    /** Write the current [on] state to the first present node. Used by the boot receiver too. */
    fun apply(on: Boolean): Boolean {
        val value = if (on) "$LIMIT" else "100"
        for (n in NODES) {
            val f = File(n)
            if (!f.exists()) continue
            if (runCatching { f.writeText(value) }.isSuccess) return true
        }
        return false
    }

    private fun readLong(path: String): Long? =
        runCatching { File(path).readText().trim().toLong() }.getOrNull()
}
