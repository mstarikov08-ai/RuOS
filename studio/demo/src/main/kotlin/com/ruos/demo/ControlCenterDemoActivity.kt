package com.ruos.demo

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Bundle
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

class ControlCenterDemoActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setDecorFitsSystemWindows(false)
        window.insetsController?.apply {
            hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#0A0A0A"))
        }

        // Faint "app" background
        root.addView(TextView(this).apply {
            text = "← Your app is here"
            setTextColor(Color.argb(60, 255, 255, 255))
            textSize = 14f
            gravity = Gravity.CENTER
        }, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)

        // Control centre panel
        val cc = ControlCenterPanel(this)
        root.addView(cc, FrameLayout.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.92f).toInt(),
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).also {
            it.gravity = Gravity.TOP or Gravity.END
            it.topMargin = dp(52)
            it.rightMargin = dp(12)
        })

        // Slide in
        cc.translationY = -400f
        SpringAnimation(cc, SpringAnimation.TRANSLATION_Y, 0f).apply {
            spring.stiffness = SpringForce.STIFFNESS_MEDIUM
            spring.dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
            startDelay = 100
            start()
        }

        // Back tap dismisses
        root.setOnClickListener { finish() }
        cc.setOnClickListener { }  // consume so root doesn't get it

        setContentView(root)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

class ControlCenterPanel(context: Context) : LinearLayout(context) {

    private val density = resources.displayMetrics.density
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 28, 28, 30)
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(40, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 0.5f * density
    }
    private val corner = 20f * density
    private val rect = RectF()

    init {
        orientation = VERTICAL
        setWillNotDraw(false)
        val p = dp(16)
        setPadding(p, p, p, p)

        // Connectivity 2×2
        addView(buildConnectivityGrid())
        addView(spacer(12))

        // Quick toggles
        addView(buildQuickToggles())
        addView(spacer(16))

        // Brightness
        addView(sliderRow("☀  Яркость"))
        addView(spacer(12))

        // Volume
        addView(sliderRow("🔊  Громкость"))
        addView(spacer(16))

        // Now playing card
        addView(buildNowPlaying())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        rect.set(0f, 0f, w.toFloat(), h.toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRoundRect(rect, corner, corner, bgPaint)
        canvas.drawRoundRect(rect, corner, corner, borderPaint)
    }

    private fun buildConnectivityGrid(): View {
        val grid = android.widget.GridLayout(context).apply {
            columnCount = 2; rowCount = 2
        }
        listOf("Wi-Fi ✓", "Bluetooth", "Сотовая", "Точка доступа").forEachIndexed { i, label ->
            val tile = ConnTile(context, label, i == 0 || i == 2)
            val spec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
            grid.addView(tile, android.widget.GridLayout.LayoutParams(spec, spec).apply {
                width = 0
                height = dp(72)
                setMargins(dp(4), dp(4), dp(4), dp(4))
            })
        }
        return grid
    }

    private fun buildQuickToggles(): View {
        val row = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER }
        listOf("🔇", "🔦", "⛔", "🔄").forEach { icon ->
            row.addView(QuickToggle(context, icon), LinearLayout.LayoutParams(0, dp(56), 1f).apply {
                setMargins(dp(4), 0, dp(4), 0)
            })
        }
        return row
    }

    private fun sliderRow(label: String): View {
        val col = LinearLayout(context).apply { orientation = VERTICAL }
        col.addView(TextView(context).apply {
            text = label; setTextColor(Color.WHITE); textSize = 13f
            setPadding(0, 0, 0, dp(6))
        })
        col.addView(SeekBar(context).apply {
            max = 100; progress = 70
            progressTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            thumbTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
        })
        return col
    }

    private fun buildNowPlaying(): View {
        val card = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.argb(80, 80, 80, 80))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            clipToOutline = true
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 14f * density
                setColor(Color.argb(80, 80, 80, 80))
            }
        }
        val albumBox = View(context).apply {
            setBackgroundColor(Color.parseColor("#D94F3D"))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 8f * density
                setColor(Color.parseColor("#D94F3D"))
            }
        }
        card.addView(albumBox, dp(40), dp(40))
        card.addView(View(context), dp(10), dp(1))
        val col = LinearLayout(context).apply { orientation = VERTICAL }
        col.addView(TextView(context).apply {
            text = "Прогулка"; setTextColor(Color.WHITE); textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        col.addView(TextView(context).apply {
            text = "Zemfira"; setTextColor(Color.argb(160, 255, 255, 255)); textSize = 12f
        })
        card.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(TextView(context).apply {
            text = "▶"; setTextColor(Color.WHITE); textSize = 20f
        })
        return card
    }

    private fun spacer(dpH: Int) = View(context).also {
        it.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(dpH))
    }

    private fun dp(v: Int) = (v * density).toInt()
}

private class ConnTile(context: Context, label: String, initialOn: Boolean) : FrameLayout(context) {
    private val density = context.resources.displayMetrics.density
    private var on = initialOn

    init {
        background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = 14f * density
            setColor(if (on) Color.WHITE else Color.argb(80, 80, 80, 80))
        }
        isClickable = true
        isFocusable = true
        setPadding(dp(12), dp(12), dp(12), dp(12))

        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.BOTTOM or android.view.Gravity.START
        }
        col.addView(TextView(context).apply {
            text = label
            textSize = 13f
            setTextColor(if (on) Color.BLACK else Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        addView(col, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

        setOnClickListener {
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            on = !on
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 14f * density
                setColor(if (on) Color.WHITE else Color.argb(80, 80, 80, 80))
            }
            (col.getChildAt(0) as TextView).setTextColor(if (on) Color.BLACK else Color.WHITE)
        }
    }

    private fun dp(v: Int) = (v * density).toInt()
}

private class QuickToggle(context: Context, icon: String) : FrameLayout(context) {
    private val density = context.resources.displayMetrics.density
    private var on = false

    init {
        background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = 14f * density
            setColor(Color.argb(80, 80, 80, 80))
        }
        isClickable = true
        addView(TextView(context).apply {
            text = icon; textSize = 22f
            gravity = android.view.Gravity.CENTER
        }, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        setOnClickListener {
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            on = !on
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 14f * density
                setColor(if (on) Color.WHITE else Color.argb(80, 80, 80, 80))
            }
        }
    }
}
