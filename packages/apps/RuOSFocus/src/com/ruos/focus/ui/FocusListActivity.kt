package com.ruos.focus.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.ruos.focus.model.FocusStore
import com.ruos.focus.schedule.FocusController
import com.ruos.focus.schedule.FocusScheduler
import com.ruos.focus.util.Fonts

/**
 * The Focus home: a grid of mode tiles. Tap toggles a focus on/off (only one active at a
 * time, iOS-style). Long-press opens the editor. A "+" tile adds a custom focus.
 *
 * Launched from RuOSSettings → Фокусирование, or directly.
 */
class FocusListActivity : Activity() {

    private lateinit var store: FocusStore
    private lateinit var grid: GridLayout
    private val d get() = resources.displayMetrics.density
    private fun dp(v: Float) = (v * d).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = FocusStore(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK)
        }
        root.addView(TextView(this).apply {
            text = "Фокусирование"; setTextColor(Color.WHITE); textSize = 30f; typeface = Fonts.bold
            setPadding(dp(16f), dp(48f), dp(16f), dp(4f))
        })
        root.addView(TextView(this).apply {
            text = "Фокус приглушает уведомления от приложений и людей, которых вы не выбрали."
            setTextColor(0xFF8E8E93.toInt()); textSize = 14f; typeface = Fonts.regular
            setPadding(dp(16f), 0, dp(16f), dp(16f))
        })

        grid = GridLayout(this).apply {
            columnCount = 2
            setPadding(dp(10f), 0, dp(10f), dp(24f))
        }
        val scroll = ScrollView(this).apply { addView(grid) }
        root.addView(scroll)
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        rebuild()
    }

    private fun rebuild() {
        grid.removeAllViews()
        val activeId = store.activeId()
        store.all().forEach { mode ->
            val tile = FocusTileView(this, mode).apply {
                activeNow = (mode.id == activeId)
                onTap = {
                    val nowOn = FocusController.toggle(this@FocusListActivity, mode.id)
                    FocusScheduler.evaluateAndReschedule(this@FocusListActivity)
                    rebuild()
                    showStatus(if (nowOn) "${mode.name}: включён" else "${mode.name}: выключен")
                }
                setOnLongClickListener { openEditor(mode.id); true }
            }
            grid.addView(tile, cell())
        }
        grid.addView(addTile(), cell())
    }

    private fun cell(): GridLayout.LayoutParams {
        val cols = 2
        val w = (resources.displayMetrics.widthPixels - dp(20f)) / cols
        return GridLayout.LayoutParams().apply {
            width = w; height = dp(120f)
            setMargins(dp(6f), dp(6f), dp(6f), dp(6f))
        }
    }

    private fun addTile(): View {
        return TextView(this).apply {
            text = "+  Создать"
            setTextColor(0xFF8E8E93.toInt()); textSize = 16f; typeface = Fonts.medium
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF1C1C1E.toInt())
            isClickable = true
            setOnClickListener { openEditor(null) }
        }
    }

    private fun openEditor(id: String?) {
        startActivity(Intent(this, FocusEditActivity::class.java).apply {
            if (id != null) putExtra("id", id)
        })
    }

    private fun showStatus(msg: String) {
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
    }
}
