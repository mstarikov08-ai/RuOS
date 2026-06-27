package com.ruos.settings.sections

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/**
 * Lists every app that requests a given permission category, with a switch reflecting the
 * grant state. Toggling grants/revokes via the platform PackageManager APIs (reflection,
 * since they are @SystemApi); if those aren't held it falls back to the app's system
 * details page where the user can change it manually.
 */
class PermissionDetailActivity : Activity() {

    private val golos = Typeface.create("golos", Typeface.NORMAL)
    private lateinit var perms: Array<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val titleText = intent.getStringExtra("title") ?: "Разрешение"
        perms = intent.getStringArrayExtra("perms") ?: arrayOf()

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#F2F2F7")); setPadding(0, dp(100), 0, dp(40))
        }
        col.addView(title(titleText))
        col.addView(note("Приложения, запросившие доступ. Переключатель включает или " +
            "отзывает разрешение."))

        val pm = packageManager
        val apps = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            .filter { pkg -> pkg.requestedPermissions?.any { it in perms } == true }
            .sortedBy { runCatching { pm.getApplicationLabel(it.applicationInfo!!).toString().lowercase() }.getOrDefault(it.packageName) }

        if (apps.isEmpty()) col.addView(note("Ни одно приложение не запрашивало этот доступ."))
        apps.forEach { col.addView(appRow(it.packageName)) }

        setContentView(ScrollView(this).apply { addView(col) })
    }

    private fun appRow(pkg: String): View {
        val pm = packageManager
        val ai = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
        val label = ai?.let { pm.getApplicationLabel(it).toString() } ?: pkg
        val icon = ai?.let { runCatching { pm.getApplicationIcon(it) }.getOrNull() }
        val sw = Switch(this).apply { isChecked = isGranted(pkg) }
        sw.setOnCheckedChangeListener { _, want -> toggle(pkg, want, sw) }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(10), dp(16), dp(10))
            addView(ImageView(context).apply { setImageDrawable(icon) }, LinearLayout.LayoutParams(dp(34), dp(34)).also { it.marginEnd = dp(12) })
            addView(TextView(context).apply { text = label; setTextColor(Color.BLACK); textSize = 16f; typeface = golos },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(sw)
        }
    }

    private fun isGranted(pkg: String): Boolean =
        perms.any { runCatching { packageManager.checkPermission(it, pkg) == PackageManager.PERMISSION_GRANTED }.getOrDefault(false) }

    private fun toggle(pkg: String, want: Boolean, sw: Switch) {
        val ok = perms.all { perm ->
            runCatching {
                val user: UserHandle = Process.myUserHandle()
                if (want) PackageManager::class.java.getMethod("grantRuntimePermission",
                    String::class.java, String::class.java, UserHandle::class.java).invoke(packageManager, pkg, perm, user)
                else PackageManager::class.java.getMethod("revokeRuntimePermission",
                    String::class.java, String::class.java, UserHandle::class.java).invoke(packageManager, pkg, perm, user)
                true
            }.getOrDefault(false)
        }
        if (!ok) {
            // No platform grant — send the user to the app's permission page instead.
            sw.isChecked = isGranted(pkg)
            Toast.makeText(this, "Откройте настройки приложения для изменения", Toast.LENGTH_SHORT).show()
            runCatching {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
            }
        }
    }

    private fun title(t: String) = TextView(this).apply {
        text = t; textSize = 28f; setTextColor(Color.BLACK); typeface = Typeface.create(golos, Typeface.BOLD); setPadding(dp(20), dp(4), dp(20), dp(12))
    }
    private fun note(t: String) = TextView(this).apply {
        text = t; textSize = 13f; setTextColor(Color.parseColor("#6C6C70")); typeface = golos; setPadding(dp(20), dp(8), dp(20), dp(10))
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
