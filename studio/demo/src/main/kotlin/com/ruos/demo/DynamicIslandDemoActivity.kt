package com.ruos.demo

import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

class DynamicIslandDemoActivity : Activity() {

    private lateinit var island: DemoIslandView
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setDecorFitsSystemWindows(false)
        window.insetsController?.apply {
            hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContentView(buildUI())

        // Auto-demo: cycle through states
        scheduleDemo()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    private fun buildUI(): View {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#1C1C1E"))
        }

        island = DemoIslandView(this)
        root.addView(island, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            dp(120)
        ).also { it.gravity = Gravity.TOP })

        // Content area — simulated app background
        val appBg = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(140), dp(24), dp(24))
        }
        appBg.addView(TextView(this).apply {
            text = "Dynamic Island"
            textSize = 28f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        appBg.addView(TextView(this).apply {
            text = "Tap the island or use the buttons below"
            textSize = 14f
            setTextColor(Color.argb(140, 255, 255, 255))
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(40))
        })

        // Control buttons
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        listOf("Music" to IslandState.MUSIC, "Call" to IslandState.CALL, "Timer" to IslandState.TIMER)
            .forEach { (label, state) ->
                btnRow.addView(demoButton(label) { island.setState(state) })
            }
        btnRow.addView(demoButton("Pill") { island.setState(IslandState.PILL) })

        appBg.addView(btnRow, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        root.addView(appBg, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)

        // Back button
        root.addView(backButton(), FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).also { it.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; it.bottomMargin = dp(40) })

        return root
    }

    private fun scheduleDemo() {
        val states = listOf(IslandState.PILL, IslandState.MUSIC, IslandState.CALL, IslandState.TIMER, IslandState.PILL)
        var i = 0
        fun next() {
            island.setState(states[i % states.size])
            i++
            handler.postDelayed({ next() }, 2200)
        }
        handler.postDelayed({ next() }, 1000)
    }

    private fun demoButton(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.parseColor("#D94F3D"))
        setPadding(dp(16), dp(10), dp(16), dp(10))
        setOnClickListener { onClick() }
        val m = (LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )).also { it.setMargins(dp(6), 0, dp(6), 0) }
        layoutParams = m
    }

    private fun backButton() = Button(this).apply {
        text = "← Back"
        setTextColor(Color.argb(160, 255, 255, 255))
        setBackgroundColor(Color.TRANSPARENT)
        setOnClickListener { finish() }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

// ─── Island view ──────────────────────────────────────────────────────────────

enum class IslandState { PILL, MUSIC, CALL, TIMER }

class DemoIslandView(context: android.content.Context) : View(context) {

    private val density = resources.displayMetrics.density

    private var animW = 130f * density
    private var animH = 36f * density
    private var animCorner = 18f * density

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 13f * density
        textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()
    private var labelText = ""

    private var state = IslandState.PILL

    init {
        isClickable = true
        setOnClickListener {
            if (state == IslandState.PILL) setState(IslandState.MUSIC)
            else setState(IslandState.PILL)
        }
    }

    fun setState(newState: IslandState) {
        state = newState
        val (tw, th, tc, label) = when (newState) {
            IslandState.PILL  -> Dims(130f, 36f, 18f, "")
            IslandState.MUSIC -> Dims(resources.displayMetrics.widthPixels / density * 0.82f, 88f, 22f, "♪  Zemfira · Прогулка  ›")
            IslandState.CALL  -> Dims(resources.displayMetrics.widthPixels / density * 0.70f, 52f, 26f, "📞  Входящий звонок")
            IslandState.TIMER -> Dims(180f, 44f, 22f, "⏱  4:32")
        }
        labelText = label
        animateTo(tw * density, th * density, tc * density)
    }

    private fun animateTo(tw: Float, th: Float, tc: Float) {
        springTo(this, "animW", tw) { animW = it; invalidate() }
        springTo(this, "animH", th) { animH = it; invalidate() }
        springTo(this, "animCorner", tc) { animCorner = it; invalidate() }
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = 60f * density    // top margin from screen top
        rect.set(cx - animW / 2f, cy - animH / 2f, cx + animW / 2f, cy + animH / 2f)
        canvas.drawRoundRect(rect, animCorner, animCorner, bgPaint)
        if (labelText.isNotEmpty()) {
            canvas.drawText(labelText, cx, cy + textPaint.textSize / 3f, textPaint)
        }
    }

    private data class Dims(val w: Float, val h: Float, val c: Float, val label: String)
}

private fun springTo(target: Any, name: String, finalValue: Float, update: (Float) -> Unit) {
    val prop = object : FloatPropertyCompat<Any>(name) {
        private var v = finalValue  // dummy — we use the lambda
        override fun getValue(obj: Any) = v
        override fun setValue(obj: Any, value: Float) { v = value; update(value) }
    }
    SpringAnimation(target, prop, finalValue).apply {
        spring.stiffness = 320f
        spring.dampingRatio = 0.72f
        start()
    }
}
