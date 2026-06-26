package com.ruos.keychain.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.ruos.keychain.store.TotpAccount
import com.ruos.keychain.totp.Totp

/**
 * One authenticator row: issuer / account on the left, the live 6-digit code (regrouped
 * "123 456"), and a 30-second countdown ring that depletes and refreshes the code. Ticks
 * itself once per second while attached.
 */
class TotpRowView(context: Context, private val account: TotpAccount) : LinearLayout(context) {

    private val d = resources.displayMetrics.density
    private fun dp(v: Float) = (v * d).toInt()
    private val main = Handler(Looper.getMainLooper())

    private val codeText = TextView(context).apply {
        setTextColor(0xFF0A84FF.toInt()); textSize = 28f; typeface = Fonts.medium
        letterSpacing = 0.08f
    }
    private val ring = RingView(context)

    private val tick = object : Runnable {
        override fun run() { refresh(); main.postDelayed(this, 1000) }
    }

    init {
        orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16f), dp(12f), dp(16f), dp(12f))
        val col = LinearLayout(context).apply { orientation = VERTICAL }
        col.addView(TextView(context).apply {
            text = account.issuer.ifEmpty { "Код" }; setTextColor(Color.WHITE); textSize = 16f; typeface = Fonts.medium
        })
        col.addView(TextView(context).apply {
            text = account.account; setTextColor(0xFF8E8E93.toInt()); textSize = 13f; typeface = Fonts.regular
        })
        col.addView(codeText)
        addView(col, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(ring, LayoutParams(dp(40f), dp(40f)))
    }

    private fun refresh() {
        val raw = Totp.code(account.secret)
        codeText.text = if (raw.length == 6) "${raw.substring(0, 3)} ${raw.substring(3)}" else raw
        ring.setRemaining(Totp.secondsRemaining(), 30)
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); refresh(); main.post(tick) }
    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); main.removeCallbacks(tick) }

    private class RingView(context: Context) : View(context) {
        private val d = resources.displayMetrics.density
        private var frac = 1f
        private var secs = 30
        private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 3f * d; color = 0xFF2C2C2E.toInt() }
        private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 3f * d; strokeCap = Paint.Cap.ROUND; color = 0xFF0A84FF.toInt() }
        private val num = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textAlign = Paint.Align.CENTER; textSize = 13f * d; typeface = Fonts.regular }

        fun setRemaining(remaining: Int, period: Int) {
            secs = remaining; frac = remaining.toFloat() / period
            arc.color = if (remaining <= 5) 0xFFFF3B30.toInt() else 0xFF0A84FF.toInt()
            invalidate()
        }
        override fun onDraw(c: Canvas) {
            val pad = 4f * d
            val r = RectF(pad, pad, width - pad, height - pad)
            c.drawArc(r, 0f, 360f, false, track)
            c.drawArc(r, -90f, -360f * frac, false, arc)
            c.drawText(secs.toString(), width / 2f, height / 2f + 4f * d, num)
        }
    }
}
