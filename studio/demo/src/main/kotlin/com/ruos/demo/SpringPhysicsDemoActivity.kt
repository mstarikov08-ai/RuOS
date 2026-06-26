package com.ruos.demo

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

/**
 * Interactive spring physics playground.
 * Drag the red ball anywhere — release and watch it spring back.
 * Adjust stiffness and damping with sliders to feel the difference.
 */
class SpringPhysicsDemoActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setDecorFitsSystemWindows(false)
        window.insetsController?.apply {
            hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContentView(buildUI())
    }

    private fun buildUI(): View {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#1C1C1E")) }

        val springView = SpringBallView(this)
        root.addView(springView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)

        // Sliders at bottom
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(200, 28, 28, 30))
            setPadding(dp(20), dp(16), dp(20), dp(40))
        }

        controls.addView(sliderRow("Жёсткость (Stiffness)", 50, 1500) { v ->
            springView.stiffness = v.toFloat()
        })
        controls.addView(sliderRow("Демпфирование (Damping)", 0, 100) { v ->
            springView.damping = v / 100f
        })

        controls.addView(TextView(this).apply {
            text = "← Back"
            setTextColor(Color.argb(140, 255, 255, 255))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
            isClickable = true
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        root.addView(controls, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).also { it.gravity = Gravity.BOTTOM })

        return root
    }

    private fun sliderRow(label: String, min: Int, max: Int, onChange: (Int) -> Unit): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(12))
        }
        col.addView(TextView(this).apply {
            text = label; textSize = 12f; setTextColor(Color.argb(160, 255, 255, 255))
        })
        col.addView(SeekBar(this).apply {
            this.min = min; this.max = max; progress = (min + max) / 2
            progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#D94F3D"))
            thumbTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#D94F3D"))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) { if (fromUser) onChange(p) }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        })
        return col
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

class SpringBallView(context: android.content.Context) : View(context) {

    var stiffness = SpringForce.STIFFNESS_MEDIUM
    var damping = SpringForce.DAMPING_RATIO_LOW_BOUNCY

    private val density = resources.displayMetrics.density
    private val ballRadius = 32f * density

    private var ballX = 0f
    private var ballY = 0f
    private var restX = 0f
    private var restY = 0f

    private val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#D94F3D") }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(60, 255, 255, 255); strokeWidth = 2f * density
    }
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(30, 217, 79, 61); strokeWidth = 4f * density; strokeCap = Paint.Cap.ROUND
    }

    private val trail = ArrayDeque<Pair<Float, Float>>(40)

    private var springX: SpringAnimation? = null
    private var springY: SpringAnimation? = null
    private var dragging = false

    private val xProp = object : FloatPropertyCompat<SpringBallView>("ballX") {
        override fun getValue(obj: SpringBallView) = obj.ballX
        override fun setValue(obj: SpringBallView, value: Float) { obj.ballX = value; obj.addTrail(); obj.invalidate() }
    }
    private val yProp = object : FloatPropertyCompat<SpringBallView>("ballY") {
        override fun getValue(obj: SpringBallView) = obj.ballY
        override fun setValue(obj: SpringBallView, value: Float) { obj.ballY = value; obj.invalidate() }
    }

    init {
        isClickable = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        restX = w / 2f; restY = h / 2f - 80f * density
        ballX = restX; ballY = restY
    }

    override fun onDraw(canvas: Canvas) {
        // Draw trail
        for (i in 1 until trail.size) {
            trailPaint.alpha = (i.toFloat() / trail.size * 50).toInt()
            canvas.drawLine(trail[i-1].first, trail[i-1].second, trail[i].first, trail[i].second, trailPaint)
        }

        // Spring line from rest to ball
        canvas.drawLine(restX, restY, ballX, ballY, linePaint)
        // Rest point (anchor)
        canvas.drawCircle(restX, restY, 6f * density, linePaint)
        // Ball
        canvas.drawCircle(ballX, ballY, ballRadius, ballPaint)

        // Label
        val dist = Math.hypot((ballX - restX).toDouble(), (ballY - restY).toDouble()).toInt()
        if (dist > 10) {
            val lp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE; textSize = 11f * density; textAlign = Paint.Align.CENTER
            }
            canvas.drawText("${dist}px", ballX, ballY + ballRadius + 18f * density, lp)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val dist = Math.hypot((event.x - ballX).toDouble(), (event.y - ballY).toDouble())
                if (dist < ballRadius * 2) {
                    dragging = true
                    springX?.cancel(); springY?.cancel()
                    trail.clear()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    ballX = event.x; ballY = event.y
                    addTrail(); invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    dragging = false
                    release()
                }
            }
        }
        return true
    }

    private fun release() {
        springX = SpringAnimation(this, xProp, restX).apply {
            spring.stiffness = stiffness
            spring.dampingRatio = damping
            start()
        }
        springY = SpringAnimation(this, yProp, restY).apply {
            spring.stiffness = stiffness
            spring.dampingRatio = damping
            start()
        }
    }

    private fun addTrail() {
        trail.addLast(Pair(ballX, ballY))
        while (trail.size > 35) trail.removeFirst()
    }
}
