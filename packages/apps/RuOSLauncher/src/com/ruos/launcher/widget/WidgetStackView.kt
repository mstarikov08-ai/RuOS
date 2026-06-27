package com.ruos.launcher.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.ruos.launcher.Haptics
import kotlin.math.abs

/**
 * iOS-style Smart Stack: several widgets occupy one frame, and a vertical swipe pages
 * between them with a spring snap. The card behind peeks a few dp so it reads as a stack,
 * and a dot rail on the right shows the position. Wraps around at both ends. Optionally
 * auto-rotates (Smart Rotate) on a slow timer.
 *
 * Self-contained and reusable: `WidgetStackView(ctx).apply { addWidget(a); addWidget(b) }`.
 * All physics reuse the launcher's existing SpringForce vocabulary (stiffness 400).
 */
class WidgetStackView(context: Context) : FrameLayout(context) {

    private val d = resources.displayMetrics.density
    private fun dp(v: Float) = v * d

    private val pages = ArrayList<View>()
    private var index = 0
    private var incoming = -1            // the page sliding in during a drag (-1 = none)

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downY = 0f
    private var dragging = false
    private var tracker: VelocityTracker? = null

    private val dotOn = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val dotOff = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66FFFFFF }

    private var autoMs = 0L
    private val autoRotate = Runnable { page(+1, haptic = false); scheduleAuto() }

    init {
        setWillNotDraw(false)
        clipChildren = false
    }

    fun addWidget(v: View) {
        pages.add(v)
        addView(v, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        relayoutStack()
    }

    /** Enable Smart Rotate; [seconds] between auto-advances (0 disables). */
    fun setAutoRotate(seconds: Int) {
        autoMs = seconds * 1000L
        removeCallbacks(autoRotate)
        if (autoMs > 0 && pages.size > 1) scheduleAuto()
    }

    private fun scheduleAuto() {
        if (autoMs > 0) { removeCallbacks(autoRotate); postDelayed(autoRotate, autoMs) }
    }

    /** Stack the cards: current on top at y=0, the rest parked one frame below, hidden. */
    private fun relayoutStack() {
        pages.forEachIndexed { i, v ->
            v.translationY = if (i == index) 0f else height.toFloat()
            v.alpha = if (i == index) 1f else 0f
            v.visibility = if (i == index) View.VISIBLE else View.INVISIBLE
            v.elevation = if (i == index) dp(1f) else 0f
        }
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        relayoutStack()
    }

    // ── touch / paging ────────────────────────────────────────────────────────
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (pages.size < 2) return false
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> { downY = ev.y; dragging = false }
            MotionEvent.ACTION_MOVE ->
                if (!dragging && abs(ev.y - downY) > touchSlop) { dragging = true; startDrag(ev) }
        }
        return dragging
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (pages.size < 2) return false
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> { downY = ev.y; removeCallbacks(autoRotate) }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && abs(ev.y - downY) > touchSlop) { dragging = true; startDrag(ev) }
                if (dragging) drag(ev.y - downY, ev)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) endDrag(ev.y - downY) else scheduleAuto()
                dragging = false
            }
        }
        return true
    }

    private fun startDrag(ev: MotionEvent) {
        tracker = VelocityTracker.obtain()
        tracker?.addMovement(ev)
        Haptics.light(this)
    }

    private fun drag(dy: Float, ev: MotionEvent) {
        tracker?.addMovement(ev)
        val h = height.toFloat().coerceAtLeast(1f)
        val cur = pages[index]
        // pick which neighbour is coming in based on direction (wrap-around)
        val target = if (dy < 0) (index + 1) % pages.size else (index - 1 + pages.size) % pages.size
        if (incoming != target) {
            pages.getOrNull(incoming)?.let { it.visibility = View.INVISIBLE }
            incoming = target
            pages[incoming].visibility = View.VISIBLE
        }
        cur.translationY = dy
        cur.alpha = (1f - abs(dy) / h * 0.4f).coerceIn(0.5f, 1f)
        val inc = pages[incoming]
        inc.translationY = if (dy < 0) h + dy else -h + dy   // slides toward 0 as |dy|→h
        inc.alpha = (abs(dy) / h).coerceIn(0f, 1f)
    }

    private fun endDrag(dy: Float) {
        val h = height.toFloat().coerceAtLeast(1f)
        tracker?.apply { computeCurrentVelocity(1000) }
        val vy = tracker?.yVelocity ?: 0f
        tracker?.recycle(); tracker = null
        val commit = incoming >= 0 && (abs(dy) > h * 0.3f || abs(vy) > 1200f)
        if (commit) {
            val from = pages[index]; val to = pages[incoming]
            val dir = if (dy < 0) -1f else 1f
            springTo(from, dir * h) { from.visibility = View.INVISIBLE; from.translationY = h; from.alpha = 0f }
            springTo(to, 0f) {}
            to.animate().alpha(1f).setDuration(120).start()
            index = incoming; incoming = -1
            from.elevation = 0f; to.elevation = dp(1f)
            Haptics.light(this)
            invalidate()
        } else {
            // snap back
            val cur = pages[index]
            springTo(cur, 0f) {}
            cur.animate().alpha(1f).setDuration(120).start()
            pages.getOrNull(incoming)?.let { ic ->
                val dir = if (dy < 0) 1f else -1f
                springTo(ic, dir * h) { ic.visibility = View.INVISIBLE; ic.alpha = 0f }
            }
            incoming = -1
        }
        scheduleAuto()
    }

    /** Programmatic page step (+1 / -1), used by Smart Rotate. */
    private fun page(delta: Int, haptic: Boolean) {
        if (pages.size < 2) return
        val h = height.toFloat().coerceAtLeast(1f)
        val next = (index + delta + pages.size) % pages.size
        val from = pages[index]; val to = pages[next]
        to.visibility = View.VISIBLE; to.translationY = if (delta > 0) h else -h; to.alpha = 0f
        springTo(from, if (delta > 0) -h else h) { from.visibility = View.INVISIBLE; from.translationY = h; from.alpha = 0f }
        springTo(to, 0f) {}
        to.animate().alpha(1f).setDuration(160).start()
        from.elevation = 0f; to.elevation = dp(1f)
        index = next
        if (haptic) Haptics.light(this)
        invalidate()
    }

    private fun springTo(v: View, end: Float, onEnd: () -> Unit) {
        SpringAnimation(v, TRANSLATION_Y).apply {
            spring = SpringForce(end).setStiffness(400f).setDampingRatio(0.85f)
            addEndListener { _, _, _, _ -> onEnd() }
        }.start()
    }

    // ── dot rail ──────────────────────────────────────────────────────────────
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (pages.size < 2) return
        val r = dp(2.5f); val gap = dp(8f)
        val totalH = pages.size * gap
        var cy = height / 2f - totalH / 2f + gap / 2f
        val cx = width - dp(9f)
        for (i in pages.indices) {
            canvas.drawCircle(cx, cy, r, if (i == index) dotOn else dotOff)
            cy += gap
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(autoRotate)
        tracker?.recycle(); tracker = null
    }

    companion object {
        private val TRANSLATION_Y = object : FloatPropertyCompat<View>("translationY") {
            override fun getValue(v: View) = v.translationY
            override fun setValue(v: View, value: Float) { v.translationY = value }
        }
    }
}
