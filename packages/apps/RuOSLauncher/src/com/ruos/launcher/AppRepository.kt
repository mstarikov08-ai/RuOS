package com.ruos.launcher

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.UserHandle
import android.os.UserManager

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

        val launcherApps = context.getSystemService(LauncherApps::class.java)
        val user = android.os.Process.myUserHandle()
        val pm = context.packageManager

        val activities = launcherApps.getActivityList(null, user)
        val apps = activities.map { info ->
            AppInfo(
                packageName = info.applicationInfo.packageName,
                activityName = info.name,
                label = info.label.toString(),
                icon = info.getIcon(0) ?: pm.defaultActivityIcon
            )
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

        // Default dock: first four prominent Russian apps, fallback to first 4
        val preferred = listOf("com.vk.android", "com.yandex.browser", "ru.rustore", "com.google.android.dialer")
        val byPkg = all.associateBy { it.packageName }
        val dock = preferred.mapNotNull { byPkg[it] }
        return if (dock.size >= 4) dock.take(4) else all.take(4)
    }

    fun saveDockOrder(packages: List<String>) {
        prefs.edit().putString("dock_packages", packages.joinToString(",")).apply()
    }

    fun invalidateCache() {
        cachedApps = null
    }
}
