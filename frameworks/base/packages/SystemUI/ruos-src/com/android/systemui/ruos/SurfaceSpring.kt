package com.android.systemui.ruos

import android.view.Choreographer
import kotlin.math.abs

/**
 * A critically-tuned spring integrator for a single scalar driving a SurfaceControl
 * property.
 *
 * androidx's [androidx.dynamicanimation.animation.SpringAnimation] is designed to
 * animate View properties on the UI thread. For surface leashes we instead step the
 * spring ourselves on the [Choreographer] frame callback and apply the result inside
 * one batched [android.view.SurfaceControl.Transaction] per frame, so the whole
 * transform (position + scale + radius + alpha) lands atomically on the render
 * thread. This is the same analytic spring math SpringAnimation uses — closed-form
 * underdamped/overdamped solution — but decoupled from the View system.
 *
 * iOS-matching defaults: stiffness 400, dampingRatio 0.75 (slightly underdamped,
 * giving the tiny overshoot-then-settle that reads as "physical").
 */
class SurfaceSpring(
    private var stiffness: Float = 400f,
    private var dampingRatio: Float = 0.75f
) {
    var value = 0f
        private set
    var velocity = 0f
        private set
    private var target = 0f
    private var running = false

    private val restThreshold = 0.001f
    private val restVelocity = 0.01f

    fun snapTo(v: Float) { value = v; velocity = 0f; target = v; running = false }

    fun setTarget(t: Float) { target = t; running = true }

    fun setStartVelocity(v: Float) { velocity = v; running = true }

    fun isRunning() = running

    /**
     * Advance the spring by [dtSeconds]. Returns the new [value].
     * Uses semi-implicit Euler at a fixed sub-step for stability at high stiffness.
     */
    fun step(dtSeconds: Float): Float {
        if (!running) return value
        val omega = Math.sqrt(stiffness.toDouble()).toFloat()           // natural freq
        val c = 2f * dampingRatio * omega                               // damping coeff
        // Sub-step to stay stable when a frame is long (e.g. first frame after jank)
        var remaining = dtSeconds.coerceAtMost(0.064f)
        val h = 1f / 1000f  // 1ms sub-steps
        while (remaining > 0f) {
            val dt = if (remaining < h) remaining else h
            val x = value - target
            val accel = -stiffness * x - c * velocity
            velocity += accel * dt
            value += velocity * dt
            remaining -= dt
        }
        if (abs(value - target) < restThreshold && abs(velocity) < restVelocity) {
            value = target
            velocity = 0f
            running = false
        }
        return value
    }

    fun reconfigure(stiffness: Float, dampingRatio: Float) {
        this.stiffness = stiffness
        this.dampingRatio = dampingRatio
    }

    companion object {
        const val STIFFNESS_DEFAULT = 400f
        const val DAMPING_DEFAULT = 0.75f
    }
}

/**
 * Drives a per-frame callback from the [Choreographer] until told to stop, passing
 * the wall-clock delta between frames. One instance per active gesture animation.
 */
class FrameDriver(private val onFrame: (dtSeconds: Float) -> Boolean) :
    Choreographer.FrameCallback {

    private val choreographer = Choreographer.getInstance()
    private var lastFrameNanos = 0L
    private var scheduled = false

    fun start() {
        if (scheduled) return
        scheduled = true
        lastFrameNanos = 0L
        choreographer.postFrameCallback(this)
    }

    fun stop() {
        scheduled = false
        choreographer.removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!scheduled) return
        val dt = if (lastFrameNanos == 0L) 1f / 120f
                 else (frameTimeNanos - lastFrameNanos) / 1_000_000_000f
        lastFrameNanos = frameTimeNanos
        val keepGoing = onFrame(dt)
        if (keepGoing && scheduled) {
            choreographer.postFrameCallback(this)
        } else {
            scheduled = false
        }
    }
}
