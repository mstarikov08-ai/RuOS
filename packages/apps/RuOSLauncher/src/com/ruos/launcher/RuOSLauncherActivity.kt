package com.ruos.launcher

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.view.WindowInsets
import android.view.WindowInsetsController

class RuOSLauncherActivity : Activity() {

    private var homeView: HomeView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Full-screen, edge-to-edge
        window.setDecorFitsSystemWindows(false)
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        window.insetsController?.apply {
            hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        try {
            val hv = HomeView(this)
            homeView = hv
            setContentView(hv)
        } catch (_: Exception) {
            // HomeView construction failed — show an empty black screen so the
            // launcher process stays alive and Android doesn't fall back.
            setContentView(android.widget.FrameLayout(this))
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            homeView?.onResume()
        } catch (_: Exception) {
            // Swallow refresh errors — a blank home screen beats a crash.
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            homeView?.onPause()
        } catch (_: Exception) {}
    }

    override fun onBackPressed() {
        // Swallow back — launcher has no back destination.
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        try {
            homeView?.returnHome()
        } catch (_: Exception) {}
    }
}
