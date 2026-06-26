package com.ruos.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import com.ruos.launcher.widget.WeatherWidgetView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Root home-screen container.
 *
 * Fixed layers (bottom → top z-order):
 *   1. Deep-space gradient background (drawn by GradientDrawable)
 *   2. Clock + date (top-left, over wallpaper)
 *   3. Weather widget (glass card, below clock)
 *   4. HomePagePager (fills space between header and dock)
 *   5. PageDotIndicator (above dock)
 *   6. DockView (pinned bottom)
 */
class HomeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val pagePager = HomePagePager(context)
    private val dockView = DockView(context)
    private val dotIndicator = PageDotIndicator(context)
    private val weatherWidget = WeatherWidgetView(context)
    private val clockLabel = TextView(context)
    private val dateLabel = TextView(context)

    private var isJiggleMode = false

    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockTick = object : Runnable {
        override fun run() {
            updateClock()
            clockHandler.postDelayed(this, 30_000L)
        }
    }

    init {
        val d = resources.displayMetrics.density
        val statusH = statusBarHeight()

        // ── Background ───────────────────────────────────────────────────────
        background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                Color.parseColor("#060A18"),   // near-black navy
                Color.parseColor("#0C1D45"),   // deep space blue
                Color.parseColor("#090E20")    // dark night
            )
        )
        setWillNotDraw(false)

        // ── Clock ────────────────────────────────────────────────────────────
        clockLabel.apply {
            setTextColor(Color.WHITE)
            textSize = 58f
            setTypeface(android.graphics.Typeface.create("sans-serif-thin", android.graphics.Typeface.NORMAL))
            setShadowLayer(12f, 0f, 3f, Color.argb(90, 0, 0, 0))
            gravity = Gravity.CENTER
        }
        addView(clockLabel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).also {
            it.topMargin = statusH + dp(d, 20)
        })

        dateLabel.apply {
            setTextColor(Color.argb(190, 255, 255, 255))
            textSize = 17f
            gravity = Gravity.CENTER
        }
        addView(dateLabel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).also {
            it.topMargin = statusH + dp(d, 92)
        })

        // ── Weather widget ───────────────────────────────────────────────────
        val weatherTop = statusH + dp(d, 130)
        val weatherH   = dp(d, 110)
        val hPad       = dp(d, 16)
        addView(weatherWidget, LayoutParams(LayoutParams.MATCH_PARENT, weatherH).also {
            it.topMargin  = weatherTop
            it.marginStart = hPad
            it.marginEnd   = hPad
        })

        // ── App pager ────────────────────────────────────────────────────────
        val dockH        = resources.getDimensionPixelSize(R.dimen.dock_height)
        val dockBottomM  = resources.getDimensionPixelSize(R.dimen.dock_bottom_margin)
        val pagerTop     = weatherTop + weatherH + dp(d, 14)
        val pagerBottom  = dockH + dockBottomM + dp(d, 36)

        addView(pagePager, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).also {
            it.topMargin    = pagerTop
            it.bottomMargin = pagerBottom
        })

        // ── Dot indicator ────────────────────────────────────────────────────
        addView(dotIndicator, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).also {
            it.gravity     = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            it.bottomMargin = dockH + dockBottomM + dp(d, 6)
        })

        // ── Dock ─────────────────────────────────────────────────────────────
        addView(dockView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).also {
            it.gravity = Gravity.BOTTOM
        })

        // ── Callbacks ─────────────────────────────────────────────────────────
        pagePager.onPageChanged = { index -> dotIndicator.setCurrentPage(index) }
        pagePager.onPageCountChanged = { count -> dotIndicator.setPageCount(count) }
        pagePager.onJiggleModeToggle = { active ->
            isJiggleMode = active
            dockView.setJiggleMode(active)
        }
        pagePager.onPinchOverview = { openAppSwitcher() }

        updateClock()
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    fun onResume() {
        pagePager.refreshApps()
        dockView.refreshApps()
        clockHandler.removeCallbacks(clockTick)
        clockHandler.post(clockTick)
    }

    fun onPause() {
        clockHandler.removeCallbacks(clockTick)
        if (isJiggleMode) exitJiggleMode()
    }

    fun returnHome() {
        exitJiggleMode()
        pagePager.snapToPage(0, animated = true)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun updateClock() {
        val now = Date()
        val ruLocale = Locale("ru")
        clockLabel.text = SimpleDateFormat("HH:mm", ruLocale).format(now)
        dateLabel.text  = SimpleDateFormat("EEEE, d MMMM", ruLocale).format(now)
    }

    private fun exitJiggleMode() {
        isJiggleMode = false
        pagePager.setJiggleMode(false)
        dockView.setJiggleMode(false)
    }

    private fun openAppSwitcher() {
        val intent = android.content.Intent(
            context, com.ruos.launcher.switcher.AppSwitcherActivity::class.java
        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun statusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else dp(resources.displayMetrics.density, 24)
    }

    private fun dp(d: Float, dp: Int) = (dp * d).toInt()
}
