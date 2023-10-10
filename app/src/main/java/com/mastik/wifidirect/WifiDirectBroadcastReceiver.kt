package com.mastik.wifidirect

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Parcelable
import android.util.Log
import android.widget.TextView
import com.mastik.wifidirect.tasks.SocketServerStartTask
import com.mastik.wifidirect.tasks.TaskExecutors
import com.mastik.wifidirect.util.Utils
import timber.log.Timber

/**
 * A BroadcastReceiver that notifies of important wifi p2p events.
 */
class WiFiDirectBroadcastReceiver(
    private val manager: WifiP2pManager?,
    private val channel: WifiP2pManager.Channel,
    private val activity: MainActivity
) : BroadcastReceiver() {
    /**
     * @param manager WifiP2pManager system service
     * @param channel Wifi p2p channel
     * @param activity activity associated with the receiver
     */

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION == action) {
            val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)

            if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                Timber.tag(TAG).d("Wifi Direct mode is enabled")
            } else {
                Timber.tag(TAG).d("Wifi Direct mode is not enabled")
            }

            Timber.tag(TAG).d("P2P state changed - $state")
        } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION == action) {
            Utils.checkWifiDirectPermissions(activity)
            manager?.requestPeers(
                channel
            ) { deviceList ->
                activity.findViewById<TextView>(R.id.textView).text = deviceList.toString()
                for (device in deviceList.deviceList) {
                    if (device.deviceName.equals("RAZRULETEL-PC")) {
                        activity.setDevice(device)
                    }
                }
            }
            Timber.tag(TAG).d("P2P peers changed")
        } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION == action) {
            if (manager == null) {
                return
            }
            val networkInfo = intent
                .getParcelableExtra<Parcelable>(WifiP2pManager.EXTRA_NETWORK_INFO) as NetworkInfo?
            if (networkInfo!!.isConnected) {
                // we are connected with the other device, request connection
                // info to find group owner IP
                manager.requestConnectionInfo(channel,
                    WifiP2pManager.ConnectionInfoListener { info ->
                        Timber.tag(TAG).d("onConnectionInfoAvailable: $info")
                        if (info.groupFormed)
                            if (!info.isGroupOwner) {
                                //Send request to owner
                            } else {
                                TaskExecutors.getFixedPool().execute(
                                    SocketServerStartTask(
                                        R.integer.port,
                                        activity.findViewById(R.id.message_text),
                                        { message ->
                                            activity.findViewById<TextView>(R.id.socket_status).text = message
                                        },
                                        activity.findViewById(R.id.message_send)
                                    )
                                )
                            }
                    })
            } else {
                // It's a disconnect
            }
        } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION == action) {
            Timber.tag(TAG)
                .d(
                    "THIS_DEVICE_CHANGED ${
                        intent.getParcelableExtra<Parcelable>(
                            WifiP2pManager.EXTRA_WIFI_P2P_DEVICE
                        ) as WifiP2pDevice?
                    }"
                )
        }
    }

    companion object {
        const val TAG = "BroadcastReceiver"
    }
}