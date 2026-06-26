package com.ruos.screenrecord

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast

/**
 * Records the screen via MediaProjection + MediaRecorder into Movies/RuOS, with an
 * optional microphone track. Shows an iOS-style countdown, a red recording indicator
 * (tap to stop), and an ongoing notification with a Stop action. Broadcasts its state so
 * the Control Centre tile can reflect it.
 */
class ScreenRecordService : Service() {

    private val main = Handler(Looper.getMainLooper())
    private val wm get() = getSystemService(WindowManager::class.java)

    private var projection: MediaProjection? = null
    private var recorder: MediaRecorder? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var pfd: ParcelFileDescriptor? = null
    private var outUri: Uri? = null

    private var indicator: RedIndicatorView? = null
    private var countdown: CountdownView? = null
    private var startElapsed = 0L
    private val ticker = object : Runnable {
        override fun run() {
            indicator?.setElapsed((android.os.SystemClock.elapsedRealtime() - startElapsed))
            main.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopRecording(); return START_NOT_STICKY }
            else -> beginFlow(intent)
        }
        return START_STICKY
    }

    private fun beginFlow(intent: Intent?) {
        val code = intent?.getIntExtra(EXTRA_CODE, 0) ?: 0
        val data = intent?.getParcelableExtra<Intent>(EXTRA_DATA)
        val mic = intent?.getBooleanExtra(EXTRA_MIC, false) ?: false
        if (code == 0 || data == null) { stopSelf(); return }

        // FGS (type mediaProjection) must be running before the projection is used (A14).
        startForeground(NOTIF_ID, buildNotification(),
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        val mpm = getSystemService(MediaProjectionManager::class.java)
        projection = mpm.getMediaProjection(code, data)
        if (projection == null) { stopSelf(); return }
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stopRecording() }
        }, main)

        isRunning = true
        broadcastState(true)
        showCountdown { startRecording(mic) }
    }

    private fun showCountdown(then: () -> Unit) {
        val view = CountdownView(this); countdown = view
        val lp = overlayParams(touchable = false).apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            gravity = Gravity.CENTER
        }
        runCatching { wm.addView(view, lp) }
        view.run(3) { removeView(view); countdown = null; then() }
    }

    private fun startRecording(mic: Boolean) {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(metrics)
        val w = metrics.widthPixels and 1.inv()      // even dimensions for the encoder
        val h = metrics.heightPixels and 1.inv()
        val dpi = metrics.densityDpi

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "RuOS_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/RuOS")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        outUri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        val uri = outUri ?: run { stopRecording(); return }
        pfd = contentResolver.openFileDescriptor(uri, "rw")

        val rec = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
        recorder = rec
        runCatching {
            if (mic) rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            if (mic) {
                rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                rec.setAudioEncodingBitRate(128_000); rec.setAudioSamplingRate(44_100)
            }
            rec.setVideoSize(w, h)
            rec.setVideoEncodingBitRate(8_000_000)
            rec.setVideoFrameRate(30)
            rec.setOutputFile(pfd!!.fileDescriptor)
            rec.prepare()
            virtualDisplay = projection!!.createVirtualDisplay(
                "RuOSScreenRecord", w, h, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, rec.surface, null, null)
            rec.start()
        }.onFailure {
            Toast.makeText(this, "Не удалось начать запись", Toast.LENGTH_SHORT).show()
            stopRecording(); return
        }

        startElapsed = android.os.SystemClock.elapsedRealtime()
        showIndicator()
        main.post(ticker)
    }

    private fun showIndicator() {
        val view = RedIndicatorView(this) { stopRecording() }
        indicator = view
        val lp = overlayParams(touchable = true).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (8 * resources.displayMetrics.density).toInt()
        }
        runCatching { wm.addView(view, lp) }
    }

    private fun stopRecording() {
        main.removeCallbacks(ticker)
        runCatching { recorder?.stop() }
        runCatching { recorder?.reset(); recorder?.release() }; recorder = null
        runCatching { virtualDisplay?.release() }; virtualDisplay = null
        runCatching { projection?.stop() }; projection = null
        runCatching { pfd?.close() }; pfd = null
        outUri?.let { uri ->
            runCatching {
                val v = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                contentResolver.update(uri, v, null, null)
            }
            main.post { Toast.makeText(this, "Видео сохранено в Фото", Toast.LENGTH_SHORT).show() }
        }
        outUri = null
        indicator?.let { removeView(it) }; indicator = null
        countdown?.let { removeView(it) }; countdown = null
        isRunning = false
        broadcastState(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun removeView(v: android.view.View) { runCatching { wm.removeView(v) } }

    private fun overlayParams(touchable: Boolean) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT
    )

    private fun broadcastState(active: Boolean) {
        runCatching {
            sendBroadcast(Intent(ACTION_STATE).putExtra(EXTRA_ACTIVE, active)
                .setPackage("com.android.systemui"))
        }
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(
            CHANNEL, "Запись экрана", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 1,
            Intent(this, ScreenRecordService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setContentTitle("Идёт запись экрана")
            .setContentText("Нажмите, чтобы остановить")
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Остановить", stop).build())
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.ruos.screenrecord.STOP"
        const val ACTION_STATE = "com.ruos.screenrecord.STATE"
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        const val EXTRA_MIC = "mic"
        const val EXTRA_ACTIVE = "active"
        private const val CHANNEL = "ruos_screenrec"
        private const val NOTIF_ID = 4242
        @Volatile var isRunning = false
    }
}
