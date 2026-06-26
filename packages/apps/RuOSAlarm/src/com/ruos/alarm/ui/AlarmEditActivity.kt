package com.ruos.alarm.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.ruos.alarm.alarm.AlarmScheduler
import com.ruos.alarm.model.Alarm
import com.ruos.alarm.model.AlarmSound
import com.ruos.alarm.model.AlarmStore
import com.ruos.alarm.model.VibrationPattern
import com.ruos.alarm.util.Constants
import com.ruos.alarm.util.Haptics
import com.ruos.alarm.util.UpcomingNotifier
import java.util.Calendar

/** Add/edit an alarm: iOS wheel time picker + per-alarm settings, all in Russian. */
class AlarmEditActivity : Activity() {

    private lateinit var store: AlarmStore
    private lateinit var alarm: Alarm
    private var isNew = false

    private lateinit var hourWheel: WheelPicker
    private lateinit var minuteWheel: WheelPicker
    private var preview: MediaPlayer? = null

    // Rows we update in place
    private lateinit var repeatValue: TextView
    private lateinit var soundValue: TextView
    private lateinit var labelValue: TextView
    private lateinit var vibrationValue: TextView
    private lateinit var dayChips: LinearLayout

    private val dayNames = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = AlarmStore(this)
        val id = intent.getLongExtra(Constants.EXTRA_ALARM_ID, -1L)
        val existing = if (id >= 0) store.get(id) else null
        if (existing != null) {
            alarm = existing
        } else {
            isNew = true
            val now = Calendar.getInstance()
            alarm = Alarm(store.newId(), now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))
        }
        setContentView(buildUi())
    }

    private fun buildUi(): View {
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(Ui.BG))
        }

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(44), dp(16), dp(8))
        }
        header.addView(TextView(this).apply {
            text = "Отмена"; setTextColor(Color.parseColor(Ui.ACCENT)); textSize = 17f
            isClickable = true; setOnClickListener { finish() }
        })
        header.addView(TextView(this).apply {
            text = if (isNew) "Добавить" else "Изменить"
            setTextColor(Color.WHITE); textSize = 17f; gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(TextView(this).apply {
            text = "Сохранить"; setTextColor(Color.parseColor(Ui.ACCENT)); textSize = 17f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            isClickable = true; setOnClickListener { save() }
        })
        root.addView(header)

        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(col)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        // ── Wheel time picker ──────────────────────────────────────────────────
        val wheels = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(16))
        }
        hourWheel = WheelPicker(this).apply {
            setItems((0..23).map { "%02d".format(it) }, alarm.hour)
        }
        minuteWheel = WheelPicker(this).apply {
            setItems((0..59).map { "%02d".format(it) }, alarm.minute)
        }
        wheels.addView(hourWheel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        wheels.addView(TextView(this).apply {
            text = ":"; setTextColor(Color.WHITE); textSize = 28f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        })
        wheels.addView(minuteWheel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        col.addView(wheels)

        // ── Repeat days ────────────────────────────────────────────────────────
        repeatValue = TextView(this)
        col.addView(navRow("Повтор", repeatValue) { /* toggles chips below */ toggleDayChips() })
        dayChips = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        rebuildDayChips()
        col.addView(dayChips)
        updateRepeatValue()

        // ── Label ──────────────────────────────────────────────────────────────
        labelValue = TextView(this).apply { text = alarm.label.ifBlank { "Будильник" } }
        col.addView(navRow("Этикетка", labelValue) { editLabel() })

        // ── Sound ────────────────────────────────────────────────────────────────
        soundValue = TextView(this).apply { text = alarm.sound.ruName }
        col.addView(navRow("Звук", soundValue) { pickSound() })

        // ── Vibration ────────────────────────────────────────────────────────────
        vibrationValue = TextView(this).apply { text = alarm.vibration.ruName }
        col.addView(navRow("Вибрация", vibrationValue) { pickVibration() })

        // ── Snooze ───────────────────────────────────────────────────────────────
        col.addView(switchRow("Повтор сигнала (9 мин)", alarm.snoozeEnabled) {
            alarm.snoozeEnabled = it
        })

        // ── Sunrise ──────────────────────────────────────────────────────────────
        col.addView(switchRow("Рассвет (экран светлеет за 5 мин)", alarm.sunrise) {
            alarm.sunrise = it
        })

        // ── Skip next (recurring only) ──────────────────────────────────────────
        if (!alarm.isOneShot) {
            col.addView(switchRow("Пропустить следующий", alarm.skipNext) {
                alarm.skipNext = it
            })
        }

        // ── Delete ───────────────────────────────────────────────────────────────
        if (!isNew) {
            col.addView(TextView(this).apply {
                text = "Удалить будильник"
                setTextColor(Color.parseColor(Ui.RED)); textSize = 17f
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(28), dp(16), dp(28))
                isClickable = true
                setOnClickListener {
                    Haptics.confirm(it)
                    AlarmScheduler(this@AlarmEditActivity).cancel(alarm)
                    store.delete(alarm.id)
                    UpcomingNotifier(this@AlarmEditActivity).refresh()
                    finish()
                }
            })
        }
        return root
    }

    // ── Rows ──────────────────────────────────────────────────────────────────

    private fun navRow(title: String, value: TextView, onClick: () -> Unit): View {
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply { setColor(Color.parseColor(Ui.CARD)) }
            setPadding(dp(16), dp(14), dp(16), dp(14))
            isClickable = true
            setOnClickListener { Haptics.select(it); onClick() }
        }
        row.addView(TextView(this).apply { text = title; setTextColor(Color.WHITE); textSize = 16f },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        value.apply { setTextColor(Color.parseColor(Ui.TEXT_DIM)); textSize = 16f }
        row.addView(value)
        return wrapWithSep(row)
    }

    private fun switchRow(title: String, initial: Boolean, onChange: (Boolean) -> Unit): View {
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply { setColor(Color.parseColor(Ui.CARD)) }
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        row.addView(TextView(this).apply { text = title; setTextColor(Color.WHITE); textSize = 16f },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(Switch(this).apply {
            isChecked = initial
            setOnCheckedChangeListener { btn, c -> Haptics.tick(btn); onChange(c) }
        })
        return wrapWithSep(row)
    }

    private fun wrapWithSep(row: View): View {
        val d = resources.displayMetrics.density
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        wrap.addView(row)
        wrap.addView(View(this).apply { setBackgroundColor(Color.parseColor(Ui.SEP)) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (d).toInt()).also {
                it.marginStart = (16 * d).toInt()
            })
        return wrap
    }

    // ── Repeat day chips ────────────────────────────────────────────────────────

    private fun toggleDayChips() {
        dayChips.visibility = if (dayChips.visibility == View.GONE) View.VISIBLE else View.GONE
    }

    private fun rebuildDayChips() {
        dayChips.removeAllViews()
        val d = resources.displayMetrics.density
        for (i in 0..6) {
            val on = alarm.repeatDays.contains(i)
            val chip = TextView(this).apply {
                text = dayNames[i]; textSize = 14f; gravity = Gravity.CENTER
                setTextColor(if (on) Color.WHITE else Color.parseColor(Ui.TEXT_DIM))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (on) Color.parseColor(Ui.ACCENT) else Color.parseColor(Ui.CARD))
                }
                isClickable = true
                setOnClickListener {
                    Haptics.select(it)
                    if (alarm.repeatDays.contains(i)) alarm.repeatDays.remove(i)
                    else alarm.repeatDays.add(i)
                    rebuildDayChips(); updateRepeatValue()
                }
            }
            dayChips.addView(chip, LinearLayout.LayoutParams((40 * d).toInt(), (40 * d).toInt()).also {
                it.marginStart = (4 * d).toInt(); it.marginEnd = (4 * d).toInt()
            })
        }
    }

    private fun updateRepeatValue() { repeatValue.text = alarm.repeatSummary() }

    // ── Dialogs ───────────────────────────────────────────────────────────────

    private fun editLabel() {
        val input = EditText(this).apply {
            setText(alarm.label); inputType = InputType.TYPE_CLASS_TEXT
            setTextColor(Color.WHITE); setHintTextColor(Color.parseColor(Ui.TEXT_DIM))
            hint = "Будильник"
        }
        AlertDialog.Builder(this)
            .setTitle("Этикетка")
            .setView(input)
            .setPositiveButton("ОК") { _, _ ->
                alarm.label = input.text.toString().trim()
                labelValue.text = alarm.label.ifBlank { "Будильник" }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun pickSound() {
        val sounds = AlarmSound.values()
        val names = sounds.map { it.ruName }.toTypedArray()
        val checked = sounds.indexOf(alarm.sound).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("Звук")
            .setSingleChoiceItems(names, checked) { _, which ->
                alarm.soundId = sounds[which].id
                soundValue.text = sounds[which].ruName
                previewSound(sounds[which])
            }
            .setOnDismissListener { stopPreview() }
            .setPositiveButton("Готово", null)
            .show()
    }

    private fun pickVibration() {
        val patterns = VibrationPattern.values()
        val names = patterns.map { it.ruName }.toTypedArray()
        val checked = patterns.indexOf(alarm.vibration).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("Вибрация")
            .setSingleChoiceItems(names, checked) { _, which ->
                alarm.vibrationId = patterns[which].id
                vibrationValue.text = patterns[which].ruName
                val v = getSystemService(android.os.Vibrator::class.java)
                if (patterns[which] != VibrationPattern.NONE)
                    v?.vibrate(android.os.VibrationEffect.createWaveform(patterns[which].timings, -1))
            }
            .setPositiveButton("Готово", null)
            .show()
    }

    // ── Sound preview ───────────────────────────────────────────────────────────

    private fun previewSound(sound: AlarmSound) {
        stopPreview()
        val resId = resources.getIdentifier(sound.rawName, "raw", packageName)
        if (resId == 0) return
        preview = MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            val afd = resources.openRawResourceFd(resId)
            setDataSource(afd.fileDescriptor, afd.startOffset, afd.length); afd.close()
            isLooping = true; setVolume(0.6f, 0.6f); prepare(); start()
        }
    }

    private fun stopPreview() {
        preview?.runCatching { stop(); release() }
        preview = null
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    private fun save() {
        alarm.hour = hourWheel.selectedIndex
        alarm.minute = minuteWheel.selectedIndex
        alarm.enabled = true
        store.upsert(alarm)
        AlarmScheduler(this).schedule(alarm)
        UpcomingNotifier(this).refresh()
        finish()
    }

    override fun onPause() { super.onPause(); stopPreview() }
    override fun onDestroy() { super.onDestroy(); stopPreview() }
}
