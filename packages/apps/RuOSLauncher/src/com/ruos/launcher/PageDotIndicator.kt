package com.ruos.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

/** Page dot indicator — exact iOS style: selected dot = full white, rest = 35% white */
class PageDotIndicator @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var pageCount = 0
    private var currentPage = 0

    private val dotRadius = context.resources.getDimension(R.dimen.dot_radius)
    private val dotSpacing = context.resources.getDimension(R.dimen.dot_spacing)

    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }
    private val inactivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(90, 255, 255, 255)
    }

    // Animated x position of the active indicator
    private var indicatorX = 0f

    private val indicatorSpring = object : FloatPropertyCompat_indicatorX() {
        override fun getValue(obj: PageDotIndicator) = obj.indicatorX
        override fun setValue(obj: PageDotIndicator, value: Float) {
            obj.indicatorX = value
            obj.invalidate()
        }
    }

    fun setPageCount(count: Int) {
        pageCount = count
        indicatorX = dotX(currentPage)
        invalidate()
    }

    fun setCurrentPage(page: Int) {
        currentPage = page
        val targetX = dotX(page)
        androidx.dynamicanimation.animation.SpringAnimation(this, object :
            androidx.dynamicanimation.animation.FloatPropertyCompat<PageDotIndicator>("indicatorX") {
            override fun getValue(obj: PageDotIndicator) = obj.indicatorX
            override fun setValue(obj: PageDotIndicator, value: Float) {
                obj.indicatorX = value
                obj.invalidate()
            }
        }, targetX).apply {
            spring.stiffness = SpringForce.STIFFNESS_HIGH
            spring.dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
            start()
        }
    }

    private fun dotX(page: Int): Float {
        val totalWidth = (pageCount - 1) * (dotRadius * 2 + dotSpacing)
        val startX = (width - totalWidth) / 2f
        return startX + page * (dotRadius * 2 + dotSpacing) + dotRadius
    }

    override fun onDraw(canvas: Canvas) {
        if (pageCount <= 1) return
        val y = height / 2f
        for (i in 0 until pageCount) {
            val x = dotX(i)
            canvas.drawCircle(x, y, dotRadius, inactivePaint)
        }
        // Active dot follows spring
        canvas.drawCircle(indicatorX, y, dotRadius, activePaint)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = ((pageCount * (dotRadius * 2 + dotSpacing)) - dotSpacing).toInt()
        val h = (dotRadius * 2).toInt()
        setMeasuredDimension(
            resolveSize(w, widthMeasureSpec),
            resolveSize(h, heightMeasureSpec)
        )
    }
}

// Workaround: can't make anonymous FloatPropertyCompat inside a secondary constructor
private abstract class FloatPropertyCompat_indicatorX :
    androidx.dynamicanimation.animation.FloatPropertyCompat<PageDotIndicator>("indicatorX")
