package com.ruos.launcher.recents

import android.app.ActivityOptions
import android.content.Context
import android.graphics.Rect
import android.util.Log
import android.view.Choreographer
import android.view.RemoteAnimationAdapter
import android.view.RemoteAnimationTarget
import android.view.SurfaceControl
import android.view.animation.Interpolator
import android.view.animation.PathInterpolator

/**
 * iOS app-OPEN transition, launcher side. The tapped icon's rect grows into the
 * full-screen app: scale from icon-fraction → 1.0, corner radius 15dp → 0, over
 * 400 ms on cubic-bezier(0.32, 0.72, 0, 1) — the iOS open curve.
 *
 * This mirrors SystemUI's AppLaunchAnimator math but runs in the launcher process
 * via a RemoteAnimation so it owns the opening app's leash directly. (Kept as a
 * separate copy rather than a shared lib because the two run in different modules.)
 *
 * [device] confidence: RemoteAnimationAdapter / IRemoteAnimationRunner / the
 * RemoteAnimationTarget fields are @hide and can drift across AOSP 14 point
 * releases; reconcile here if the first on-device build complains. makeLaunchOptions
 * returns null on any failure so callers fall back to a plain launch.
 */
class AppOpenAnimationRunner(
    private val context: Context,
    private val iconBounds: Rect
) : android.view.IRemoteAnimationRunner.Stub() {

    private val density = context.resources.displayMetrics.density
    private val startRadius = ICON_RADIUS_DP * density
    private val interpolator: Interpolator = PathInterpolator(0.32f, 0.72f, 0f, 1f)

    private var choreographer: Choreographer? = null
    private var startNanos = 0L

    override fun onAnimationStart(
        transit: Int,
        apps: Array<out RemoteAnimationTarget>?,
        wallpapers: Array<out RemoteAnimationTarget>?,
        nonApps: Array<out RemoteAnimationTarget>?,
        finishedCallback: android.view.IRemoteAnimationFinishedCallback?
    ) {
        val opening = apps?.firstOrNull { it.mode == RemoteAnimationTarget.MODE_OPENING }
            ?: apps?.firstOrNull()
        if (opening == null) { safeFinish(finishedCallback); return }

        val leash = opening.leash
        val display = opening.screenSpaceBounds ?: Rect(
            0, 0, context.resources.displayMetrics.widthPixels,
            context.resources.displayMetrics.heightPixels
        )

        val startCx = iconBounds.centerX().toFloat()
        val startCy = iconBounds.centerY().toFloat()
        val startScale = (iconBounds.width().toFloat() / display.width().toFloat())
            .coerceIn(0.05f, 1f)

        startNanos = 0L
        val cho = Choreographer.getInstance()
        choreographer = cho
        val cb = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (startNanos == 0L) startNanos = frameTimeNanos
                val raw = ((frameTimeNanos - startNanos) / 1_000_000f / DURATION_MS).coerceIn(0f, 1f)
                val t = interpolator.getInterpolation(raw)

                val scale = startScale + (1f - startScale) * t
                val radius = startRadius * (1f - t)
                val cx = startCx + (display.centerX() - startCx) * t
                val cy = startCy + (display.centerY() - startCy) * t
                val tx = cx - display.width() * scale / 2f
                val ty = cy - display.height() * scale / 2f

                try {
                    SurfaceControl.Transaction().apply {
                        setMatrix(leash, scale, 0f, 0f, scale)
                        setPosition(leash, tx, ty)
                        setCornerRadius(leash, radius)
                        setAlpha(leash, (0.4f + 0.6f * t).coerceIn(0f, 1f))
                        apply()
                    }
                } catch (t2: Throwable) {
                    Log.w(TAG, "transaction failed", t2); safeFinish(finishedCallback); return
                }

                if (raw >= 1f) safeFinish(finishedCallback)
                else cho.postFrameCallback(this)
            }
        }
        cho.postFrameCallback(cb)
    }

    override fun onAnimationCancelled(isKeyguardOccluded: Boolean) {
        // Nothing to clean up; WindowManager reparents the leash on cancel.
    }

    private fun safeFinish(cb: android.view.IRemoteAnimationFinishedCallback?) {
        try { cb?.onAnimationFinished() } catch (_: Throwable) {}
    }

    companion object {
        private const val TAG = "RuOSAppOpen"
        const val DURATION_MS = 400f
        const val ICON_RADIUS_DP = 15f

        /**
         * Build the ActivityOptions that hand the open transition to this runner.
         * Returns null if RemoteAnimation isn't available so the caller can fall
         * back to a plain startActivity.
         */
        fun makeLaunchOptions(context: Context, iconBounds: Rect): ActivityOptions? {
            return try {
                val runner = AppOpenAnimationRunner(context, iconBounds)
                val adapter = RemoteAnimationAdapter(runner, DURATION_MS.toLong(), 0L)
                ActivityOptions.makeRemoteAnimation(adapter)
            } catch (t: Throwable) {
                Log.w(TAG, "makeRemoteAnimation unavailable; falling back to plain launch", t)
                null
            }
        }
    }
}
