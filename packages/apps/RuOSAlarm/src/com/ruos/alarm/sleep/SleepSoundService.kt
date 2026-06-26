package com.ruos.alarm.sleep

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.ruos.alarm.model.SleepSound
import com.ruos.alarm.ui.BedtimeActivity

/**
 * Plays a looping sleep sound (white noise / rain / ocean) at low volume until the
 * wake time or until stopped. Best-effort switches the phone to Do-Not-Disturb
 * (alarms still ring) while it plays, if notification-policy access has been granted.
 */
class SleepSoundService : Service() {

    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var dndChanged = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopEverything(); return START_NOT_STICKY }

        val soundId = intent?.getStringExtra("soundId") ?: SleepSound.NONE.id
        val stopAt = intent?.getLongExtra("stopAt", 0L) ?: 0L
        val sound = SleepSound.byId(soundId)
        if (sound.rawName == null) { stopEverything(); return START_NOT_STICKY }

        startForeground(NOTIF_ID, buildNotif(sound.ruName))
        enableDnd()
        play(sound)

        if (stopAt > System.currentTimeMillis()) {
            handler.postDelayed({ stopEverything() }, stopAt - System.currentTimeMillis())
        }
        return START_STICKY
    }

    private fun play(sound: SleepSound) {
        val resId = resources.getIdentifier(sound.rawName, "raw", packageName)
        if (resId == 0) return
        player = MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            val afd = resources.openRawResourceFd(resId)
            setDataSource(afd.fileDescriptor, afd.startOffset, afd.length); afd.close()
            isLooping = true; setVolume(0.4f, 0.4f); prepare(); start()
        }
    }

    private fun enableDnd() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.isNotificationPolicyAccessGranted) {
            runCatching {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
                dndChanged = true
            }
        }
    }

    private fun restoreDnd() {
        if (!dndChanged) return
        val nm = getSystemService(NotificationManager::class.java)
        runCatching { nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL) }
        dndChanged = false
    }

    private fun stopEverything() {
        handler.removeCallbacksAndMessages(null)
        player?.runCatching { stop(); release() }
        player = null
        restoreDnd()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotif(name: String): android.app.Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(
                CHANNEL, "Звуки сна", NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) })
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, BedtimeActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(
            this, 1, Intent(this, SleepSoundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return android.app.Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode)
            .setContentTitle("Звуки сна: $name")
            .setContentText("Играет до пробуждения")
            .setContentIntent(open)
            .addAction(android.app.Notification.Action.Builder(
                null, "Стоп", stop).build())
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.runCatching { release() }
        restoreDnd()
    }

    companion object {
        private const val CHANNEL = "ruos_sleep"
        private const val NOTIF_ID = 4203
        const val ACTION_STOP = "com.ruos.alarm.SLEEP_STOP"
    }
}
