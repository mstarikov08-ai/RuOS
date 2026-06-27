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
) : FrameLayout(context, attrs), com.ruos.launcher.recents.HomeTargetBridge.Host {

    private val pagePager = HomePagePager(context)
    private val dockView = DockView(context)
    private val dotIndicator = PageDotIndicator(context)
    private val weatherWidget = WeatherWidgetView(context)
    private val clockLabel = TextView(context)
    private val dateLabel = TextView(context)
    private val searchView = SpotlightSearchView(context)
    private val folderView = FolderView(context)
    private val appLibrary = AppLibraryView(context)
    private val todayView = TodayView(context)

    private var isJiggleMode = false

    // Badge state: packages currently showing a badge (so we can clear stale ones).
    private val badgedPackages = mutableSetOf<String>()
    private val badgeReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: android.content.Context?, i: android.content.Intent?) {
            i ?: return
            val pkgs = i.getStringArrayListExtra("packages") ?: arrayListOf()
            val counts = i.getIntArrayExtra("counts") ?: IntArray(0)
            applyBadges(pkgs, counts)
        }
    }

    // Swipe-down-to-search tracking
    private var swipeDownX = 0f
    private var swipeDownY = 0f
    private val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop

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
        pagePager.onRevealAppLibrary = { setContentBlur(true); appLibrary.show() }
        pagePager.onRevealTodayView = { setContentBlur(true); todayView.show() }
        pagePager.onOpenFolder = { fi ->
            fi.boundFolder()?.let { folder ->
                Haptics.confirm(this)
                setContentBlur(true)
                folderView.open(folder, fi.screenBounds())
            }
        }

        // ── Overlays (top z-order) ─────────────────────────────────────────────
        folderView.onTitleChanged = { pagePager.persistLayout() }
        folderView.onDismiss = { setContentBlur(false) }
        searchView.onDismiss = { setContentBlur(false) }
        appLibrary.onDismiss = { setContentBlur(false) }
        todayView.onDismiss = { setContentBlur(false) }
        todayView.onOpenSearch = { setContentBlur(true); searchView.show() }
        addView(folderView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(searchView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(todayView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(appLibrary, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        updateClock()
    }

    /** Real backdrop blur for the overlays: blur the home content behind them. */
    private fun setContentBlur(on: Boolean) {
        val effect = if (on)
            android.graphics.RenderEffect.createBlurEffect(
                40f, 40f, android.graphics.Shader.TileMode.CLAMP)
        else null
        iconLayerViews().forEach { it.setRenderEffect(effect) }
    }

    // ── Swipe down on home → Spotlight search ──────────────────────────────────

    override fun onInterceptTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (isJiggleMode || searchView.isShown() || folderView.isOpen() ||
            appLibrary.isShown2() || todayView.isShown2()) return false
        when (ev.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> { swipeDownX = ev.x; swipeDownY = ev.y }
            android.view.MotionEvent.ACTION_MOVE -> {
                val dx = ev.x - swipeDownX
                val dy = ev.y - swipeDownY
                if (dy > touchSlop * 2 && dy > Math.abs(dx) * 1.5f) {
                    Haptics.light(this)
                    setContentBlur(true)
                    searchView.show()
                    return true
                }
            }
        }
        return false
    }

    /** Returns true if a back press was consumed by an open overlay. */
    fun onBackPressed(): Boolean {
        if (searchView.isShown()) { searchView.hide(); return true }
        if (appLibrary.isShown2()) { appLibrary.hide(); return true }
        if (todayView.isShown2()) { todayView.hide(); return true }
        if (folderView.isOpen()) { folderView.close(); return true }
        if (isJiggleMode) { exitJiggleMode(); return true }
        return false
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    fun onResume() {
        pagePager.refreshApps()
        dockView.refreshApps()
        clockHandler.removeCallbacks(clockTick)
        clockHandler.post(clockTick)
        com.ruos.launcher.recents.HomeTargetBridge.register(this)
        runCatching {
            context.registerReceiver(badgeReceiver,
                android.content.IntentFilter("com.ruos.notify.BADGES"),
                android.content.Context.RECEIVER_EXPORTED)
        }
    }

    fun onPause() {
        clockHandler.removeCallbacks(clockTick)
        if (isJiggleMode) exitJiggleMode()
        com.ruos.launcher.recents.HomeTargetBridge.unregister(this)
        runCatching { context.unregisterReceiver(badgeReceiver) }
    }

    /** Apply unread badges from RuOSNotify to home + dock icons. */
    private fun applyBadges(pkgs: List<String>, counts: IntArray) {
        val incoming = mutableSetOf<String>()
        pkgs.forEachIndexed { i, pkg ->
            val count = counts.getOrElse(i) { 0 }
            if (count > 0) {
                incoming.add(pkg)
                (pagePager.findIcon(pkg) ?: dockView.findIcon(pkg))?.setBadge(count)
            }
        }
        // Clear badges that are no longer present.
        (badgedPackages - incoming).forEach { pkg ->
            (pagePager.findIcon(pkg) ?: dockView.findIcon(pkg))?.setBadge(0)
        }
        badgedPackages.clear(); badgedPackages.addAll(incoming)
    }

    // ── HomeTargetBridge.Host: SystemUI-driven home-swipe reveal ───────────────

    /** Icon layer = everything except the wallpaper background. */
    private fun iconLayerViews() = listOf(pagePager, dockView, dotIndicator, weatherWidget, clockLabel, dateLabel)

    override fun setRevealProgress(progress: Float) {
        val p = progress.coerceIn(0f, 1f)
        // Icons fade in (0→1) and rise slightly into place as the app shrinks home.
        val rise = (1f - p) * dp(resources.displayMetrics.density, 24)
        iconLayerViews().forEach {
            it.alpha = p
            it.translationY = rise
        }
    }

    override fun onHomeSettled(toHome: Boolean) {
        // Once settled the home grid is fully shown regardless of commit/cancel
        // (cancel returns into the app, which covers home anyway). Reset transforms.
        iconLayerViews().forEach { it.alpha = 1f; it.translationY = 0f }
    }

    override fun pulseIcon(packageName: String) {
        (pagePager.findIcon(packageName) ?: dockView.findIcon(packageName))?.pulse()
    }

    override fun landingBounds(packageName: String): android.graphics.Rect? {
        val icon = pagePager.findIcon(packageName) ?: dockView.findIcon(packageName) ?: return null
        return icon.screenBounds()
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
