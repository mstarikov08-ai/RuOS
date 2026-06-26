package com.android.systemui.ruos

import android.app.Notification
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.service.notification.StatusBarNotification
import android.text.format.DateFormat
import android.util.AttributeSet
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.dynamicanimation.animation.FlingAnimation
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import java.util.Date

/**
 * RuOS Notification Centre — swipe down from top-left.
 *
 * Layout:
 *   Date header (today's date, prominent)
 *   Scrollable list of notification groups, each frosted-glass card
 *   "Clear All" button at top right of list
 *
 * Each notification card:
 *   - Frosted glass (dark) with app icon, bold title, body text, timestamp
 *   - Swipe left to dismiss with spring throw
 *   - Grouped by package name exactly like iOS
 */
class NotificationCenterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val density = resources.displayMetrics.density

    // Background
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 28, 28, 30)
    }
    private val bgRect = RectF()
    private val cornerRadius = 20f * density

    // Header
    private val dateLabel = TextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = 28f
        setTypeface(null, android.graphics.Typeface.BOLD)
    }
    private val clearAllBtn = TextView(context).apply {
        text = "Очистить все"
        setTextColor(Color.argb(180, 255, 255, 255))
        textSize = 13f
    }

    // Notification list
    private val scroll = ScrollView(context).apply {
        isVerticalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
    }
    private val list = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
    }

    // Current notifications grouped by package
    private val groups = LinkedHashMap<String, MutableList<StatusBarNotification>>()

    init {
        setWillNotDraw(false)

        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((16 * density).toInt(), (20 * density).toInt(), (16 * density).toInt(), (8 * density).toInt())
        }
        updateDate()
        headerRow.addView(dateLabel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        headerRow.addView(clearAllBtn)
        clearAllBtn.setOnClickListener {
            performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            clearAll()
        }

        scroll.addView(list, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((12 * density).toInt(), 0, (12 * density).toInt(), (24 * density).toInt())
        }
        content.addView(headerRow, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        content.addView(scroll, ViewGroup.LayoutParams.MATCH_PARENT, 0)

        addView(content, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        bgRect.set(0f, 0f, w.toFloat(), h.toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRoundRect(bgRect, cornerRadius, cornerRadius, bgPaint)
    }

    fun updateNotifications(notifications: List<StatusBarNotification>) {
        groups.clear()
        notifications.forEach { sbn ->
            groups.getOrPut(sbn.packageName) { mutableListOf() }.add(sbn)
        }
        rebuildList()
    }

    private fun rebuildList() {
        list.removeAllViews()
        groups.forEach { (pkg, sbns) ->
            val card = NotificationGroupCard(context, pkg, sbns).apply {
                onDismissGroup = { dismissGroup(pkg) }
                onDismissItem = { sbn -> dismissItem(sbn) }
            }
            list.addView(card, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            val margin = (8 * density).toInt()
            (card.layoutParams as LinearLayout.LayoutParams).setMargins(0, 0, 0, margin)
        }
    }

    private fun updateDate() {
        val today = Date()
        val dayOfWeek = DateFormat.format("EEEE", today).toString().replaceFirstChar { it.uppercaseChar() }
        val day = DateFormat.format("d MMMM", today).toString()
        dateLabel.text = "$dayOfWeek, $day"
    }

    private fun clearAll() {
        groups.clear()
        list.removeAllViews()
    }

    private fun dismissGroup(pkg: String) {
        groups.remove(pkg)
        rebuildList()
    }

    private fun dismissItem(sbn: StatusBarNotification) {
        groups[sbn.packageName]?.remove(sbn)
        if (groups[sbn.packageName]?.isEmpty() == true) groups.remove(sbn.packageName)
        rebuildList()
    }

    fun animateIn() {
        translationY = -height.toFloat()
        SpringAnimation(this, SpringAnimation.TRANSLATION_Y, 0f).apply {
            spring.stiffness = SpringForce.STIFFNESS_MEDIUM
            spring.dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
            start()
        }
    }

    fun animateOut(onDone: () -> Unit) {
        SpringAnimation(this, SpringAnimation.TRANSLATION_Y, -height.toFloat()).apply {
            spring.stiffness = SpringForce.STIFFNESS_HIGH
            spring.dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
            addEndListener { _, _, _, _ -> onDone() }
            start()
        }
    }
}

// ---------------------------------------------------------------------------

private class NotificationGroupCard(
    context: Context,
    pkg: String,
    sbns: List<StatusBarNotification>
) : FrameLayout(context) {

    var onDismissGroup: (() -> Unit)? = null
    var onDismissItem: ((StatusBarNotification) -> Unit)? = null

    private val density = context.resources.displayMetrics.density
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(160, 44, 44, 46)
    }
    private val bgRect = RectF()
    private val cornerRadius = 14f * density

    private var velocityTracker: VelocityTracker? = null
    private var downX = 0f
    private var isDragging = false

    init {
        setWillNotDraw(false)
        clipChildren = false
        val pm = context.packageManager
        val icon = try { pm.getApplicationIcon(pkg) } catch (_: Exception) { null }
        val label = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (_: Exception) { pkg }

        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt())
        }

        // App header row
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        if (icon != null) {
            val iconView = ImageView(context).apply { setImageDrawable(icon) }
            headerRow.addView(iconView, (18 * density).toInt(), (18 * density).toInt())
            headerRow.addView(spacer(6), (6 * density).toInt(), 1)
        }
        val appName = TextView(context).apply {
            text = label.uppercase()
            setTextColor(Color.argb(160, 255, 255, 255))
            textSize = 11f
            letterSpacing = 0.05f
        }
        headerRow.addView(appName)
        col.addView(headerRow)

        // Notification rows (up to 3 visible, rest collapsed)
        sbns.take(3).forEach { sbn ->
            col.addView(NotificationRow(context, sbn).apply {
                onDismiss = { onDismissItem?.invoke(sbn) }
            })
        }

        addView(col, LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        bgRect.set(0f, 0f, w.toFloat(), h.toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRoundRect(bgRect, cornerRadius, cornerRadius, bgPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain()
                velocityTracker?.addMovement(event)
                downX = event.x
                isDragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                val dx = event.x - downX
                if (!isDragging && dx < -8f) {
                    isDragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                if (isDragging && dx < 0) translationX = dx
            }
            MotionEvent.ACTION_UP -> {
                velocityTracker?.addMovement(event)
                velocityTracker?.computeCurrentVelocity(1000)
                val vx = velocityTracker?.xVelocity ?: 0f
                velocityTracker?.recycle()
                velocityTracker = null

                if (isDragging && (vx < -800f || translationX < -width * 0.5f)) {
                    animate().translationX(-width.toFloat()).alpha(0f).setDuration(240).withEndAction {
                        onDismissGroup?.invoke()
                    }.start()
                } else {
                    SpringAnimation(this, SpringAnimation.TRANSLATION_X, 0f).apply {
                        spring.stiffness = SpringForce.STIFFNESS_MEDIUM
                        spring.dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
                        start()
                    }
                }
                isDragging = false
            }
            MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.recycle()
                velocityTracker = null
                SpringAnimation(this, SpringAnimation.TRANSLATION_X, 0f).apply {
                    spring.stiffness = SpringForce.STIFFNESS_MEDIUM
                    start()
                }
                isDragging = false
            }
        }
        return true
    }

    private fun spacer(widthDp: Int) = View(context)
}

private class NotificationRow(context: Context, sbn: StatusBarNotification) : LinearLayout(context) {
    var onDismiss: (() -> Unit)? = null

    init {
        orientation = HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        val density = context.resources.displayMetrics.density
        val notif = sbn.notification
        val extras = notif.extras

        setPadding(0, (8 * density).toInt(), 0, (8 * density).toInt())

        val col = LinearLayout(context).apply { orientation = VERTICAL }
        val title = TextView(context).apply {
            text = extras.getString(Notification.EXTRA_TITLE) ?: ""
            setTextColor(Color.WHITE)
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        val body = TextView(context).apply {
            text = extras.getString(Notification.EXTRA_TEXT) ?: ""
            setTextColor(Color.argb(200, 255, 255, 255))
            textSize = 13f
            maxLines = 2
        }
        col.addView(title)
        col.addView(body)

        val time = TextView(context).apply {
            text = android.text.format.DateFormat.getTimeFormat(context).format(Date(sbn.postTime))
            setTextColor(Color.argb(140, 255, 255, 255))
            textSize = 12f
        }

        addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(time)
    }
}
