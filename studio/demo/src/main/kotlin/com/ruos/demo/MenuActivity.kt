package com.ruos.demo

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Entry point for the RuOS component demo.
 * Lists all system components — tap any row to preview it full-screen.
 */
class MenuActivity : Activity() {

    private val components = listOf(
        Component("Dynamic Island",
            "Pill expands for music, calls, timers. Spring physics on expand/collapse.",
            DynamicIslandDemoActivity::class.java),
        Component("Control Centre",
            "Swipe down from top-right. Frosted glass, WiFi/BT grid, brightness, volume.",
            ControlCenterDemoActivity::class.java),
        Component("Notification Centre",
            "Swipe down from top-left. Grouped cards, swipe-left to dismiss.",
            NotificationCenterDemoActivity::class.java),
        Component("Lock Screen",
            "Golos Thin 80sp clock, parallax wallpaper, swipe-up unlock.",
            LockScreenDemoActivity::class.java),
        Component("Gesture Navigation",
            "Home swipe, back edge swipe, app switcher hold. 1:1 velocity matching.",
            GestureNavDemoActivity::class.java),
        Component("Spring Physics",
            "Interactive spring playground. Drag the ball — feel the physics.",
            SpringPhysicsDemoActivity::class.java),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setDecorFitsSystemWindows(false)
        window.insetsController?.apply {
            hide(WindowInsets.Type.navigationBars())
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContentView(buildUI())
    }

    private fun buildUI(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1C1C1E"))
        }

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(72), dp(24), dp(20))
        }
        header.addView(TextView(this).apply {
            text = "RuOS"
            textSize = 42f
            setTextColor(Color.parseColor("#D94F3D"))
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        header.addView(TextView(this).apply {
            text = "Component Preview"
            textSize = 17f
            setTextColor(Color.argb(160, 255, 255, 255))
        })
        root.addView(header)

        // Divider
        root.addView(View(this).apply {
            setBackgroundColor(Color.argb(40, 255, 255, 255))
        }, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))

        // Scroll list
        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(48))
        }

        components.forEach { comp ->
            list.addView(buildRow(comp))
            list.addView(View(this).apply {
                setBackgroundColor(Color.argb(30, 255, 255, 255))
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
                    .also { (it as LinearLayout.LayoutParams).setMargins(dp(16), 0, dp(16), 0) }
            })
        }

        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams.MATCH_PARENT, 0)
        (scroll.layoutParams as LinearLayout.LayoutParams).weight = 1f

        // Footer
        root.addView(TextView(this).apply {
            text = "RuOS 1.0 · android-14.0.0_r50 · #D94F3D"
            textSize = 11f
            setTextColor(Color.argb(80, 255, 255, 255))
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(28))
        }, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        return root
    }

    private fun buildRow(comp: Component): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            setPadding(dp(16), dp(18), dp(16), dp(18))
            setOnClickListener { startActivity(Intent(this@MenuActivity, comp.target)) }
            foreground = android.util.TypedValue().let { tv ->
                theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
                getDrawable(tv.resourceId)
            }
        }

        // Icon dot — red for first, dimmed for rest
        val dot = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#D94F3D"))
            }
        }
        row.addView(dot, dp(10), dp(10))
        row.addView(spacer(16))

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(TextView(this).apply {
            text = comp.title
            textSize = 17f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        col.addView(TextView(this).apply {
            text = comp.description
            textSize = 13f
            setTextColor(Color.argb(140, 255, 255, 255))
            setPadding(0, dp(2), 0, 0)
        })
        row.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        // Arrow
        row.addView(TextView(this).apply {
            text = "›"
            textSize = 24f
            setTextColor(Color.argb(80, 255, 255, 255))
        })

        return row
    }

    private fun spacer(dpV: Int) = View(this).also {
        it.layoutParams = ViewGroup.LayoutParams(dp(dpV), 1)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    data class Component(val title: String, val description: String, val target: Class<out Activity>)
}
