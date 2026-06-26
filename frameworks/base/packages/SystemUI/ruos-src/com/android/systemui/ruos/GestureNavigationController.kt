package com.android.systemui.ruos

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Insets
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.InputDevice
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.WindowManager
import android.window.BackEvent
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.FlingAnimation
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

/**
 * RuOS gesture navigation — drives all system navigation gestures.
 *
 * Swipe up from bottom:
 *   - App follows thumb 1:1 (SurfaceControl transaction on render thread)
 *   - Fast flick (> 1200 px/s) → instant dismissal matching finger speed
 *   - Slow drag → app scales down 1.0→0.85, corner radius 0→28dp
 *   - Swipe + hold 300ms → app switcher
 *
 * Swipe from side edge:
 *   - Interactive back gesture with spring shadow
 *   - Corner radius animates on back target preview
 *
 * All transitions fire via SurfaceControl.Transaction on the render thread.
 */
class GestureNavigationController(private val context: Context) {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val viewConfig = ViewConfiguration.get(context)

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------
    private var velocityTracker: VelocityTracker? = null
    private var gestureState = GestureState.IDLE
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f

    // Home swipe progress [0..1]: 0 = fully in foreground app, 1 = home
    private var homeProgress = 0f

    // Back gesture progress [0..1]
    private var backProgress = 0f

    // App switcher hold timer
    private val switcherHandler = Handler(Looper.getMainLooper())
    private var switcherPending = false

    // Listener for the SurfaceControl layer
    var listener: GestureListener? = null

    // -------------------------------------------------------------------------
    // Constants matching iOS physics
    // -------------------------------------------------------------------------
    companion object {
        private const val HOME_SWIPE_ZONE_DP = 20         // bottom zone height
        private const val BACK_SWIPE_ZONE_DP = 24          // side zone width
        private const val FLING_VELOCITY_HOME = 1200f       // px/s → instant dismiss
        private const val SWITCHER_HOLD_MS = 300L
        private const val APP_MIN_SCALE = 0.85f
        private const val CORNER_RADIUS_MAX_DP = 28f
    }

    enum class GestureState {
        IDLE,
        HOME_DRAG,
        BACK_DRAG,
        SWITCHER_HOLD,
        COMMITTED
    }

    interface GestureListener {
        fun onHomeProgress(progress: Float, velocityY: Float)
        fun onHomeDismiss(velocityY: Float)
        fun onHomeCancel()
        fun onBackProgress(progress: Float, edge: BackEvent.SwipeEdge)
        fun onBackCommit()
        fun onBackCancel()
        fun onAppSwitcherRequested()
    }

    // -------------------------------------------------------------------------
    // Touch dispatcher — called from InputEventReceiver on render thread
    // -------------------------------------------------------------------------
    fun onMotionEvent(event: MotionEvent, displayHeight: Int, displayWidth: Int): Boolean {
        val homeSwopeZone = (HOME_SWIPE_ZONE_DP * context.resources.displayMetrics.density).toInt()
        val backZone = (BACK_SWIPE_ZONE_DP * context.resources.displayMetrics.density).toInt()

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain()
                velocityTracker?.addMovement(event)
                downX = event.x
                downY = event.y
                lastX = event.x
                lastY = event.y
                switcherPending = false

                gestureState = when {
                    event.y > displayHeight - homeSwopeZone -> GestureState.HOME_DRAG
                    event.x < backZone -> GestureState.BACK_DRAG   // left edge
                    event.x > displayWidth - backZone -> GestureState.BACK_DRAG  // right edge
                    else -> GestureState.IDLE
                }

                if (gestureState == GestureState.HOME_DRAG) {
                    switcherHandler.postDelayed({
                        if (gestureState == GestureState.HOME_DRAG) {
                            gestureState = GestureState.SWITCHER_HOLD
                            listener?.onAppSwitcherRequested()
                        }
                    }, SWITCHER_HOLD_MS)
                }

                return gestureState != GestureState.IDLE
            }

            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                val dy = event.y - downY
                val dx = event.x - downX

                when (gestureState) {
                    GestureState.HOME_DRAG -> {
                        if (dy > 0) return false  // user dragged down — ignore
                        val rawProgress = (-dy / displayHeight).coerceIn(0f, 1f)
                        homeProgress = rawProgress
                        velocityTracker?.computeCurrentVelocity(1000)
                        val vy = velocityTracker?.yVelocity ?: 0f
                        listener?.onHomeProgress(homeProgress, vy)
                    }
                    GestureState.BACK_DRAG -> {
                        val rawProgress = (Math.abs(dx) / (displayWidth * 0.4f)).coerceIn(0f, 1f)
                        backProgress = rawProgress
                        val edge = if (downX < displayWidth / 2)
                            BackEvent.SwipeEdge.LEFT else BackEvent.SwipeEdge.RIGHT
                        listener?.onBackProgress(backProgress, edge)
                    }
                    else -> {}
                }
                lastX = event.x
                lastY = event.y
            }

            MotionEvent.ACTION_UP -> {
                switcherHandler.removeCallbacksAndMessages(null)
                velocityTracker?.addMovement(event)
                velocityTracker?.computeCurrentVelocity(1000)
                val vx = velocityTracker?.xVelocity ?: 0f
                val vy = velocityTracker?.yVelocity ?: 0f
                velocityTracker?.recycle()
                velocityTracker = null

                when (gestureState) {
                    GestureState.HOME_DRAG -> {
                        if (vy < -FLING_VELOCITY_HOME || homeProgress > 0.5f) {
                            gestureState = GestureState.COMMITTED
                            listener?.onHomeDismiss(vy)
                        } else {
                            gestureState = GestureState.IDLE
                            listener?.onHomeCancel()
                        }
                        homeProgress = 0f
                    }
                    GestureState.BACK_DRAG -> {
                        if (backProgress > 0.5f || Math.abs(vx) > 600f) {
                            gestureState = GestureState.COMMITTED
                            listener?.onBackCommit()
                        } else {
                            gestureState = GestureState.IDLE
                            listener?.onBackCancel()
                        }
                        backProgress = 0f
                    }
                    else -> gestureState = GestureState.IDLE
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                switcherHandler.removeCallbacksAndMessages(null)
                velocityTracker?.recycle()
                velocityTracker = null
                when (gestureState) {
                    GestureState.HOME_DRAG -> listener?.onHomeCancel()
                    GestureState.BACK_DRAG -> listener?.onBackCancel()
                    else -> {}
                }
                gestureState = GestureState.IDLE
                homeProgress = 0f
                backProgress = 0f
            }
        }
        return gestureState != GestureState.IDLE
    }
}
