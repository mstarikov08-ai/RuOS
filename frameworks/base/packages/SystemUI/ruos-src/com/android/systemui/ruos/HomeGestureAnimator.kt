package com.android.systemui.ruos

import android.content.Context
import android.graphics.Rect
import android.view.SurfaceControl
import android.view.SurfaceControl.Transaction
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

/**
 * Drives the SurfaceControl layer of the foreground app during the home swipe gesture.
 *
 * On each frame (called from the render thread via Choreographer):
 *   - Scale the app layer from 1.0 to 0.85 proportionally to gesture progress
 *   - Translate Y upward to give the "lifting off" effect
 *   - Animate corner radius from device natural radius to 28dp
 *   - When dismissed: fling the layer up matching exactly the finger release velocity
 *   - When cancelled: spring back to 1.0 scale, 0 translation
 *
 * The layer is the top SurfaceControl in the WindowManager hierarchy.
 */
class HomeGestureAnimator(private val context: Context) {

    private val density = context.resources.displayMetrics.density
    private val cornerRadiusTarget = 28f * density   // 28dp

    // The foreground app's SurfaceControl — obtained from WindowManager
    private var appLayer: SurfaceControl? = null
    private var displayBounds = Rect()

    // Spring animations for cancel path
    private var scaleSpring: SpringAnimation? = null
    private var translationSpring: SpringAnimation? = null

    fun setAppLayer(layer: SurfaceControl, bounds: Rect) {
        appLayer = layer
        displayBounds = bounds
    }

    /**
     * Called every frame during the drag. progress = [0..1].
     * Must be called on the render thread.
     */
    fun onProgress(progress: Float, velocityY: Float) {
        val layer = appLayer ?: return
        val scale = 1f - (progress * (1f - APP_MIN_SCALE))
        val ty = -progress * displayBounds.height() * 0.08f   // subtle upward drift

        Transaction().apply {
            setMatrix(layer,
                scale, 0f, 0f, scale,
                displayBounds.centerX() * (1 - scale),
                ty + displayBounds.centerY() * (1 - scale)
            )
            setCornerRadius(layer, progress * cornerRadiusTarget)
            apply()
        }
    }

    /**
     * Dismiss: fling the app layer off screen upward at the finger's velocity.
     */
    fun onDismiss(velocityY: Float) {
        val layer = appLayer ?: return
        val startY = 0f
        val targetY = -displayBounds.height().toFloat() * 1.2f
        val duration = computeFlingDuration(velocityY, startY, targetY)

        val animator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            interpolator = android.view.animation.AccelerateInterpolator(1.5f)
            addUpdateListener { va ->
                val t = va.animatedFraction
                val ty = startY + t * (targetY - startY)
                val scale = (APP_MIN_SCALE + (1f - APP_MIN_SCALE) * (1f - t)).coerceAtLeast(0.5f)
                Transaction().apply {
                    setMatrix(layer,
                        scale, 0f, 0f, scale,
                        displayBounds.centerX() * (1 - scale),
                        ty
                    )
                    setAlpha(layer, (1f - t * 0.5f).coerceAtLeast(0f))
                    apply()
                }
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    // Layer is now off-screen; WindowManager will handle the actual finish
                    Transaction().apply {
                        setAlpha(layer, 0f)
                        apply()
                    }
                }
            })
        }
        animator.start()
    }

    /**
     * Cancel: spring the app layer back to its natural position.
     */
    fun onCancel() {
        val layer = appLayer ?: return
        // Simple spring back to scale 1, translate 0, radius 0
        var progress = 1f
        val animator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 380
            interpolator = android.view.animation.DecelerateInterpolator(2f)
            addUpdateListener { va ->
                val t = va.animatedFraction
                val scale = APP_MIN_SCALE + t * (1f - APP_MIN_SCALE)
                val ty = -(1f - t) * displayBounds.height() * 0.08f
                Transaction().apply {
                    setMatrix(layer,
                        scale, 0f, 0f, scale,
                        displayBounds.centerX() * (1 - scale),
                        ty
                    )
                    setCornerRadius(layer, (1f - t) * cornerRadiusTarget)
                    apply()
                }
            }
        }
        animator.start()
    }

    private fun computeFlingDuration(velocityY: Float, start: Float, end: Float): Long {
        // Match iOS: fast flings are near-instant (120ms), slow flings 350ms
        val distance = Math.abs(end - start)
        val absVel = Math.abs(velocityY).coerceAtLeast(200f)
        return (distance / absVel * 1000).toLong().coerceIn(120, 350)
    }

    companion object {
        private const val APP_MIN_SCALE = 0.85f
    }
}
