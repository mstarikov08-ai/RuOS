package com.ruos.settings.sections

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * iOS-style Settings search. A flat index of every settings destination (title +
 * Russian keywords) is filtered instantly as the user types; tapping a result opens it.
 * Reached from the search bar at the top of [com.ruos.settings.MainActivity].
 */
class SettingsSearchActivity : Activity() {

    private data class Entry(
        val title: String,
        val group: String,
        val keywords: String,
        val cls: Class<*>? = null,
        val component: ComponentName? = null
    )

    private val index by lazy { buildIndex() }
    private lateinit var results: LinearLayout
    private val density get() = resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).toInt()
    private val golos = Typeface.create("golos", Typeface.NORMAL)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F2F2F7"))
            setPadding(0, dp(44), 0, 0)
        }

        // search bar row: [ field ] Отмена
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        val field = EditText(this).apply {
            hint = "Поиск"; setHintTextColor(Color.parseColor("#8E8E93"))
            setTextColor(Color.BLACK); textSize = 17f; typeface = golos
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat(); setColor(Color.parseColor("#E3E3E8"))
            }
            setPadding(dp(12), dp(9), dp(12), dp(9))
            isSingleLine = true
        }
        bar.addView(field, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(TextView(this).apply {
            text = "Отмена"; setTextColor(Color.parseColor("#007AFF")); textSize = 16f; typeface = golos
            setPadding(dp(12), 0, dp(4), 0); isClickable = true
            setOnClickListener { finish() }
        })
        root.addView(bar)

        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(results) })
        setContentView(root)

        field.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { render(s?.toString().orEmpty()) }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        render("")
        field.requestFocus()
        field.postDelayed({
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
        }, 120)
    }

    private fun render(query: String) {
        results.removeAllViews()
        val q = query.trim().lowercase()
        val matches = if (q.isEmpty()) index
            else index.filter { it.title.lowercase().contains(q) || it.keywords.lowercase().contains(q) }
        if (matches.isEmpty()) {
            results.addView(TextView(this).apply {
                text = "Ничего не найдено"; setTextColor(Color.parseColor("#8E8E93"))
                textSize = 15f; typeface = golos; gravity = Gravity.CENTER
                setPadding(0, dp(40), 0, 0)
            })
            return
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat(); setColor(Color.WHITE)
            }
        }
        matches.forEachIndexed { i, e ->
            card.addView(rowFor(e))
            if (i < matches.size - 1) card.addView(View(this).apply {
                setBackgroundColor(Color.parseColor("#E5E5EA"))
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).also { it.marginStart = dp(16) })
        }
        results.addView(LinearLayout(this).apply {
            setPadding(dp(16), dp(6), dp(16), dp(6)); addView(card,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        })
    }

    private fun rowFor(e: Entry): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(11), dp(16), dp(11)); isClickable = true
        setOnClickListener { open(e) }
        addView(TextView(this@SettingsSearchActivity).apply {
            text = e.title; setTextColor(Color.BLACK); textSize = 17f; typeface = golos
        })
        addView(TextView(this@SettingsSearchActivity).apply {
            text = e.group; setTextColor(Color.parseColor("#8E8E93")); textSize = 13f; typeface = golos
        })
    }

    private fun open(e: Entry) {
        val intent = when {
            e.cls != null -> Intent(this, e.cls)
            e.component != null -> Intent().setComponent(e.component)
            else -> return
        }
        runCatching { startActivity(intent) }
    }

    private fun buildIndex(): List<Entry> = listOf(
        Entry("Wi-Fi", "Связь", "вайфай wifi сеть интернет пароль qr", WifiSettingsActivity::class.java),
        Entry("Bluetooth", "Связь", "блютус наушники устройства сопряжение", BluetoothSettingsActivity::class.java),
        Entry("Экран и яркость", "Экран", "яркость авто адаптивная ночной режим night shift тёмная светлая тема размер текста поворот",
            DisplaySettingsActivity::class.java),
        Entry("Обои", "Экран", "фон рабочий стол заставка картинка", WallpaperPickerActivity::class.java),
        Entry("Защита и Face ID", "Безопасность", "пароль код отпечаток лицо биометрия блокировка",
            cls = SecuritySettingsActivity::class.java),
        Entry("Об устройстве", "Система", "версия android память модель серийный сборка имя",
            AboutDeviceActivity::class.java),
        Entry("Режим ожидания", "Экран", "standby режим ожидания часы фоторамка",
            component = ComponentName("com.ruos.standby", "com.ruos.standby.ui.StandbySettingsActivity")),
        Entry("Уведомления", "Приложения", "баннеры звуки бейджи уведомления",
            component = ComponentName("com.ruos.notify", "com.ruos.notify.ui.NotificationSettingsActivity")),
        Entry("Журнал", "Приложения", "дневник журнал записи",
            component = ComponentName("com.ruos.journal", "com.ruos.journal.ui.JournalSettingsActivity")),
        Entry("Фокусирование", "Приложения", "фокус не беспокоить работа сон концентрация",
            component = ComponentName("com.ruos.focus", "com.ruos.focus.ui.FocusListActivity")),
        Entry("Face ID и код-пароль", "Безопасность", "фейс айди код пароль биометрия",
            component = ComponentName("com.ruos.auth", "com.ruos.auth.ui.AuthSettingsActivity"))
    )
}
