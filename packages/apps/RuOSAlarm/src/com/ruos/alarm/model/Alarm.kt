package com.ruos.alarm.model

/** Built-in alarm sounds (bundled CC0 originals in res/raw). */
enum class AlarmSound(val id: String, val ruName: String, val rawName: String) {
    GENTLE("gentle", "Нежный", "alarm_gentle"),
    CLASSIC("classic", "Классический", "alarm_classic"),
    NATURE("nature", "Природа", "alarm_nature"),
    MELODY("melody", "Мелодия", "alarm_melody");

    companion object {
        fun byId(id: String?): AlarmSound = values().firstOrNull { it.id == id } ?: GENTLE
    }
}

/** Sleep (bedtime) sounds that play until the alarm. */
enum class SleepSound(val id: String, val ruName: String, val rawName: String?) {
    NONE("none", "Выключено", null),
    WHITE("white", "Белый шум", "sleep_white"),
    RAIN("rain", "Дождь", "sleep_rain"),
    OCEAN("ocean", "Океан", "sleep_ocean");

    companion object {
        fun byId(id: String?): SleepSound = values().firstOrNull { it.id == id } ?: NONE
    }
}

/** Haptic pattern used while the alarm rings. */
enum class VibrationPattern(val id: String, val ruName: String, val timings: LongArray) {
    BASIC("basic", "Базовая", longArrayOf(0, 400, 600)),
    HEARTBEAT("heartbeat", "Сердцебиение", longArrayOf(0, 120, 120, 120, 700)),
    RAPID("rapid", "Частая", longArrayOf(0, 100, 150)),
    NONE("none", "Нет", longArrayOf(0));

    companion object {
        fun byId(id: String?): VibrationPattern = values().firstOrNull { it.id == id } ?: BASIC
    }
}

/**
 * A single alarm. [repeatDays] holds 0=Mon .. 6=Sun (RuOS week starts Monday).
 * An empty set means a one-shot alarm that auto-disables after firing.
 */
data class Alarm(
    val id: Long,
    var hour: Int,
    var minute: Int,
    var label: String = "",
    var repeatDays: MutableSet<Int> = mutableSetOf(),
    var enabled: Boolean = true,
    var soundId: String = AlarmSound.GENTLE.id,
    var snoozeEnabled: Boolean = true,
    var vibrationId: String = VibrationPattern.BASIC.id,
    var sunrise: Boolean = false,
    /** When true, the next occurrence is skipped once, then this clears. */
    var skipNext: Boolean = false
) {
    val isOneShot: Boolean get() = repeatDays.isEmpty()

    val sound: AlarmSound get() = AlarmSound.byId(soundId)
    val vibration: VibrationPattern get() = VibrationPattern.byId(vibrationId)

    /** Russian summary of repeat days, iOS-style. */
    fun repeatSummary(): String {
        val names = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
        val sorted = repeatDays.sorted()
        return when {
            sorted.isEmpty() -> "Никогда"
            sorted.size == 7 -> "Каждый день"
            sorted == listOf(0, 1, 2, 3, 4) -> "По будням"
            sorted == listOf(5, 6) -> "По выходным"
            else -> sorted.joinToString(", ") { names[it] }
        }
    }

    fun timeText(): String = "%02d:%02d".format(hour, minute)
}
