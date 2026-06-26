package com.ruos.reminders.ui

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.location.LocationManager
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.ruos.reminders.model.Reminder
import com.ruos.reminders.schedule.ReminderScheduler
import com.ruos.reminders.store.ReminderStore
import java.util.Calendar

/** Create / edit a reminder: title, notes, time trigger, location (arrive/leave) trigger, flag. */
class ReminderEditActivity : Activity() {

    private lateinit var store: ReminderStore
    private var existing: Reminder? = null
    private var listId = "default"
    private val d get() = resources.displayMetrics.density
    private fun dp(v: Float) = (v * d).toInt()

    private lateinit var titleF: EditText
    private lateinit var notesF: EditText
    private var hasTime = false
    private var due = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1); set(Calendar.MINUTE, 0) }
    private var hasLoc = false
    private var lat = 0.0; private var lng = 0.0; private var radius = 150f; private var locLabel = ""
    private var onArrival = true
    private var flagged = false

    private lateinit var timeValue: TextView
    private lateinit var timeBox: LinearLayout
    private lateinit var locBox: LinearLayout
    private lateinit var locStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ReminderStore(this)
        requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.POST_NOTIFICATIONS), 1)

        intent.getStringExtra("id")?.let { id -> existing = store.byId(id) }
        existing?.let { r ->
            listId = r.listId
            hasTime = r.hasTime; if (r.dueMillis > 0) due.timeInMillis = r.dueMillis
            hasLoc = r.hasLocation; lat = r.locLat; lng = r.locLng; radius = r.locRadius
            locLabel = r.locLabel; onArrival = r.onArrival; flagged = r.flagged
        } ?: run { listId = intent.getStringExtra("listId") ?: "default" }

        setContentView(build())
    }

    private fun build(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK)
            setPadding(dp(16f), dp(48f), dp(16f), dp(40f))
        }
        col.addView(TextView(this).apply {
            text = if (existing == null) "Новое напоминание" else "Изменить"
            setTextColor(Color.WHITE); textSize = 26f; typeface = Fonts.bold; setPadding(0, 0, 0, dp(12f))
        })

        titleF = field("Название", existing?.title ?: "")
        notesF = field("Заметки", existing?.notes ?: "")
        col.addView(card(titleF)); col.addView(card(notesF))

        // time trigger
        col.addView(switchRow("В назначенное время", hasTime) { on -> hasTime = on; timeBox.visibility = vis(on) })
        timeValue = TextView(this).apply { setTextColor(0xFF0A84FF.toInt()); textSize = 16f; typeface = Fonts.medium; updateTimeText() }
        timeBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14f), dp(10f), dp(14f), dp(10f)); visibility = vis(hasTime)
            background = GradientDrawable().apply { cornerRadius = dp(12f).toFloat(); setColor(0xFF1C1C1E.toInt()) }
            addView(TextView(this@ReminderEditActivity).apply { text = "Когда"; setTextColor(Color.WHITE); textSize = 16f; typeface = Fonts.regular },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(timeValue)
            isClickable = true; setOnClickListener { pickDateTime() }
        }
        col.addView(wrap(timeBox))

        // location trigger
        col.addView(switchRow("По месту", hasLoc) { on -> hasLoc = on; locBox.visibility = vis(on) })
        locBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; visibility = vis(hasLoc)
            background = GradientDrawable().apply { cornerRadius = dp(12f).toFloat(); setColor(0xFF1C1C1E.toInt()) }
            setPadding(dp(14f), dp(10f), dp(14f), dp(10f))
        }
        locStatus = TextView(this).apply {
            text = if (hasLoc && (lat != 0.0 || lng != 0.0)) "Место: ${locLabel.ifEmpty { "задано" }}" else "Место не выбрано"
            setTextColor(0xFF8E8E93.toInt()); textSize = 14f; typeface = Fonts.regular
        }
        locBox.addView(locStatus)
        locBox.addView(linkBtn("Использовать текущее место") { captureCurrentLocation() })
        // arrival / leave segmented
        val seg = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8f), 0, dp(8f)) }
        val arrive = segPill("Когда приду", onArrival) { onArrival = true; refreshSeg() }
        val leave = segPill("Когда уйду", !onArrival) { onArrival = false; refreshSeg() }
        segArrive = arrive; segLeave = leave
        seg.addView(arrive, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also { it.marginEnd = dp(6f) })
        seg.addView(leave, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        locBox.addView(seg)
        // radius
        locBox.addView(TextView(this).apply { text = "Радиус"; setTextColor(0xFF8E8E93.toInt()); textSize = 13f })
        locBox.addView(SeekBar(this).apply {
            max = 100; progress = ((radius - 50) / 9.5f).toInt().coerceIn(0, 100)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, f: Boolean) { radius = 50f + p * 9.5f }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        })
        col.addView(wrap(locBox))

        col.addView(switchRow("Флажок", flagged) { on -> flagged = on })

        col.addView(Button(this).apply {
            text = "Сохранить"; setTextColor(Color.WHITE); typeface = Fonts.medium; isAllCaps = false
            background = GradientDrawable().apply { cornerRadius = dp(12f).toFloat(); setColor(0xFFFF9F0A.toInt()) }
            setOnClickListener { save() }
        }, lp(dp(52f)).also { it.topMargin = dp(20f) })

        if (existing != null) col.addView(Button(this).apply {
            text = "Удалить"; setTextColor(0xFFFF453A.toInt()); typeface = Fonts.medium; isAllCaps = false
            background = GradientDrawable().apply { cornerRadius = dp(12f).toFloat(); setColor(0xFF1C1C1E.toInt()) }
            setOnClickListener {
                existing?.let { ReminderScheduler.cancel(this@ReminderEditActivity, it); store.delete(it.id) }; finish()
            }
        }, lp(dp(52f)).also { it.topMargin = dp(10f) })

        return ScrollView(this).apply { addView(col) }
    }

    private lateinit var segArrive: TextView
    private lateinit var segLeave: TextView
    private fun refreshSeg() {
        segArrive.background = pill(onArrival); segArrive.setTextColor(if (onArrival) Color.WHITE else 0xFF8E8E93.toInt())
        segLeave.background = pill(!onArrival); segLeave.setTextColor(if (!onArrival) Color.WHITE else 0xFF8E8E93.toInt())
    }
    private fun pill(sel: Boolean) = GradientDrawable().apply { cornerRadius = dp(9f).toFloat(); setColor(if (sel) 0xFF0A84FF.toInt() else 0xFF2C2C2E.toInt()) }
    private fun segPill(label: String, sel: Boolean, onTap: () -> Unit) = TextView(this).apply {
        text = label; gravity = Gravity.CENTER; textSize = 14f; typeface = Fonts.medium
        setPadding(0, dp(9f), 0, dp(9f)); background = pill(sel)
        setTextColor(if (sel) Color.WHITE else 0xFF8E8E93.toInt()); isClickable = true; setOnClickListener { onTap() }
    }

    private fun captureCurrentLocation() {
        val lm = getSystemService(LocationManager::class.java)
        val loc = runCatching {
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        }.getOrNull()
        if (loc == null) { Toast.makeText(this, "Местоположение пока недоступно", Toast.LENGTH_SHORT).show(); return }
        lat = loc.latitude; lng = loc.longitude; locLabel = "Текущее место"
        locStatus.text = "Место: текущее (${"%.4f".format(lat)}, ${"%.4f".format(lng)})"
    }

    private fun pickDateTime() {
        DatePickerDialog(this, { _, y, m, day ->
            due.set(Calendar.YEAR, y); due.set(Calendar.MONTH, m); due.set(Calendar.DAY_OF_MONTH, day)
            TimePickerDialog(this, { _, h, min ->
                due.set(Calendar.HOUR_OF_DAY, h); due.set(Calendar.MINUTE, min); due.set(Calendar.SECOND, 0)
                updateTimeText()
            }, due.get(Calendar.HOUR_OF_DAY), due.get(Calendar.MINUTE), true).show()
        }, due.get(Calendar.YEAR), due.get(Calendar.MONTH), due.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun updateTimeText() {
        timeValue.text = java.text.SimpleDateFormat("d MMM, HH:mm", java.util.Locale("ru")).format(due.time)
    }

    private fun save() {
        if (titleF.text.toString().isBlank()) { Toast.makeText(this, "Введите название", Toast.LENGTH_SHORT).show(); return }
        val r = Reminder(
            id = existing?.id ?: "rem_${System.currentTimeMillis()}",
            listId = listId, title = titleF.text.toString().trim(), notes = notesF.text.toString().trim(),
            hasTime = hasTime, dueMillis = if (hasTime) due.timeInMillis else 0L,
            hasLocation = hasLoc && (lat != 0.0 || lng != 0.0),
            locLat = lat, locLng = lng, locRadius = radius, locLabel = locLabel, onArrival = onArrival,
            flagged = flagged, completed = existing?.completed ?: false,
            createdAt = existing?.createdAt ?: System.currentTimeMillis())
        store.upsert(r); ReminderScheduler.schedule(this, r)
        finish()
    }

    // builders
    private fun field(hint: String, value: String) = EditText(this).apply {
        setText(value); setHint(hint); setHintTextColor(0xFF8E8E93.toInt()); setTextColor(Color.WHITE)
        textSize = 17f; typeface = Fonts.regular; inputType = InputType.TYPE_CLASS_TEXT
    }
    private fun card(inner: View) = LinearLayout(this).apply {
        background = GradientDrawable().apply { cornerRadius = dp(12f).toFloat(); setColor(0xFF1C1C1E.toInt()) }
        setPadding(dp(14f), dp(8f), dp(14f), dp(8f))
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); lp.topMargin = dp(6f); layoutParams = lp
        addView(inner, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }
    private fun wrap(v: View) = LinearLayout(this).apply { setPadding(0, dp(6f), 0, 0); addView(v,
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)) }
    private fun switchRow(label: String, initial: Boolean, onChange: (Boolean) -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(4f), dp(12f), dp(4f), dp(2f))
        addView(TextView(this@ReminderEditActivity).apply { text = label; setTextColor(Color.WHITE); textSize = 16f; typeface = Fonts.regular },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(Switch(this@ReminderEditActivity).apply { isChecked = initial; setOnCheckedChangeListener { _, v -> onChange(v) } })
    }
    private fun linkBtn(label: String, onTap: () -> Unit) = TextView(this).apply {
        text = label; setTextColor(0xFF0A84FF.toInt()); textSize = 15f; typeface = Fonts.medium
        setPadding(0, dp(8f), 0, dp(4f)); isClickable = true; setOnClickListener { onTap() }
    }
    private fun vis(b: Boolean) = if (b) View.VISIBLE else View.GONE
    private fun lp(h: Int) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h)
}
