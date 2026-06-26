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
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.ruos.alarm.model.AlarmStore
import com.ruos.alarm.model.SleepSound
import com.ruos.alarm.sleep.SleepSoundService
import com.ruos.alarm.util.Haptics
import java.util.Calendar

/**
 * Bedtime / Sleep mode: set a sleep + wake schedule, choose a sleep sound, and start
 * it playing now (until wake time). When enabled, starting sleep also switches the
 * phone to alarms-only DND if that access is granted.
 */
class BedtimeActivity : Activity() {

    private lateinit var store: AlarmStore
    private lateinit var bedtime: AlarmStore.Bedtime
    private var preview: MediaPlayer? = null

    private lateinit var sleepH: WheelPicker
    private lateinit var sleepM: WheelPicker
    private lateinit var wakeH: WheelPicker
    private lateinit var wakeM: WheelPicker
    private lateinit var soundValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = AlarmStore(this)
        bedtime = store.getBedtime()
        setContentView(buildUi())
    }

    private fun buildUi(): View {
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(Ui.BG))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(44), dp(16), dp(8))
        }
        header.addView(TextView(this).apply {
            text = "Назад"; setTextColor(Color.parseColor(Ui.ACCENT)); textSize = 17f
            isClickable = true; setOnClickListener { saveAndFinish() }
        })
        header.addView(TextView(this).apply {
            text = "Режим сна"; setTextColor(Color.WHITE); textSize = 17f; gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(TextView(this).apply { text = "  "; textSize = 17f })
        root.addView(header)

        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(12), dp(24)) }
        scroll.addView(col)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        col.addView(switchRow("Включить режим сна", bedtime.enabled) { bedtime.enabled = it })

        col.addView(sectionLabel("Отбой"))
        val (sh, sm, sleepCard) = timeCard(bedtime.sleepHour, bedtime.sleepMinute)
        sleepH = sh; sleepM = sm; col.addView(sleepCard)

        col.addView(sectionLabel("Подъём"))
        val (wh, wm, wakeCard) = timeCard(bedtime.wakeHour, bedtime.wakeMinute)
        wakeH = wh; wakeM = wm; col.addView(wakeCard)

        soundValue = TextView(this).apply { text = SleepSound.byId(bedtime.sleepSoundId).ruName }
        col.addView(navRow("Звук для сна", soundValue) { pickSleepSound() })

        col.addView(actionButton("Включить звуки сна", Ui.ACCENT) {
            startSleep()
        })
        col.addView(actionButton("Остановить", Ui.CARD) {
            startService(Intent(this, SleepSoundService::class.java).setAction(SleepSoundService.ACTION_STOP))
        })

        return root
    }

    private fun sectionLabel(t: String) = TextView(this).apply {
        text = t; setTextColor(Color.parseColor(Ui.TEXT_DIM)); textSize = 13f
        setPadding((Ui.dp(this@BedtimeActivity, 8f)).toInt(),
            (Ui.dp(this@BedtimeActivity, 16f)).toInt(), 0, (Ui.dp(this@BedtimeActivity, 6f)).toInt())
    }

    private fun timeCard(h: Int, m: Int): Triple<WheelPicker, WheelPicker, View> {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = Ui.dp(this@BedtimeActivity, 14f); setColor(Color.parseColor(Ui.CARD))
            }
        }
        val hw = WheelPicker(this).apply { setItems((0..23).map { "%02d".format(it) }, h) }
        val mw = WheelPicker(this).apply { setItems((0..59).map { "%02d".format(it) }, m) }
        card.addView(hw, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(TextView(this).apply { text = ":"; setTextColor(Color.WHITE); textSize = 24f })
        card.addView(mw, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        return Triple(hw, mw, card)
    }

    private fun navRow(title: String, value: TextView, onClick: () -> Unit): View {
        val d = resources.displayMetrics.density
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply { cornerRadius = 14f * d; setColor(Color.parseColor(Ui.CARD)) }
            setPadding((16 * d).toInt(), (14 * d).toInt(), (16 * d).toInt(), (14 * d).toInt())
            isClickable = true; setOnClickListener { Haptics.select(it); onClick() }
        }
        row.addView(TextView(this).apply { text = title; setTextColor(Color.WHITE); textSize = 16f },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        value.apply { setTextColor(Color.parseColor(Ui.TEXT_DIM)); textSize = 16f }
        row.addView(value)
        return wrap(row)
    }

    private fun switchRow(title: String, initial: Boolean, onChange: (Boolean) -> Unit): View {
        val d = resources.displayMetrics.density
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply { cornerRadius = 14f * d; setColor(Color.parseColor(Ui.CARD)) }
            setPadding((16 * d).toInt(), (6 * d).toInt(), (16 * d).toInt(), (6 * d).toInt())
        }
        row.addView(TextView(this).apply { text = title; setTextColor(Color.WHITE); textSize = 16f },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(Switch(this).apply { isChecked = initial
            setOnCheckedChangeListener { b, c -> Haptics.tick(b); onChange(c) } })
        return wrap(row)
    }

    private fun actionButton(text: String, bgColor: String, onClick: (View) -> Unit): View {
        val d = resources.displayMetrics.density
        return TextView(this).apply {
            this.text = text; setTextColor(Color.WHITE); textSize = 17f; gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            background = GradientDrawable().apply { cornerRadius = 14f * d; setColor(Color.parseColor(bgColor)) }
            setPadding(0, (14 * d).toInt(), 0, (14 * d).toInt())
            isClickable = true
            setOnClickListener { Haptics.confirm(it); onClick(it) }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .also { it.topMargin = (12 * d).toInt() }
        }
    }

    private fun wrap(row: View): View {
        val d = resources.displayMetrics.density
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (6 * d).toInt(), 0, 0)
            addView(row)
        }
    }

    private fun pickSleepSound() {
        val sounds = SleepSound.values()
        val names = sounds.map { it.ruName }.toTypedArray()
        val checked = sounds.indexOf(SleepSound.byId(bedtime.sleepSoundId)).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("Звук для сна")
            .setSingleChoiceItems(names, checked) { _, which ->
                bedtime.sleepSoundId = sounds[which].id
                soundValue.text = sounds[which].ruName
                previewSleep(sounds[which])
            }
            .setOnDismissListener { stopPreview() }
            .setPositiveButton("Готово", null)
            .show()
    }

    private fun previewSleep(sound: SleepSound) {
        stopPreview()
        val raw = sound.rawName ?: return
        val resId = resources.getIdentifier(raw, "raw", packageName)
        if (resId == 0) return
        preview = MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            val afd = resources.openRawResourceFd(resId)
            setDataSource(afd.fileDescriptor, afd.startOffset, afd.length); afd.close()
            isLooping = true; setVolume(0.5f, 0.5f); prepare(); start()
        }
    }

    private fun stopPreview() { preview?.runCatching { stop(); release() }; preview = null }

    private fun startSleep() {
        captureWheels()
        val sound = SleepSound.byId(bedtime.sleepSoundId)
        if (sound == SleepSound.NONE) return
        startForegroundService(Intent(this, SleepSoundService::class.java).apply {
            putExtra("soundId", bedtime.sleepSoundId)
            putExtra("stopAt", nextWakeMillis())
        })
    }

    private fun nextWakeMillis(): Long {
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, bedtime.wakeHour); set(Calendar.MINUTE, bedtime.wakeMinute)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= System.currentTimeMillis()) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    private fun captureWheels() {
        bedtime.sleepHour = sleepH.selectedIndex; bedtime.sleepMinute = sleepM.selectedIndex
        bedtime.wakeHour = wakeH.selectedIndex; bedtime.wakeMinute = wakeM.selectedIndex
    }

    private fun saveAndFinish() {
        captureWheels(); store.saveBedtime(bedtime); finish()
    }

    override fun onPause() { super.onPause(); stopPreview() }
    override fun onBackPressed() { saveAndFinish() }
}
