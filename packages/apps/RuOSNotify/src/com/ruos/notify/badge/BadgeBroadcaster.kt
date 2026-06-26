package com.ruos.notify.badge

import android.content.Context
import android.content.Intent

/**
 * Broadcasts per-package unread counts to the launcher, which draws iOS-style red
 * badges on home + dock icons. The count clears when the app is opened (the launcher
 * drops a package's badge on launch and we also recompute as notifications are
 * dismissed).
 */
object BadgeBroadcaster {
    const val ACTION_BADGES = "com.ruos.notify.BADGES"
    const val EXTRA_PACKAGES = "packages"
    const val EXTRA_COUNTS = "counts"

    fun send(context: Context, counts: Map<String, Int>) {
        val pkgs = ArrayList(counts.keys)
        val nums = IntArray(pkgs.size) { counts[pkgs[it]] ?: 0 }
        context.sendBroadcast(Intent(ACTION_BADGES).apply {
            setPackage("com.ruos.launcher")
            putStringArrayListExtra(EXTRA_PACKAGES, pkgs)
            putExtra(EXTRA_COUNTS, nums)
        })
    }
}
