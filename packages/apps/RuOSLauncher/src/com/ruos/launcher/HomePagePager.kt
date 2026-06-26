package com.ruos.launcher

import android.content.Context
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.VelocityTracker
import android.view.ViewGroup
import androidx.dynamicanimation.animation.FlingAnimation
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

/**
 * Horizontal pager that holds AppGridPage views — one per page of apps.
 *
 * iOS-matching behaviour:
 *  - Pages snap with spring (stiffness 800, damping 0.8)
 *  - Rubber-band at first/last page: offset = delta * 0.3
 *  - Fling uses VelocityTracker; fast swipe jumps page, slow returns
 *  - Pinch gesture fires onPinchOverview callback
 *  - Long press enters jiggle mode
 */
class HomePagePager @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewGroup(context, attrs) {

    // Callbacks
    var onPageChanged: ((Int) -> Unit)? = null
    var onPageCountChanged: ((Int) -> Unit)? = null
    var onJiggleModeToggle: ((Boolean) -> Unit)? = null
    var onPinchOverview: (() -> Unit)? = null
    var onOpenFolder: ((FolderIcon) -> Unit)? = null

    private val pages = mutableListOf<AppGridPage>()
    private var currentPage = 0
    private var scrollX = 0f  // virtual scroll offset in pixels

    // Gesture support
    private var velocityTracker: VelocityTracker? = null
    private var lastTouchX = 0f
    private var isDragging = false
    private var downX = 0f
    private var downY = 0f

    // Spring / fling animations
    private val scrollProperty = object : FloatPropertyCompat<HomePagePager>("scrollX") {
        override fun getValue(obj: HomePagePager) = obj.scrollX
        override fun setValue(obj: HomePagePager, value: Float) {
            obj.scrollX = value
            obj.requestLayout()
        }
    }
    private val scrollSpring = SpringAnimation(this, scrollProperty).apply {
        spring = SpringForce().apply {
            stiffness = SpringForce.STIFFNESS_MEDIUM   // 400 — matches iOS snap
            dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY  // 0.75
        }
    }
    private val flingAnim = FlingAnimation(this, scrollProperty)

    private val longPressHandler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private var isJiggleMode = false

    // Pinch
    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            private var startScale = 1f
            override fun onScaleBegin(det: ScaleGestureDetector): Boolean {
                startScale = det.scaleFactor
                return true
            }
            override fun onScale(det: ScaleGestureDetector): Boolean {
                if (det.scaleFactor < 0.85f) {
                    onPinchOverview?.invoke()
                }
                return true
            }
        }
    )

    fun refreshApps() {
        val repo = RuOSApp.instance.appRepository
        val allItems = repo.getHomeItems()
        val perPage = COLS * ROWS
        val pageCount = maxOf(1, (allItems.size + perPage - 1) / perPage)

        // Rebuild pages
        removeAllViews()
        pages.clear()

        for (i in 0 until pageCount) {
            val slice = allItems.subList(i * perPage, minOf((i + 1) * perPage, allItems.size))
            val page = AppGridPage(context).apply {
                setItems(slice)
                onAppLongPress = { enterJiggleMode() }
                onOpenFolder = { fi -> this@HomePagePager.onOpenFolder?.invoke(fi) }
                // commit() persists the WHOLE layout, reassembled across pages.
                allLayoutItemsSupplier = { pages.flatMap { it.currentItems() } }
            }
            pages.add(page)
            addView(page)
        }
        onPageCountChanged?.invoke(pageCount)
        onPageChanged?.invoke(currentPage)
        requestLayout()
    }

    fun setJiggleMode(active: Boolean) {
        isJiggleMode = active
        pages.forEach { it.setJiggleMode(active) }
    }

    /** Find the icon view for a package across all pages, or null. */
    fun findIcon(pkg: String): AppIconView? {
        pages.forEach { page -> page.findIcon(pkg)?.let { return it } }
        return null
    }

    /** Persist the current full home layout (all pages). */
    fun persistLayout() {
        RuOSApp.instance.appRepository.saveHomeLayout(pages.flatMap { it.currentItems() })
    }

    fun snapToPage(index: Int, animated: Boolean) {
        val target = index.coerceIn(0, pages.size - 1)
        currentPage = target
        val targetX = target.toFloat() * width
        if (animated) {
            scrollSpring.cancel()
            scrollSpring.animateToFinalPosition(targetX)
        } else {
            scrollX = targetX
            requestLayout()
        }
        onPageChanged?.invoke(currentPage)
    }

    private fun enterJiggleMode() {
        if (isJiggleMode) return
        isJiggleMode = true
        onJiggleModeToggle?.invoke(true)
        pages.forEach { it.setJiggleMode(true) }
        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        pages.forEach { child ->
            child.measure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val w = r - l
        val h = b - t
        pages.forEachIndexed { index, child ->
            val left = (index * w - scrollX.toInt())
            child.layout(left, 0, left + w, h)
        }
    }

    private fun initDown(event: MotionEvent) {
        velocityTracker?.recycle()
        velocityTracker = VelocityTracker.obtain()
        velocityTracker?.addMovement(event)
        lastTouchX = event.x
        downX = event.x
        downY = event.y
        isDragging = false
        scrollSpring.cancel()
        flingAnim.cancel()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // In jiggle mode the pages own the touch stream (drag-to-reorder).
        if (isJiggleMode) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initDown(ev)
                // Schedule long press to enter jiggle from empty space.
                longPressRunnable = Runnable { enterJiggleMode() }
                longPressHandler.postDelayed(longPressRunnable!!, LONG_PRESS_TIMEOUT)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (!isDragging && Math.abs(dx) > SLOP && Math.abs(dx) > Math.abs(dy)) {
                    isDragging = true
                    longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true   // steal horizontal swipes for paging
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
            }
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initDown(event)

                // Schedule long press
                longPressRunnable = Runnable { enterJiggleMode() }
                longPressHandler.postDelayed(longPressRunnable!!, LONG_PRESS_TIMEOUT)
            }

            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                val dx = event.x - lastTouchX
                val totalDx = event.x - downX
                val totalDy = event.y - downY

                if (!isDragging && Math.abs(totalDx) > SLOP && Math.abs(totalDx) > Math.abs(totalDy)) {
                    isDragging = true
                    longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                    parent?.requestDisallowInterceptTouchEvent(true)
                }

                if (isDragging) {
                    val raw = scrollX - dx
                    scrollX = applyRubberBand(raw)
                    requestLayout()
                }
                lastTouchX = event.x
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                velocityTracker?.addMovement(event)
                velocityTracker?.computeCurrentVelocity(1000)
                val vx = velocityTracker?.xVelocity ?: 0f
                velocityTracker?.recycle()
                velocityTracker = null

                if (isDragging) {
                    settleAfterFling(vx)
                } else if (isJiggleMode) {
                    // Single tap exits jiggle mode
                    isJiggleMode = false
                    onJiggleModeToggle?.invoke(false)
                    pages.forEach { it.setJiggleMode(false) }
                }
                isDragging = false
            }
        }
        return true
    }

    private fun applyRubberBand(raw: Float): Float {
        val maxScroll = (pages.size - 1).toFloat() * width
        return when {
            raw < 0 -> raw * RUBBER_BAND
            raw > maxScroll -> maxScroll + (raw - maxScroll) * RUBBER_BAND
            else -> raw
        }
    }

    private fun settleAfterFling(velocityX: Float) {
        val pageWidth = width.toFloat()
        val nearestPage = (scrollX / pageWidth).toInt()
        val targetPage = when {
            velocityX < -FLING_VELOCITY_THRESHOLD && nearestPage < pages.size - 1 -> nearestPage + 1
            velocityX > FLING_VELOCITY_THRESHOLD && nearestPage > 0 -> nearestPage - 1
            else -> scrollX.roundToPage()
        }.coerceIn(0, pages.size - 1)

        currentPage = targetPage
        scrollSpring.animateToFinalPosition(targetPage.toFloat() * pageWidth)
        onPageChanged?.invoke(currentPage)
    }

    private fun Float.roundToPage(): Int = Math.round(this / width).coerceIn(0, pages.size - 1)

    companion object {
        const val COLS = 4
        const val ROWS = 6
        const val RUBBER_BAND = 0.3f
        const val FLING_VELOCITY_THRESHOLD = 600f  // px/s
        const val LONG_PRESS_TIMEOUT = 400L
        const val SLOP = 12f
    }
}
