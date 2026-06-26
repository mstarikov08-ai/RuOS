package com.ruos.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

/**
 * A folder cell on the home grid — a rounded translucent tile showing a 3×3 mini
 * preview of the contained app icons, with the folder title beneath. Same outer
 * layout shape as [AppIconView] so it occupies one grid slot identically.
 */
class FolderIcon(context: Context) : FrameLayout(context) {

    var onLongPress: (() -> Unit)? = null
    var onOpen: ((FolderIcon) -> Unit)? = null

    private var folder: HomeItem.Folder? = null
    val folderId: String? get() = folder?.id
    fun boundFolder(): HomeItem.Folder? = folder

    private val tile = TileView(context)
    private val iconContainer = FrameLayout(context).apply {
        clipChildren = false
        addView(tile, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }
    private val label = TextView(context).apply {
        textSize = 10f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        setShadowLayer(4f, 0f, 1f, Color.BLACK)
    }

    private val scaleXSpring = SpringAnimation(this, SpringAnimation.SCALE_X, 1f).apply {
        spring.stiffness = SpringForce.STIFFNESS_MEDIUM
        spring.dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
    }
    private val scaleYSpring = SpringAnimation(this, SpringAnimation.SCALE_Y, 1f).apply {
        spring.stiffness = SpringForce.STIFFNESS_MEDIUM
        spring.dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
    }

    private val longPressHandler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null

    init {
        setWillNotDraw(false)
        clipChildren = false
        val iconSize = context.resources.getDimensionPixelSize(R.dimen.icon_size)
        iconContainer.layoutParams = LayoutParams(iconSize, iconSize).also {
            it.gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
        }
        addView(iconContainer)
        addView(label, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).also {
            it.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            it.topMargin = iconSize + context.resources.getDimensionPixelSize(R.dimen.icon_label_margin)
        })
    }

    fun bind(f: HomeItem.Folder) {
        folder = f
        label.text = f.title
        tile.setApps(f.apps)
    }

    fun screenBounds(): android.graphics.Rect {
        val loc = IntArray(2)
        iconContainer.getLocationOnScreen(loc)
        return android.graphics.Rect(loc[0], loc[1], loc[0] + iconContainer.width, loc[1] + iconContainer.height)
    }

    fun pulse() {
        SpringAnimation(this, SpringAnimation.SCALE_X).apply {
            spring = SpringForce(1f).apply {
                stiffness = SpringForce.STIFFNESS_MEDIUM
                dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
            }
            setStartValue(1.18f); start()
        }
        SpringAnimation(this, SpringAnimation.SCALE_Y).apply {
            spring = SpringForce(1f).apply {
                stiffness = SpringForce.STIFFNESS_MEDIUM
                dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
            }
            setStartValue(1.18f); start()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scaleXSpring.animateToFinalPosition(0.9f)
                scaleYSpring.animateToFinalPosition(0.9f)
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
                onOpen?.invoke(this)
            }
            MotionEvent.ACTION_CANCEL -> {
                longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                scaleXSpring.animateToFinalPosition(1f)
                scaleYSpring.animateToFinalPosition(1f)
            }
        }
        return true
    }

    /** The rounded tile that paints the 3×3 mini app-icon preview. */
    private class TileView(context: Context) : View(context) {
        private var apps: List<AppInfo> = emptyList()
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(60, 255, 255, 255)
        }
        private val radius = context.resources.getDimensionPixelSize(R.dimen.icon_corner_radius).toFloat()

        fun setApps(list: List<AppInfo>) { apps = list; invalidate() }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            canvas.drawRoundRect(RectF(0f, 0f, w, h), radius, radius, bgPaint)
            // 3×3 mini grid inset within the tile
            val pad = w * 0.14f
            val gap = w * 0.06f
            val cell = (w - 2 * pad - 2 * gap) / 3f
            for (i in 0 until minOf(9, apps.size)) {
                val r = i / 3; val c = i % 3
                val left = pad + c * (cell + gap)
                val top = pad + r * (cell + gap)
                val d = apps[i].icon
                d.setBounds(left.toInt(), top.toInt(), (left + cell).toInt(), (top + cell).toInt())
                d.draw(canvas)
            }
        }
    }
}
