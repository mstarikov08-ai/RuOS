package com.ruos.launcher

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.view.WindowInsets
import android.view.WindowInsetsController

class RuOSLauncherActivity : Activity() {

    private lateinit var homeView: HomeView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Full-screen, edge-to-edge
        window.setDecorFitsSystemWindows(false)
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        window.insetsController?.apply {
            hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        homeView = HomeView(this)
        setContentView(homeView)
    }

    override fun onResume() {
        super.onResume()
        try {
            homeView.onResume()
        } catch (_: Exception) {
            // Swallow refresh errors — a blank home screen is better than a crash.
        }
    }

    override fun onPause() {
        super.onPause()
        homeView.onPause()
    }

    override fun onBackPressed() {
        // Swallow back — launcher has no back destination
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // Return to first page when home is tapped while already in launcher
        homeView.returnHome()
    }
}
