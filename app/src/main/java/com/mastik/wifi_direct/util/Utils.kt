package com.mastik.wifi_direct.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.widget.CompoundButton
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
        if(checkWifiDirectPermissionsSoft(activity))
            return

        Timber.tag("Utils").d("Permission not granted")

        var perms: Array<String> = arrayOf()
        if(Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2)
            perms += Manifest.permission.ACCESS_FINE_LOCATION
        else
            perms += Manifest.permission.NEARBY_WIFI_DEVICES

        activity.requestPermissions.launch(perms)

        TaskExecutors.getCachedPool().execute {
            if (!activity.permissionRequestResultExchanger.exchange(null).values.all { e -> e })
                activity.runOnUiThread {
                    Toast.makeText(activity.applicationContext, "Всего хорошего", Toast.LENGTH_LONG).show()
                    exitProcess(1)
                }
        }
    }

    fun checkWifiDirectPermissionsSoft(activity: ComponentActivity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            return true

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

    @SuppressLint("MissingPermission", "NewApi")
    fun bindWifiDirectControls(
        manager: WifiP2pManager,
        channel: WifiP2pManager.Channel,
        toggleListen: ToggleButton,
        toggleScan: ToggleButton
    ) {
        manager.requestDiscoveryState(channel) {
            toggleScan.isChecked = it == WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED
            toggleListen.isChecked =
                toggleScan.isChecked // There is no way to separately check listen state in android sdk lvl < 34
        }
        var scanStateChanged: CompoundButton.OnCheckedChangeListener? = null

        val listenStateChanged: CompoundButton.OnCheckedChangeListener =
            CompoundButton.OnCheckedChangeListener { _, checked ->
                if (checked) {
                    manager.startListening(channel, object : WifiP2pManager.ActionListener {
                        override fun onSuccess() {}

                        override fun onFailure(reasonCode: Int) {
                            toggleListen.isChecked = false
                            Timber.tag("WIFI P2P LISTEN").d("Failure $reasonCode")
                        }
                    })
                } else {
                    manager.stopListening(channel, object : WifiP2pManager.ActionListener {
                        override fun onSuccess() {
                            toggleScan.setOnCheckedChangeListener(null)
                            toggleScan.isChecked = false
                            toggleScan.setOnCheckedChangeListener(scanStateChanged)
                        }

                        override fun onFailure(p0: Int) {}
                    })
                }
            }

        scanStateChanged =
            CompoundButton.OnCheckedChangeListener { _, checked ->
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
                        override fun onSuccess() {
                            toggleListen.setOnCheckedChangeListener(null)
                            toggleListen.isChecked = false
                            toggleListen.setOnCheckedChangeListener(listenStateChanged)
                        }

                        override fun onFailure(p0: Int) {}
                    })

                }
            }

        toggleListen.setOnCheckedChangeListener(listenStateChanged)


        toggleScan.setOnCheckedChangeListener(scanStateChanged)
    }
}