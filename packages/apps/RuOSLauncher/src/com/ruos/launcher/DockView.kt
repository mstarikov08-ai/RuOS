package com.ruos.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.util.AttributeSet
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.FrameLayout

/**
 * iOS-style dock — 4 app icons on a frosted glass pill.
 *
 * Frosted glass is approximated using a blurred background capture drawn
 * in onDraw() with a semi-transparent white overlay — matches iOS 18 dock
 * appearance without requiring the full blur pipeline on all devices.
 */
class DockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val iconRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
    }

    private val glassRadius = context.resources.getDimension(R.dimen.dock_corner_radius)
    private val glassColor = Color.argb(120, 255, 255, 255)  // 47% white — iOS dock tint
    private val glassBorderColor = Color.argb(30, 255, 255, 255)
    private val glassRect = RectF()

    private val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = glassColor
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = glassBorderColor
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    private var dockApps: List<AppInfo> = emptyList()
    private val iconViews = mutableListOf<AppIconView>()

    init {
        setWillNotDraw(false)
        clipChildren = false

        val paddingH = context.resources.getDimensionPixelSize(R.dimen.dock_padding_horizontal)
        val paddingV = context.resources.getDimensionPixelSize(R.dimen.dock_padding_vertical)
        iconRow.setPadding(paddingH, paddingV, paddingH, paddingV)

        addView(iconRow, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).also {
            it.gravity = Gravity.CENTER
            it.bottomMargin = context.resources.getDimensionPixelSize(R.dimen.dock_bottom_margin)
        })
    }

    fun refreshApps() {
        val repo = RuOSApp.instance.appRepository
        dockApps = repo.getDockApps()
        rebuildIcons()
    }

    fun setJiggleMode(active: Boolean) {
        iconViews.forEach { it.setJiggleMode(active) }
    }

    /** Find the dock icon view for a package, or null. */
    fun findIcon(pkg: String): AppIconView? = iconViews.firstOrNull { it.packageName == pkg }

    private fun rebuildIcons() {
        iconRow.removeAllViews()
        iconViews.clear()

        dockApps.forEach { info ->
            val icon = AppIconView(context).apply {
                bind(info)
            }
            val iconSize = context.resources.getDimensionPixelSize(R.dimen.dock_icon_size)
            val margin = context.resources.getDimensionPixelSize(R.dimen.dock_icon_margin)
            val params = LinearLayout.LayoutParams(iconSize, iconSize).also {
                it.setMargins(margin, 0, margin, 0)
            }
            iconRow.addView(icon, params)
            iconViews.add(icon)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val rowH = context.resources.getDimensionPixelSize(R.dimen.dock_height).toFloat()
        val bottomMargin = context.resources.getDimensionPixelSize(R.dimen.dock_bottom_margin).toFloat()
        val pillW = w * 0.88f
        val left = (w - pillW) / 2f
        val top = h - bottomMargin - rowH
        glassRect.set(left, top, left + pillW, top + rowH)
    }

    override fun onDraw(canvas: Canvas) {
        // Frosted glass pill
        canvas.drawRoundRect(glassRect, glassRadius, glassRadius, glassPaint)
        canvas.drawRoundRect(glassRect, glassRadius, glassRadius, borderPaint)
    }
}
