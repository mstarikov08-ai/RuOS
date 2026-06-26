package com.ruos.focus.ui

import android.app.Activity
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.ruos.focus.model.FocusIcon
import com.ruos.focus.model.FocusMode
import com.ruos.focus.model.FocusSchedule
import com.ruos.focus.model.FocusStore
import com.ruos.focus.schedule.FocusController
import com.ruos.focus.schedule.FocusScheduler
import com.ruos.focus.util.Fonts

/**
 * Create / edit a Focus: name, glyph, colour, the people/apps that break through, the
 * lock-screen options, an optional schedule and auto-reply text. Built-ins can be edited
 * but not deleted or renamed off their identity.
 */
class FocusEditActivity : Activity() {

    private lateinit var store: FocusStore
    private lateinit var mode: FocusMode
    private var isNew = false

    private val d get() = resources.displayMetrics.density
    private fun dp(v: Float) = (v * d).toInt()

    private var pickedColor: Long = 0xFF5E5CE6
    private var pickedIcon: FocusIcon = FocusIcon.MOON
    private val allowedApps = linkedSetOf<String>()
    private var schedEnabled = false
    private var startMin = 22 * 60
    private var endMin = 7 * 60
    private var days = 0x7F

    private lateinit var nameField: EditText
    private lateinit var iconRow: LinearLayout
    private lateinit var colorRow: LinearLayout
    private lateinit var swCalls: Switch
    private lateinit var swSuppress: Switch
    private lateinit var swDim: Switch
    private lateinit var swHide: Switch
    private lateinit var replyField: EditText
    private lateinit var swSched: Switch
    private lateinit var startBtn: Button
    private lateinit var endBtn: Button
    private lateinit var dayRow: LinearLayout
    private lateinit var appsSummary: TextView

    private val PALETTE = longArrayOf(
        0xFF5E5CE6, 0xFF0A84FF, 0xFFAF52DE, 0xFFFF9F0A,
        0xFF30D158, 0xFFFF375F, 0xFFFF453A, 0xFF64D2FF, 0xFFFFD60A
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = FocusStore(this)
        val id = intent.getStringExtra("id")
        mode = id?.let { store.byId(it) } ?: run {
            isNew = true
            FocusMode(id = "custom_${System.currentTimeMillis()}", name = "Новый фокус",
                colorHex = 0xFF5E5CE6, icon = FocusIcon.STAR)
        }
        pickedColor = mode.colorHex; pickedIcon = mode.icon
        allowedApps.addAll(mode.allowedPackages)
        mode.schedule?.let { schedEnabled = it.enabled; startMin = it.startMin; endMin = it.endMin; days = it.days }

        buildUi()
        refreshAppsSummary()
    }

    override fun onResume() {
        super.onResume()
        // AppPickerActivity hands its selection back via a scratch prefs keyed by id.
        val scratch = getSharedPreferences(AppPickerActivity.SCRATCH, MODE_PRIVATE)
        scratch.getStringSet(mode.id, null)?.let {
            allowedApps.clear(); allowedApps.addAll(it)
            scratch.edit().remove(mode.id).apply()
        }
        refreshAppsSummary()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK)
        }
        root.addView(TextView(this).apply {
            text = if (isNew) "Новый фокус" else mode.name
            setTextColor(Color.WHITE); textSize = 28f; typeface = Fonts.bold
            setPadding(dp(16f), dp(48f), dp(16f), dp(12f))
        })

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16f), 0, dp(16f), dp(40f)) }
        root.addView(ScrollView(this).apply { addView(col) })
        setContentView(root)

        // ── name ──
        nameField = EditText(this).apply {
            setText(mode.name); setTextColor(Color.WHITE); typeface = Fonts.regular; textSize = 18f
            setHintTextColor(0xFF8E8E93.toInt()); inputType = InputType.TYPE_CLASS_TEXT
            isEnabled = !mode.builtIn
        }
        col.addView(label("Название")); col.addView(card(nameField))

        // ── icon ──
        col.addView(label("Значок"))
        iconRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        FocusIcon.values().forEach { ic -> iconRow.addView(iconChip(ic)) }
        col.addView(HorizontalScrollView(this).apply { addView(iconRow); isHorizontalScrollBarEnabled = false })

        // ── color ──
        col.addView(label("Цвет"))
        colorRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        PALETTE.forEach { c -> colorRow.addView(colorChip(c)) }
        col.addView(HorizontalScrollView(this).apply { addView(colorRow); isHorizontalScrollBarEnabled = false })

        // ── people & apps ──
        col.addView(label("Разрешённые приложения и люди"))
        appsSummary = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 16f; typeface = Fonts.regular
        }
        col.addView(card(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            setOnClickListener {
                startActivity(Intent(this@FocusEditActivity, AppPickerActivity::class.java).apply {
                    putStringArrayListExtra("selected", ArrayList(allowedApps))
                    putExtra("focus_id", mode.id)
                })
            }
            addView(appsSummary, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(this@FocusEditActivity).apply {
                text = "›"; setTextColor(0xFF8E8E93.toInt()); textSize = 22f
            })
        }))

        swCalls = toggle(col, "Разрешить звонки", mode.allowCalls)
        swSuppress = toggle(col, "Полная тишина (ничего не пропускать)", mode.suppressAll)
        swDim = toggle(col, "Приглушать экран блокировки", mode.dimLockScreen)
        swHide = toggle(col, "Скрывать беззвучные уведомления", mode.hideNotifications)

        // ── auto-reply ──
        col.addView(label("Автоответ"))
        replyField = EditText(this).apply {
            setText(mode.autoReply); hint = "Например: Я за рулём, отвечу позже"
            setTextColor(Color.WHITE); setHintTextColor(0xFF8E8E93.toInt())
            typeface = Fonts.regular; textSize = 16f; inputType = InputType.TYPE_CLASS_TEXT
        }
        col.addView(card(replyField))

        // ── schedule ──
        col.addView(label("Расписание"))
        swSched = toggle(col, "Включать по расписанию", schedEnabled)
        swSched.setOnCheckedChangeListener { _, v -> schedEnabled = v; scheduleVis(scheduleBox, v) }

        scheduleBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        startBtn = timeButton("С", startMin) { startMin = it; startBtn.text = "С  ${hhmm(startMin)}" }
        endBtn = timeButton("До", endMin) { endMin = it; endBtn.text = "До  ${hhmm(endMin)}" }
        scheduleBox.addView(card(startBtn)); scheduleBox.addView(card(endBtn))
        dayRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val names = arrayOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
        for (i in 0..6) dayRow.addView(dayChip(names[i], i))
        scheduleBox.addView(dayRow)
        col.addView(scheduleBox)
        scheduleVis(scheduleBox, schedEnabled)

        // ── actions ──
        col.addView(Button(this).apply {
            text = "Сохранить"; setTextColor(Color.WHITE); typeface = Fonts.medium
            setBackgroundColor(pickedColor.toInt()); tag = "save"
            setOnClickListener { save() }
        }.also { saveBtn = it }, lp(dp(52f)).also { it.topMargin = dp(24f) })

        if (!mode.builtIn && !isNew) {
            col.addView(Button(this).apply {
                text = "Удалить фокус"; setTextColor(0xFFFF453A.toInt()); typeface = Fonts.medium
                setBackgroundColor(0xFF1C1C1E.toInt())
                setOnClickListener { store.delete(mode.id); finish() }
            }, lp(dp(52f)).also { it.topMargin = dp(12f) })
        }
    }

    private lateinit var scheduleBox: LinearLayout
    private lateinit var saveBtn: Button

    private fun scheduleVis(v: View, on: Boolean) { v.visibility = if (on) View.VISIBLE else View.GONE }

    private fun save() {
        val updated = mode.copy(
            name = if (mode.builtIn) mode.name else nameField.text.toString().ifBlank { "Фокус" },
            colorHex = pickedColor, icon = pickedIcon,
            allowedPackages = LinkedHashSet(allowedApps),
            allowCalls = swCalls.isChecked, suppressAll = swSuppress.isChecked,
            dimLockScreen = swDim.isChecked, hideNotifications = swHide.isChecked,
            autoReply = replyField.text.toString(),
            schedule = FocusSchedule(schedEnabled, startMin, endMin, days)
        )
        store.upsert(updated)
        // if this focus is currently active, re-broadcast its new filter
        if (store.activeId() == updated.id) FocusController.activate(this, updated)
        FocusScheduler.evaluateAndReschedule(this)
        finish()
    }

    // ── small builders ─────────────────────────────────────────────────────────

    private fun label(t: String) = TextView(this).apply {
        text = t; setTextColor(0xFF8E8E93.toInt()); textSize = 13f; typeface = Fonts.medium
        setPadding(dp(4f), dp(18f), 0, dp(6f))
    }

    private fun card(inner: View) = LinearLayout(this).apply {
        setBackgroundColor(0xFF1C1C1E.toInt()); setPadding(dp(14f), dp(12f), dp(14f), dp(12f))
        addView(inner, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun lp(h: Int) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h)

    private fun toggle(parent: LinearLayout, text: String, initial: Boolean): Switch {
        val sw = Switch(this).apply { isChecked = initial }
        parent.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xFF1C1C1E.toInt()); setPadding(dp(14f), dp(10f), dp(14f), dp(10f))
            val lpRow = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lpRow.topMargin = dp(2f); layoutParams = lpRow
            addView(TextView(this@FocusEditActivity).apply {
                this.text = text; setTextColor(Color.WHITE); textSize = 16f; typeface = Fonts.regular
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(sw)
        })
        return sw
    }

    private fun timeButton(prefix: String, initial: Int, onSet: (Int) -> Unit): Button {
        return Button(this).apply {
            text = "$prefix  ${hhmm(initial)}"; setTextColor(Color.WHITE)
            typeface = Fonts.regular; setBackgroundColor(Color.TRANSPARENT); gravity = Gravity.START
            setOnClickListener {
                val cur = if (prefix == "С") startMin else endMin
                TimePickerDialog(this@FocusEditActivity, { _, h, m -> onSet(h * 60 + m) },
                    cur / 60, cur % 60, true).show()
            }
        }
    }

    private fun dayChip(name: String, index: Int): View {
        val tv = TextView(this)
        fun paint() {
            val on = (days shr index) and 1 == 1
            tv.setBackgroundColor(if (on) pickedColor.toInt() else 0xFF2C2C2E.toInt())
            tv.setTextColor(if (on) Color.WHITE else 0xFF8E8E93.toInt())
        }
        tv.apply {
            text = name; textSize = 13f; typeface = Fonts.medium; gravity = Gravity.CENTER
            setPadding(dp(2f), dp(8f), dp(2f), dp(8f)); paint()
            setOnClickListener { days = days xor (1 shl index); paint() }
        }
        return tv.also {
            val lpc = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            lpc.setMargins(dp(3f), dp(8f), dp(3f), 0); it.layoutParams = lpc
        }
    }

    private fun iconChip(ic: FocusIcon): View {
        val v = object : View(this) {
            override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(dp(52f), dp(52f))
            override fun onDraw(canvas: Canvas) {
                val sel = ic == pickedIcon
                val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (sel) pickedColor.toInt() else 0xFF2C2C2E.toInt() }
                canvas.drawCircle(width / 2f, height / 2f, dp(22f).toFloat(), bg)
                FocusGlyph.draw(canvas, ic, width / 2f, height / 2f, dp(26f).toFloat(),
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
            }
        }
        v.setOnClickListener { pickedIcon = ic; iconRow.children().forEach { it.invalidate() } }
        val lpc = LinearLayout.LayoutParams(dp(60f), dp(60f)); v.layoutParams = lpc
        return v
    }

    private fun colorChip(c: Long): View {
        val v = object : View(this) {
            override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(dp(46f), dp(46f))
            override fun onDraw(canvas: Canvas) {
                canvas.drawCircle(width / 2f, height / 2f, dp(17f).toFloat(),
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c.toInt() })
                if (c == pickedColor) {
                    canvas.drawCircle(width / 2f, height / 2f, dp(21f).toFloat(),
                        Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            style = Paint.Style.STROKE; strokeWidth = dp(2.5f).toFloat(); color = Color.WHITE
                        })
                }
            }
        }
        v.setOnClickListener {
            pickedColor = c; colorRow.children().forEach { it.invalidate() }
            iconRow.children().forEach { it.invalidate() }
            saveBtn.setBackgroundColor(c.toInt())
        }
        v.layoutParams = LinearLayout.LayoutParams(dp(50f), dp(50f))
        return v
    }

    private fun refreshAppsSummary() {
        appsSummary.text = when (allowedApps.size) {
            0 -> "Никого — только звонки (если включены)"
            1 -> "1 приложение"
            else -> "${allowedApps.size} приложений"
        }
    }

    private fun hhmm(m: Int) = "%02d:%02d".format(m / 60, m % 60)

    private fun LinearLayout.children(): List<View> = (0 until childCount).map { getChildAt(it) }
}
