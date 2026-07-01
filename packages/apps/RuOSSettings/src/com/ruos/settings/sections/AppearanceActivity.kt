package com.ruos.settings.sections

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.ruos.settings.util.RuosAccent

/**
 * «Оформление» — pick the interactive tint (accent), like iOS's per-device colour. Tapping a
 * swatch writes it to Settings.Secure via [RuosAccent] and repaints the live preview + this
 * screen immediately, so the control visibly does something even before every app adopts it.
 */
class AppearanceActivity : Activity() {

    private val golos = Typeface.create("golos", Typeface.NORMAL)
    private val golosM = Typeface.create("golos-medium", Typeface.NORMAL)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private var accent = 0
    private lateinit var preview: PreviewCard
    private lateinit var grid: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        accent = RuosAccent.read(this)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#F2F2F7"))
            setPadding(0, dp(60), 0, dp(40))
        }
        col.addView(title("Оформление"))
        col.addView(note("Основной цвет — кнопки, ссылки, переключатели во всех приложениях RuOS."))

        col.addView(sectionLabel("ЦВЕТ"))
        grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), 0, dp(16), 0) }
        col.addView(card(grid))
        buildSwatches()

        col.addView(sectionLabel("ПРЕДПРОСМОТР"))
        preview = PreviewCard(this) { accent }
        col.addView(card(preview))

        setContentView(ScrollView(this).apply { addView(col) })
    }

    private fun buildSwatches() {
        grid.removeAllViews()
        val perRow = 4
        var row: LinearLayout? = null
        RuosAccent.SWATCHES.forEachIndexed { i, color ->
            if (i % perRow == 0) {
                row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    lp.topMargin = dp(10); lp.bottomMargin = dp(6); layoutParams = lp
                }
                grid.addView(row)
            }
            row!!.addView(Swatch(this, color, selected = (color == accent)) {
                if (RuosAccent.write(this, color)) {
                    accent = color; buildSwatches(); preview.refresh()
                } else Toast.makeText(this, "Нет прав на изменение", Toast.LENGTH_SHORT).show()
            }, LinearLayout.LayoutParams(0, dp(56), 1f))
        }
    }

    /** A tappable colour circle with a white ring + checkmark when selected. */
    private inner class Swatch(ctx: Activity, val color: Int, val selected: Boolean, val onTap: () -> Unit) : View(ctx) {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = this@Swatch.color }
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(3).toFloat(); this.color = Color.WHITE }
        private val check = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(3).toFloat(); this.color = Color.WHITE; strokeCap = Paint.Cap.ROUND }
        init { isClickable = true; setOnClickListener { onTap() } }
        override fun onDraw(c: Canvas) {
            val cx = width / 2f; val cy = height / 2f; val r = dp(20).toFloat()
            c.drawCircle(cx, cy, r, fill)
            if (selected) {
                c.drawCircle(cx, cy, r + dp(3), ring)
                c.drawLine(cx - dp(7), cy, cx - dp(2), cy + dp(6), check)
                c.drawLine(cx - dp(2), cy + dp(6), cx + dp(8), cy - dp(6), check)
            }
        }
    }

    /** Live preview: a filled button, a link, and a toggle, all tinted with [accentOf]. */
    private inner class PreviewCard(ctx: Activity, val accentOf: () -> Int) : LinearLayout(ctx) {
        init {
            orientation = VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(14)); build()
        }
        /** Rebuild the tinted sample views. Call explicitly on accent change (NOT via
         *  invalidate(), which the framework fires on its own). */
        fun refresh() { removeAllViews(); build() }
        private fun build() {
            val a = accentOf()
            addView(TextView(context).apply {
                text = "Кнопка"; setTextColor(Color.WHITE); textSize = 16f; typeface = golosM; gravity = Gravity.CENTER
                background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(a) }
                setPadding(0, dp(12), 0, dp(12))
            })
            addView(TextView(context).apply {
                text = "Ссылка"; setTextColor(a); textSize = 16f; typeface = golosM
                setPadding(0, dp(12), 0, 0)
            })
        }
    }

    // ── shared iOS-style helpers ──────────────────────────────────────────────
    private fun card(inner: View) = LinearLayout(this).apply {
        setPadding(dp(16), 0, dp(16), 0)
        addView(LinearLayout(this@AppearanceActivity).apply {
            background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(Color.WHITE) }
            setPadding(dp(6), dp(4), dp(6), dp(8))
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(6); layoutParams = lp
            addView(inner, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }
    private fun title(t: String) = TextView(this).apply {
        text = t; textSize = 28f; setTextColor(Color.BLACK); typeface = Typeface.create(golos, Typeface.BOLD)
        setPadding(dp(20), dp(4), dp(20), dp(12))
    }
    private fun sectionLabel(t: String) = TextView(this).apply {
        text = t; textSize = 13f; setTextColor(Color.parseColor("#6C6C70")); typeface = golosM
        setPadding(dp(32), dp(16), dp(16), dp(6))
    }
    private fun note(t: String) = TextView(this).apply {
        text = t; textSize = 13f; setTextColor(Color.parseColor("#6C6C70")); typeface = golos
        setPadding(dp(20), dp(2), dp(20), dp(10))
    }
}
