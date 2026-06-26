package com.ruos.health

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StepCounterService : Service(), SensorEventListener {

    companion object {
        const val CHANNEL_ID = "ruos_health_steps"
        const val NOTIF_ID = 1001
        const val ACTION_STEP_UPDATE = "com.ruos.health.STEP_UPDATE"
        const val EXTRA_STEPS = "steps"
        const val PREFS_NAME = "ruos_health_prefs"
        const val KEY_BASELINE = "step_baseline"
        const val KEY_BASELINE_DATE = "step_baseline_date"
        const val KEY_DAILY_STEPS = "daily_steps"
    }

    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null
    private lateinit var prefs: SharedPreferences
    private var dailySteps = 0

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification(0))

        stepSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_STEP_COUNTER) return
        val totalSteps = event.values[0].toLong()

        // Check if we need to reset baseline for a new day
        val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val baselineDate = prefs.getString(KEY_BASELINE_DATE, "")
        val baseline = prefs.getLong(KEY_BASELINE, -1L)

        if (baseline == -1L || baselineDate != today) {
            // New session or new day — reset baseline
            prefs.edit()
                .putLong(KEY_BASELINE, totalSteps)
                .putString(KEY_BASELINE_DATE, today)
                .apply()
            dailySteps = 0
        } else {
            dailySteps = (totalSteps - baseline).toInt().coerceAtLeast(0)
        }

        prefs.edit().putInt(KEY_DAILY_STEPS, dailySteps).apply()

        // Update notification
        val notifManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notifManager.notify(NOTIF_ID, buildNotification(dailySteps))

        // Broadcast to activity
        val broadcastIntent = Intent(ACTION_STEP_UPDATE).apply {
            putExtra(EXTRA_STEPS, dailySteps)
            setPackage(packageName)
        }
        sendBroadcast(broadcastIntent)
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Шагомер",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Подсчёт шагов в фоне"
            setShowBadge(false)
        }
        val notifManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notifManager.createNotificationChannel(channel)
    }

    private fun buildNotification(steps: Int): Notification {
        val activityIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, HealthActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Здоровье")
            .setContentText("Подсчёт шагов активен: $steps шагов сегодня")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(activityIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }
}
