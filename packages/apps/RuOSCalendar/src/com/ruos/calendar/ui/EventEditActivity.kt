package com.ruos.calendar.ui

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.ruos.calendar.data.CalendarRepo
import com.ruos.calendar.data.EventDetail
import java.util.Calendar

/** Create / edit / delete a calendar event (written to the system CalendarContract). */
class EventEditActivity : Activity() {

    private lateinit var repo: CalendarRepo
    private var existing: EventDetail? = null
    private val d get() = resources.displayMetrics.density
    private fun dp(v: Float) = (v * d).toInt()

    private lateinit var titleF: EditText
    private lateinit var locF: EditText
    private lateinit var notesF: EditText
    private val begin = Calendar.getInstance()
    private val end = Calendar.getInstance()
    private var allDay = false
    private var reminderMin = 10

    private lateinit var beginBtn: TextView
    private lateinit var endBtn: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = CalendarRepo(this)

        val eventId = intent.getLongExtra("eventId", -1L)
        if (eventId > 0) existing = repo.loadEvent(eventId)

        existing?.let { e ->
            begin.timeInMillis = e.begin; end.timeInMillis = e.end; allDay = e.allDay
        } ?: run {
            val day = intent.getLongExtra("day", System.currentTimeMillis())
            begin.timeInMillis = day; begin.set(Calendar.HOUR_OF_DAY, 9); begin.set(Calendar.MINUTE, 0)
            end.timeInMillis = begin.timeInMillis + 3600_000
        }
        setContentView(build())
    }

    private fun build(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK); setPadding(dp(16f), dp(48f), dp(16f), dp(40f))
        }
        col.addView(TextView(this).apply {
            text = if (existing == null) "Новое событие" else "Событие"
            setTextColor(Color.WHITE); textSize = 26f; typeface = Fonts.bold; setPadding(0, 0, 0, dp(12f))
        })

        titleF = field("Название", existing?.title ?: "")
        locF = field("Место", existing?.location ?: "")
        notesF = field("Заметки", existing?.notes ?: "")
        col.addView(card(titleF)); col.addView(card(locF))

        col.addView(switchRow("Весь день", allDay) { on -> allDay = on; updateTimeButtons() })

        beginBtn = timeRow("Начало") { pick(begin) { updateTimeButtons() } }
        endBtn = timeRow("Конец") { pick(end) { updateTimeButtons() } }
        col.addView(card(beginBtn)); col.addView(card(endBtn))
        updateTimeButtons()

        col.addView(card(notesF))

        // reminder selector (cycles)
        val remValues = listOf(-1, 0, 5, 10, 30, 60)
        val remLabels = listOf("Нет", "Вовремя", "За 5 мин", "За 10 мин", "За 30 мин", "За 1 час")
        var remIdx = remValues.indexOf(reminderMin).coerceAtLeast(3)
        val remText = TextView(this).apply { text = remLabels[remIdx]; setTextColor(0xFF8E8E93.toInt()); textSize = 16f }
        col.addView(card(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; isClickable = true
            setOnClickListener { remIdx = (remIdx + 1) % remValues.size; reminderMin = remValues[remIdx]; remText.text = remLabels[remIdx] }
            addView(TextView(this@EventEditActivity).apply { text = "Напоминание"; setTextColor(Color.WHITE); textSize = 16f },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(remText)
        }))

        col.addView(Button(this).apply {
            text = "Сохранить"; setTextColor(Color.WHITE); typeface = Fonts.medium; isAllCaps = false
            background = GradientDrawable().apply { cornerRadius = dp(12f).toFloat(); setColor(0xFFFF3B30.toInt()) }
            setOnClickListener { save() }
        }, lp(dp(52f)).also { it.topMargin = dp(20f) })

        if (existing != null) col.addView(Button(this).apply {
            text = "Удалить событие"; setTextColor(0xFFFF453A.toInt()); typeface = Fonts.medium; isAllCaps = false
            background = GradientDrawable().apply { cornerRadius = dp(12f).toFloat(); setColor(0xFF1C1C1E.toInt()) }
            setOnClickListener {
                if (repo.delete(existing!!.eventId)) finish()
                else Toast.makeText(this@EventEditActivity, "Не удалось удалить", Toast.LENGTH_SHORT).show()
            }
        }, lp(dp(52f)).also { it.topMargin = dp(10f) })

        return ScrollView(this).apply { addView(col) }
    }

    private fun updateTimeButtons() {
        val fmt = if (allDay) java.text.SimpleDateFormat("d MMM", java.util.Locale("ru"))
        else java.text.SimpleDateFormat("d MMM, HH:mm", java.util.Locale("ru"))
        beginBtn.text = "Начало:  ${fmt.format(begin.time)}"
        endBtn.text = "Конец:  ${fmt.format(end.time)}"
    }

    private fun pick(c: Calendar, after: () -> Unit) {
        DatePickerDialog(this, { _, y, m, day ->
            c.set(Calendar.YEAR, y); c.set(Calendar.MONTH, m); c.set(Calendar.DAY_OF_MONTH, day)
            if (allDay) { after() } else TimePickerDialog(this, { _, h, min ->
                c.set(Calendar.HOUR_OF_DAY, h); c.set(Calendar.MINUTE, min); after()
            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show()
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun save() {
        if (titleF.text.toString().isBlank()) { Toast.makeText(this, "Введите название", Toast.LENGTH_SHORT).show(); return }
        if (end.timeInMillis <= begin.timeInMillis) end.timeInMillis = begin.timeInMillis + 3600_000
        val title = titleF.text.toString().trim(); val loc = locF.text.toString().trim(); val notes = notesF.text.toString().trim()
        val ok = if (existing != null) {
            repo.update(existing!!.eventId, title, loc, notes, begin.timeInMillis, end.timeInMillis, allDay)
        } else {
            val calId = repo.defaultCalendarId()
            if (calId == null) { Toast.makeText(this, "Нет доступного календаря", Toast.LENGTH_SHORT).show(); return }
            repo.insert(calId, title, loc, notes, begin.timeInMillis, end.timeInMillis, allDay, reminderMin) != null
        }
        if (ok) finish() else Toast.makeText(this, "Не удалось сохранить", Toast.LENGTH_SHORT).show()
    }

    private fun field(hint: String, value: String) = EditText(this).apply {
        setText(value); setHint(hint); setHintTextColor(0xFF8E8E93.toInt()); setTextColor(Color.WHITE)
        textSize = 17f; typeface = Fonts.regular; inputType = InputType.TYPE_CLASS_TEXT
    }
    private fun timeRow(label: String, onTap: () -> Unit) = TextView(this).apply {
        setTextColor(Color.WHITE); textSize = 16f; typeface = Fonts.regular; isClickable = true; setOnClickListener { onTap() }
    }
    private fun switchRow(label: String, initial: Boolean, onChange: (Boolean) -> Unit) = card(LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        addView(TextView(this@EventEditActivity).apply { text = label; setTextColor(Color.WHITE); textSize = 16f },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(Switch(this@EventEditActivity).apply { isChecked = initial; setOnCheckedChangeListener { _, v -> onChange(v) } })
    })
    private fun card(inner: View) = LinearLayout(this).apply {
        background = GradientDrawable().apply { cornerRadius = dp(12f).toFloat(); setColor(0xFF1C1C1E.toInt()) }
        setPadding(dp(14f), dp(12f), dp(14f), dp(12f))
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); lp.topMargin = dp(8f); layoutParams = lp
        addView(inner, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }
    private fun lp(h: Int) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h)
}
