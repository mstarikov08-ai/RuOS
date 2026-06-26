package com.ruos.focus.ui

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.ruos.focus.util.Fonts

/**
 * Pick which apps may break through a focus with banners/sounds. The chosen set is handed
 * back to [FocusEditActivity] via a scratch SharedPreferences keyed by the focus id (the
 * focus isn't saved yet, so we can't write it onto the model directly).
 */
class AppPickerActivity : Activity() {

    private val selected = linkedSetOf<String>()
    private lateinit var focusId: String

    private val d get() = resources.displayMetrics.density
    private fun dp(v: Float) = (v * d).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        focusId = intent.getStringExtra("focus_id") ?: "tmp"
        intent.getStringArrayListExtra("selected")?.let { selected.addAll(it) }

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK) }
        root.addView(TextView(this).apply {
            text = "Приложения"; setTextColor(Color.WHITE); textSize = 28f; typeface = Fonts.bold
            setPadding(dp(16f), dp(48f), dp(16f), dp(12f))
        })
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(col) })
        setContentView(root)

        val pm = packageManager
        pm.getInstalledApplications(0)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
            .forEach { col.addView(row(it)) }
    }

    private fun row(ai: ApplicationInfo): View {
        val pm = packageManager
        val cb = CheckBox(this).apply { isChecked = selected.contains(ai.packageName) }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f), dp(10f), dp(16f), dp(10f))
            isClickable = true
            setOnClickListener { cb.isChecked = !cb.isChecked }
            cb.setOnCheckedChangeListener { _, v ->
                if (v) selected.add(ai.packageName) else selected.remove(ai.packageName)
            }
            addView(ImageView(context).apply { setImageDrawable(pm.getApplicationIcon(ai)) },
                LinearLayout.LayoutParams(dp(36f), dp(36f)).also { it.marginEnd = dp(14f) })
            addView(TextView(context).apply {
                text = pm.getApplicationLabel(ai); setTextColor(Color.WHITE); textSize = 16f; typeface = Fonts.regular
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(cb)
        }
    }

    override fun onPause() {
        super.onPause()
        // hand the selection back to the editor
        getSharedPreferences(SCRATCH, Context.MODE_PRIVATE).edit()
            .putStringSet(focusId, selected).apply()
    }

    companion object {
        const val SCRATCH = "ruos_focus_pick"
    }
}
