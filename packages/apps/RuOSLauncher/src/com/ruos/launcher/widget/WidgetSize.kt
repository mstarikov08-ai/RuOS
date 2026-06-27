package com.ruos.launcher.widget

/**
 * iOS widget sizes. Unlike Android's free-resize, iOS offers three fixed footprints chosen
 * when the widget is added; each maps to a span on the home grid. We keep the same model so
 * RuOS widgets feel native-iOS: pick S / M / L, the widget re-lays-out for that footprint.
 */
enum class WidgetSize(val label: String, val cols: Int, val rows: Int, val heightDp: Int) {
    SMALL("Маленький", 2, 2, 150),
    MEDIUM("Средний", 4, 2, 150),
    LARGE("Большой", 4, 4, 320);

    companion object {
        fun from(name: String?): WidgetSize =
            values().firstOrNull { it.name == name } ?: MEDIUM
    }
}

/** A widget placed by the user: which widget [type] (e.g. "weather") at which [size]. */
data class WidgetSpec(val type: String, val size: WidgetSize)
