package com.ruos.alarm.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.ruos.alarm.alarm.AlarmScheduler
import com.ruos.alarm.model.Alarm
import com.ruos.alarm.model.AlarmStore
import com.ruos.alarm.util.Constants
import com.ruos.alarm.util.Haptics
import com.ruos.alarm.util.UpcomingNotifier

/**
 * The alarm list — the app's home. Large thin time, label/repeat beneath, a toggle
 * on the right. Edit mode reveals red delete circles on the left. Add button is a
 * round "+" pinned bottom-centre, iOS-style.
 */
class AlarmListActivity : Activity() {

    private lateinit var store: AlarmStore
    private lateinit var scheduler: AlarmScheduler
    private lateinit var listColumn: LinearLayout
    private lateinit var editButton: TextView
    private var editMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = AlarmStore(this)
        scheduler = AlarmScheduler(this)
        requestNotificationPermission()
        setContentView(buildUi())
    }

    override fun onResume() {
        super.onResume()
        rebuildList()
        UpcomingNotifier(this).refresh()
    }

    private fun buildUi(): View {
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val root = FrameLayout(this).apply { setBackgroundColor(Color.parseColor(Ui.BG)) }

        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(content, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(48), dp(16), dp(8))
        }
        editButton = TextView(this).apply {
            text = "Изменить"; setTextColor(Color.parseColor(Ui.ACCENT)); textSize = 17f
            isClickable = true
            setOnClickListener { toggleEdit() }
        }
        header.addView(editButton)
        header.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        val bedtime = TextView(this).apply {
            text = "Сон"; setTextColor(Color.parseColor(Ui.ACCENT)); textSize = 17f
            isClickable = true
            setOnClickListener { startActivity(Intent(this@AlarmListActivity, BedtimeActivity::class.java)) }
        }
        header.addView(bedtime)
        content.addView(header)

        val title = TextView(this).apply {
            text = "Будильник"; setTextColor(Color.WHITE); textSize = 34f
            typeface = Typeface.create("sans-serif-bold", Typeface.NORMAL)
            setPadding(dp(16), 0, dp(16), dp(8))
        }
        content.addView(title)

        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        listColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(listColumn)
        content.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        // Add button (bottom centre)
        val add = ImageView(this).apply {
            setImageDrawable(Ui.plusIcon(this@AlarmListActivity, Color.WHITE))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL; setColor(Color.parseColor(Ui.ACCENT))
            }
            setPadding(dp(18), dp(18), dp(18), dp(18))
            isClickable = true
            setOnClickListener {
                Haptics.confirm(it)
                startActivity(Intent(this@AlarmListActivity, AlarmEditActivity::class.java))
            }
        }
        root.addView(add, FrameLayout.LayoutParams(dp(64), dp(64), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
            .also { it.bottomMargin = dp(28) })

        return root
    }

    private fun toggleEdit() {
        editMode = !editMode
        editButton.text = if (editMode) "Готово" else "Изменить"
        rebuildList()
    }

    private fun rebuildList() {
        listColumn.removeAllViews()
        val alarms = store.getAlarms()
        if (alarms.isEmpty()) {
            listColumn.addView(TextView(this).apply {
                text = "Нет будильников"; setTextColor(Color.parseColor(Ui.TEXT_DIM)); textSize = 16f
                gravity = Gravity.CENTER
                setPadding(0, (Ui.dp(this@AlarmListActivity, 60f)).toInt(), 0, 0)
            })
            return
        }
        alarms.forEach { listColumn.addView(buildRow(it)) }
    }

    private fun buildRow(alarm: Alarm): View {
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            isClickable = true
            setOnClickListener {
                startActivity(Intent(this@AlarmListActivity, AlarmEditActivity::class.java)
                    .putExtra(Constants.EXTRA_ALARM_ID, alarm.id))
            }
        }

        if (editMode) {
            row.addView(ImageView(this).apply {
                setImageDrawable(Ui.deleteCircle(this@AlarmListActivity))
                setOnClickListener {
                    Haptics.confirm(it)
                    scheduler.cancel(alarm); store.delete(alarm.id)
                    rebuildList(); UpcomingNotifier(this@AlarmListActivity).refresh()
                }
            }, LinearLayout.LayoutParams(dp(28), dp(28)).also { it.marginEnd = dp(14) })
        }

        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(this).apply {
            text = alarm.timeText()
            setTextColor(if (alarm.enabled) Color.WHITE else Color.parseColor(Ui.TEXT_DIM))
            textSize = 52f
            typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
        })
        val sub = buildString {
            if (alarm.label.isNotBlank()) append(alarm.label).append("  •  ")
            append(alarm.repeatSummary())
            if (alarm.skipNext) append("  •  Пропустить раз")
        }
        texts.addView(TextView(this).apply {
            text = sub
            setTextColor(Color.parseColor(Ui.TEXT_DIM)); textSize = 13f
        })
        row.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        if (!editMode) {
            row.addView(Switch(this).apply {
                isChecked = alarm.enabled
                setOnCheckedChangeListener { btn, checked ->
                    if (!btn.isPressed && checked == alarm.enabled) return@setOnCheckedChangeListener
                    alarm.enabled = checked
                    store.upsert(alarm)
                    if (checked) scheduler.schedule(alarm) else scheduler.cancel(alarm)
                    Haptics.tick(btn)
                    UpcomingNotifier(this@AlarmListActivity).refresh()
                    // Dim/undim the time without a full rebuild.
                    (texts.getChildAt(0) as TextView).setTextColor(
                        if (checked) Color.WHITE else Color.parseColor(Ui.TEXT_DIM))
                }
            })
        }

        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        wrap.addView(row)
        wrap.addView(View(this).apply {
            setBackgroundColor(Color.parseColor(Ui.SEP))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).also {
            it.marginStart = dp(16)
        })
        return wrap
    }

    private fun requestNotificationPermission() {
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }
}
