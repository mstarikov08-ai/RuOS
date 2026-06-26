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

/** A slot on the home grid: either a single app or a folder of apps. */
sealed class HomeItem {
    abstract val id: String
    data class App(val info: AppInfo) : HomeItem() {
        override val id get() = info.packageName
    }
    data class Folder(
        override val id: String,
        var title: String,
        val apps: MutableList<AppInfo>
    ) : HomeItem()
}

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

    // ── Home layout: ordering + folders ─────────────────────────────────────────

    /**
     * The ordered home-screen items (apps + folders). Built from the saved layout,
     * with newly-installed apps appended and uninstalled ones dropped. Dock apps are
     * excluded so they don't appear twice.
     */
    fun getHomeItems(): List<HomeItem> {
        val all = getInstalledApps().associateBy { it.packageName }
        val dockPkgs = getDockApps().map { it.packageName }.toSet()
        val placed = HashSet<String>()
        val items = mutableListOf<HomeItem>()

        // 1. Restore saved layout, skipping anything no longer installed.
        val savedJson = prefs.getString(KEY_HOME_LAYOUT, null)
        if (savedJson != null) {
            try {
                val arr = org.json.JSONObject(savedJson).getJSONArray("items")
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    when (o.getString("type")) {
                        "app" -> {
                            val pkg = o.getString("pkg")
                            if (pkg !in dockPkgs) all[pkg]?.let { items.add(HomeItem.App(it)); placed.add(pkg) }
                        }
                        "folder" -> {
                            val pkgs = o.getJSONArray("apps")
                            val folderApps = mutableListOf<AppInfo>()
                            for (j in 0 until pkgs.length()) {
                                val pkg = pkgs.getString(j)
                                all[pkg]?.let { folderApps.add(it); placed.add(pkg) }
                            }
                            if (folderApps.isNotEmpty()) {
                                items.add(HomeItem.Folder(o.getString("id"), o.getString("title"), folderApps))
                            }
                        }
                    }
                }
            } catch (_: Exception) { /* corrupt layout — fall through to defaults */ }
        }

        // 2. Append any installed app not yet placed and not in the dock.
        for (info in getInstalledApps()) {
            if (info.packageName in placed || info.packageName in dockPkgs) continue
            items.add(HomeItem.App(info))
        }
        return items
    }

    fun saveHomeLayout(items: List<HomeItem>) {
        val arr = org.json.JSONArray()
        for (item in items) {
            val o = org.json.JSONObject()
            when (item) {
                is HomeItem.App -> { o.put("type", "app"); o.put("pkg", item.info.packageName) }
                is HomeItem.Folder -> {
                    o.put("type", "folder"); o.put("id", item.id); o.put("title", item.title)
                    val pkgs = org.json.JSONArray()
                    item.apps.forEach { pkgs.put(it.packageName) }
                    o.put("apps", pkgs)
                }
            }
            arr.put(o)
        }
        prefs.edit().putString(KEY_HOME_LAYOUT, org.json.JSONObject().put("items", arr).toString()).apply()
    }

    fun newFolderId(): String = "f" + System.currentTimeMillis().toString(36)

    companion object {
        private const val KEY_HOME_LAYOUT = "home_layout"
    }
}
