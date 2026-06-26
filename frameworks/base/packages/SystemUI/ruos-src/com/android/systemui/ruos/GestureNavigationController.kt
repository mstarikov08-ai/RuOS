package com.android.systemui.ruos

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.window.BackEvent
import kotlin.math.abs

/**
 * RuOS gesture navigation orchestrator.
 *
 * Owns the recents leash provider and every animator, parses raw touch from
 * [RuOSGestureInputMonitor], and routes each gesture to the right surface driver:
 *
 *   swipe up from bottom .......... [HomeGestureAnimator] + [HomeRevealController]
 *   swipe up + hold ............... [AppSwitcherController]
 *   swipe up + sideways ........... between-app scrub (foreground leash slides X)
 *   swipe in from side edge ....... [BackGestureAnimator]
 *
 * The leashes for all of these come from ONE recents handover ([RecentsLeashProvider]),
 * started the instant a bottom-edge touch begins so surfaces are ready before the
 * drag produces displacement.
 *
 * Decision thresholds (per spec):
 *   home commit  : progress > 0.40  OR  upward fling > FLING_VELOCITY_HOME
 *   back commit  : progress > 0.50  OR  |Vx| > FLING_VELOCITY_BACK
 *   switcher     : held in bottom zone > SWITCHER_HOLD_MS with low travel
 */
class GestureNavigationController(
    private val context: Context,
    private val leashProvider: RecentsLeashProvider =
        SharedRecentsLeashProvider(context)
) : RecentsLeashProvider.Callbacks {

    private val density = context.resources.displayMetrics.density
    private val viewConfig = ViewConfiguration.get(context)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val homeAnimator = HomeGestureAnimator(context)
    private val homeReveal = HomeRevealController(context)
    private val switcher = AppSwitcherController(context)
    private val backAnimator = BackGestureAnimator(context)

    /** Set by SystemUI so we can ask the launcher where an app's grid slot is. */
    var landingSlotProvider: ((taskId: Int) -> Pair<Float, Float>?)? = null
    /** Forwarded to the launcher to fade its icon grid during the home swipe. */
    var homeIconProgressSink: ((Float) -> Unit)? = null

    // ── touch state ───────────────────────────────────────────────────────────
    private var velocityTracker: VelocityTracker? = null
    private var state = State.IDLE
    private var downX = 0f
    private var downY = 0f
    private var progress = 0f
    private var foregroundLeash: RecentsLeashProvider.RemoteLeash? = null
    private val display = Rect()

    private var switcherArmed = false
    private val switcherRunnable = Runnable {
        if (state == State.HOME_DRAG && progress < SWITCHER_MAX_TRAVEL) {
            state = State.SWITCHER
        }
    }

    enum class State { IDLE, HOME_DRAG, SWITCHER, BETWEEN_APP, BACK_DRAG, COMMITTED }

    init {
        homeAnimator.progressListener = { p -> homeReveal.onProgress(p) }
        homeReveal.homeProgressSink = { p -> homeIconProgressSink?.invoke(p) }
        homeAnimator.onSettled = { toHome -> finishHome(toHome) }
        backAnimator.onSettled = { committed -> leashProvider.finish(toHome = false) }
        switcher.onSettledIntoSwitcher = { /* hand off to RuOS recents UI */ }
        switcher.onCardKilled = { taskId -> Log.d(TAG, "killed task $taskId") }
    }

    // ── raw touch entry point (from InputMonitor, off the UI thread) ───────────
    fun onMotionEvent(event: MotionEvent, displayHeight: Int, displayWidth: Int): Boolean {
        display.set(0, 0, displayWidth, displayHeight)
        val homeZone = (HOME_SWIPE_ZONE_DP * density)
        val backZone = (BACK_SWIPE_ZONE_DP * density)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
                downX = event.x; downY = event.y; progress = 0f

                state = when {
                    event.y > displayHeight - homeZone -> State.HOME_DRAG
                    event.x < backZone || event.x > displayWidth - backZone -> State.BACK_DRAG
                    else -> State.IDLE
                }

                if (state == State.HOME_DRAG) {
                    // Start the recents handover NOW so leashes are ready.
                    startRecents()
                    switcherArmed = true
                    mainHandler.postDelayed(switcherRunnable, SWITCHER_HOLD_MS)
                } else if (state == State.BACK_DRAG) {
                    startRecents()  // back also needs current+previous leashes
                }
                return state != State.IDLE
            }

            MotionEvent.ACTION_MOVE -> {
                val vt = velocityTracker ?: return false
                vt.addMovement(event)
                val dy = event.y - downY
                val dx = event.x - downX

                when (state) {
                    State.HOME_DRAG -> {
                        if (dy > 0) return false   // dragging back down — ignore
                        // Detect a sideways scrub: large horizontal, small vertical.
                        if (abs(dx) > viewConfig.scaledTouchSlop * 2 &&
                            abs(dx) > abs(dy) * 1.4f && progress < SWITCHER_MAX_TRAVEL) {
                            state = State.BETWEEN_APP
                            mainHandler.removeCallbacks(switcherRunnable)
                            return true
                        }
                        progress = (-dy / displayHeight).coerceIn(0f, 1f)
                        homeAnimator.onDrag(progress)   // 1:1 finger tracking
                    }
                    State.SWITCHER -> {
                        val rise = (-dy / displayHeight).coerceIn(0f, 1f)
                        switcher.onDragRise(rise)
                    }
                    State.BETWEEN_APP -> driveScrub(dx)
                    State.BACK_DRAG -> {
                        progress = (abs(dx) / (displayWidth * 0.5f)).coerceIn(0f, 1f)
                        backAnimator.onDrag(progress)
                    }
                    else -> {}
                }
            }

            MotionEvent.ACTION_UP -> {
                mainHandler.removeCallbacks(switcherRunnable)
                val vt = velocityTracker
                vt?.addMovement(event); vt?.computeCurrentVelocity(1000)
                val vy = vt?.yVelocity ?: 0f
                val vx = vt?.xVelocity ?: 0f
                velocityTracker?.recycle(); velocityTracker = null

                when (state) {
                    State.HOME_DRAG -> {
                        val commit = progress > HOME_COMMIT_THRESHOLD || vy < -FLING_VELOCITY_HOME
                        state = State.COMMITTED
                        homeAnimator.settle(progress, vy, commit)
                    }
                    State.SWITCHER -> switcher.settleIntoSwitcher()
                    State.BETWEEN_APP -> settleScrub(vx)
                    State.BACK_DRAG -> {
                        val commit = progress > BACK_COMMIT_THRESHOLD || abs(vx) > FLING_VELOCITY_BACK
                        state = State.COMMITTED
                        backAnimator.settle(progress, vx, commit)
                    }
                    else -> state = State.IDLE
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                mainHandler.removeCallbacks(switcherRunnable)
                velocityTracker?.recycle(); velocityTracker = null
                when (state) {
                    State.HOME_DRAG -> homeAnimator.settle(progress, 0f, commitToHome = false)
                    State.BACK_DRAG -> backAnimator.settle(progress, 0f, commit = false)
                    else -> leashProvider.finish(toHome = false)
                }
                state = State.IDLE
            }
        }
        return state != State.IDLE
    }

    // ── recents handover ───────────────────────────────────────────────────────
    private fun startRecents() {
        if (leashProvider.isActive) return
        val homeIntent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        leashProvider.startRecents(homeIntent, this)
    }

    override fun onRecentsStarted(
        apps: List<RecentsLeashProvider.RemoteLeash>,
        wallpaper: android.view.SurfaceControl?,
        homeContentInsets: Rect
    ) {
        foregroundLeash = apps.firstOrNull { it.isForeground } ?: apps.firstOrNull()
        homeReveal.attach(wallpaper)

        foregroundLeash?.let { fg ->
            val landing = landingSlotProvider?.invoke(fg.taskId)
            homeAnimator.attach(fg.leash, fg.startBounds, landing)
        }
        switcher.attach(apps, display)

        // Back gesture wants current + the task behind it.
        if (state == State.BACK_DRAG) {
            val cur = apps.getOrNull(0)?.leash
            val prev = apps.getOrNull(1)?.leash
            val edge = if (downX < display.width() / 2) BackEvent.EDGE_LEFT else BackEvent.EDGE_RIGHT
            if (cur != null) backAnimator.attach(cur, prev, display, edge)
        }
    }

    override fun onRecentsCancelled() {
        state = State.IDLE
        homeAnimator.detach(); homeReveal.detach(); switcher.detach(); backAnimator.detach()
    }

    private fun finishHome(toHome: Boolean) {
        leashProvider.finish(toHome) {
            homeAnimator.detach(); homeReveal.detach()
            state = State.IDLE
        }
    }

    // ── between-app horizontal scrub ───────────────────────────────────────────
    private fun driveScrub(dx: Float) {
        val fg = foregroundLeash ?: return
        android.view.SurfaceControl.Transaction().apply {
            setPosition(fg.leash, dx, 0f)
            apply()
        }
    }

    private fun settleScrub(vx: Float) {
        // Snap the foreground app back; a full impl would commit to the adjacent
        // task when |dx|+velocity crosses half a screen. Spring it home for now.
        val fg = foregroundLeash ?: run { leashProvider.finish(false); return }
        val spring = SurfaceSpring().also { it.snapTo(currentScrubX(fg)); it.setStartVelocity(vx) }
        spring.setTarget(0f)
        FrameDriver { dt ->
            val x = spring.step(dt)
            android.view.SurfaceControl.Transaction().apply { setPosition(fg.leash, x, 0f); apply() }
            if (!spring.isRunning()) { leashProvider.finish(false); false } else true
        }.start()
    }

    private fun currentScrubX(fg: RecentsLeashProvider.RemoteLeash): Float = 0f // tracked externally in full impl

    companion object {
        private const val TAG = "RuOSGesture"
        private const val HOME_SWIPE_ZONE_DP = 20f
        private const val BACK_SWIPE_ZONE_DP = 24f
        private const val FLING_VELOCITY_HOME = 1200f
        private const val FLING_VELOCITY_BACK = 800f
        private const val SWITCHER_HOLD_MS = 300L
        private const val SWITCHER_MAX_TRAVEL = 0.15f
        private const val HOME_COMMIT_THRESHOLD = 0.40f
        private const val BACK_COMMIT_THRESHOLD = 0.50f
    }
}
