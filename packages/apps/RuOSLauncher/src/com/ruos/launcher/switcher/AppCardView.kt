package com.ruos.launcher.switcher

import android.content.Context
import android.graphics.Color
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.dynamicanimation.animation.FlingAnimation
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.ruos.launcher.R

/**
 * Single card in the app switcher.
 * - Rounded 20dp corners (R.dimen.card_corner_radius)
 * - Shows live thumbnail (from ActivityManager screenshot) + app label
 * - Swipe up with spring throw to dismiss
 * - Tap to switch
 */
class AppCardView(context: Context) : FrameLayout(context) {

    var onSwipeUp: ((Int) -> Unit)? = null
    var onTap: ((Int) -> Unit)? = null

    private var taskId = -1
    private val thumbnail = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        setBackgroundColor(Color.parseColor("#1C1C1E"))
    }
    private val appLabel = TextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = 12f
        gravity = android.view.Gravity.CENTER
        setShadowLayer(4f, 0f, 1f, Color.BLACK)
    }
    private val appIcon = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_INSIDE
    }

    private var velocityTracker: VelocityTracker? = null
    private var downY = 0f
    private var isDragging = false

    // Spring Y for dismiss fling
    private val translateYProp = object : FloatPropertyCompat<AppCardView>("translationY") {
        override fun getValue(obj: AppCardView) = obj.translationY
        override fun setValue(obj: AppCardView, value: Float) { obj.translationY = value }
    }
    private val dismissSpring = SpringAnimation(this, SpringAnimation.TRANSLATION_Y, 0f).apply {
        spring.stiffness = SpringForce.STIFFNESS_MEDIUM
        spring.dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
    }

    init {
        clipToOutline = true
        outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        background = context.getDrawable(R.drawable.bg_card)
        elevation = 8f

        addView(thumbnail, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

        val labelHeight = context.resources.getDimensionPixelSize(R.dimen.card_label_height)
        addView(appLabel, LayoutParams(LayoutParams.MATCH_PARENT, labelHeight).also {
            it.gravity = android.view.Gravity.BOTTOM
        })

        val iconSize = context.resources.getDimensionPixelSize(R.dimen.card_icon_size)
        val iconParams = LayoutParams(iconSize, iconSize).also {
            it.gravity = android.view.Gravity.TOP or android.view.Gravity.START
            val m = context.resources.getDimensionPixelSize(R.dimen.card_icon_margin)
            it.setMargins(m, m, 0, 0)
        }
        addView(appIcon, iconParams)
    }

    fun setApp(label: String, icon: Drawable, taskId: Int) {
        this.taskId = taskId
        appLabel.text = label
        appIcon.setImageDrawable(icon)
    }

    fun animateIn(delayMs: Long) {
        alpha = 0f
        translationY = 80f
        postDelayed({
            animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(320)
                .setInterpolator(android.view.animation.DecelerateInterpolator(2f))
                .start()
        }, delayMs)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain()
                velocityTracker?.addMovement(event)
                downY = event.y
                isDragging = false
                dismissSpring.cancel()
            }
            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                val dy = event.y - downY
                if (!isDragging && Math.abs(dy) > 8f) {
                    isDragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                if (isDragging && dy < 0) {
                    translationY = dy
                }
            }
            MotionEvent.ACTION_UP -> {
                velocityTracker?.addMovement(event)
                velocityTracker?.computeCurrentVelocity(1000)
                val vy = velocityTracker?.yVelocity ?: 0f
                velocityTracker?.recycle()
                velocityTracker = null

                if (isDragging) {
                    if (vy < -800f || translationY < -height * 0.4f) {
                        // Throw card off screen
                        performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        animate()
                            .translationY(-height.toFloat() * 2)
                            .alpha(0f)
                            .setDuration(280)
                            .withEndAction { onSwipeUp?.invoke(taskId) }
                            .start()
                    } else {
                        // Snap back
                        dismissSpring.animateToFinalPosition(0f)
                    }
                } else {
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onTap?.invoke(taskId)
                }
                isDragging = false
            }
            MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.recycle()
                velocityTracker = null
                dismissSpring.animateToFinalPosition(0f)
                isDragging = false
            }
        }
        return true
    }
}
