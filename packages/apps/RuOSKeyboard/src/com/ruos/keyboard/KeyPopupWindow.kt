package com.ruos.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.PopupWindow

// iOS-style key pop-up bubble shown above a pressed key.
class KeyPopupWindow(private val context: Context) {

    private val popupView = PopupBubbleView(context)
    private val popup = PopupWindow(context).apply {
        contentView = popupView
        isOutsideTouchable = false
        isFocusable = false
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        elevation = 0f
    }

    fun show(anchor: View, label: String, keyRect: RectF, shiftOn: Boolean) {
        val displayLabel = if (shiftOn && label.length == 1 && label[0].isLetter())
            label.uppercase() else label

        val loc = IntArray(2)
        anchor.getLocationOnScreen(loc)

        val keyW = keyRect.width()
        val keyH = keyRect.height()

        // Bubble is wider and taller than the key; sits above it
        val bubbleW = (keyW * 1.6f).toInt().coerceAtLeast(dp(50f))
        val bubbleH = (keyH * 2.4f).toInt()

        popupView.setLabel(displayLabel)
        popup.width  = bubbleW
        popup.height = bubbleH

        val screenX = loc[0] + keyRect.left.toInt()
        val screenY = loc[1] + keyRect.top.toInt()

        // Centre bubble horizontally over the key; place its bottom at key top
        val popX = screenX - (bubbleW - keyW.toInt()) / 2
        val popY = screenY - bubbleH + (keyH * 0.3f).toInt()

        if (popup.isShowing) {
            popup.update(popX, popY, bubbleW, bubbleH)
        } else {
            popup.showAtLocation(anchor, Gravity.NO_GRAVITY, popX, popY)
        }
    }

    fun showAlternatives(anchor: View, alts: List<String>, keyRect: RectF) {
        if (alts.isEmpty()) return
        dismiss()

        val cellW = dp(44f)
        val bubbleW = cellW * alts.size + dp(12f)
        val bubbleH = dp(52f)

        val loc = IntArray(2)
        anchor.getLocationOnScreen(loc)
        val screenX = loc[0] + keyRect.left.toInt()
        val screenY = loc[1] + keyRect.top.toInt()

        val popX = (screenX - (bubbleW - keyRect.width().toInt()) / 2)
            .coerceAtLeast(dp(4f))
        val popY = screenY - bubbleH

        popupView.setAlternatives(alts)
        popup.width  = bubbleW
        popup.height = bubbleH
        popup.showAtLocation(anchor, Gravity.NO_GRAVITY, popX, popY)
    }

    fun dismiss() { if (popup.isShowing) popup.dismiss() }
    val isShowing get() = popup.isShowing
    fun getSelectedAlt(x: Float): String? = popupView.altAt(x)

    private fun dp(v: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, context.resources.displayMetrics).toInt()
}

// ── Custom View that draws the bubble ─────────────────────────────────────────

class PopupBubbleView(context: Context) : View(context) {

    private var label = ""
    private var alts: List<String> = emptyList()
    private var mode = Mode.SINGLE

    private enum class Mode { SINGLE, ALTERNATIVES }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path  = Path()

    fun setLabel(l: String) { label = l; mode = Mode.SINGLE; alts = emptyList(); invalidate() }
    fun setAlternatives(a: List<String>) { alts = a; mode = Mode.ALTERNATIVES; label = ""; invalidate() }

    fun altAt(localX: Float): String? {
        if (alts.isEmpty()) return null
        val cellW = width.toFloat() / alts.size
        val idx = (localX / cellW).toInt().coerceIn(0, alts.size - 1)
        return alts.getOrNull(idx)
    }

    override fun onDraw(canvas: Canvas) {
        when (mode) {
            Mode.SINGLE       -> drawSingleBubble(canvas)
            Mode.ALTERNATIVES -> drawAltStrip(canvas)
        }
    }

    // iOS key-pop bubble: rounded rect body + triangular pointer at bottom-centre
    private fun drawSingleBubble(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val bodyH = h * 0.76f
        val r = dp(10f)
        val triW = w * 0.32f

        path.reset()
        path.addRoundRect(RectF(0f, 0f, w, bodyH), r, r, Path.Direction.CW)
        path.moveTo(w / 2 - triW / 2, bodyH)
        path.lineTo(w / 2, h)
        path.lineTo(w / 2 + triW / 2, bodyH)
        path.close()

        paint.color = Color.parseColor("#E5E5EA")
        paint.style = Paint.Style.FILL
        canvas.drawPath(path, paint)

        // Letter
        paint.color = Color.BLACK
        paint.textSize = bodyH * 0.56f
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        val textY = bodyH * 0.52f - (paint.descent() + paint.ascent()) / 2
        canvas.drawText(label, w / 2f, textY, paint)
    }

    // Horizontal strip for long-press alternatives
    private fun drawAltStrip(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = dp(10f)

        paint.color = Color.parseColor("#3A3A3C")
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(RectF(0f, 0f, w, h), r, r, paint)

        if (alts.isEmpty()) return
        val cellW = w / alts.size
        paint.textSize = h * 0.44f
        paint.textAlign = Paint.Align.CENTER

        for ((i, alt) in alts.withIndex()) {
            paint.color = if (i == selectedAlt) Color.parseColor("#D94F3D") else Color.WHITE
            val cx = cellW * i + cellW / 2
            val ty = h / 2 - (paint.descent() + paint.ascent()) / 2
            canvas.drawText(alt, cx, ty, paint)
        }
    }

    var selectedAlt = 0
        set(v) { field = v; invalidate() }

    private fun dp(v: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, context.resources.displayMetrics)
}
