package com.ruos.launcher

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.ruos.launcher.widget.MusicWidgetView
import com.ruos.launcher.widget.WeatherWidgetView
import com.ruos.launcher.widget.WidgetGalleryView
import com.ruos.launcher.widget.WidgetSize
import com.ruos.launcher.widget.WidgetSpec
import com.ruos.launcher.widget.WidgetStore
import java.util.Calendar

/**
 * iOS-style Today View: the widgets page to the left of the home screen. Stacks a date
 * widget, weather, now-playing, and quick-action shortcuts. Revealed by over-scrolling
 * right of the first home page. Tapping the search bar opens Spotlight.
 */
class TodayView(context: Context) : LinearLayout(context) {

    private val d = resources.displayMetrics.density
    private fun dp(v: Float) = (v * d).toInt()
    private val golos = Typeface.create("golos", Typeface.NORMAL)
    private val golosM = Typeface.create("golos-medium", Typeface.NORMAL)

    var onDismiss: (() -> Unit)? = null
    var onOpenSearch: (() -> Unit)? = null

    private val store = WidgetStore(context)
    private lateinit var widgetsCol: LinearLayout

    private val catalogue = listOf("weather" to "Погода", "music" to "Музыка")

    init {
        orientation = VERTICAL
        setBackgroundColor(0xF20E0E10.toInt())
        setPadding(dp(14f), dp(48f), dp(14f), 0)
        visibility = View.GONE

        addView(TextView(context).apply {
            text = "Сегодня"; setTextColor(Color.WHITE); textSize = 28f; typeface = Typeface.create(golos, Typeface.BOLD)
            setPadding(dp(4f), 0, 0, dp(10f))
        })
        // search bar → Spotlight
        addView(TextView(context).apply {
            text = "   Поиск"; setTextColor(0xFF8E8E93.toInt()); textSize = 17f; typeface = golos
            background = GradientDrawable().apply { cornerRadius = dp(10f).toFloat(); setColor(0xFF1C1C1E.toInt()) }
            setPadding(dp(12f), dp(10f), dp(12f), dp(10f)); isClickable = true
            setOnClickListener { hide(); onOpenSearch?.invoke() }
        })

        val col = LinearLayout(context).apply { orientation = VERTICAL }
        col.addView(dateWidget())
        // User-chosen widgets, each at its chosen S/M/L footprint (driven by WidgetStore).
        widgetsCol = LinearLayout(context).apply { orientation = VERTICAL }
        col.addView(widgetsCol)
        rebuildWidgets()
        col.addView(editButton())
        col.addView(quickActions())
        addView(ScrollView(context).apply { addView(col); isVerticalScrollBarEnabled = false },
            LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }

    fun show() {
        visibility = View.VISIBLE
        translationX = -width.toFloat().coerceAtLeast(1000f)
        animate().translationX(0f).setDuration(260).start()
    }

    fun hide() {
        animate().translationX(-width.toFloat()).setDuration(220).withEndAction {
            visibility = View.GONE; onDismiss?.invoke()
        }.start()
    }

    fun isShown2() = visibility == View.VISIBLE

    private fun card(): GradientDrawable = GradientDrawable().apply { cornerRadius = dp(22f).toFloat(); setColor(0xFF1C1C1E.toInt()) }

    private fun widgetWrap(v: View): View = LinearLayout(context).apply {
        background = card(); setPadding(dp(12f), dp(12f), dp(12f), dp(12f))
        val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT); lp.topMargin = dp(10f); layoutParams = lp
        addView(v, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    private fun dateWidget(): View {
        val c = Calendar.getInstance()
        val weekday = java.text.SimpleDateFormat("EEEE", java.util.Locale("ru")).format(c.time).replaceFirstChar { it.uppercase() }
        val day = c.get(Calendar.DAY_OF_MONTH).toString()
        val month = java.text.SimpleDateFormat("LLLL", java.util.Locale("ru")).format(c.time).replaceFirstChar { it.uppercase() }
        return LinearLayout(context).apply {
            orientation = VERTICAL; background = card(); setPadding(dp(18f), dp(16f), dp(18f), dp(16f))
            val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT); lp.topMargin = dp(10f); layoutParams = lp
            isClickable = true; setOnClickListener { launch("com.ruos.calendar") }
            addView(TextView(context).apply { text = weekday; setTextColor(0xFFFF453A.toInt()); textSize = 15f; typeface = golosM })
            addView(TextView(context).apply { text = day; setTextColor(Color.WHITE); textSize = 46f; typeface = Typeface.create(golos, Typeface.BOLD) })
            addView(TextView(context).apply { text = month; setTextColor(0xFF8E8E93.toInt()); textSize = 14f; typeface = golos })
        }
    }

    private fun quickActions(): View {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL; background = card(); setPadding(dp(8f), dp(12f), dp(8f), dp(12f))
            val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT); lp.topMargin = dp(10f); lp.bottomMargin = dp(16f); layoutParams = lp
        }
        listOf(
            Triple("Календарь", "com.ruos.calendar", 0xFFFF3B30),
            Triple("Напоминания", "com.ruos.reminders", 0xFFFF9F0A),
            Triple("Часы", "com.ruos.clock", 0xFF8E8E93),
            Triple("Заметки", "com.ruos.notes", 0xFFFFCC00)
        ).forEach { (label, pkg, color) ->
            row.addView(LinearLayout(context).apply {
                orientation = VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; isClickable = true
                setOnClickListener { launch(pkg) }
                addView(View(context).apply {
                    background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color.toInt()) }
                }, LayoutParams(dp(34f), dp(34f)))
                addView(TextView(context).apply {
                    text = label; setTextColor(0xFFC7C7CC.toInt()); textSize = 11f; typeface = golos
                    gravity = Gravity.CENTER; maxLines = 1; setPadding(0, dp(4f), 0, 0)
                }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            }, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        }
        return row
    }

    private fun launch(pkg: String) {
        context.packageManager.getLaunchIntentForPackage(pkg)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); runCatching { context.startActivity(it) }; hide()
        }
    }

    /** Rebuild the widget list from the store, each at its chosen footprint. */
    private fun rebuildWidgets() {
        widgetsCol.removeAllViews()
        store.widgets().forEach { spec ->
            val v = buildWidget(spec) ?: return@forEach
            widgetsCol.addView(LinearLayout(context).apply {
                background = card(); setPadding(dp(12f), dp(12f), dp(12f), dp(12f))
                val lp = LayoutParams(LayoutParams.MATCH_PARENT, dp(spec.size.heightDp.toFloat()))
                lp.topMargin = dp(10f); layoutParams = lp
                addView(v, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            })
        }
    }

    /** Map a widget spec to its view, applying the chosen size. Unknown types → null. */
    private fun buildWidget(spec: WidgetSpec): View? = when (spec.type) {
        "weather" -> WeatherWidgetView(context).apply { configure(spec.size) }
        "music" -> MusicWidgetView(context)
        else -> null
    }

    private fun editButton(): View = TextView(context).apply {
        text = "Изменить виджеты"; setTextColor(0xFF0A84FF.toInt()); textSize = 15f; typeface = golosM
        gravity = Gravity.CENTER
        background = GradientDrawable().apply { cornerRadius = dp(12f).toFloat(); setColor(0xFF1C1C1E.toInt()) }
        setPadding(0, dp(12f), 0, dp(12f)); isClickable = true
        val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT); lp.topMargin = dp(12f); layoutParams = lp
        setOnClickListener { openGallery() }
    }

    private fun openGallery() {
        val root = rootView as? android.view.ViewGroup ?: return
        val gallery = WidgetGalleryView(context, store, catalogue, onChanged = { rebuildWidgets() })
        root.addView(gallery, android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT))
        gallery.animateIn()
    }
}
