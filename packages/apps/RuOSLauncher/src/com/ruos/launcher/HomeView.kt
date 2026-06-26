package com.ruos.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.VelocityTracker
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.dynamicanimation.animation.FlingAnimation
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

/**
 * Root container for the RuOS home screen.
 *
 * Layout (bottom to top z-order):
 *   wallpaper (handled by WallpaperManager)
 *   page pager (HomePagePager) — horizontal swipe between pages
 *   dock (DockView) — pinned to bottom
 *   widget overlay on page 0
 *   jiggle-mode delete buttons (invisible until long-press)
 */
class HomeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val pagePager = HomePagePager(context)
    private val dockView = DockView(context)
    private val dotIndicator = PageDotIndicator(context)

    private var isJiggleMode = false

    init {
        setWillNotDraw(false)

        addView(pagePager, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(dockView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).also {
            it.gravity = android.view.Gravity.BOTTOM
        })
        addView(dotIndicator, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).also {
            it.gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
            it.bottomMargin = resources.getDimensionPixelSize(R.dimen.dock_height) +
                resources.getDimensionPixelSize(R.dimen.dot_indicator_margin)
        })

        pagePager.onPageChanged = { index ->
            dotIndicator.setCurrentPage(index)
        }

        pagePager.onJiggleModeToggle = { active ->
            isJiggleMode = active
            dockView.setJiggleMode(active)
        }

        pagePager.onPinchOverview = {
            openAppSwitcher()
        }
    }

    fun onResume() {
        pagePager.refreshApps()
        dockView.refreshApps()
    }

    fun onPause() {
        if (isJiggleMode) exitJiggleMode()
    }

    fun returnHome() {
        exitJiggleMode()
        pagePager.snapToPage(0, animated = true)
    }

    private fun exitJiggleMode() {
        isJiggleMode = false
        pagePager.setJiggleMode(false)
        dockView.setJiggleMode(false)
    }

    private fun openAppSwitcher() {
        val ctx = context
        val intent = android.content.Intent(ctx, com.ruos.launcher.switcher.AppSwitcherActivity::class.java)
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
    }
}
