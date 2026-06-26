package com.android.systemui.ruos

import android.app.ActivityManager
import android.content.Intent
import android.graphics.Rect
import android.view.SurfaceControl

/**
 * ──────────────────────────────────────────────────────────────────────────────
 *  THE INTEGRATION SEAM
 * ──────────────────────────────────────────────────────────────────────────────
 *
 * Everything in RuOS's gesture engine drives a [SurfaceControl] "leash" — a handle
 * to a running app's surface that the render thread can transform (position,
 * scale, corner radius, alpha) with zero UI-thread involvement.
 *
 * There is NO public/hidden API that hands you an arbitrary foreground app's
 * SurfaceControl on demand. WindowManager does not expose one. The ONLY legitimate
 * way to obtain a transformable leash for a running app is the **recents
 * animation**: you ask the system to start a recents transition, and in return it
 * hands you one [RemoteLeash] per visible task, each wrapping a real leash whose
 * lifetime is owned by the recents controller until you call [finish].
 *
 * This interface is the single place where RuOS touches that version-fragile API.
 * The concrete implementation ([SharedRecentsLeashProvider]) uses SystemUI's
 * shared recents compat layer (com.android.systemui.shared.system.*), which is the
 * same surface Launcher3 Quickstep uses and is far more stable across AOSP point
 * releases than calling IActivityTaskManager.startRecentsActivity() directly.
 *
 * If the target AOSP tree's recents API differs, THIS is the only file that needs
 * to be reconciled — the animators and gesture controller above it are pure math.
 */
interface RecentsLeashProvider {

    /** A single running task's transformable surface. */
    data class RemoteLeash(
        val taskId: Int,
        val leash: SurfaceControl,
        /** The task's on-screen bounds at the moment the animation started. */
        val startBounds: Rect,
        /** True for the task that was in the foreground when the gesture began. */
        val isForeground: Boolean,
        val taskInfo: ActivityManager.RunningTaskInfo?
    )

    /** Callbacks delivered on the render/animation thread. */
    interface Callbacks {
        /**
         * The system has parented every visible task under leashes we control.
         * From this instant until [finish], we own their transforms.
         *
         * @param apps      one leash per visible task, foreground first
         * @param wallpaper the wallpaper leash (may be null), so we can blur/reveal it
         * @param homeContentInsets insets to lay the home screen out under the apps
         */
        fun onRecentsStarted(
            apps: List<RemoteLeash>,
            wallpaper: SurfaceControl?,
            homeContentInsets: Rect
        )

        /** The system cancelled the transition (e.g. another app took over). */
        fun onRecentsCancelled()
    }

    /**
     * Begin a recents transition. Non-blocking; [Callbacks.onRecentsStarted] fires
     * when the system has handed over the leashes (typically within one frame).
     *
     * Call this the moment the user's finger enters the home/switcher gesture, so
     * the leashes are ready before the drag produces meaningful displacement.
     */
    fun startRecents(homeIntent: Intent, callbacks: Callbacks)

    /**
     * End the transition.
     *
     * @param toHome true  → the foreground task is released at its shrunken state and
     *                       the home activity becomes resumed (swipe-to-home committed)
     *               false → the foreground task is restored to fullscreen
     *                       (gesture cancelled, or returning into an app)
     * @param onFinished invoked once WindowManager has fully reparented surfaces back.
     */
    fun finish(toHome: Boolean, onFinished: (() -> Unit)? = null)

    /** True between [startRecents] handover and [finish]. */
    val isActive: Boolean
}
