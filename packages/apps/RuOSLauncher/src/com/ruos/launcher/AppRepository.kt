package com.ruos.launcher

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable

data class AppInfo(
    val packageName: String,
    val activityName: String,
    val label: String,
    val icon: Drawable
)

/**
 * Loads and caches the list of launchable apps.
 * Dock uses the first 4 apps (or a saved order from prefs).
 */
class AppRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("ruos_launcher", Context.MODE_PRIVATE)
    private var cachedApps: List<AppInfo>? = null

    fun getInstalledApps(): List<AppInfo> {
        cachedApps?.let { return it }

        val pm = context.packageManager
        val apps = try {
            // LauncherApps.getActivityList() requires this app to be the active default launcher.
            // When not set as default (e.g. first launch from app drawer), it throws SecurityException.
            val launcherApps = context.getSystemService(LauncherApps::class.java)
            val user = android.os.Process.myUserHandle()
            launcherApps.getActivityList(null, user).map { info ->
                AppInfo(
                    packageName = info.applicationInfo.packageName,
                    activityName = info.name,
                    label = info.label.toString(),
                    icon = info.getIcon(0) ?: pm.defaultActivityIcon
                )
            }
        } catch (_: Exception) {
            // Fallback for when the app is not yet set as the default launcher.
            try {
                val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                    addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                }
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, 0).mapNotNull { ri ->
                    try {
                        val ai = ri.activityInfo ?: return@mapNotNull null
                        AppInfo(
                            packageName = ai.packageName,
                            activityName = ai.name,
                            label = ri.loadLabel(pm).toString(),
                            icon = ri.loadIcon(pm)
                        )
                    } catch (_: Exception) { null }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }.sortedBy { it.label.lowercase() }

        cachedApps = apps
        return apps
    }

    fun getDockApps(): List<AppInfo> {
        val all = getInstalledApps()
        val savedPkg = prefs.getString("dock_packages", null)

        if (savedPkg != null) {
            val pkgList = savedPkg.split(",")
            val byPkg = all.associateBy { it.packageName }
            val ordered = pkgList.mapNotNull { byPkg[it] }
            if (ordered.size == 4) return ordered
        }

        // Default dock: RuOS system apps first, then fallback to installed apps
        val preferred = listOf(
            "com.ruos.phone",      // RuOS Phone
            "com.ruos.messages",   // RuOS Messages
            "com.ruos.browser",    // RuOS Browser
            "com.ruos.camera",     // RuOS Camera
            // Fallbacks if RuOS apps not present (sideloaded APK scenario)
            "com.vk.android", "com.yandex.browser", "ru.rustore", "com.google.android.dialer"
        )
        val byPkg = all.associateBy { it.packageName }
        val dock = preferred.mapNotNull { byPkg[it] }.distinctBy { it.packageName }
        return if (dock.size >= 4) dock.take(4) else all.take(4)
    }

    fun saveDockOrder(packages: List<String>) {
        prefs.edit().putString("dock_packages", packages.joinToString(",")).apply()
    }

    fun invalidateCache() {
        cachedApps = null
    }
}
