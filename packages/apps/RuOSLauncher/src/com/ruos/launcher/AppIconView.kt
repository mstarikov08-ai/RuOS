package com.ruos.launcher

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

/**
 * Single app icon — 14dp corner radius, subtle top-left light source gradient,
 * jiggle animation when in edit mode, spring scale on tap.
 */
class AppIconView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    var onLongPress: (() -> Unit)? = null
    var onDeleteClick: ((AppInfo) -> Unit)? = null

    private var appInfo: AppInfo? = null

    private val iconContainer = FrameLayout(context)
    private val iconImage = ImageView(context)
    private val lightOverlay = LightSourceOverlayView(context)
    private val label = TextView(context).apply {
        textSize = 10f
        setTextColor(Color.WHITE)
        gravity = android.view.Gravity.CENTER
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        setShadowLayer(4f, 0f, 1f, Color.BLACK)
    }
    private val deleteButton = ImageView(context).apply {
        setImageResource(R.drawable.ic_delete_badge)
        visibility = View.GONE
        scaleX = 0f
        scaleY = 0f
    }

    // Spring for tap press feedback
    private val scaleXSpring = SpringAnimation(this, SpringAnimation.SCALE_X, 1f).apply {
        spring.stiffness = SpringForce.STIFFNESS_MEDIUM
        spring.dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
    }
    private val scaleYSpring = SpringAnimation(this, SpringAnimation.SCALE_Y, 1f).apply {
        spring.stiffness = SpringForce.STIFFNESS_MEDIUM
        spring.dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
    }

    // Jiggle
    private var jiggleAnimator: ValueAnimator? = null

    // Long press
    private val longPressHandler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null

    init {
        setWillNotDraw(false)
        clipChildren = false
        clipToPadding = false

        val iconSize = context.resources.getDimensionPixelSize(R.dimen.icon_size)
        iconContainer.layoutParams = LayoutParams(iconSize, iconSize).also {
            it.gravity = android.view.Gravity.CENTER_HORIZONTAL or android.view.Gravity.TOP
        }
        iconContainer.clipChildren = false
        iconContainer.addView(iconImage, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        iconContainer.addView(lightOverlay, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

        // Delete badge — top-left corner of icon
        val badgeSize = context.resources.getDimensionPixelSize(R.dimen.delete_badge_size)
        val deleteParams = LayoutParams(badgeSize, badgeSize).also {
            it.gravity = android.view.Gravity.TOP or android.view.Gravity.START
        }
        iconContainer.addView(deleteButton, deleteParams)

        addView(iconContainer)

        val labelParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).also {
            it.gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
            it.topMargin = iconSize + context.resources.getDimensionPixelSize(R.dimen.icon_label_margin)
        }
        addView(label, labelParams)
    }

    fun bind(info: AppInfo) {
        appInfo = info
        iconImage.setImageDrawable(info.icon)
        label.text = info.label

        deleteButton.setOnClickListener {
            onDeleteClick?.invoke(info)
        }
    }

    fun setJiggleMode(active: Boolean) {
        if (active) {
            startJiggle()
            showDeleteButton()
        } else {
            stopJiggle()
            hideDeleteButton()
        }
    }

    private fun startJiggle() {
        val angle = (Math.random() * 4 - 2).toFloat()
        jiggleAnimator = ValueAnimator.ofFloat(-angle, angle).apply {
            duration = 130
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = DecelerateInterpolator()
            addUpdateListener { iconContainer.rotation = it.animatedValue as Float }
            start()
        }
    }

    private fun stopJiggle() {
        jiggleAnimator?.cancel()
        jiggleAnimator = null
        SpringAnimation(iconContainer, SpringAnimation.ROTATION, 0f).apply {
            spring.stiffness = SpringForce.STIFFNESS_HIGH
            spring.dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
            start()
        }
    }

    private fun showDeleteButton() {
        deleteButton.visibility = View.VISIBLE
        SpringAnimation(deleteButton, SpringAnimation.SCALE_X, 1f).apply {
            spring.stiffness = SpringForce.STIFFNESS_MEDIUM
            spring.dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
            start()
        }
        SpringAnimation(deleteButton, SpringAnimation.SCALE_Y, 1f).apply {
            spring.stiffness = SpringForce.STIFFNESS_MEDIUM
            spring.dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
            start()
        }
    }

    private fun hideDeleteButton() {
        SpringAnimation(deleteButton, SpringAnimation.SCALE_X, 0f).apply {
            spring.stiffness = SpringForce.STIFFNESS_MEDIUM
            addEndListener { _, _, _, _ -> deleteButton.visibility = View.GONE }
            start()
        }
        SpringAnimation(deleteButton, SpringAnimation.SCALE_Y, 0f).apply {
            spring.stiffness = SpringForce.STIFFNESS_MEDIUM
            start()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scaleXSpring.animateToFinalPosition(0.88f)
                scaleYSpring.animateToFinalPosition(0.88f)

                longPressRunnable = Runnable {
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    onLongPress?.invoke()
                }
                longPressHandler.postDelayed(longPressRunnable!!, 400L)
            }
            MotionEvent.ACTION_UP -> {
                longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                scaleXSpring.animateToFinalPosition(1f)
                scaleYSpring.animateToFinalPosition(1f)
                appInfo?.let { launchApp(it) }
            }
            MotionEvent.ACTION_CANCEL -> {
                longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                scaleXSpring.animateToFinalPosition(1f)
                scaleYSpring.animateToFinalPosition(1f)
            }
        }
        return true
    }

    private fun launchApp(info: AppInfo) {
        val ctx = context
        val intent = ctx.packageManager.getLaunchIntentForPackage(info.packageName) ?: return
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
    }
}

/** Subtle top-left gradient that simulates a light source on icons */
private class LightSourceOverlayView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cornerRadius = context.resources.getDimensionPixelSize(R.dimen.icon_corner_radius).toFloat()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        paint.shader = LinearGradient(
            0f, 0f, w * 0.6f, h * 0.6f,
            intArrayOf(
                Color.argb(40, 255, 255, 255),
                Color.argb(0, 255, 255, 255)
            ),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), cornerRadius, cornerRadius, paint)
    }
}
