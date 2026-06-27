package com.ruos.settings

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.ruos.settings.sections.*

/**
 * RuOS Settings — iOS Settings clone.
 *
 * Structure:
 *   Profile card at top (VK ID avatar + name)
 *   Grouped sections with iOS-style separators
 *   Each row: coloured icon + label + disclosure arrow (›)
 *   Toggle switches with spring animation
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setDecorFitsSystemWindows(false)
        window.insetsController?.apply {
            setSystemBarsAppearance(
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        }
        setContentView(buildUI())
    }

    private fun buildUI(): View {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#F2F2F7"))  // iOS settings grey
        }

        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, statusBarHeight() + dp(8), 0, dp(40))
        }

        // Title
        val title = TextView(this).apply {
            text = "Настройки"
            textSize = 34f
            setTextColor(Color.BLACK)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        content.addView(title)

        // iOS-style search bar — opens instant search across all settings.
        content.addView(buildSearchBar())
        content.addView(spacer(14))

        // Profile card
        content.addView(buildProfileCard())
        content.addView(spacer(20))

        // Section: Connectivity
        content.addView(sectionHeader(""))
        content.addView(buildSection(listOf(
            Row("Wi-Fi", 0xFF34AADC.toInt(), android.R.drawable.stat_sys_wifi_signal_4, WifiSettingsActivity::class.java),
            Row("Bluetooth", 0xFF007AFF.toInt(), android.R.drawable.stat_sys_data_bluetooth, BluetoothSettingsActivity::class.java),
            Row("Сотовая связь", 0xFF4CD964.toInt(), android.R.drawable.stat_sys_signal_4, null),
            Row("Режим модема", 0xFF4CD964.toInt(), android.R.drawable.ic_menu_share, null)
        )))
        content.addView(spacer(20))

        // Section: Personalization
        content.addView(buildSection(listOf(
            Row("Экран и яркость", 0xFFFF9500.toInt(), android.R.drawable.ic_lock_idle_alarm, DisplaySettingsActivity::class.java),
            Row("Звуки и тактильный отклик", 0xFFFF3B30.toInt(), android.R.drawable.ic_lock_silent_mode, null),
            Row("Обои", 0xFF5856D6.toInt(), android.R.drawable.ic_menu_gallery, WallpaperPickerActivity::class.java),
            Row("Режим ожидания", 0xFF000000.toInt(), android.R.drawable.ic_lock_idle_alarm, null,
                component = android.content.ComponentName(
                    "com.ruos.standby", "com.ruos.standby.ui.StandbySettingsActivity")),
            Row("Экран «Домой»", 0xFF5856D6.toInt(), android.R.drawable.ic_menu_manage, null),
            Row("Аккумулятор", 0xFF34C759.toInt(), android.R.drawable.ic_lock_idle_low_battery, BatterySettingsActivity::class.java),
            Row("Хранилище", 0xFF8E8E93.toInt(), android.R.drawable.ic_menu_save, StorageSettingsActivity::class.java),
            Row("Универсальный доступ", 0xFF0A84FF.toInt(), android.R.drawable.ic_menu_view, AccessibilitySettingsActivity::class.java),
            Row("AssistiveTouch", 0xFF8E8E93.toInt(), android.R.drawable.ic_menu_compass, null,
                component = android.content.ComponentName(
                    "com.ruos.assist", "com.ruos.assist.ui.AssistiveTouchActivity"))
        )))
        content.addView(spacer(20))

        // Section: Apps
        content.addView(buildSection(listOf(
            Row("Уведомления", 0xFFFF3B30.toInt(), android.R.drawable.stat_notify_chat, null,
                component = android.content.ComponentName(
                    "com.ruos.notify", "com.ruos.notify.ui.NotificationSettingsActivity")),
            Row("Журнал", 0xFFFF9500.toInt(), android.R.drawable.ic_menu_edit, null,
                component = android.content.ComponentName(
                    "com.ruos.journal", "com.ruos.journal.ui.JournalSettingsActivity")),
            Row("Фокусирование", 0xFF5856D6.toInt(), android.R.drawable.ic_lock_silent_mode_off, null,
                component = android.content.ComponentName(
                    "com.ruos.focus", "com.ruos.focus.ui.FocusListActivity")),
            Row("Время использования", 0xFFFF9500.toInt(), android.R.drawable.ic_menu_recent_history, null)
        )))
        content.addView(spacer(20))

        // Section: Security
        content.addView(buildSection(listOf(
            Row("Face ID и код-пароль", 0xFF1C1C1E.toInt(), android.R.drawable.ic_secure, null,
                component = android.content.ComponentName(
                    "com.ruos.auth", "com.ruos.auth.ui.AuthSettingsActivity")),
            Row("Пароли", 0xFF8E8E93.toInt(), android.R.drawable.ic_lock_lock, null,
                component = android.content.ComponentName(
                    "com.ruos.keychain", "com.ruos.keychain.ui.KeychainActivity")),
            Row("Найти устройство", 0xFF34C759.toInt(), android.R.drawable.ic_menu_mylocation, null,
                component = android.content.ComponentName(
                    "com.ruos.findmy", "com.ruos.findmy.ui.FindMyActivity")),
            Row("Экстренный вызов — SOS", 0xFFFF3B30.toInt(), android.R.drawable.ic_menu_call, null,
                component = android.content.ComponentName(
                    "com.ruos.emergency", "com.ruos.emergency.ui.MedicalIdEditActivity")),
            Row("Конфиденциальность", 0xFF34AADC.toInt(), android.R.drawable.ic_partial_secure, null)
        )))
        content.addView(spacer(20))

        // Section: System
        content.addView(buildSection(listOf(
            Row("Об устройстве", 0xFF8E8E93.toInt(), android.R.drawable.ic_menu_info_details, AboutDeviceActivity::class.java)
        )))

        scroll.addView(content)
        root.addView(scroll)
        return root
    }

    private fun buildSearchBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat(); setColor(Color.parseColor("#E3E3E8"))
            }
            setPadding(dp(10), dp(9), dp(10), dp(9))
            isClickable = true
            setOnClickListener {
                startActivity(Intent(this@MainActivity,
                    com.ruos.settings.sections.SettingsSearchActivity::class.java))
                overridePendingTransition(0, 0)
            }
        }
        // canvas-drawn magnifier glyph (no emoji)
        bar.addView(object : View(this) {
            val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#8E8E93"); style = android.graphics.Paint.Style.STROKE
                strokeWidth = dp(2).toFloat(); strokeCap = android.graphics.Paint.Cap.ROUND
            }
            override fun onDraw(c: android.graphics.Canvas) {
                val u = dp(1).toFloat()
                c.drawCircle(7 * u, 7 * u, 4.5f * u, p)
                c.drawLine(10.2f * u, 10.2f * u, 14f * u, 14f * u, p)
            }
        }, LinearLayout.LayoutParams(dp(18), dp(18)).also { it.marginStart = dp(4); it.marginEnd = dp(8) })
        bar.addView(TextView(this).apply {
            text = "Поиск"; setTextColor(Color.parseColor("#8E8E93")); textSize = 17f
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        return FrameLayout(this).apply {
            setPadding(dp(16), 0, dp(16), 0)
            addView(bar, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun buildProfileCard(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }

        val avatar = ImageView(this).apply {
            setBackgroundColor(Color.parseColor("#D94F3D"))
            clipToOutline = true
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#D94F3D"))
            }
        }
        card.addView(avatar, dp(56), dp(56))
        card.addView(spacerH(12), dp(12), 1)

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(TextView(this).apply {
            text = "Профиль VK"
            textSize = 17f
            setTextColor(Color.BLACK)
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        col.addView(TextView(this).apply {
            text = "Apple ID, iCloud, контент и покупки"
            textSize = 13f
            setTextColor(Color.GRAY)
        })
        card.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(disclosureArrow())

        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        wrapper.addView(card)
        // Round corners
        val wrapperBg = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(Color.WHITE)
        }
        wrapper.background = wrapperBg

        val outer = FrameLayout(this)
        outer.setPadding(dp(16), 0, dp(16), 0)
        outer.addView(wrapper)
        return outer
    }

    data class Row(
        val label: String, val iconColor: Int, val iconRes: Int, val target: Class<*>?,
        val component: android.content.ComponentName? = null   // cross-app deep link
    )

    private fun buildSection(rows: List<Row>): View {
        val wrapper = FrameLayout(this)
        wrapper.setPadding(dp(16), 0, dp(16), 0)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(Color.WHITE)
            }
        }

        rows.forEachIndexed { i, row ->
            col.addView(buildRow(row))
            if (i < rows.size - 1) {
                val divider = View(this).apply {
                    setBackgroundColor(Color.parseColor("#C6C6C8"))
                }
                col.addView(divider, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).also {
                    it.marginStart = dp(52)
                })
            }
        }

        wrapper.addView(col)
        return wrapper
    }

    private fun buildRow(row: Row): View {
        val rowView = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            setPadding(dp(12), dp(11), dp(12), dp(11))
            setOnClickListener {
                when {
                    row.component != null -> runCatching {
                        startActivity(Intent().setComponent(row.component))
                    }
                    row.target != null -> startActivity(Intent(this@MainActivity, row.target))
                }
            }
            background = android.util.TypedValue().let { tv ->
                context.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
                context.getDrawable(tv.resourceId)
            }
        }

        // Icon with coloured background
        val iconBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(7).toFloat()
            setColor(row.iconColor)
        }
        val iconView = ImageView(this).apply {
            setImageResource(row.iconRes)
            setColorFilter(Color.WHITE)
            background = iconBg
            setPadding(dp(5), dp(5), dp(5), dp(5))
        }
        rowView.addView(iconView, dp(30), dp(30))
        rowView.addView(spacerH(12), dp(12), 1)

        val label = TextView(this).apply {
            text = row.label
            textSize = 17f
            setTextColor(Color.BLACK)
        }
        rowView.addView(label, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        rowView.addView(disclosureArrow())

        return rowView
    }

    private fun disclosureArrow() = TextView(this).apply {
        text = "›"
        textSize = 22f
        setTextColor(Color.parseColor("#C7C7CC"))
    }

    private fun sectionHeader(text: String) = TextView(this).apply {
        this.text = text.uppercase()
        textSize = 13f
        setTextColor(Color.parseColor("#6C6C70"))
        setPadding(dp(36), dp(4), dp(16), dp(4))
    }

    private fun spacer(dp: Int) = View(this).also {
        it.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(dp))
    }

    private fun spacerH(dp: Int) = View(this)

    private fun statusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else dp(24)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
