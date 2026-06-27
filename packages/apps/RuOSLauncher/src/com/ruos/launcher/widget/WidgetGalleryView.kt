package com.ruos.launcher.widget

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.ruos.launcher.Haptics

/**
 * iOS-style "edit widgets" gallery. A dimmed sheet slides up: the top section lists the
 * widgets you've added (each with an S / M / L segmented control and a Remove), the bottom
 * lists the catalogue you can add. Every change writes straight through [WidgetStore] and
 * fires [onChanged] so Today View rebuilds live. Reuses the launcher spring/haptic vocabulary.
 */
class WidgetGalleryView(
    context: Context,
    private val store: WidgetStore,
    private val catalogue: List<Pair<String, String>>,   // (type, human label)
    private val onChanged: () -> Unit
) : LinearLayout(context) {

    private val d = resources.displayMetrics.density
    private fun dp(v: Float) = (v * d).toInt()
    private val golos = Typeface.create("golos", Typeface.NORMAL)
    private val golosM = Typeface.create("golos-medium", Typeface.NORMAL)
    private val labelOf = catalogue.toMap()

    private val sheet: LinearLayout
    var onDismiss: (() -> Unit)? = null

    init {
        orientation = VERTICAL
        gravity = Gravity.BOTTOM
        setBackgroundColor(0x99000000.toInt())
        setOnClickListener { dismiss() }

        sheet = LinearLayout(context).apply {
            orientation = VERTICAL
            background = GradientDrawable().apply {
                cornerRadii = floatArrayOf(dp(28f).toFloat(), dp(28f).toFloat(), dp(28f).toFloat(), dp(28f).toFloat(), 0f, 0f, 0f, 0f)
                setColor(0xFF1C1C1E.toInt())
            }
            setPadding(dp(16f), dp(10f), dp(16f), dp(24f))
            isClickable = true   // swallow taps so they don't dismiss
        }
        addView(sheet, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        rebuild()
    }

    private fun rebuild() {
        sheet.removeAllViews()
        // grab handle
        sheet.addView(View(context).apply {
            background = GradientDrawable().apply { cornerRadius = dp(3f).toFloat(); setColor(0xFF48484A.toInt()) }
        }, LayoutParams(dp(36f), dp(5f)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(10f) })

        sheet.addView(header("Виджеты", done = true))

        val current = store.widgets()
        sheet.addView(sectionLabel("ДОБАВЛЕННЫЕ"))
        if (current.isEmpty()) sheet.addView(note("Нет добавленных виджетов."))
        current.forEachIndexed { i, spec -> sheet.addView(addedRow(i, spec)) }

        sheet.addView(sectionLabel("ДОСТУПНЫЕ"))
        val scroll = ScrollView(context).apply { isVerticalScrollBarEnabled = false }
        val col = LinearLayout(context).apply { orientation = VERTICAL }
        catalogue.forEach { (type, label) -> col.addView(catalogueRow(type, label)) }
        scroll.addView(col)
        sheet.addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    /** A row for an already-added widget: name + S/M/L segmented control + remove. */
    private fun addedRow(index: Int, spec: WidgetSpec): View = card().apply {
        orientation = VERTICAL
        addView(LinearLayout(context).apply {
            orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(TextView(context).apply {
                text = labelOf[spec.type] ?: spec.type; setTextColor(Color.WHITE); textSize = 16f; typeface = golosM
            }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(context).apply {
                text = "Удалить"; setTextColor(0xFFFF453A.toInt()); textSize = 14f; typeface = golos
                isClickable = true; setOnClickListener { Haptics.light(this); store.removeAt(index); onChanged(); rebuild() }
            })
        })
        addView(segmentedSize(spec.size) { newSize ->
            store.setSizeAt(index, newSize); onChanged(); rebuild()
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10f) })
    }

    /** A catalogue row: label + "Добавить". */
    private fun catalogueRow(type: String, label: String): View = card().apply {
        orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        addView(TextView(context).apply {
            text = label; setTextColor(Color.WHITE); textSize = 16f; typeface = golos
        }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(TextView(context).apply {
            text = "＋ Добавить"; setTextColor(0xFF0A84FF.toInt()); textSize = 15f; typeface = golosM
            isClickable = true
            setOnClickListener { Haptics.confirm(this); store.add(WidgetSpec(type, WidgetSize.MEDIUM)); onChanged(); rebuild() }
        })
    }

    /** iOS segmented control for the three sizes. */
    private fun segmentedSize(selected: WidgetSize, onPick: (WidgetSize) -> Unit): View =
        LinearLayout(context).apply {
            orientation = HORIZONTAL
            background = GradientDrawable().apply { cornerRadius = dp(9f).toFloat(); setColor(0xFF2C2C2E.toInt()) }
            val pad = dp(2f); setPadding(pad, pad, pad, pad)
            WidgetSize.values().forEach { sz ->
                addView(TextView(context).apply {
                    text = sz.label; textSize = 13f; gravity = Gravity.CENTER
                    typeface = if (sz == selected) golosM else golos
                    setTextColor(if (sz == selected) Color.WHITE else 0xFF9E9EA3.toInt())
                    setPadding(0, dp(7f), 0, dp(7f))
                    if (sz == selected) background = GradientDrawable().apply {
                        cornerRadius = dp(7f).toFloat(); setColor(0xFF48484A.toInt())
                    }
                    isClickable = true
                    setOnClickListener { if (sz != selected) { Haptics.light(this); onPick(sz) } }
                }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            }
        }

    private fun card(): LinearLayout = LinearLayout(context).apply {
        background = GradientDrawable().apply { cornerRadius = dp(14f).toFloat(); setColor(0xFF2C2C2E.toInt()) }
        setPadding(dp(14f), dp(12f), dp(14f), dp(12f))
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8f) }
    }

    private fun header(t: String, done: Boolean) = LinearLayout(context).apply {
        orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        addView(TextView(context).apply {
            text = t; setTextColor(Color.WHITE); textSize = 22f; typeface = Typeface.create(golos, Typeface.BOLD)
        }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        if (done) addView(TextView(context).apply {
            text = "Готово"; setTextColor(0xFF0A84FF.toInt()); textSize = 17f; typeface = golosM
            isClickable = true; setOnClickListener { dismiss() }
        })
    }

    private fun sectionLabel(t: String) = TextView(context).apply {
        text = t; setTextColor(0xFF8E8E93.toInt()); textSize = 13f; typeface = golosM
        setPadding(dp(4f), dp(16f), 0, dp(2f))
    }
    private fun note(t: String) = TextView(context).apply {
        text = t; setTextColor(0xFF8E8E93.toInt()); textSize = 14f; typeface = golos; setPadding(dp(4f), dp(6f), 0, dp(4f))
    }

    fun animateIn() {
        alpha = 0f; animate().alpha(1f).setDuration(160).start()
        sheet.post {
            sheet.translationY = sheet.height.toFloat()
            SpringAnimation(sheet, SpringAnimation.TRANSLATION_Y).apply {
                spring = SpringForce(0f).setStiffness(400f).setDampingRatio(0.85f)
            }.start()
        }
    }

    fun dismiss() {
        animate().alpha(0f).setDuration(160).withEndAction {
            (parent as? android.view.ViewGroup)?.removeView(this); onDismiss?.invoke()
        }.start()
        sheet.animate().translationY(sheet.height.toFloat()).setDuration(180).start()
    }
}
