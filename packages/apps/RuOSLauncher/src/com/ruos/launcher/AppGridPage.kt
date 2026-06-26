package com.ruos.launcher

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.GridLayout

/**
 * One page of the iOS-style home screen — a 4×6 grid of AppIconViews.
 */
class AppGridPage @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GridLayout(context, attrs) {

    var onAppLongPress: (() -> Unit)? = null

    private val iconViews = mutableListOf<AppIconView>()
    private var apps = emptyList<AppInfo>()

    init {
        columnCount = HomePagePager.COLS
        rowCount = HomePagePager.ROWS
        orientation = HORIZONTAL
    }

    fun setApps(list: List<AppInfo>) {
        apps = list
        removeAllViews()
        iconViews.clear()

        list.forEach { info ->
            val icon = AppIconView(context).apply {
                bind(info)
                onLongPress = { onAppLongPress?.invoke() }
            }
            val spec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            val params = GridLayout.LayoutParams(spec, spec).apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                setGravity(Gravity.FILL_HORIZONTAL or Gravity.TOP)
                val margin = context.resources.getDimensionPixelSize(R.dimen.icon_margin)
                setMargins(margin, margin, margin, margin)
            }
            addView(icon, params)
            iconViews.add(icon)
        }
    }

    fun setJiggleMode(active: Boolean) {
        iconViews.forEach { it.setJiggleMode(active) }
    }
}
