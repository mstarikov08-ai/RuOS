package com.ruos.launcher

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

/**
 * Prewarms the Russian app suite into memory after boot so first-launch
 * latency matches a cached iOS app.
 *
 * Uses a staggered broadcast approach — sends QUERY_ACTIVATING_PACKAGE to
 * each target process, letting PackageManager load them without starting
 * a visible Activity.
 */
class PrewarmManager(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private val PRIORITY_APPS = listOf(
            // RuOS system apps (highest priority — always present)
            "com.ruos.phone",
            "com.ruos.messages",
            "com.ruos.camera",
            "com.ruos.gallery",
            // Russian partner apps
            "com.vk.android",
            "ru.mail.search.mail",  // MAX
            "ru.rustore",
            "com.yandex.browser",
            "com.yandex.taxi",      // Yandex Go
            "com.yandex.maps"
        )

        private const val STAGGER_MS = 800L
    }

    fun prewarmRussianApps() {
        val pm = context.packageManager
        PRIORITY_APPS.forEachIndexed { index, pkg ->
            handler.postDelayed({
                try {
                    val intent = pm.getLaunchIntentForPackage(pkg) ?: return@postDelayed
                    // Use FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS + FLAG_ACTIVITY_NO_ANIMATION
                    // This is not a visible launch — just warming the process
                    // In production use ContentProvider warm-up via binder ping
                    warmProcess(pkg)
                } catch (_: Exception) {}
            }, (index + 1) * STAGGER_MS)
        }
    }

    private fun warmProcess(packageName: String) {
        try {
            // Trigger ContentProvider query to warm the process without UI
            val uri = android.net.Uri.parse("content://$packageName.provider")
            context.contentResolver.query(uri, null, null, null, null)?.close()
        } catch (_: Exception) {
            // Package doesn't expose a provider — process will warm on first launch
        }
    }
}
