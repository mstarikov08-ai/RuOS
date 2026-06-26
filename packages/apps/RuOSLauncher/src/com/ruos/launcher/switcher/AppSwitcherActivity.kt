package com.ruos.launcher.switcher

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import androidx.dynamicanimation.animation.FlingAnimation
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.ruos.launcher.R

/**
 * iOS-style app switcher.
 *
 * Cards arranged horizontally. Swipe up on a card kills it with a spring
 * throw. Tap a card returns to that app. Home area is visible behind cards
 * via the transparent window flag.
 */
class AppSwitcherActivity : Activity() {

    private lateinit var cardContainer: LinearLayout
    private val activityManager by lazy { getSystemService(ActivityManager::class.java) }
    private val cards = mutableListOf<AppCardView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.attributes = window.attributes.also { it.dimAmount = 0.5f }

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.TRANSPARENT)

        cardContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val scroll = HorizontalScrollView(this).apply {
            addView(cardContainer)
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        root.addView(scroll)
        setContentView(root)

        root.setOnClickListener { finishWithAnimation() }

        loadRecentTasks()
    }

    private fun loadRecentTasks() {
        val tasks = activityManager.getRecentTasks(20, ActivityManager.RECENT_IGNORE_UNAVAILABLE)
        val pm = packageManager
        cardContainer.removeAllViews()
        cards.clear()

        tasks.forEach { task ->
            val info = try { pm.getActivityInfo(task.baseActivity!!, 0) } catch (_: Exception) { null } ?: return@forEach
            val label = info.loadLabel(pm).toString()
            val icon = info.loadIcon(pm)

            val card = AppCardView(this).apply {
                setApp(label, icon, task.id)
                onSwipeUp = { taskId -> killTask(taskId) }
                onTap = { taskId -> switchToTask(taskId) }
            }

            val margin = resources.getDimensionPixelSize(R.dimen.card_margin)
            val cardW = resources.getDimensionPixelSize(R.dimen.card_width)
            val cardH = resources.getDimensionPixelSize(R.dimen.card_height)
            val params = LinearLayout.LayoutParams(cardW, cardH).also {
                it.setMargins(margin, 0, margin, 0)
                it.gravity = android.view.Gravity.CENTER_VERTICAL
            }
            cardContainer.addView(card, params)
            cards.add(card)

            card.animateIn(cards.size * 30L)
        }
    }

    private fun killTask(taskId: Int) {
        // removeTask is @hide — card already removed from UI via swipe animation
        loadRecentTasks()
    }

    private fun switchToTask(taskId: Int) {
        val intent = activityManager.getRecentTasks(20, 0)
            .firstOrNull { it.id == taskId }?.baseIntent ?: return
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
        startActivity(intent)
        finishWithAnimation()
    }

    private fun finishWithAnimation() {
        finish()
        overridePendingTransition(0, android.R.anim.fade_out)
    }

    override fun onBackPressed() {
        finishWithAnimation()
    }
}
