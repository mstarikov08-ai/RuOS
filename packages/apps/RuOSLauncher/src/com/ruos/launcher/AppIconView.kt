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

    /** Package of the bound app, or null if unbound. */
    val packageName: String? get() = appInfo?.packageName

    /** On-screen bounds of the icon image (used as the app-open / home-landing rect). */
    fun screenBounds(): android.graphics.Rect {
        val loc = IntArray(2)
        iconContainer.getLocationOnScreen(loc)
        return android.graphics.Rect(loc[0], loc[1], loc[0] + iconContainer.width, loc[1] + iconContainer.height)
    }

    /** Show/hide the unread badge with a small pop. */
    fun setBadge(count: Int) {
        if (count <= 0) {
            if (badgeView.visibility != View.GONE) {
                SpringAnimation(badgeView, SpringAnimation.SCALE_X, 0f).apply {
                    spring.stiffness = SpringForce.STIFFNESS_MEDIUM
                    addEndListener { _, _, _, _ -> badgeView.visibility = View.GONE }; start()
                }
                SpringAnimation(badgeView, SpringAnimation.SCALE_Y, 0f).apply {
                    spring.stiffness = SpringForce.STIFFNESS_MEDIUM; start()
                }
            }
            return
        }
        badgeView.text = if (count > 99) "99+" else count.toString()
        if (badgeView.visibility != View.VISIBLE) {
            badgeView.visibility = View.VISIBLE
            badgeView.scaleX = 0f; badgeView.scaleY = 0f
            SpringAnimation(badgeView, SpringAnimation.SCALE_X, 1f).apply {
                spring.stiffness = SpringForce.STIFFNESS_MEDIUM
                spring.dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY; start()
            }
            SpringAnimation(badgeView, SpringAnimation.SCALE_Y, 1f).apply {
                spring.stiffness = SpringForce.STIFFNESS_MEDIUM
                spring.dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY; start()
            }
        }
    }

    /** Quick "landed" pulse — scale up past 1 then spring back, iOS-style. */
    fun pulse() {
        SpringAnimation(this, SpringAnimation.SCALE_X).apply {
            spring = SpringForce(1f).apply {
                stiffness = SpringForce.STIFFNESS_MEDIUM
                dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
            }
            setStartValue(1.18f); setStartVelocity(0f); start()
        }
        SpringAnimation(this, SpringAnimation.SCALE_Y).apply {
            spring = SpringForce(1f).apply {
                stiffness = SpringForce.STIFFNESS_MEDIUM
                dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
            }
            setStartValue(1.18f); setStartVelocity(0f); start()
        }
    }

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

    // iOS-style unread badge (red circle, top-right of the icon).
    private val badgeView = TextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = 10f
        gravity = android.view.Gravity.CENTER
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(Color.parseColor("#FF3B30"))
            setStroke((1.5f * context.resources.displayMetrics.density).toInt(), Color.parseColor("#FF000000"))
        }
        visibility = View.GONE
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
    private var jiggleActive = false

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

        // Badge — top-right corner of the icon.
        val badgeSz = (18 * context.resources.displayMetrics.density).toInt()
        iconContainer.addView(badgeView, LayoutParams(badgeSz, badgeSz).also {
            it.gravity = android.view.Gravity.TOP or android.view.Gravity.END
        })

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
        jiggleActive = active
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
                // In jiggle (edit) mode, tapping an icon must NOT launch it.
                if (!jiggleActive) appInfo?.let { launchApp(it) }
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
        // iOS icon-expand: the opening app surface grows from this icon's rect.
        // Falls back to a plain launch if the remote-animation path is unavailable
        // so an app NEVER fails to open.
        val opts = com.ruos.launcher.recents.AppOpenAnimationRunner
            .makeLaunchOptions(ctx, screenBounds())
        try {
            if (opts != null) ctx.startActivity(intent, opts.toBundle())
            else ctx.startActivity(intent)
        } catch (_: Exception) {
            ctx.startActivity(intent)
        }
        // iOS: opening the app clears its badge immediately.
        setBadge(0)
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
