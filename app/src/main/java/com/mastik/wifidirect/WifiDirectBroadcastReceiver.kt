package com.mastik.wifidirect

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener
import android.net.wifi.p2p.WifiP2pManager.PeerListListener
import android.os.Parcelable
import android.util.Log
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import com.mastik.wifidirect.R

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

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION == action) {
            val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)

            if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                Log.d(TAG, "Wifi Direct mode is enabled")
            } else {
                Log.d(TAG, "Wifi Direct mode is not enabled")
            }

            Log.d(TAG, "P2P state changed - $state")
        } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION == action) {
            manager?.requestPeers(
                channel
            ) { deviceList ->
                //Log.d(TAG, "onPeersAvailable")
                //Log.d(TAG, deviceList.toString())
                activity.findViewById<TextView>(R.id.textView).text = deviceList.toString()
                for(device in deviceList.deviceList){
                    if(device.deviceName.equals("RAZRULETEL-PC")){
                        activity.setDevice(device)
                    }
                }
            }
            Log.d(TAG, "P2P peers changed")
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
                        Log.d(TAG, "onConnectionInfoAvailable: $info")
                        if(info.groupFormed)
                        if(!info.isGroupOwner){
                            //Send request to owner
                        }else{
                            //Start server
                        }
                    })
            } else {
                // It's a disconnect
            }
        } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION == action) {
            Log.d(TAG, "THIS_DEVICE_CHANGED ${intent.getParcelableExtra<Parcelable>(
                WifiP2pManager.EXTRA_WIFI_P2P_DEVICE
            ) as WifiP2pDevice?}")
        }
    }

    companion object{
        const val TAG = "BroadcastReceiver"
    }
}