package com.ruos.notify.banner

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.ruos.notify.model.NotifItem

/**
 * Owns the heads-up banner overlay. A single host window sits under the status bar
 * and holds a stack of [BannerView]s: the newest is fully shown in front; older ones
 * peek behind, pushed down and scaled like iOS. Each banner slides in from above,
 * auto-dismisses after 4s (unless persistent), and the window blurs what's behind it.
 */
class BannerManager(private val context: Context) {

    private val wm = context.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val d = context.resources.displayMetrics.density

    private var host: FrameLayout? = null
    private val banners = ArrayList<BannerView>()
    private val timeouts = HashMap<String, Runnable>()

    fun show(item: NotifItem, persistent: Boolean) = addBanner(BannerView(context, item), persistent)

    fun showSummary(item: NotifItem, count: Int) =
        addBanner(BannerView(context, item, summaryCount = count), persistent = false)

    private fun addBanner(banner: BannerView, persistent: Boolean) {
        ensureHost()
        val host = host ?: return

        // Replace any existing banner for the same key.
        dismissKey(banner.item.key, animate = false)

        banner.onDismiss = { remove(banner) }
        banner.onOpen = {
            runCatching { banner.item.contentIntent?.send() }
            remove(banner)
        }

        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP
        ).also {
            it.leftMargin = dp(8); it.rightMargin = dp(8); it.topMargin = dp(8)
        }
        host.addView(banner, lp)
        banners.add(0, banner)            // newest in front
        restack()

        // Slide in from above.
        banner.post {
            banner.translationY = -banner.height.toFloat() - dp(20)
            SpringAnimation(banner, SpringAnimation.TRANSLATION_Y, restingY(0)).apply {
                spring.stiffness = SpringForce.STIFFNESS_MEDIUM
                spring.dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
                start()
            }
        }

        if (!persistent) {
            val r = Runnable { remove(banner) }
            timeouts[banner.item.key] = r
            main.postDelayed(r, DISPLAY_MS)
        }
        trimToMax()
    }

    /** Lay out the stack: front banner flat, older ones nudged down + scaled behind. */
    private fun restack() {
        banners.forEachIndexed { depth, b ->
            b.elevation = dp(8).toFloat() - depth
            val scale = 1f - 0.04f * depth.coerceAtMost(MAX_VISIBLE)
            b.animate().scaleX(scale).scaleY(scale).setDuration(180).start()
            if (b.translationY == 0f || depth > 0) {
                SpringAnimation(b, SpringAnimation.TRANSLATION_Y, restingY(depth)).apply {
                    spring.stiffness = SpringForce.STIFFNESS_MEDIUM
                    spring.dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
                    start()
                }
            }
        }
    }

    private fun restingY(depth: Int): Float = (depth.coerceAtMost(MAX_VISIBLE) * dp(10)).toFloat()

    private fun trimToMax() {
        while (banners.size > MAX_VISIBLE + 1) remove(banners.last())
    }

    fun dismissKey(key: String, animate: Boolean = true) {
        val b = banners.firstOrNull { it.item.key == key } ?: return
        if (animate) remove(b) else removeNow(b)
    }

    private fun remove(banner: BannerView) {
        timeouts.remove(banner.item.key)?.let { main.removeCallbacks(it) }
        SpringAnimation(banner, SpringAnimation.TRANSLATION_Y, -banner.height.toFloat() - dp(20)).apply {
            spring.stiffness = SpringForce.STIFFNESS_MEDIUM
            spring.dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
            addEndListener { _, _, _, _ -> removeNow(banner) }
            start()
        }
    }

    private fun removeNow(banner: BannerView) {
        banner.release()
        host?.removeView(banner)
        banners.remove(banner)
        restack()
        if (banners.isEmpty()) teardownHost()
    }

    private fun ensureHost() {
        if (host != null) return
        val h = FrameLayout(context)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            dp(360),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            // Frosted glass: blur whatever is behind the banners.
            if (wm.isCrossWindowBlurEnabled) {
                flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                blurBehindRadius = dp(30)
            }
        }
        runCatching { wm.addView(h, lp); host = h }
    }

    private fun teardownHost() {
        host?.let { runCatching { wm.removeView(it) } }
        host = null
    }

    private fun dp(v: Int) = (v * d).toInt()

    companion object {
        private const val DISPLAY_MS = 4000L      // iOS banner dwell
        private const val MAX_VISIBLE = 3
    }
}
