package com.ruos.reminders.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.ruos.reminders.model.Reminder
import com.ruos.reminders.schedule.ReminderScheduler
import com.ruos.reminders.store.ReminderStore

/**
 * iOS-style Reminders home: a row of list chips (with open counts) and the selected
 * list's reminders below. Tap the circle to complete, tap the row to edit, «+» to add.
 */
class RemindersActivity : Activity() {

    private lateinit var store: ReminderStore
    private lateinit var chips: LinearLayout
    private lateinit var items: LinearLayout
    private var currentList = "default"
    private val d get() = resources.displayMetrics.density
    private fun dp(v: Float) = (v * d).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ReminderStore(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK) }
        root.addView(TextView(this).apply {
            text = "Напоминания"; setTextColor(Color.WHITE); textSize = 30f; typeface = Fonts.bold
            setPadding(dp(16f), dp(48f), dp(16f), dp(8f))
        })
        chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(12f), 0, dp(12f), dp(8f)) }
        root.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(chips) })

        items = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(items) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        root.addView(TextView(this).apply {
            text = "+  Новое напоминание"; setTextColor(0xFFFF9F0A.toInt()); textSize = 17f; typeface = Fonts.medium
            setPadding(dp(20f), dp(14f), dp(16f), dp(18f)); isClickable = true
            setOnClickListener {
                startActivity(Intent(this@RemindersActivity, ReminderEditActivity::class.java)
                    .putExtra("listId", currentList))
            }
        })
        setContentView(root)
    }

    override fun onResume() { super.onResume(); rebuildChips(); rebuildItems() }

    private fun rebuildChips() {
        chips.removeAllViews()
        store.lists().forEach { list ->
            val selected = list.id == currentList
            val chip = TextView(this).apply {
                text = "${list.name}  ${store.count(list.id)}"
                setTextColor(if (selected) Color.WHITE else 0xFF8E8E93.toInt())
                textSize = 14f; typeface = Fonts.medium; setPadding(dp(14f), dp(8f), dp(14f), dp(8f))
                background = GradientDrawable().apply {
                    cornerRadius = dp(16f).toFloat(); setColor(if (selected) list.color else 0xFF1C1C1E.toInt())
                }
                isClickable = true; setOnClickListener { currentList = list.id; rebuildChips(); rebuildItems() }
            }
            chips.addView(chip, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).also { it.marginEnd = dp(8f) })
        }
    }

    private fun rebuildItems() {
        items.removeAllViews()
        val list = store.forList(currentList)
        val accent = store.lists().firstOrNull { it.id == currentList }?.color ?: 0xFFFF9F0A.toInt()
        if (list.isEmpty()) items.addView(TextView(this).apply {
            text = "Нет напоминаний"; setTextColor(0xFF8E8E93.toInt()); textSize = 15f; typeface = Fonts.regular
            gravity = Gravity.CENTER; setPadding(0, dp(40f), 0, 0)
        })
        list.forEach { items.addView(rowFor(it, accent)) }
    }

    private fun rowFor(r: Reminder, accent: Int): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f), dp(10f), dp(16f), dp(10f))
        }
        row.addView(CircleCheck(this, r.completed, accent).apply {
            setOnClickListener {
                val updated = r.copy(completed = !r.completed)
                store.upsert(updated); ReminderScheduler.schedule(this@RemindersActivity, updated)
                rebuildItems(); rebuildChips()
            }
        }, LinearLayout.LayoutParams(dp(26f), dp(26f)).also { it.marginEnd = dp(12f) })

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; isClickable = true
            setOnClickListener {
                startActivity(Intent(this@RemindersActivity, ReminderEditActivity::class.java).putExtra("id", r.id))
            }
        }
        col.addView(TextView(this).apply {
            text = r.title.ifEmpty { "Без названия" }
            setTextColor(if (r.completed) 0xFF6C6C70.toInt() else Color.WHITE); textSize = 17f; typeface = Fonts.regular
            if (r.completed) paintFlags = paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
        })
        val sub = r.subtitle()
        if (sub.isNotEmpty()) col.addView(TextView(this).apply {
            text = (if (r.flagged) "Флажок · " else "") + sub
            setTextColor(if (r.flagged) 0xFFFF9F0A.toInt() else 0xFF8E8E93.toInt()); textSize = 13f; typeface = Fonts.regular
        })
        row.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }

    /** A tappable iOS completion circle (canvas-drawn). */
    private class CircleCheck(context: android.content.Context, val done: Boolean, val accent: Int) : View(context) {
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.5f * resources.displayMetrics.density }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 2.5f * resources.displayMetrics.density
            strokeCap = Paint.Cap.ROUND; color = Color.WHITE }
        init { isClickable = true }
        override fun onDraw(c: Canvas) {
            val r = width / 2f - ring.strokeWidth
            val cx = width / 2f; val cy = height / 2f
            if (done) {
                fill.color = accent; c.drawCircle(cx, cy, r, fill)
                c.drawLine(cx - r*0.4f, cy, cx - r*0.05f, cy + r*0.4f, tick)
                c.drawLine(cx - r*0.05f, cy + r*0.4f, cx + r*0.45f, cy - r*0.35f, tick)
            } else {
                ring.color = 0xFF5A5A5E.toInt(); c.drawCircle(cx, cy, r, ring)
            }
        }
    }
}
