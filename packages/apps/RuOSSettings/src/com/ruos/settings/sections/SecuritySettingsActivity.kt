package com.ruos.settings.sections

import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

class SecuritySettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUI())
    }

    private fun buildUI(): View {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#F2F2F7")) }
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(100), 0, dp(40))
        }

        col.addView(sectionLabel("FACE ID И БИОМЕТРИЯ"))
        col.addView(toggleSection(listOf(
            ToggleRow("Face ID", isFaceUnlockEnabled(), { enabled ->
                Settings.Secure.putInt(contentResolver, "face_unlock_enabled", if (enabled) 1 else 0)
            }),
            ToggleRow("Биометрия для приложений", true, {})
        )))
        col.addView(spacer(20))

        col.addView(sectionLabel("ИСТОЧНИКИ"))
        col.addView(toggleSection(listOf(
            ToggleRow("Неизвестные источники", isUnknownSourcesEnabled(), { enabled ->
                Settings.Secure.putInt(contentResolver, Settings.Secure.INSTALL_NON_MARKET_APPS, if (enabled) 1 else 0)
            })
        )))
        col.addView(spacer(4))
        col.addView(sectionNote("Разрешить установку приложений не из RuStore. Не рекомендуется."))

        col.addView(spacer(20))
        col.addView(sectionLabel("ПРОВЕРКА ЗАГРУЗКИ"))
        col.addView(infoRow("Verified Boot", "Включена"))
        col.addView(spacer(20))

        col.addView(sectionLabel("GOOGLE"))
        col.addView(toggleSection(listOf(
            ToggleRow("Google Play Services", isGoogleServicesEnabled(), {})
        )))
        col.addView(sectionNote("Google Play Services присутствует, но не используется по умолчанию."))

        scroll.addView(col)
        root.addView(scroll)
        return root
    }

    private fun isFaceUnlockEnabled() =
        Settings.Secure.getInt(contentResolver, "face_unlock_enabled", 1) == 1

    private fun isUnknownSourcesEnabled() =
        Settings.Secure.getInt(contentResolver, Settings.Secure.INSTALL_NON_MARKET_APPS, 0) == 1

    private fun isGoogleServicesEnabled() = try {
        packageManager.getApplicationInfo("com.google.android.gms", 0)
        true
    } catch (_: PackageManager.NameNotFoundException) { false }

    data class ToggleRow(val label: String, val enabled: Boolean, val onToggle: (Boolean) -> Unit)

    private fun toggleSection(rows: List<ToggleRow>): View {
        val wrapper = FrameLayout(this).apply { setPadding(dp(16), 0, dp(16), 0) }
        val section = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(Color.WHITE)
            }
        }
        rows.forEachIndexed { i, row ->
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(8), dp(16), dp(8))
            }
            r.addView(TextView(this).apply {
                text = row.label; textSize = 17f; setTextColor(Color.BLACK)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            val toggle = Switch(this).apply {
                isChecked = row.enabled
                setOnCheckedChangeListener { _, checked -> row.onToggle(checked) }
                // Spring animation on the thumb handled by system
            }
            r.addView(toggle)
            section.addView(r)
            if (i < rows.size - 1) {
                section.addView(View(this).apply { setBackgroundColor(Color.parseColor("#C6C6C8")) },
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).also { it.marginStart = dp(16) })
            }
        }
        wrapper.addView(section)
        return wrapper
    }

    private fun infoRow(label: String, value: String): View {
        val wrapper = FrameLayout(this).apply { setPadding(dp(16), 0, dp(16), 0) }
        val section = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(Color.WHITE)
            }
        }
        section.addView(TextView(this).apply { text = label; textSize = 17f; setTextColor(Color.BLACK) },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        section.addView(TextView(this).apply { text = value; textSize = 17f; setTextColor(Color.GRAY) })
        wrapper.addView(section)
        return wrapper
    }

    private fun sectionLabel(text: String) = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(Color.parseColor("#6C6C70"))
        setPadding(dp(32), dp(4), dp(16), dp(4))
    }

    private fun sectionNote(text: String) = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(Color.parseColor("#6C6C70"))
        setPadding(dp(32), dp(4), dp(32), dp(4))
    }

    private fun spacer(dp: Int) = View(this).also {
        it.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(dp))
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
