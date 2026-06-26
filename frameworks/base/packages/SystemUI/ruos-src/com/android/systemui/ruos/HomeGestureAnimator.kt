package com.android.systemui.ruos

import android.content.Context
import android.graphics.Rect
import android.view.SurfaceControl

/**
 * Drives the foreground app's recents leash during the swipe-up-to-home gesture.
 *
 * Lifecycle:
 *   1. [attach] — receive the real leash + bounds from the recents handover.
 *   2. [onDrag] — called per touch sample (up to 240 Hz). Applies the app transform
 *      1:1 with the finger via one batched SurfaceControl.Transaction. No spring
 *      while the finger is down — the app tracks the thumb exactly.
 *   3. [settle] — finger lifted. A velocity-seeded spring (stiffness 400, damping
 *      0.75) carries the leash the rest of the way: to home (committed) or back to
 *      fullscreen (cancelled), stepped on the Choreographer.
 *
 * Transform model (matches the spec):
 *   progress 0 → app fullscreen, scale 1.0, radius 0, fully opaque
 *   progress 1 → app at 0.6 scale, radius 28dp, lifted toward its home-grid slot
 *
 * [progressListener] is fired every frame with progress in [0,1] so the layer below
 * (home icons + wallpaper) can fade/de-blur in lockstep — see [HomeRevealController].
 */
class HomeGestureAnimator(private val context: Context) {

    private val density = context.resources.displayMetrics.density
    private val cornerRadiusTarget = CORNER_RADIUS_MAX_DP * density

    private var leash: SurfaceControl? = null
    private val bounds = Rect()

    /** Where the app should appear to fly to when it lands home (its grid slot). */
    private var landingCenterX = 0f
    private var landingCenterY = 0f

    private var driver: FrameDriver? = null
    private val progressSpring = SurfaceSpring()

    var progressListener: ((progress: Float) -> Unit)? = null
    var onSettled: ((committedToHome: Boolean) -> Unit)? = null

    fun attach(leash: SurfaceControl, startBounds: Rect, landingCenter: Pair<Float, Float>?) {
        this.leash = leash
        bounds.set(startBounds)
        landingCenterX = landingCenter?.first ?: startBounds.centerX().toFloat()
        landingCenterY = landingCenter?.second ?: (startBounds.top - startBounds.height() * 0.4f)
        progressSpring.snapTo(0f)
    }

    fun detach() {
        driver?.stop(); driver = null
        leash = null
    }

    /** Finger-tracking frame. [progress] is the raw gesture progress (0..1). */
    fun onDrag(progress: Float) {
        val l = leash ?: return
        applyTransform(l, progress.coerceIn(0f, 1f))
        progressListener?.invoke(progress.coerceIn(0f, 1f))
    }

    /**
     * Finger lifted. Seed the spring with the finger's normalised velocity and let
     * it run to 1 (home) or 0 (fullscreen).
     *
     * @param fromProgress    progress at release
     * @param velocityYpxPerS raw finger velocity (negative = upward)
     * @param commitToHome    decision already made by the gesture controller
     */
    fun settle(fromProgress: Float, velocityYpxPerS: Float, commitToHome: Boolean) {
        val l = leash ?: run { onSettled?.invoke(commitToHome); return }

        // Convert px/s of finger travel into progress/s (progress spans ~full height).
        val travelPx = bounds.height().toFloat().coerceAtLeast(1f)
        val progressVel = -velocityYpxPerS / travelPx   // upward finger → +progress/s

        progressSpring.reconfigure(SurfaceSpring.STIFFNESS_DEFAULT, SurfaceSpring.DAMPING_DEFAULT)
        progressSpring.snapTo(fromProgress)
        progressSpring.setStartVelocity(progressVel)
        progressSpring.setTarget(if (commitToHome) 1f else 0f)

        driver?.stop()
        driver = FrameDriver { dt ->
            val p = progressSpring.step(dt).coerceIn(0f, 1.05f)
            applyTransform(l, p)
            progressListener?.invoke(p.coerceIn(0f, 1f))
            if (!progressSpring.isRunning()) {
                onSettled?.invoke(commitToHome)
                false
            } else true
        }.also { it.start() }
    }

    /**
     * The single source of truth for the app's surface transform at a given progress.
     * Position interpolates from (0,0) fullscreen toward the home-grid landing point
     * so the app appears to fly into its icon, exactly like iOS.
     */
    private fun applyTransform(l: SurfaceControl, progress: Float) {
        val scale = 1f - progress * (1f - APP_MIN_SCALE)        // 1.0 → 0.6
        val radius = progress * cornerRadiusTarget              // 0 → 28dp

        // Scaled surface anchored at top-left; compute the translation that keeps
        // the surface centre travelling from screen-centre toward the landing slot.
        val curCenterX = bounds.centerX().toFloat()
        val curCenterY = bounds.centerY().toFloat()
        val wantCenterX = lerp(curCenterX, landingCenterX, progress)
        val wantCenterY = lerp(curCenterY, landingCenterY, progress)

        // For a top-left-anchored scale, the surface centre sits at
        // (left + w*scale/2). Solve tx so that centre == wantCenter.
        val tx = wantCenterX - (bounds.left + bounds.width() * scale / 2f)
        val ty = wantCenterY - (bounds.top + bounds.height() * scale / 2f)

        SurfaceControl.Transaction().apply {
            setMatrix(l, scale, 0f, 0f, scale)
            setPosition(l, tx, ty)
            setCornerRadius(l, radius)
            // App fades slightly as it shrinks so home reads through near the end.
            setAlpha(l, (1f - progress * 0.15f).coerceIn(0f, 1f))
            apply()
        }
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    companion object {
        const val APP_MIN_SCALE = 0.6f          // spec: reaches 0.6 when fully home
        const val CORNER_RADIUS_MAX_DP = 28f
    }
}
