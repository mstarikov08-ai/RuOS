package com.ruos.assist.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.ruos.assist.ui.AssistAction
import com.ruos.assist.ui.AssistGlyph
import com.ruos.assist.ui.AssistiveMenuView
import com.ruos.assist.ui.FloatingButton

/**
 * AssistiveTouch — a floating one-handed-control button. Implemented as an
 * AccessibilityService because that's the only way an overlay can perform Home / Back /
 * Recents / Notifications / Control Centre / Screenshot / Lock from anywhere. The puck is
 * an accessibility overlay window (no SYSTEM_ALERT_WINDOW needed); tapping it opens a grid
 * of actions wired to performGlobalAction.
 */
class AssistiveTouchService : AccessibilityService() {

    private val wm get() = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var button: FloatingButton? = null
    private var menu: AssistiveMenuView? = null
    private lateinit var buttonParams: WindowManager.LayoutParams
    private val prefs by lazy { getSharedPreferences("ruos_assist", Context.MODE_PRIVATE) }

    override fun onServiceConnected() {
        if (button != null) return
        val d = resources.displayMetrics.density
        button = FloatingButton(this).apply {
            onTap = { showMenu() }
            onMove = { dx, dy -> moveBy(dx, dy) }
            onMoveEnd = { savePosition() }
        }
        buttonParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt("x", (16 * d).toInt())
            y = prefs.getInt("y", (220 * d).toInt())
        }
        runCatching { wm.addView(button, buttonParams) }
    }

    private fun moveBy(dx: Float, dy: Float) {
        buttonParams.x += dx.toInt(); buttonParams.y += dy.toInt()
        runCatching { wm.updateViewLayout(button, buttonParams) }
    }

    private fun savePosition() {
        prefs.edit().putInt("x", buttonParams.x).putInt("y", buttonParams.y).apply()
    }

    private fun showMenu() {
        if (menu != null) return
        val view = AssistiveMenuView(this, actions(),
            onAction = { act -> performGlobalAction(act.code); hideMenu() },
            onDismiss = { hideMenu() })
        menu = view
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        runCatching { wm.addView(view, lp) }
    }

    private fun hideMenu() {
        menu?.let { v -> runCatching { wm.removeView(v) } }; menu = null
    }

    private fun actions(): List<AssistAction> {
        val list = mutableListOf(
            AssistAction("Домой", AssistGlyph.HOME, GLOBAL_ACTION_HOME),
            AssistAction("Назад", AssistGlyph.BACK, GLOBAL_ACTION_BACK),
            AssistAction("Меню", AssistGlyph.RECENTS, GLOBAL_ACTION_RECENTS),
            AssistAction("Уведомления", AssistGlyph.NOTIFICATIONS, GLOBAL_ACTION_NOTIFICATIONS),
            AssistAction("Пункт упр.", AssistGlyph.CONTROL, GLOBAL_ACTION_QUICK_SETTINGS)
        )
        if (Build.VERSION.SDK_INT >= 30) list.add(AssistAction("Снимок", AssistGlyph.SCREENSHOT, GLOBAL_ACTION_TAKE_SCREENSHOT))
        if (Build.VERSION.SDK_INT >= 28) list.add(AssistAction("Блокировка", AssistGlyph.LOCK, GLOBAL_ACTION_LOCK_SCREEN))
        return list
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        hideMenu()
        button?.let { v -> runCatching { wm.removeView(v) } }; button = null
        return super.onUnbind(intent)
    }
}
