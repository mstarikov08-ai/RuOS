package com.ruos.focus.model

/**
 * A time-of-day schedule for a focus. If enabled, the focus turns on at [startMin] and
 * off at [endMin] (minutes since midnight) on the selected [days]. An overnight window
 * (end <= start, e.g. Сон 23:00→07:00) is handled by [contains].
 */
data class FocusSchedule(
    val enabled: Boolean = false,
    val startMin: Int = 22 * 60,
    val endMin: Int = 7 * 60,
    /** bit set, Mon..Sun = bits 0..6; 0x7F = every day. */
    val days: Int = 0x7F
) {
    fun dayEnabled(mondayZeroIndex: Int): Boolean = (days shr mondayZeroIndex) and 1 == 1

    /** Is [nowMin] (with [todayMondayZero] = today's index Mon..Sun) inside the window? */
    fun contains(nowMin: Int, todayMondayZero: Int): Boolean {
        if (!enabled) return false
        val overnight = endMin <= startMin
        return if (!overnight) {
            dayEnabled(todayMondayZero) && nowMin in startMin until endMin
        } else {
            // window spans midnight: belongs to the day it *started* on
            if (nowMin >= startMin) dayEnabled(todayMondayZero)
            else dayEnabled((todayMondayZero + 6) % 7) && nowMin < endMin
        }
    }

    fun startText(): String = hhmm(startMin)
    fun endText(): String = hhmm(endMin)

    private fun hhmm(m: Int) = "%02d:%02d".format(m / 60, m % 60)
}
