package com.android.systemui.ruos.screenshot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import kotlin.math.abs

/**
 * iOS-style screenshot preview: a small rounded card with a white border + soft shadow,
 * bottom-left. Springs in, auto-dismisses, tap → markup, swipe left → dismiss (the shot
 * is already saved).
 */
class ScreenshotThumbnailView(
    context: Context,
    private val preview: Bitmap,
    private val onTap: () -> Unit,
    private val onDismiss: () -> Unit
) : View(context) {

    private val d = resources.displayMetrics.density
    private fun dp(v: Float) = v * d

    private val pad = dp(10f)            // shadow padding
    private val radius = dp(14f)
    private val cardW = preview.width.toFloat()
    private val cardH = preview.height.toFloat()

    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; setShadowLayer(dp(10f), 0f, dp(3f), 0x66000000)
    }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = dp(3f); color = Color.WHITE
    }
    private val clip = Paint(Paint.ANTI_ALIAS_FLAG)

    private var downX = 0f
    private var dragging = false

    private val springX = SpringAnimation(this, TRANSLATION_X).apply {
        spring = SpringForce(0f).setStiffness(500f).setDampingRatio(0.8f)
    }

    init { setLayerType(LAYER_TYPE_SOFTWARE, null) }   // for shadow layer

    override fun onMeasure(w: Int, h: Int) =
        setMeasuredDimension((cardW + pad * 2).toInt(), (cardH + pad * 2).toInt())

    override fun onDraw(canvas: Canvas) {
        val r = RectF(pad, pad, pad + cardW, pad + cardH)
        canvas.drawRoundRect(r, radius, radius, shadow)
        val save = canvas.save()
        val path = android.graphics.Path().apply { addRoundRect(r, radius, radius, android.graphics.Path.Direction.CW) }
        canvas.clipPath(path)
        canvas.drawBitmap(preview, pad, pad, clip)
        canvas.restoreToCount(save)
        canvas.drawRoundRect(r, radius, radius, border)
    }

    fun slideIn() {
        translationX = -(cardW + dp(40f)); alpha = 0f
        animate().alpha(1f).setDuration(140).start()
        springX.animateToFinalPosition(0f)
    }

    fun slideOut(end: () -> Unit) {
        animate().translationX(-(cardW + dp(40f))).alpha(0f).setDuration(180)
            .withEndAction(end).start()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> { downX = e.rawX; dragging = false; return true }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.rawX - downX
                if (abs(dx) > dp(6f)) dragging = true
                if (dragging && dx < 0) translationX = dx
                return true
            }
            MotionEvent.ACTION_UP -> {
                val dx = e.rawX - downX
                if (dragging && dx < -dp(48f)) onDismiss()
                else if (!dragging) onTap()
                else springX.animateToFinalPosition(0f)
                return true
            }
            MotionEvent.ACTION_CANCEL -> { springX.animateToFinalPosition(0f); return true }
        }
        return false
    }
}
