package com.android.systemui.ruos

import android.content.Context
import android.graphics.Rect
import android.view.SurfaceControl
import android.view.animation.Interpolator
import android.view.animation.PathInterpolator

/**
 * The app-OPEN transition: the tapped icon itself expands into the full-screen app.
 *
 * Unlike the home swipe (which is finger-driven), open is a timed transition:
 * 400 ms on cubic-bezier(0.32, 0.72, 0, 1) — the iOS app-open curve. It runs on the
 * Choreographer so it's frame-locked to the panel refresh (120 Hz where available).
 *
 * The opening app's leash is handed to us by a RemoteAnimationAdapter registered for
 * ACTIVITY_OPEN (see [RuOSTransitions]). We start it at the icon's exact grid rect
 * with a 15dp corner radius and grow it to the full display with radius 0, so the
 * surface appears to be the icon unfolding — not a window flying from screen centre.
 *
 * @param iconBounds the on-screen rect of the tapped icon, in display pixels. Passed
 *        from the launcher via the ActivityOptions launch bounds / a shared channel.
 */
class AppLaunchAnimator(private val context: Context) {

    private val density = context.resources.displayMetrics.density
    private val startRadius = ICON_RADIUS_DP * density

    private val interpolator: Interpolator =
        PathInterpolator(0.32f, 0.72f, 0f, 1f)   // iOS app-open bezier

    private var driver: FrameDriver? = null
    private var elapsedMs = 0f

    fun animateOpen(
        leash: SurfaceControl,
        iconBounds: Rect,
        displayBounds: Rect,
        onEnd: () -> Unit
    ) {
        elapsedMs = 0f
        val startCx = iconBounds.centerX().toFloat()
        val startCy = iconBounds.centerY().toFloat()
        val startScale = iconBounds.width().toFloat() / displayBounds.width().toFloat()

        driver?.stop()
        driver = FrameDriver { dt ->
            elapsedMs += dt * 1000f
            val raw = (elapsedMs / DURATION_MS).coerceIn(0f, 1f)
            val t = interpolator.getInterpolation(raw)

            // Scale grows from icon-size fraction → 1.0
            val scale = startScale + (1f - startScale) * t
            val radius = startRadius * (1f - t)            // 15dp → 0

            // Surface centre travels from icon centre → display centre.
            val cx = startCx + (displayBounds.centerX() - startCx) * t
            val cy = startCy + (displayBounds.centerY() - startCy) * t
            val tx = cx - displayBounds.width() * scale / 2f
            val ty = cy - displayBounds.height() * scale / 2f

            SurfaceControl.Transaction().apply {
                setMatrix(leash, scale, 0f, 0f, scale)
                setPosition(leash, tx, ty)
                setCornerRadius(leash, radius)
                setAlpha(leash, (0.4f + 0.6f * t).coerceIn(0f, 1f))
                apply()
            }

            if (raw >= 1f) { onEnd(); false } else true
        }.also { it.start() }
    }

    /** Reverse: full-screen app collapses back into its icon (close-to-icon). */
    fun animateClose(
        leash: SurfaceControl,
        iconBounds: Rect,
        displayBounds: Rect,
        onEnd: () -> Unit
    ) {
        elapsedMs = 0f
        val endCx = iconBounds.centerX().toFloat()
        val endCy = iconBounds.centerY().toFloat()
        val endScale = iconBounds.width().toFloat() / displayBounds.width().toFloat()

        driver?.stop()
        driver = FrameDriver { dt ->
            elapsedMs += dt * 1000f
            val raw = (elapsedMs / DURATION_MS).coerceIn(0f, 1f)
            val t = interpolator.getInterpolation(raw)

            val scale = 1f + (endScale - 1f) * t
            val radius = startRadius * t
            val cx = displayBounds.centerX() + (endCx - displayBounds.centerX()) * t
            val cy = displayBounds.centerY() + (endCy - displayBounds.centerY()) * t
            val tx = cx - displayBounds.width() * scale / 2f
            val ty = cy - displayBounds.height() * scale / 2f

            SurfaceControl.Transaction().apply {
                setMatrix(leash, scale, 0f, 0f, scale)
                setPosition(leash, tx, ty)
                setCornerRadius(leash, radius)
                setAlpha(leash, (1f - t * 0.6f).coerceIn(0f, 1f))
                apply()
            }
            if (raw >= 1f) { onEnd(); false } else true
        }.also { it.start() }
    }

    fun cancel() { driver?.stop(); driver = null }

    companion object {
        const val DURATION_MS = 400f
        const val ICON_RADIUS_DP = 15f
    }
}
