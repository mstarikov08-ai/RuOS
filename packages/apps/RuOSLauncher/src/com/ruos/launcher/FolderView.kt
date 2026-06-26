package com.ruos.launcher

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

/**
 * Full-screen dim overlay that opens a folder with an iOS zoom: the panel scales up
 * from the folder icon's rect with a spring, shows the contained apps in a grid and
 * an editable title. Tapping an app launches it; tapping the scrim or back closes
 * with the reverse zoom.
 */
class FolderView(context: Context) : FrameLayout(context) {

    var onTitleChanged: ((HomeItem.Folder) -> Unit)? = null
    var onDismiss: (() -> Unit)? = null

    private val panel = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val titleField = EditText(context)
    private val grid = GridLayout(context).apply { columnCount = 4 }

    private var folder: HomeItem.Folder? = null
    private var open = false

    init {
        setBackgroundColor(Color.parseColor("#B3000000"))
        visibility = View.GONE
        isClickable = true
        setOnClickListener { close() }

        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        panel.apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(28).toFloat()
                setColor(Color.parseColor("#3A3A3C"))
            }
            setPadding(dp(20), dp(20), dp(20), dp(20))
            // Swallow taps so they don't fall through to the scrim.
            isClickable = true
        }

        titleField.apply {
            setText("")
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            background = null
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) commitTitle()
            }
        }
        panel.addView(titleField, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also {
            it.bottomMargin = dp(16)
        })
        panel.addView(grid, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).also {
            it.gravity = Gravity.CENTER_HORIZONTAL
        })

        val panelW = (resources.displayMetrics.widthPixels * 0.82f).toInt()
        addView(panel, LayoutParams(panelW, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
    }

    fun isOpen(): Boolean = open

    fun open(f: HomeItem.Folder, fromRect: android.graphics.Rect) {
        folder = f
        open = true
        visibility = View.VISIBLE
        alpha = 0f
        titleField.setText(f.title)
        rebuildGrid(f)

        // Pivot the zoom at the folder icon's centre.
        post {
            val px = (fromRect.exactCenterX() - panel.left).coerceAtLeast(0f)
            val py = (fromRect.exactCenterY() - panel.top).coerceAtLeast(0f)
            panel.pivotX = px
            panel.pivotY = py
            panel.scaleX = 0.3f
            panel.scaleY = 0.3f
            animate().alpha(1f).setDuration(140).start()
            springScale(1f)
        }
    }

    fun close() {
        if (!open) return
        open = false
        commitTitle()
        animate().alpha(0f).setDuration(140).withEndAction { visibility = View.GONE }.start()
        springScale(0.3f)
        onDismiss?.invoke()
    }

    private fun springScale(target: Float) {
        SpringAnimation(panel, SpringAnimation.SCALE_X, target).apply {
            spring.stiffness = SpringForce.STIFFNESS_MEDIUM
            spring.dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
            start()
        }
        SpringAnimation(panel, SpringAnimation.SCALE_Y, target).apply {
            spring.stiffness = SpringForce.STIFFNESS_MEDIUM
            spring.dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
            start()
        }
    }

    private fun rebuildGrid(f: HomeItem.Folder) {
        grid.removeAllViews()
        val d = resources.displayMetrics.density
        val cell = (64 * d).toInt()
        val margin = (10 * d).toInt()
        f.apps.forEach { info ->
            val icon = AppIconView(context).apply { bind(info) }
            val params = GridLayout.LayoutParams().apply {
                width = cell
                height = cell + (18 * d).toInt()
                setMargins(margin, margin, margin, margin)
            }
            grid.addView(icon, params)
        }
    }

    private fun commitTitle() {
        val f = folder ?: return
        val newTitle = titleField.text.toString().trim()
        if (newTitle.isNotEmpty() && newTitle != f.title) {
            f.title = newTitle
            onTitleChanged?.invoke(f)
        }
    }
}
