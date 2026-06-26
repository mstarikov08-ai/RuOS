package com.android.systemui.ruos

import android.content.Context
import android.graphics.Rect
import android.view.SurfaceControl

/**
 * The app-switcher (recents) gesture: swipe up and hold so the running apps rise as
 * cards that follow the finger, then spread horizontally near the top of the screen.
 * Release settles them with a spring; swiping a card up flicks it away (kill) with
 * velocity.
 *
 * Operates on the FULL set of recents leashes (foreground first), unlike the home
 * animator which only touches the foreground app. Each card is an independent
 * [CardState] with its own spring so they settle with a slight stagger — the detail
 * that makes the rack feel like physical cards rather than a rigid row.
 *
 * Spring: stiffness 400, damping 0.75 (per spec).
 */
class AppSwitcherController(private val context: Context) {

    private val density = context.resources.displayMetrics.density
    private val cardRadius = 24f * density

    private val displayBounds = Rect()
    private val cards = mutableListOf<CardState>()
    private var driver: FrameDriver? = null

    var onCardKilled: ((taskId: Int) -> Unit)? = null
    var onSettledIntoSwitcher: (() -> Unit)? = null

    private inner class CardState(
        val taskId: Int,
        val leash: SurfaceControl,
        val homeBounds: Rect,
        val index: Int,
        val count: Int
    ) {
        // Springs per transformable property.
        val scaleSpring = SurfaceSpring().also { it.snapTo(1f) }
        val txSpring = SurfaceSpring().also { it.snapTo(0f) }
        val tySpring = SurfaceSpring().also { it.snapTo(0f) }
        var killing = false
        var dead = false

        // Target rack layout: cards shrunk to ~0.78 and fanned horizontally.
        fun settleTargets() {
            val targetScale = 0.78f
            scaleSpring.setTarget(targetScale)
            // Spread: centre card at 0, neighbours fanned out by 62% of screen width.
            val centreIndex = (count - 1) / 2f
            txSpring.setTarget((index - centreIndex) * (displayBounds.width() * 0.62f))
            // Rise so the rack sits in the vertical centre.
            tySpring.setTarget(-displayBounds.height() * 0.06f)
        }

        fun apply(tx: SurfaceControl.Transaction, dt: Float) {
            if (dead) return
            val s = scaleSpring.step(dt)
            val x = txSpring.step(dt)
            val y = tySpring.step(dt)
            tx.setMatrix(leash, s, 0f, 0f, s)
            tx.setPosition(leash,
                (homeBounds.left + (homeBounds.width() * (1 - s) / 2f)) + x,
                (homeBounds.top + (homeBounds.height() * (1 - s) / 2f)) + y)
            tx.setCornerRadius(leash, cardRadius)
            if (killing) {
                val a = (1f - (-y / (displayBounds.height() * 0.6f))).coerceIn(0f, 1f)
                tx.setAlpha(leash, a)
                if (a <= 0.02f) { dead = true; onCardKilled?.invoke(taskId) }
            }
        }

        fun running() = !dead &&
            (scaleSpring.isRunning() || txSpring.isRunning() || tySpring.isRunning())
    }

    fun attach(leashes: List<RecentsLeashProvider.RemoteLeash>, display: Rect) {
        displayBounds.set(display)
        cards.clear()
        leashes.forEachIndexed { i, rl ->
            cards.add(CardState(rl.taskId, rl.leash, rl.startBounds, i, leashes.size))
        }
    }

    fun detach() { driver?.stop(); driver = null; cards.clear() }

    /**
     * Finger-driven rise. [rise] in [0,1] = how far up from the bottom the finger is;
     * cards lift and begin to shrink 1:1 with the finger before the spring settle.
     */
    fun onDragRise(rise: Float) {
        val r = rise.coerceIn(0f, 1f)
        val scale = 1f - r * (1f - 0.82f)
        val ty = -r * displayBounds.height() * 0.10f
        val tx = SurfaceControl.Transaction()
        for (c in cards) {
            if (c.dead) continue
            c.scaleSpring.snapTo(scale)
            c.tySpring.snapTo(ty)
            c.apply(tx, 0f)
        }
        tx.apply()
    }

    /** Finger lifted while in the switcher gesture → settle into the rack. */
    fun settleIntoSwitcher() {
        for (c in cards) c.settleTargets()
        runDriver { onSettledIntoSwitcher?.invoke() }
    }

    /**
     * Swipe a specific card up to kill it. [velocityY] is the fling velocity
     * (negative = up); the card carries off-screen at that speed then dies.
     */
    fun flingCardToKill(taskId: Int, velocityY: Float) {
        val c = cards.firstOrNull { it.taskId == taskId } ?: return
        c.killing = true
        c.tySpring.reconfigure(SurfaceSpring.STIFFNESS_DEFAULT, 1.0f) // no overshoot off-screen
        c.tySpring.setStartVelocity(velocityY / displayBounds.height())
        c.tySpring.setTarget(-displayBounds.height().toFloat() * 1.2f)
        runDriver(null)
    }

    private fun runDriver(onAllRested: (() -> Unit)?) {
        driver?.stop()
        driver = FrameDriver { dt ->
            val tx = SurfaceControl.Transaction()
            var anyRunning = false
            for (c in cards) {
                c.apply(tx, dt)
                if (c.running()) anyRunning = true
            }
            tx.apply()
            if (!anyRunning) { onAllRested?.invoke(); false } else true
        }.also { it.start() }
    }
}
