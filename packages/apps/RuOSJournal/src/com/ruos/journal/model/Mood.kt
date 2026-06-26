package com.ruos.journal.model

import android.graphics.Color

/** Five moods, iOS-style. Faces are canvas-drawn (no emoji), see MoodFaceView. */
enum class Mood(val id: Int, val ruName: String, val color: Int) {
    GREAT(2, "Отлично", Color.parseColor("#30D158")),
    GOOD(1, "Хорошо", Color.parseColor("#9BD15B")),
    NEUTRAL(0, "Нормально", Color.parseColor("#FFD60A")),
    BAD(-1, "Плохо", Color.parseColor("#FF9F0A")),
    AWFUL(-2, "Ужасно", Color.parseColor("#FF453A"));

    companion object {
        fun byId(id: Int): Mood? = values().firstOrNull { it.id == id }
    }
}
