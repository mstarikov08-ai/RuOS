package com.ruos.findmy.core

import android.content.Context
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator

/**
 * Plays a loud locating alarm — like Find My iPhone's "Play Sound" — at maximum alarm
 * volume even if the phone is silenced, with vibration, for up to ~30 seconds.
 */
object AlarmPlayer {

    private val main = Handler(Looper.getMainLooper())
    private var ringtone: Ringtone? = null
    private var savedVolume = -1
    private var appContext: Context? = null

    fun play(context: Context) {
        stop()
        val ctx = context.applicationContext; appContext = ctx
        val am = ctx.getSystemService(AudioManager::class.java)
        savedVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
        runCatching { am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0) }

        val uri = RingtoneManager.getActualDefaultRingtoneUri(ctx, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        ringtone = RingtoneManager.getRingtone(ctx, uri)?.apply {
            audioAttributes = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            if (Build.VERSION.SDK_INT >= 28) isLooping = true
            play()
        }

        ctx.getSystemService(Vibrator::class.java)?.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 600, 400), 0))

        main.postDelayed({ stop() }, 30_000)
    }

    fun stop() {
        main.removeCallbacksAndMessages(null)
        runCatching { ringtone?.stop() }; ringtone = null
        appContext?.let { ctx ->
            ctx.getSystemService(Vibrator::class.java)?.cancel()
            if (savedVolume >= 0) runCatching {
                ctx.getSystemService(AudioManager::class.java)
                    .setStreamVolume(AudioManager.STREAM_ALARM, savedVolume, 0)
            }
        }
        savedVolume = -1
    }
}
