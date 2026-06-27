package com.ruos.share.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.net.wifi.p2p.WifiP2pDevice
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import com.ruos.share.p2p.WifiDirect
import com.ruos.share.transfer.SendTask

/**
 * Share-sheet target + sender. Collects the shared URIs, discovers nearby RuOS devices via
 * Wi-Fi Direct, and on tapping one connects and streams the files to the group owner with
 * a progress bar.
 */
class SendActivity : Activity() {

    private lateinit var wifi: WifiDirect
    private lateinit var uris: ArrayList<Uri>
    private lateinit var peerList: LinearLayout
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private var sending = false
    private val d get() = resources.displayMetrics.density
    private fun dp(v: Float) = (v * d).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        uris = collectUris()
        if (uris.isEmpty()) { finish(); return }

        setContentView(build())
        wifi = WifiDirect(this).apply {
            onPeers = { peers -> if (!sending) showPeers(peers) }
            onConnected = { _, host -> if (!sending) beginSend(host) }
        }
        wifi.register(); wifi.discoverPeers()
    }

    override fun onDestroy() { if (::wifi.isInitialized) wifi.unregister(); super.onDestroy() }

    private fun build(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK); setPadding(dp(16f), dp(48f), dp(16f), dp(40f))
        }
        col.addView(TextView(this).apply {
            text = "Отправить"; setTextColor(Color.WHITE); textSize = 28f; typeface = Fonts.bold
        })
        col.addView(TextView(this).apply {
            text = "${uris.size} файлов · выберите устройство поблизости"
            setTextColor(0xFF8E8E93.toInt()); textSize = 14f; typeface = Fonts.regular; setPadding(0, dp(4f), 0, dp(12f))
        })
        status = TextView(this).apply { text = "Поиск устройств…"; setTextColor(0xFF0A84FF.toInt()); textSize = 14f; typeface = Fonts.medium }
        col.addView(status)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; visibility = View.GONE
        }
        col.addView(progress, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(6f)).also { it.topMargin = dp(8f) })
        peerList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(ScrollView(this).apply { addView(peerList) })
        return col
    }

    private fun showPeers(peers: List<WifiP2pDevice>) {
        status.text = if (peers.isEmpty()) "Поиск устройств…" else "Доступные устройства"
        peerList.removeAllViews()
        peers.forEach { device ->
            peerList.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14f), dp(14f), dp(14f), dp(14f))
                background = GradientDrawable().apply { cornerRadius = dp(12f).toFloat(); setColor(0xFF1C1C1E.toInt()) }
                isClickable = true
                setOnClickListener { connectTo(device) }
                addView(TextView(this@SendActivity).apply {
                    text = device.deviceName.ifEmpty { "Устройство" }; setTextColor(Color.WHITE); textSize = 17f; typeface = Fonts.medium
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(TextView(this@SendActivity).apply { text = "›"; setTextColor(0xFF8E8E93.toInt()); textSize = 20f })
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); lp.topMargin = dp(8f); layoutParams = lp
            })
        }
    }

    private fun connectTo(device: WifiP2pDevice) {
        status.text = "Подключение к «${device.deviceName}»…"
        wifi.connect(device) { ok -> if (!ok) runOnUiThread { status.text = "Не удалось подключиться. Повторите." } }
        // the actual send starts from onConnected (groupOwnerAddress known)
    }

    private fun beginSend(host: String) {
        if (sending) return
        sending = true
        runOnUiThread { status.text = "Передача…"; progress.visibility = View.VISIBLE; peerList.removeAllViews() }
        val name = android.os.Build.MODEL
        Thread {
            SendTask(this, host, uris, name).run(
                onProgress = { sent, total ->
                    val pct = if (total > 0) (sent * 100 / total).toInt() else 0
                    runOnUiThread { progress.progress = pct }
                },
                onResult = { ok, msg ->
                    runOnUiThread {
                        status.text = if (ok) "Готово" else msg
                        if (ok) status.postDelayed({ wifi.removeGroup(); finish() }, 1200)
                        else { sending = false; progress.visibility = View.GONE }
                    }
                })
        }.start()
    }

    private fun collectUris(): ArrayList<Uri> {
        intent.getParcelableArrayListExtra<Uri>("uris")?.let { return ArrayList(it) }
        val out = ArrayList<Uri>()
        when (intent?.action) {
            Intent.ACTION_SEND -> intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { out.add(it) }
            Intent.ACTION_SEND_MULTIPLE -> intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { out.addAll(it) }
        }
        return out
    }
}
