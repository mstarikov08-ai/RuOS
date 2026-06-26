package com.ruos.settings.sections

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

class BluetoothSettingsActivity : Activity() {

    private val btAdapter by lazy {
        (getSystemService(BluetoothManager::class.java)).adapter
    }
    private lateinit var deviceList: LinearLayout

    private val btReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> showDevices()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUI())
        registerReceiver(btReceiver, IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED))
        try { btAdapter.startDiscovery() } catch (_: SecurityException) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(btReceiver) } catch (_: Exception) {}
    }

    private fun buildUI(): View {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#F2F2F7")) }
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(100), 0, dp(40))
        }

        col.addView(toggleRow())
        col.addView(spacer(20))
        col.addView(sectionLabel("МОИ УСТРОЙСТВА"))

        deviceList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val wrapper = FrameLayout(this).apply { setPadding(dp(16), 0, dp(16), 0) }
        deviceList.background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat(); setColor(Color.WHITE)
        }
        wrapper.addView(deviceList)
        col.addView(wrapper)

        showDevices()
        scroll.addView(col)
        root.addView(scroll)
        return root
    }

    private fun showDevices() {
        deviceList.removeAllViews()
        val paired = try { btAdapter.bondedDevices ?: emptySet() } catch (_: SecurityException) { emptySet() }
        if (paired.isEmpty()) {
            deviceList.addView(TextView(this).apply {
                text = "Нет сопряжённых устройств"
                textSize = 15f; setTextColor(Color.GRAY)
                setPadding(dp(16), dp(14), dp(16), dp(14))
                gravity = Gravity.CENTER
            })
        } else {
            paired.forEachIndexed { i, device ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(16), dp(12), dp(16), dp(12))
                }
                row.addView(TextView(this).apply {
                    text = try { device.name } catch (_: SecurityException) { "Устройство" }
                    textSize = 17f; setTextColor(Color.BLACK)
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(TextView(this).apply {
                    text = "Подключено"; textSize = 13f; setTextColor(Color.GRAY)
                })
                deviceList.addView(row)
            }
        }
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
            text = "Bluetooth"; textSize = 17f; setTextColor(Color.BLACK)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(Switch(this).apply {
            isChecked = try { btAdapter.isEnabled } catch (_: SecurityException) { false }
            setOnCheckedChangeListener { _, checked ->
                try {
                    if (checked) btAdapter.enable() else btAdapter.disable()
                } catch (_: SecurityException) {}
            }
        })
        wrapper.addView(row)
        return wrapper
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
