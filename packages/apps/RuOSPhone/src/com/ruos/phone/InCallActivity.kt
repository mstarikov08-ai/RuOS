package com.ruos.phone

import android.app.Activity
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.telecom.TelecomManager
import android.view.*
import android.widget.*
import java.util.Locale
import java.util.concurrent.TimeUnit

class InCallActivity : Activity() {

    companion object {
        val BG = Color.parseColor("#000000")
        val SURFACE = Color.parseColor("#1C1C1E")
        val SURFACE2 = Color.parseColor("#2C2C2E")
        val RED = Color.parseColor("#D94F3D")
        val GREEN = Color.parseColor("#30D158")
        val BLUE = Color.parseColor("#0A84FF")
        val TEXT = Color.WHITE
        val TEXT_SEC = Color.parseColor("#8E8E93")
    }

    private lateinit var durationText: TextView
    private lateinit var statusText: TextView
    private var callStartTime = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            updateDuration()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        window.statusBarColor = BG
        window.navigationBarColor = BG

        val callerName = intent.getStringExtra("caller_name") ?: "Неизвестный"
        val callerNumber = intent.getStringExtra("caller_number") ?: ""
        callStartTime = System.currentTimeMillis()

        buildUI(callerName, callerNumber)
        handler.postDelayed(timerRunnable, 1000)
    }

    private fun buildUI(callerName: String, callerNumber: String) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(80), 0, dp(40))
        }
        setContentView(root)

        // Caller info section
        val callerSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f
            )
        }

        // Avatar
        val avatarSize = dp(100)
        val avatar = object : View(this) {
            override fun onDraw(canvas: Canvas) {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#2C2C2E")
                }
                val cx = width / 2f
                val cy = height / 2f
                canvas.drawCircle(cx, cy, cx, paint)

                paint.color = Color.WHITE
                paint.textSize = width * 0.4f
                paint.textAlign = Paint.Align.CENTER
                paint.typeface = Typeface.DEFAULT_BOLD
                val initial = callerName.firstOrNull()?.uppercase() ?: "?"
                val metrics = paint.fontMetrics
                canvas.drawText(initial, cx, cy - (metrics.ascent + metrics.descent) / 2, paint)
            }
        }
        avatar.layoutParams = LinearLayout.LayoutParams(avatarSize, avatarSize).also {
            it.bottomMargin = dp(24)
            it.gravity = Gravity.CENTER_HORIZONTAL
        }
        callerSection.addView(avatar)

        // Caller name
        callerSection.addView(TextView(this).apply {
            text = callerName
            textSize = 32f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(TEXT)
            gravity = Gravity.CENTER
        })

        // Number
        if (callerNumber.isNotEmpty()) {
            callerSection.addView(TextView(this).apply {
                text = callerNumber
                textSize = 16f
                setTextColor(TEXT_SEC)
                gravity = Gravity.CENTER
                setPadding(0, dp(4), 0, dp(8))
            })
        }

        // Status / Duration
        statusText = TextView(this).apply {
            text = "Соединение..."
            textSize = 16f
            setTextColor(TEXT_SEC)
            gravity = Gravity.CENTER
        }
        callerSection.addView(statusText)

        durationText = TextView(this).apply {
            text = ""
            textSize = 16f
            setTextColor(TEXT_SEC)
            gravity = Gravity.CENTER
        }
        callerSection.addView(durationText)

        root.addView(callerSection)

        // Action buttons grid
        val actionsSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(dp(24), 0, dp(24), dp(32))
        }

        val row1 = buildActionRow(listOf(
            Triple("mic", "Выкл. микр.", false),
            Triple("#", "Клавиатура", false),
            Triple("speaker", "Динамик", false)
        ))
        val row2 = buildActionRow(listOf(
            Triple("+", "Доб. вызов", false),
            Triple("FaceTime", "FaceTime", true),
            Triple("person", "Контакты", false)
        ))

        actionsSection.addView(row1)
        actionsSection.addView(row2)
        root.addView(actionsSection)

        // End call button
        val endCallBtn = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER
        }

        val endBtn = TextView(this).apply {
            text = "Завершить"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(RED)
            }
            layoutParams = FrameLayout.LayoutParams(dp(80), dp(80)).also {
                it.gravity = Gravity.CENTER
            }
            setOnClickListener { endCall() }
        }
        endCallBtn.addView(endBtn)
        root.addView(endCallBtn)
    }

    private fun buildActionRow(actions: List<Triple<String, String, Boolean>>): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(24))

            actions.forEach { (icon, label, disabled) ->
                val btn = buildActionButton(icon, label, disabled)
                addView(btn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
        }
    }

    private fun buildActionButton(icon: String, label: String, disabled: Boolean): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER

            val circle = FrameLayout(this@InCallActivity).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (disabled) Color.parseColor("#111111") else Color.parseColor("#2C2C2E"))
                }
                layoutParams = LinearLayout.LayoutParams(dp(72), dp(72)).also {
                    it.gravity = Gravity.CENTER_HORIZONTAL
                    it.bottomMargin = dp(8)
                }

                val iconColor = if (disabled) Color.parseColor("#555555") else TEXT
                val iconSize = dp(32)
                when (icon) {
                    "mic", "speaker", "person" -> {
                        val iv = ImageView(this@InCallActivity).apply {
                            setImageDrawable(inCallIconDrawable(icon, iconColor, iconSize))
                            layoutParams = FrameLayout.LayoutParams(iconSize, iconSize).also {
                                it.gravity = Gravity.CENTER
                            }
                        }
                        addView(iv)
                    }
                    else -> {
                        val iconView = TextView(this@InCallActivity).apply {
                            text = icon
                            textSize = if (icon.length > 2) 12f else 22f
                            setTextColor(iconColor)
                            gravity = Gravity.CENTER
                            layoutParams = FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT
                            )
                        }
                        addView(iconView)
                    }
                }
            }
            addView(circle)

            addView(TextView(this@InCallActivity).apply {
                text = label
                textSize = 12f
                setTextColor(if (disabled) Color.parseColor("#555555") else TEXT)
                gravity = Gravity.CENTER
            })

            if (!disabled) {
                setOnClickListener {
                    Toast.makeText(this@InCallActivity, label, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateDuration() {
        val elapsed = System.currentTimeMillis() - callStartTime
        val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsed)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(elapsed) % 60

        if (elapsed < 3000) {
            statusText.text = "Соединение..."
        } else {
            statusText.text = "Активный вызов"
            durationText.text = String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    private fun endCall() {
        handler.removeCallbacks(timerRunnable)
        try {
            val telecomManager = getSystemService(TELECOM_SERVICE) as? TelecomManager
            telecomManager?.endCall()
        } catch (e: Exception) {}
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(timerRunnable)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun inCallIconDrawable(type: String, tint: Int, sizePx: Int): android.graphics.drawable.Drawable =
        object : android.graphics.drawable.Drawable() {
            override fun draw(canvas: Canvas) {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = tint
                    style = Paint.Style.FILL
                }
                val b = bounds
                val cx = b.exactCenterX(); val cy = b.exactCenterY()
                val r = sizePx * 0.35f
                when (type) {
                    "mic" -> {
                        // Rectangle body (microphone capsule)
                        val capW = r * 0.55f; val capTop = cy - r * 0.9f; val capBot = cy + r * 0.1f
                        val capRect = android.graphics.RectF(cx - capW, capTop, cx + capW, capBot)
                        canvas.drawRoundRect(capRect, capW, capW, p)
                        // Arc base
                        p.style = Paint.Style.STROKE
                        p.strokeWidth = sizePx * 0.09f
                        canvas.drawArc(android.graphics.RectF(cx - r * 0.7f, cy - r * 0.3f, cx + r * 0.7f, cy + r * 0.7f), 0f, 180f, false, p)
                        // Stand line
                        canvas.drawLine(cx, cy + r * 0.7f, cx, cy + r * 1.0f, p)
                        canvas.drawLine(cx - r * 0.4f, cy + r * 1.0f, cx + r * 0.4f, cy + r * 1.0f, p)
                    }
                    "speaker" -> {
                        // Speaker body (trapezoid)
                        val path = Path()
                        path.moveTo(cx - r * 0.8f, cy - r * 0.35f)
                        path.lineTo(cx - r * 0.25f, cy - r * 0.35f)
                        path.lineTo(cx + r * 0.5f, cy - r * 0.85f)
                        path.lineTo(cx + r * 0.5f, cy + r * 0.85f)
                        path.lineTo(cx - r * 0.25f, cy + r * 0.35f)
                        path.lineTo(cx - r * 0.8f, cy + r * 0.35f)
                        path.close()
                        canvas.drawPath(path, p)
                        // Sound waves
                        p.style = Paint.Style.STROKE
                        p.strokeWidth = sizePx * 0.08f
                        canvas.drawArc(android.graphics.RectF(cx + r * 0.4f, cy - r * 0.45f, cx + r * 0.9f, cy + r * 0.45f), -45f, 90f, false, p)
                        canvas.drawArc(android.graphics.RectF(cx + r * 0.55f, cy - r * 0.7f, cx + r * 1.15f, cy + r * 0.7f), -45f, 90f, false, p)
                    }
                    "person" -> {
                        // Head
                        canvas.drawCircle(cx, cy - r * 0.45f, r * 0.42f, p)
                        // Body arc
                        val bodyRect = android.graphics.RectF(cx - r * 0.8f, cy + r * 0.05f, cx + r * 0.8f, cy + r * 1.1f)
                        canvas.drawArc(bodyRect, 0f, 180f, true, p)
                    }
                    else -> canvas.drawCircle(cx, cy, r, p)
                }
            }
            override fun setAlpha(a: Int) {}
            override fun setColorFilter(cf: ColorFilter?) {}
            @Suppress("OVERRIDE_DEPRECATION")
            override fun getOpacity() = PixelFormat.TRANSLUCENT
        }
}
