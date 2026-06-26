package com.ruos.settings.sections

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.wifi.WifiManager
import android.net.wifi.ScanResult
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

class WifiSettingsActivity : Activity() {

    private val wifiManager by lazy { applicationContext.getSystemService(WifiManager::class.java) }
    private lateinit var networkList: LinearLayout
    private lateinit var wifiSwitch: Switch
    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) showScanResults()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUI())
        registerReceiver(scanReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
        wifiManager.startScan()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(scanReceiver) } catch (_: Exception) {}
    }

    private fun buildUI(): View {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#F2F2F7")) }
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(100), 0, dp(40))
        }

        // Wi-Fi toggle
        col.addView(toggleRow())
        col.addView(spacer(20))

        // Available networks
        col.addView(sectionLabel("МОИ СЕТИ"))
        networkList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val wrapper = FrameLayout(this).apply { setPadding(dp(16), 0, dp(16), 0) }
        networkList.background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat(); setColor(Color.WHITE)
        }
        wrapper.addView(networkList)
        col.addView(wrapper)

        scroll.addView(col)
        root.addView(scroll)
        return root
    }

    private fun toggleRow(): View {
        val wrapper = FrameLayout(this).apply { setPadding(dp(16), 0, dp(16), 0) }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat(); setColor(Color.WHITE)
            }
        }
        row.addView(TextView(this).apply {
            text = "Wi-Fi"; textSize = 17f; setTextColor(Color.BLACK)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        wifiSwitch = Switch(this).apply {
            isChecked = wifiManager.isWifiEnabled
            setOnCheckedChangeListener { _, checked -> wifiManager.isWifiEnabled = checked }
        }
        row.addView(wifiSwitch)
        wrapper.addView(row)
        return wrapper
    }

    private fun showScanResults() {
        networkList.removeAllViews()
        val results = wifiManager.scanResults.sortedByDescending { it.level }.take(10)
        results.forEachIndexed { i, result ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(11), dp(16), dp(11))
                isClickable = true
                isFocusable = true
            }
            row.addView(TextView(this).apply {
                text = result.SSID.ifEmpty { "Скрытая сеть" }
                textSize = 17f; setTextColor(Color.BLACK)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            val signalPct = WifiManager.calculateSignalLevel(result.level, 4)
            row.addView(TextView(this).apply {
                text = "▂▄▆█".take(signalPct + 1)
                setTextColor(Color.parseColor("#34AADC"))
                textSize = 14f
            })
            row.addView(disclosureArrow())
            networkList.addView(row)
            if (i < results.size - 1) {
                networkList.addView(View(this).apply { setBackgroundColor(Color.parseColor("#C6C6C8")) },
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).also { it.marginStart = dp(16) })
            }
        }
    }

    private fun disclosureArrow() = TextView(this).apply {
        text = "›"; textSize = 22f; setTextColor(Color.parseColor("#C7C7CC"))
    }

    private fun sectionLabel(text: String) = TextView(this).apply {
        this.text = text; textSize = 13f; setTextColor(Color.parseColor("#6C6C70"))
        setPadding(dp(32), dp(4), dp(16), dp(4))
    }

    private fun spacer(dp: Int) = View(this).also {
        it.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(dp))
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
