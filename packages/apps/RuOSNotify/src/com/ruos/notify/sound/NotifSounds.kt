package com.ruos.notify.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.VibrationEffect
import android.os.Vibrator
import com.ruos.notify.model.NotifItem

/**
 * Plays the gentle notification tone + a matching haptic. Critical alerts use the
 * alarm usage so they sound even on silent / DND (iOS "critical alerts"); normal
 * notifications use the notification usage and respect the ringer.
 */
class NotifSounds(private val context: Context) {

    private val vibrator = context.getSystemService(Vibrator::class.java)

    fun play(item: NotifItem, soundsEnabled: Boolean) {
        // Haptic always fires on arrival (subtle), even when the tone is suppressed.
        haptic(item.critical)
        if (!soundsEnabled && !item.critical) return

        val raw = context.resources.getIdentifier("notify_gentle", "raw", context.packageName)
        if (raw == 0) return
        val usage = if (item.critical) AudioAttributes.USAGE_ALARM
            else AudioAttributes.USAGE_NOTIFICATION_EVENT
        try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(AudioAttributes.Builder()
                .setUsage(usage).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            val afd = context.resources.openRawResourceFd(raw)
            mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length); afd.close()
            mp.setOnCompletionListener { it.release() }
            mp.setVolume(if (item.critical) 1f else 0.7f, if (item.critical) 1f else 0.7f)
            mp.prepare(); mp.start()
        } catch (_: Exception) {}
    }

    private fun haptic(critical: Boolean) {
        val effect = if (critical)
            VibrationEffect.createWaveform(longArrayOf(0, 60, 70, 60), -1)
        else
            VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE)
        runCatching { vibrator?.vibrate(effect) }
    }
}
