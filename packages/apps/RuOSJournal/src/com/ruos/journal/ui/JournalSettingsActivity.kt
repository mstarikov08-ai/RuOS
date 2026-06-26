package com.ruos.journal.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.ruos.journal.model.JournalStore
import com.ruos.journal.reminder.JournalReminder
import com.ruos.journal.util.Fonts

/** Settings → Журнал: reminder, suggestion toggles, lock, export, delete all. */
class JournalSettingsActivity : Activity() {

    private lateinit var store: JournalStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = JournalStore(this)
        render()
    }

    private fun render() {
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK) }
        root.addView(TextView(this).apply {
            text = "Журнал"; setTextColor(Color.WHITE); textSize = 28f; typeface = Fonts.bold
            setPadding(dp(16), dp(48), dp(16), dp(12))
        })
        val scroll = ScrollView(this); val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(col); root.addView(scroll); setContentView(root)

        col.addView(sectionLabel("Напоминание"))
        col.addView(switchRow("Ежедневное напоминание", store.reminderEnabled) {
            store.reminderEnabled = it; JournalReminder.update(this)
        })
        col.addView(navRow("Время напоминания", "%02d:00".format(store.reminderHour)) { pickHour() })

        col.addView(sectionLabel("Предложения"))
        col.addView(switchRow("По фотографиям", store.suggestionEnabled("photos")) { store.setSuggestionEnabled("photos", it) })
        col.addView(switchRow("По календарю", store.suggestionEnabled("calendar")) { store.setSuggestionEnabled("calendar", it) })
        col.addView(switchRow("Размышления", store.suggestionEnabled("reflect")) { store.setSuggestionEnabled("reflect", it) })

        col.addView(sectionLabel("Конфиденциальность"))
        col.addView(switchRow("Блокировка Face ID / Touch ID", store.lockEnabled) { store.lockEnabled = it })

        col.addView(sectionLabel(""))
        col.addView(navRow("Экспортировать журнал", "Текст") { exportText() })
        col.addView(navRow("Удалить все записи", "", danger = true) { confirmDelete() })
    }

    private fun pickHour() {
        val hours = (0..23).map { "%02d:00".format(it) }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Время напоминания")
            .setSingleChoiceItems(hours, store.reminderHour) { dlg, w ->
                store.reminderHour = w; JournalReminder.update(this); dlg.dismiss(); render()
            }.show()
    }

    private fun exportText() {
        val sb = StringBuilder("Мой журнал RuOS\n\n")
        val fmt = java.text.SimpleDateFormat("d MMMM yyyy, HH:mm", java.util.Locale("ru"))
        store.getEntries().forEach { e ->
            sb.append(fmt.format(java.util.Date(e.timestamp))).append("\n")
            e.mood?.let { sb.append("Настроение: ${it.ruName}\n") }
            sb.append(e.text).append("\n")
            if (e.tags.isNotEmpty()) sb.append(e.tags.joinToString(" ") { "#$it" }).append("\n")
            sb.append("\n———\n\n")
        }
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, sb.toString())
            putExtra(Intent.EXTRA_SUBJECT, "Мой журнал")
        }, "Экспорт журнала"))
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("Удалить все записи?")
            .setMessage("Это действие необратимо. Все записи будут навсегда удалены с устройства.")
            .setPositiveButton("Удалить") { _, _ -> store.deleteAll() }
            .setNegativeButton("Отмена", null).show()
    }

    private fun sectionLabel(t: String) = TextView(this).apply {
        text = t; setTextColor(Color.parseColor("#8E8E93")); textSize = 13f
        setPadding((16 * resources.displayMetrics.density).toInt(),
            (18 * resources.displayMetrics.density).toInt(), 0, (6 * resources.displayMetrics.density).toInt())
    }

    private fun switchRow(title: String, initial: Boolean, onChange: (Boolean) -> Unit): View {
        val d = resources.displayMetrics.density
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply { setColor(Color.parseColor("#1C1C1E")) }
            setPadding((16 * d).toInt(), (8 * d).toInt(), (16 * d).toInt(), (8 * d).toInt())
            addView(TextView(context).apply { text = title; setTextColor(Color.WHITE); textSize = 16f },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(Switch(context).apply { isChecked = initial
                setOnCheckedChangeListener { b, c -> b.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK); onChange(c) } })
        }
    }

    private fun navRow(title: String, value: String, danger: Boolean = false, onClick: () -> Unit): View {
        val d = resources.displayMetrics.density
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply { setColor(Color.parseColor("#1C1C1E")) }
            setPadding((16 * d).toInt(), (14 * d).toInt(), (16 * d).toInt(), (14 * d).toInt())
            isClickable = true; setOnClickListener { performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK); onClick() }
            addView(TextView(context).apply {
                text = title; textSize = 16f
                setTextColor(if (danger) Color.parseColor("#FF453A") else Color.WHITE)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            if (value.isNotEmpty()) addView(TextView(context).apply { text = value; setTextColor(Color.parseColor("#8E8E93")); textSize = 16f })
        }
    }
}
