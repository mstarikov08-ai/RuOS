package com.ruos.notify.banner

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.ruos.notify.model.NotifItem
import com.ruos.notify.util.Fonts
import kotlin.math.abs

/**
 * A single iOS-18 heads-up banner: dark frosted rounded card (window supplies the
 * blur-behind), app icon as an 18dp rounded square, bold app name + time on the top
 * row, bold title + regular body below. Swipe up to dismiss (follows the finger,
 * flicks on velocity), swipe down to expand the action buttons, tap to open.
 */
class BannerView(
    context: Context,
    val item: NotifItem,
    private val summaryCount: Int = 0
) : FrameLayout(context) {

    var onDismiss: (() -> Unit)? = null
    var onOpen: (() -> Unit)? = null

    private val d = resources.displayMetrics.density
    private fun dp(v: Float) = v * d

    private val card = LinearLayout(context)
    private val actionRow = LinearLayout(context)
    private var expanded = false

    // Gesture
    private val tracker = android.view.VelocityTracker.obtain()
    private var downY = 0f
    private var downX = 0f
    private var dragging = false
    private val slop = ViewConfiguration.get(context).scaledTouchSlop

    private val transY = SpringAnimation(this, SpringAnimation.TRANSLATION_Y, 0f).apply {
        spring.stiffness = SpringForce.STIFFNESS_MEDIUM
        spring.dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
    }

    init {
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(dp(14).toInt(), dp(12).toInt(), dp(14).toInt(), dp(12).toInt())
        card.background = GradientDrawable().apply {
            cornerRadius = dp(22f)
            setColor(Color.parseColor("#CC1C1C1E"))   // translucent; window adds blur
            setStroke(dp(0.5f).toInt(), Color.parseColor("#33FFFFFF"))
        }
        card.clipToOutline = true
        card.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, dp(22f))
            }
        }
        card.elevation = dp(8f)

        // Top row: icon · APP NAME · time
        val top = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val icon = ImageView(context).apply {
            setImageDrawable(item.icon)
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(18f))
                }
            }
        }
        top.addView(icon, LinearLayout.LayoutParams(dp(20f).toInt(), dp(20f).toInt()))
        top.addView(TextView(context).apply {
            text = item.appName.uppercase()
            setTextColor(Color.parseColor("#B0B0B8")); textSize = 12f
            typeface = Fonts.medium; letterSpacing = 0.02f
            setPadding(dp(7f).toInt(), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(TextView(context).apply {
            text = item.timeText()
            setTextColor(Color.parseColor("#8E8E93")); textSize = 12f; typeface = Fonts.regular
        })
        card.addView(top)

        // Title (bold)
        val titleText = if (summaryCount > 3) item.appName else item.title.toString()
        card.addView(TextView(context).apply {
            text = titleText
            setTextColor(Color.WHITE); textSize = 15f; typeface = Fonts.bold
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dp(5f).toInt(), 0, 0)
        })
        // Body (regular)
        val bodyText = if (summaryCount > 3) "$summaryCount новых уведомлений" else item.text.toString()
        card.addView(TextView(context).apply {
            text = bodyText
            setTextColor(Color.parseColor("#E5E5EA")); textSize = 14f; typeface = Fonts.regular
            maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dp(2f).toInt(), 0, 0)
        })

        // Action row (revealed on expand)
        actionRow.orientation = LinearLayout.HORIZONTAL
        actionRow.visibility = View.GONE
        actionRow.setPadding(0, dp(10f).toInt(), 0, 0)
        item.actions.take(3).forEach { a ->
            actionRow.addView(TextView(context).apply {
                text = a.label
                setTextColor(Color.parseColor("#4F9DFF")); textSize = 14f; typeface = Fonts.medium
                gravity = Gravity.CENTER
                setPadding(dp(8f).toInt(), dp(8f).toInt(), dp(8f).toInt(), dp(8f).toInt())
                isClickable = true
                setOnClickListener {
                    performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    runCatching { a.intent?.send() }
                    onDismiss?.invoke()
                }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        card.addView(actionRow)

        addView(card, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        tracker.addMovement(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downY = event.y; downX = event.x; dragging = false; transY.cancel() }
            MotionEvent.ACTION_MOVE -> {
                val dy = event.y - downY
                if (!dragging && abs(dy) > slop) dragging = true
                if (dragging) {
                    if (dy < 0) translationY = dy            // dragging up: follow 1:1
                    else if (!expanded) { translationY = dy * 0.3f; if (dy > dp(40f)) expand() }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                tracker.computeCurrentVelocity(1000)
                val vy = tracker.yVelocity
                if (!dragging) { onOpen?.invoke(); return true }
                if (translationY < -dp(40f) || vy < -800f) {
                    // Flick away upward.
                    transY.setStartVelocity(vy).animateToFinalPosition(-height.toFloat() - dp(40f))
                    postDelayed({ onDismiss?.invoke() }, 160)
                } else {
                    transY.animateToFinalPosition(0f)
                }
            }
        }
        return true
    }

    private fun expand() {
        if (expanded || item.actions.isEmpty()) return
        expanded = true
        actionRow.visibility = View.VISIBLE
        transY.animateToFinalPosition(0f)
        performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    fun release() { runCatching { tracker.recycle() } }
}
