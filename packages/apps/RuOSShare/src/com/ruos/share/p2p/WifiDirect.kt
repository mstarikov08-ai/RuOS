package com.ruos.share.p2p

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Looper

/**
 * Thin wrapper over WifiP2pManager (Wi-Fi Direct — works without Google services).
 *
 * Two roles share one helper:
 *  • Receive mode calls [createGroup] so the device becomes an autonomous group owner; the
 *    Wi-Fi Direct GO always lives at 192.168.49.1, where the receiver's ServerSocket waits.
 *  • Send mode calls [discoverPeers] + [connect]; once joined, it streams to 192.168.49.1.
 *
 * The framework callbacks (peer list, connection info, this-device) are delivered through
 * the registered broadcast receiver; subscribe via the public lambdas.
 */
class WifiDirect(private val context: Context) {

    val GO_ADDRESS = "192.168.49.1"

    private val manager = context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    private val channel = manager.initialize(context, Looper.getMainLooper(), null)
    private var receiver: BroadcastReceiver? = null

    var onPeers: ((List<WifiP2pDevice>) -> Unit)? = null
    var onConnected: ((Boolean, String) -> Unit)? = null   // isGroupOwner, groupOwnerAddress
    var onThisDevice: ((WifiP2pDevice) -> Unit)? = null

    fun register() {
        if (receiver != null) return
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
        receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                when (i?.action) {
                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> requestPeers()
                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> requestConnection()
                    WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                        val dev = i.getParcelableExtra<WifiP2pDevice>(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE)
                        dev?.let { onThisDevice?.invoke(it) }
                    }
                }
            }
        }
        context.registerReceiver(receiver, filter)
    }

    fun unregister() {
        receiver?.let { runCatching { context.unregisterReceiver(it) } }
        receiver = null
    }

    @Suppress("MissingPermission")
    fun discoverPeers() {
        runCatching { manager.discoverPeers(channel, null) }
    }

    @Suppress("MissingPermission")
    private fun requestPeers() {
        runCatching {
            manager.requestPeers(channel) { peers -> onPeers?.invoke(peers.deviceList.toList()) }
        }
    }

    @Suppress("MissingPermission")
    private fun requestConnection() {
        runCatching {
            manager.requestConnectionInfo(channel) { info ->
                if (info.groupFormed) {
                    onConnected?.invoke(info.isGroupOwner, info.groupOwnerAddress?.hostAddress ?: GO_ADDRESS)
                }
            }
        }
    }

    @Suppress("MissingPermission")
    fun connect(device: WifiP2pDevice, onResult: (Boolean) -> Unit) {
        val config = android.net.wifi.p2p.WifiP2pConfig().apply { deviceAddress = device.deviceAddress }
        runCatching {
            manager.connect(channel, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() = onResult(true)
                override fun onFailure(reason: Int) = onResult(false)
            })
        }.onFailure { onResult(false) }
    }

    /** Receive mode: become an autonomous group owner (clients can join and push to us). */
    @Suppress("MissingPermission")
    fun createGroup(onResult: (Boolean) -> Unit) {
        runCatching {
            manager.createGroup(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() = onResult(true)
                override fun onFailure(reason: Int) = onResult(reason == WifiP2pManager.BUSY) // already a GO
            })
        }.onFailure { onResult(false) }
    }

    fun removeGroup() {
        runCatching { manager.removeGroup(channel, null) }
    }
}
