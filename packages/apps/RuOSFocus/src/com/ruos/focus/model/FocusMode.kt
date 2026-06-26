package com.ruos.focus.model

/**
 * A single Focus (режим фокусирования). Mirrors iOS 18 Focus: a named, coloured mode
 * that filters which apps and people can break through with banners/sounds, optionally
 * activated on a schedule.
 *
 * The list of allowed packages is an *allow-list*: when the focus is active, only these
 * apps may show a heads-up banner / play a sound. Everything else is recorded silently
 * (still in Notification Centre, still badged). [allowCalls] lets phone calls through
 * regardless. [suppressAll] is the "Не беспокоить" extreme — nothing breaks through.
 */
data class FocusMode(
    val id: String,
    val name: String,
    val colorHex: Long,
    val icon: FocusIcon,
    val allowedPackages: MutableSet<String> = linkedSetOf(),
    val allowCalls: Boolean = true,
    val allowRepeatCalls: Boolean = true,   // second call from same person within 3 min
    val suppressAll: Boolean = false,       // DND: nothing breaks through at all
    val dimLockScreen: Boolean = true,      // iOS: dim the lock screen while active
    val hideNotifications: Boolean = false, // hide silenced notifications from lock screen
    val autoReply: String = "",             // text to (conceptually) auto-reply with
    val schedule: FocusSchedule? = null,
    val builtIn: Boolean = false
) {
    val color: Int get() = colorHex.toInt()

    companion object {
        /** The six iOS-style presets RuOS ships with. */
        fun builtIns(): List<FocusMode> = listOf(
            FocusMode(
                id = "dnd", name = "Не беспокоить", colorHex = 0xFF5E5CE6,
                icon = FocusIcon.MOON, suppressAll = true, builtIn = true
            ),
            FocusMode(
                id = "work", name = "Работа", colorHex = 0xFF0A84FF,
                icon = FocusIcon.BRIEFCASE, allowCalls = true, builtIn = true
            ),
            FocusMode(
                id = "personal", name = "Личное", colorHex = 0xFFAF52DE,
                icon = FocusIcon.PERSON, allowCalls = true, builtIn = true
            ),
            FocusMode(
                id = "sleep", name = "Сон", colorHex = 0xFFFF9F0A,
                icon = FocusIcon.BED, suppressAll = true, dimLockScreen = true,
                hideNotifications = true, builtIn = true
            ),
            FocusMode(
                id = "driving", name = "Вождение", colorHex = 0xFF30D158,
                icon = FocusIcon.CAR, allowCalls = true, allowRepeatCalls = true,
                autoReply = "Я за рулём и отвечу позже.", builtIn = true
            ),
            FocusMode(
                id = "study", name = "Учёба", colorHex = 0xFFFF375F,
                icon = FocusIcon.BOOK, allowCalls = false, builtIn = true
            )
        )
    }
}

/** Canvas-drawn glyph identity (project rule: no emoji, every icon is drawn). */
enum class FocusIcon { MOON, BRIEFCASE, PERSON, BED, CAR, BOOK, STAR, HEART, GAME, DUMBBELL }
