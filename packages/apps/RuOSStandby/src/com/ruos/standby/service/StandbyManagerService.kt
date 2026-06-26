package com.ruos.standby.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.ruos.standby.model.StandbySettings
import com.ruos.standby.ui.StandbyActivity
import kotlin.math.abs

/**
 * While charging, watches the accelerometer; once the phone is laid in landscape and
 * held stable (placed on a stand), it posts a full-screen-intent notification that the
 * OS turns into the full-screen [StandbyActivity] — the reliable Android-14 way to
 * launch a takeover screen from the background. Unplugging stops it and tells the
 * activity to exit.
 */
class StandbyManagerService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var sensorManager: SensorManager? = null
    private var launched = false
    private var landscapeSince = 0L

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            val x = e.values[0]; val y = e.values[1]
            val landscape = abs(x) > 6f && abs(x) > abs(y)
            if (landscape) {
                if (landscapeSince == 0L) landscapeSince = System.currentTimeMillis()
                if (!launched && System.currentTimeMillis() - landscapeSince > STABLE_MS) launch()
            } else {
                landscapeSince = 0L
            }
        }
        override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopEverything(); return START_NOT_STICKY }
            else -> start()
        }
        return START_STICKY
    }

    private fun start() {
        startForeground(NOTIF_ID, buildSilentNotif())
        sensorManager = getSystemService(SensorManager::class.java)
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager?.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    private fun launch() {
        if (launched) return
        launched = true
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_FS) == null) {
            nm.createNotificationChannel(NotificationChannel(
                CHANNEL_FS, "Режим ожидания", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null); enableVibration(false)
            })
        }
        val full = PendingIntent.getActivity(
            this, 0, Intent(this, StandbyActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notif = android.app.Notification.Builder(this, CHANNEL_FS)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Режим ожидания")
            .setOngoing(true)
            .setFullScreenIntent(full, true)
            .build()
        nm.notify(NOTIF_ID + 1, notif)
    }

    private fun stopEverything() {
        sensorManager?.unregisterListener(listener)
        sendBroadcast(Intent(ACTION_EXIT).setPackage(packageName))
        getSystemService(NotificationManager::class.java).cancel(NOTIF_ID + 1)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildSilentNotif(): android.app.Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(
                CHANNEL, "Зарядка", NotificationManager.IMPORTANCE_MIN).apply { setShowBadge(false) })
        }
        return android.app.Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle("Режим ожидания готов")
            .setOngoing(true).build()
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager?.unregisterListener(listener)
    }

    companion object {
        const val ACTION_START = "com.ruos.standby.START"
        const val ACTION_STOP = "com.ruos.standby.STOP"
        const val ACTION_EXIT = "com.ruos.standby.EXIT"
        private const val CHANNEL = "ruos_standby"
        private const val CHANNEL_FS = "ruos_standby_fs"
        private const val NOTIF_ID = 4301
        private const val STABLE_MS = 2500L
    }
}
