package com.ruos.share.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import com.ruos.share.p2p.WifiDirect
import com.ruos.share.transfer.ReceiveServer
import com.ruos.share.transfer.TransferHeader
import com.ruos.share.ui.IncomingActivity
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Receive mode: makes the device an autonomous Wi-Fi Direct group owner and runs the
 * [ReceiveServer]. When a transfer header arrives it blocks the receive thread on a latch
 * while [IncomingActivity] asks the user to accept; the activity resolves the latch.
 * Foreground service with progress + completion notifications.
 */
class ReceiverService : Service() {

    private lateinit var wifi: WifiDirect
    private var server: ReceiveServer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopEverything(); return START_NOT_STICKY }
            else -> startReceiving()
        }
        return START_STICKY
    }

    private fun startReceiving() {
        startForeground(NOTIF_ID, notif("Приём включён", "Устройство видно поблизости для RuOS Share"),
            if (android.os.Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0)
        running = true
        wifi = WifiDirect(this).also { it.register(); it.createGroup { } }
        server = ReceiveServer(this,
            accept = { header -> promptBlocking(header) },
            onProgress = { header, received ->
                val pct = if (header.totalBytes > 0) (received * 100 / header.totalBytes).toInt() else 0
                update(NOTIF_ID, notif("Приём файлов — $pct%", "От: ${header.sender}"))
            },
            onComplete = { header, ok ->
                update(NOTIF_PROGRESS, notif(
                    if (ok) "Файлы получены" else "Передача не удалась",
                    if (ok) "${header.files.size} файлов в «Загрузки/RuOS Share»" else ""))
            }
        ).also { it.start() }
    }

    private fun stopEverything() {
        running = false
        server?.stop(); server = null
        if (::wifi.isInitialized) { wifi.removeGroup(); wifi.unregister() }
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }

    /** Blocks the receive thread until the user accepts/declines via IncomingActivity. */
    private fun promptBlocking(header: TransferHeader): Boolean {
        latch = CountDownLatch(1); accepted = false
        val summary = "${header.files.size} файлов · ${human(header.totalBytes)}"
        runCatching {
            startActivity(Intent(this, IncomingActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("sender", header.sender).putExtra("summary", summary))
        }
        runCatching { latch?.await(60, TimeUnit.SECONDS) }
        return accepted
    }

    private fun notif(title: String, text: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "RuOS Share", NotificationManager.IMPORTANCE_LOW))
        return Notification.Builder(this, CHANNEL)
            .setContentTitle(title).setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload).setOngoing(running).build()
    }

    private fun update(id: Int, n: Notification) = getSystemService(NotificationManager::class.java).notify(id, n)

    private fun human(bytes: Long): String {
        if (bytes < 1024) return "$bytes Б"
        val u = arrayOf("КБ", "МБ", "ГБ"); var v = bytes.toDouble() / 1024; var i = 0
        while (v >= 1024 && i < u.size - 1) { v /= 1024; i++ }
        return "%.1f %s".format(v, u[i])
    }

    companion object {
        const val ACTION_START = "com.ruos.share.START"
        const val ACTION_STOP = "com.ruos.share.STOP"
        private const val CHANNEL = "ruos_share"
        private const val NOTIF_ID = 7001
        private const val NOTIF_PROGRESS = 7002

        @Volatile var running = false; private set
        @Volatile private var latch: CountDownLatch? = null
        @Volatile private var accepted = false

        /** Called by IncomingActivity with the user's choice. */
        fun resolve(ok: Boolean) { accepted = ok; latch?.countDown() }
    }
}
