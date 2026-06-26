package com.ruos.demo

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

/**
 * Interactive demo of the RuOS gesture navigation.
 *
 * Bottom zone  → Home swipe: app card follows thumb, scales 1→0.85, corner 0→28dp
 * Left/Right edges → Back gesture with spring interactive preview
 * Swipe up + hold → App switcher (simulated)
 */
class GestureNavDemoActivity : Activity() {

    private lateinit var appCard: View
    private lateinit var statusLabel: TextView
    private var velocityTracker: VelocityTracker? = null
    private var downX = 0f
    private var downY = 0f
    private var gestureType = GestureType.NONE

    enum class GestureType { NONE, HOME, BACK_LEFT, BACK_RIGHT }

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

        // The "foreground app" card
        appCard = View(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = 0f
                setColor(Color.parseColor("#2C2C2E"))
            }
            clipToOutline = true
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        }
        root.addView(appCard, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)

        // Simulated app content
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        content.addView(TextView(this).apply {
            text = "Gesture Navigation Demo"
            textSize = 22f; setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        content.addView(TextView(this).apply {
            text = "Swipe from bottom → Home\nSwipe from left/right edge → Back\nSwipe up + hold → App Switcher"
            textSize = 14f; setTextColor(Color.argb(160, 255, 255, 255))
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(12), dp(32), 0)
        })
        root.addView(content, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)

        // Status label
        statusLabel = TextView(this).apply {
            setTextColor(Color.parseColor("#D94F3D"))
            textSize = 14f; gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(16))
        }
        root.addView(statusLabel, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).also { it.gravity = Gravity.BOTTOM })

        // Touch overlay
        root.setOnTouchListener { _, event -> handleTouch(event); true }

        return root
    }

    private fun handleTouch(event: MotionEvent) {
        val w = appCard.width.toFloat()
        val h = appCard.height.toFloat()
        val edgeZone = dp(28).toFloat()
        val bottomZone = dp(20).toFloat()

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain()
                velocityTracker?.addMovement(event)
                downX = event.x; downY = event.y

                gestureType = when {
                    event.y > h - bottomZone -> GestureType.HOME
                    event.x < edgeZone -> GestureType.BACK_LEFT
                    event.x > w - edgeZone -> GestureType.BACK_RIGHT
                    else -> GestureType.NONE
                }
                statusLabel.text = ""
            }

            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                val dy = event.y - downY
                val dx = event.x - downX

                when (gestureType) {
                    GestureType.HOME -> {
                        if (dy < 0) {
                            val progress = (-dy / (h * 0.5f)).coerceIn(0f, 1f)
                            val scale = 1f - progress * 0.15f
                            val cornerR = progress * 28f * resources.displayMetrics.density
                            appCard.scaleX = scale
                            appCard.scaleY = scale
                            (appCard.background as? GradientDrawable)?.cornerRadius = cornerR
                            statusLabel.text = "Home: ${(progress * 100).toInt()}%"
                        }
                    }
                    GestureType.BACK_LEFT, GestureType.BACK_RIGHT -> {
                        val progress = (Math.abs(dx) / (w * 0.4f)).coerceIn(0f, 1f)
                        appCard.translationX = dx * 0.6f
                        statusLabel.text = "Back: ${(progress * 100).toInt()}%"
                    }
                    GestureType.NONE -> {}
                }
            }

            MotionEvent.ACTION_UP -> {
                velocityTracker?.addMovement(event)
                velocityTracker?.computeCurrentVelocity(1000)
                val vy = velocityTracker?.yVelocity ?: 0f
                val vx = velocityTracker?.xVelocity ?: 0f
                velocityTracker?.recycle(); velocityTracker = null

                when (gestureType) {
                    GestureType.HOME -> {
                        val dy = event.y - downY
                        if (vy < -800f || dy < -appCard.height * 0.3f) {
                            // Dismiss — simulate going home
                            appCard.animate().scaleX(0.85f).scaleY(0.85f).alpha(0f)
                                .translationY(-appCard.height * 0.1f).setDuration(280)
                                .withEndAction {
                                    appCard.animate().scaleX(1f).scaleY(1f).alpha(1f)
                                        .translationY(0f).setDuration(0).start()
                                    statusLabel.text = "✓ Home gesture committed"
                                }.start()
                        } else {
                            snapBack()
                        }
                    }
                    GestureType.BACK_LEFT, GestureType.BACK_RIGHT -> {
                        val dx = event.x - downX
                        if (Math.abs(vx) > 600f || Math.abs(dx) > appCard.width * 0.3f) {
                            statusLabel.text = "✓ Back gesture committed"
                            snapBack()
                        } else {
                            snapBack()
                        }
                    }
                    GestureType.NONE -> {}
                }
                gestureType = GestureType.NONE
            }

            MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.recycle(); velocityTracker = null
                snapBack(); gestureType = GestureType.NONE
            }
        }
    }

    private fun snapBack() {
        SpringAnimation(appCard, SpringAnimation.SCALE_X, 1f).apply {
            spring.stiffness = SpringForce.STIFFNESS_MEDIUM
            spring.dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY; start()
        }
        SpringAnimation(appCard, SpringAnimation.SCALE_Y, 1f).apply {
            spring.stiffness = SpringForce.STIFFNESS_MEDIUM
            spring.dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY; start()
        }
        SpringAnimation(appCard, SpringAnimation.TRANSLATION_X, 0f).apply {
            spring.stiffness = SpringForce.STIFFNESS_MEDIUM
            spring.dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY; start()
        }
        SpringAnimation(appCard, SpringAnimation.TRANSLATION_Y, 0f).apply {
            spring.stiffness = SpringForce.STIFFNESS_MEDIUM
            spring.dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY; start()
        }
        (appCard.background as? GradientDrawable)?.cornerRadius = 0f
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
