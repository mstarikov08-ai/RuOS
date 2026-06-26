package com.ruos.alarm.util

object Constants {
    const val SNOOZE_MINUTES = 9
    const val SUNRISE_LEAD_MINUTES = 5
    const val VOLUME_RAMP_SECONDS = 30

    // Intent actions
    const val ACTION_ALARM_FIRE = "com.ruos.alarm.ACTION_FIRE"
    const val ACTION_SUNRISE = "com.ruos.alarm.ACTION_SUNRISE"
    const val ACTION_SNOOZE = "com.ruos.alarm.ACTION_SNOOZE"
    const val ACTION_STOP = "com.ruos.alarm.ACTION_STOP"
    const val ACTION_PREVIEW_START = "com.ruos.alarm.PREVIEW_START"
    const val ACTION_PREVIEW_STOP = "com.ruos.alarm.PREVIEW_STOP"

    const val EXTRA_ALARM_ID = "alarm_id"
    const val EXTRA_SOUND_ID = "sound_id"
    const val EXTRA_SNOOZED = "snoozed"

    // PendingIntent request-code offsets (kept distinct per alarm id)
    const val RC_MAIN = 100000
    const val RC_SUNRISE = 200000
    const val RC_SHOW = 300000

    const val NOTIF_CHANNEL_RING = "ruos_alarm_ring"
    const val NOTIF_CHANNEL_UPCOMING = "ruos_alarm_upcoming"
    const val NOTIF_ID_RING = 4201
}
