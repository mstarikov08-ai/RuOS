package com.android.systemui.ruos

import android.content.Context
import android.view.SurfaceControl

/**
 * Reveals what's *behind* the dismissing app during the home swipe: the wallpaper
 * (de-blurs and brightens) and — via [homeProgressSink] — the launcher's icon grid
 * (fades 0→1 in the launcher process).
 *
 * SystemUI owns the wallpaper leash during the recents animation, so the blur/alpha
 * are applied here directly. The home-screen *icons* live in the RuOSLauncher
 * process; SystemUI cannot transform them, so progress is forwarded through
 * [homeProgressSink], which the launcher's RecentsAnimation home target consumes to
 * drive icon opacity + the subtle "landed" pulse on the target app's icon. Wiring
 * that forward channel is the launcher side of the contract (see RuOSLauncher
 * RecentsHomeTarget) — kept as a simple callback here so this class stays testable.
 */
class HomeRevealController(context: Context) {

    private val density = context.resources.displayMetrics.density
    private val maxBlurPx = (BLUR_MAX_DP * density).toInt()

    private var wallpaper: SurfaceControl? = null

    /** Forwarded to the launcher to drive home-icon opacity (0 hidden → 1 shown). */
    var homeProgressSink: ((progress: Float) -> Unit)? = null

    fun attach(wallpaper: SurfaceControl?) {
        this.wallpaper = wallpaper
    }

    fun detach() { wallpaper = null }

    /**
     * @param progress 0 = app fullscreen (wallpaper hidden/blurred),
     *                 1 = app dismissed (wallpaper sharp + bright, icons visible)
     */
    fun onProgress(progress: Float) {
        val p = progress.coerceIn(0f, 1f)
        wallpaper?.let { wp ->
            val blur = ((1f - p) * maxBlurPx).toInt()      // de-blur as app leaves
            val alpha = (0.3f + 0.7f * p)                  // brighten in
            SurfaceControl.Transaction().apply {
                setBackgroundBlurRadius(wp, blur)
                setAlpha(wp, alpha)
                apply()
            }
        }
        homeProgressSink?.invoke(p)
    }

    companion object {
        private const val BLUR_MAX_DP = 60f
    }
}
