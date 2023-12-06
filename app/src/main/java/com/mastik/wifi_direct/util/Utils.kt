package com.mastik.wifi_direct.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.widget.Toast
import android.widget.ToggleButton
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import com.mastik.wifi_direct.MainActivity
import com.mastik.wifi_direct.tasks.TaskExecutors
import timber.log.Timber
import kotlin.system.exitProcess

/**
 * Utilities class containing static methods
 */
object Utils {

    fun checkWifiDirectPermissions(activity: MainActivity) {
        if (checkWifiDirectPermissionsSoft(activity))
            return

        Timber.tag("Utils").d("Permission not granted")

        var perms: Array<String> = arrayOf()
        perms += if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2)
            Manifest.permission.ACCESS_FINE_LOCATION
        else
            Manifest.permission.NEARBY_WIFI_DEVICES

        activity.requestPermissions.launch(perms)

        TaskExecutors.getCachedPool().execute {
            if (!activity.permissionRequestResultExchanger.exchange(null).values.all { e -> e })
                activity.runOnUiThread {
                    Toast.makeText(activity.applicationContext, "Всего хорошего", Toast.LENGTH_LONG)
                        .show()
                    exitProcess(1)
                }
        }
    }

    fun checkWifiDirectPermissionsSoft(activity: ComponentActivity): Boolean {

        if (
            (Build.VERSION.SDK_INT > Build.VERSION_CODES.S_V2 ||
                    ActivityCompat.checkSelfPermission(
                        activity,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED) &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ActivityCompat.checkSelfPermission(
                        activity,
                        Manifest.permission.NEARBY_WIFI_DEVICES
                    ) == PackageManager.PERMISSION_GRANTED)
        )
            return true

        return false
    }

    @SuppressLint("MissingPermission")
    fun bindWifiDirectControls(
        manager: WifiP2pManager,
        channel: WifiP2pManager.Channel,
        toggleScan: ToggleButton
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            manager.requestDiscoveryState(channel) {
                toggleScan.isChecked = it == WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED
            }


        toggleScan.setOnClickListener {
            val checked = toggleScan.isChecked
            if (checked) {
                manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {}

                    override fun onFailure(reasonCode: Int) {
                        Timber.tag("WIFI P2P SCAN").d("Failure $reasonCode")
                        toggleScan.isChecked = false
                    }
                })
            } else {
                manager.stopPeerDiscovery(channel, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {}

                    override fun onFailure(p0: Int) {}
                })

            }
        }
    }
}