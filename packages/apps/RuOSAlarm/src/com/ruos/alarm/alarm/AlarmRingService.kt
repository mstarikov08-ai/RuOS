package com.ruos.alarm.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import com.ruos.alarm.model.Alarm
import com.ruos.alarm.model.AlarmStore
import com.ruos.alarm.model.VibrationPattern
import com.ruos.alarm.ui.AlarmRingActivity
import com.ruos.alarm.util.Constants
import com.ruos.alarm.util.UpcomingNotifier

/**
 * Foreground service that actually makes the alarm ring:
 *  - plays the chosen sound on STREAM_ALARM (so it sounds even when the phone is on
 *    silent — the alarm stream is not muted by the ringer),
 *  - ramps the volume from near-silent to full over 30s so it doesn't shock you,
 *  - pulses the haptic pattern continuously,
 *  - holds a wake lock and posts a full-screen-intent notification that the OS turns
 *    into the full-screen [AlarmRingActivity] even on the lock screen.
 *
 * "sunrise" mode posts the full-screen screen (which brightens) without sound, 5
 * minutes ahead; the real fire then re-enters in "ring" mode.
 */
class AlarmRingService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var rampStep = 0
    private var savedAlarmVolume = -1
    private var currentAlarm: Alarm? = null

    private val rampRunnable = object : Runnable {
        override fun run() {
            val steps = Constants.VOLUME_RAMP_SECONDS
            rampStep++
            val frac = (rampStep.toFloat() / steps).coerceIn(0f, 1f)
            // Ease-in curve so the first seconds are very gentle.
            val gain = frac * frac
            player?.setVolume(gain, gain)
            if (rampStep < steps) handler.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            Constants.ACTION_STOP -> { stopEverything(); return START_NOT_STICKY }
            Constants.ACTION_SNOOZE -> { snooze(); return START_NOT_STICKY }
        }

        val id = intent?.getLongExtra(Constants.EXTRA_ALARM_ID, -1L) ?: -1L
        val mode = intent?.getStringExtra("mode") ?: "ring"
        val alarm = AlarmStore(this).get(id)
        currentAlarm = alarm

        startForeground(Constants.NOTIF_ID_RING, buildRingNotification(id, mode, alarm?.label ?: ""))

        if (mode == "sunrise") {
            // Screen-brightening happens in the activity; the service just needs to
            // have launched it. Keep alive briefly, then release.
            handler.postDelayed({ stopSelf() }, 2000)
            return START_STICKY
        }

        if (alarm != null) startRinging(alarm)
        return START_STICKY
    }

    private fun startRinging(alarm: Alarm) {
        acquireWakeLock()

        val am = getSystemService(AudioManager::class.java)
        savedAlarmVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
        am.setStreamVolume(AudioManager.STREAM_ALARM,
            am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)

        val resId = resources.getIdentifier(alarm.sound.rawName, "raw", packageName)
        if (resId != 0) {
            player = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build())
                val afd = resources.openRawResourceFd(resId)
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                isLooping = true
                setVolume(0.04f, 0.04f)   // start near-silent for the ramp
                prepare()
                start()
            }
            rampStep = 0
            handler.postDelayed(rampRunnable, 1000)
        }

        startVibration(alarm.vibration)
    }

    private fun startVibration(pattern: VibrationPattern) {
        if (pattern == VibrationPattern.NONE) return
        vibrator = getSystemService(Vibrator::class.java)
        // Repeat the pattern from index 0 → continuous pulsing.
        vibrator?.vibrate(VibrationEffect.createWaveform(pattern.timings, 0))
    }

    private fun snooze() {
        currentAlarm?.let { AlarmScheduler(this).scheduleSnooze(it, Constants.SNOOZE_MINUTES) }
        stopEverything()
        UpcomingNotifier(this).refresh()
    }

    private fun stopEverything() {
        handler.removeCallbacks(rampRunnable)
        player?.runCatching { stop(); release() }
        player = null
        vibrator?.cancel()
        if (savedAlarmVolume >= 0) {
            runCatching {
                getSystemService(AudioManager::class.java)
                    .setStreamVolume(AudioManager.STREAM_ALARM, savedAlarmVolume, 0)
            }
        }
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        UpcomingNotifier(this).refresh()
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, "ruos:alarm").apply { acquire(5 * 60_000L) }
    }

    private fun releaseWakeLock() {
        wakeLock?.runCatching { if (isHeld) release() }
        wakeLock = null
    }

    private fun buildRingNotification(id: Long, mode: String, label: String): android.app.Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(Constants.NOTIF_CHANNEL_RING) == null) {
            nm.createNotificationChannel(NotificationChannel(
                Constants.NOTIF_CHANNEL_RING, "Сигнал будильника",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setSound(null, null)            // we play audio ourselves
                enableVibration(false)          // we vibrate ourselves
                setBypassDnd(true)
            })
        }

        val full = PendingIntent.getActivity(
            this, Constants.RC_SHOW + id.toInt(),
            Intent(this, AlarmRingActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(Constants.EXTRA_ALARM_ID, id)
                putExtra("mode", mode)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return android.app.Notification.Builder(this, Constants.NOTIF_CHANNEL_RING)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(if (mode == "sunrise") "Скоро будильник" else "Будильник")
            .setContentText(if (label.isNotBlank()) label else "Подъём")
            .setCategory(android.app.Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setFullScreenIntent(full, true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(rampRunnable)
        player?.runCatching { release() }
        vibrator?.cancel()
        releaseWakeLock()
    }
}
