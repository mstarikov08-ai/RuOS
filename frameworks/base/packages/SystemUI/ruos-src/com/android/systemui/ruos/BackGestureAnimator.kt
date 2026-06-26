package com.android.systemui.ruos

import android.content.Context
import android.graphics.Rect
import android.view.SurfaceControl
import android.window.BackEvent

/**
 * Interactive predictive-back: swipe in from a side edge and the current screen
 * follows the finger horizontally while the previous screen is revealed underneath,
 * scaled to 0.95 and translated from -30dp toward 0 as the gesture progresses.
 *
 * On Android 14 the system already exposes predictive back via
 * [android.window.OnBackAnimationCallback]; this animator implements the *visual*
 * model RuOS wants (iOS card-slide rather than AOSP's default) and is driven either
 * by that callback's [BackEvent]s or directly by the gesture controller's raw touch.
 *
 * Two leashes:
 *   - current : the screen being dismissed (slides out under the finger)
 *   - previous: the screen being revealed (parallax in from the left)
 * Both come from the transition's RemoteAnimationTargets.
 *
 * Spring on release: stiffness 400, damping 0.75.
 */
class BackGestureAnimator(private val context: Context) {

    private val density = context.resources.displayMetrics.density
    private val previousStartOffset = -30f * density   // previous screen starts at -30dp
    private val cardRadius = 22f * density

    private val displayBounds = Rect()
    private var current: SurfaceControl? = null
    private var previous: SurfaceControl? = null
    private var edge = BackEvent.EDGE_LEFT

    private val progressSpring = SurfaceSpring()
    private var driver: FrameDriver? = null
    var onSettled: ((committed: Boolean) -> Unit)? = null

    fun attach(current: SurfaceControl, previous: SurfaceControl?, display: Rect, edge: Int) {
        this.current = current
        this.previous = previous
        this.edge = edge
        displayBounds.set(display)
        progressSpring.snapTo(0f)
    }

    fun detach() { driver?.stop(); driver = null; current = null; previous = null }

    /** Finger-driven frame. [progress] 0 = untouched, 1 = fully back. */
    fun onDrag(progress: Float) {
        applyTransform(progress.coerceIn(0f, 1f))
    }

    fun settle(fromProgress: Float, velocityXpxPerS: Float, commit: Boolean) {
        val travel = displayBounds.width().toFloat().coerceAtLeast(1f)
        val dir = if (edge == BackEvent.EDGE_LEFT) 1f else -1f
        val progressVel = (velocityXpxPerS * dir) / travel

        progressSpring.reconfigure(SurfaceSpring.STIFFNESS_DEFAULT, SurfaceSpring.DAMPING_DEFAULT)
        progressSpring.snapTo(fromProgress)
        progressSpring.setStartVelocity(progressVel)
        progressSpring.setTarget(if (commit) 1f else 0f)

        driver?.stop()
        driver = FrameDriver { dt ->
            val p = progressSpring.step(dt).coerceIn(0f, 1f)
            applyTransform(p)
            if (!progressSpring.isRunning()) { onSettled?.invoke(commit); false } else true
        }.also { it.start() }
    }

    private fun applyTransform(progress: Float) {
        val w = displayBounds.width().toFloat()
        val dir = if (edge == BackEvent.EDGE_LEFT) 1f else -1f

        SurfaceControl.Transaction().apply {
            // Current screen slides off toward the opposite edge, shrinking slightly.
            current?.let { c ->
                val slide = dir * progress * w * 0.92f
                val scale = 1f - progress * 0.06f
                setMatrix(c, scale, 0f, 0f, scale)
                setPosition(c,
                    slide + (w * (1 - scale) / 2f),
                    displayBounds.height() * (1 - scale) / 2f)
                setCornerRadius(c, progress * cardRadius)
                setAlpha(c, (1f - progress * 0.15f).coerceIn(0f, 1f))
            }
            // Previous screen parallax: scale 0.95→1.0, translate -30dp→0.
            previous?.let { p ->
                val scale = 0.95f + 0.05f * progress
                val tx = previousStartOffset * (1f - progress) * -dir
                setMatrix(p, scale, 0f, 0f, scale)
                setPosition(p,
                    tx + (w * (1 - scale) / 2f),
                    displayBounds.height() * (1 - scale) / 2f)
                setAlpha(p, (0.6f + 0.4f * progress).coerceIn(0f, 1f))
            }
            apply()
        }
    }
}
