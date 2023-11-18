package com.mastik.wifidirect

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.graphics.Color
import android.net.Uri
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.ActionListener
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.View.OnClickListener
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.core.view.children
import androidx.core.view.forEach
import com.mastik.wifidirect.util.Utils
import timber.log.Timber
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.Exchanger
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.TimeUnit


class MainActivity : ComponentActivity() {

    private val intentFilter = IntentFilter()

    val manager: WifiP2pManager? by lazy(LazyThreadSafetyMode.NONE) {
        getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager?
    }

    var channel: WifiP2pManager.Channel? = null
    var receiver: BroadcastReceiver? = null

    private val permissionRequestResults: SynchronousQueue<Boolean> = SynchronousQueue(true)

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_layout)

        Timber.plant(Timber.DebugTree())

        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Timber.tag(TAG).d("Permission granted: $granted")
            permissionRequestResults.add(granted)
        }

        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)

        channel = manager?.initialize(applicationContext, mainLooper, null)
        channel?.also { channel ->
            receiver = WiFiDirectBroadcastReceiver(manager, channel, this)
        }

        registerReceiver(receiver, intentFilter)

        findViewById<Button>(R.id.disconnect).setOnClickListener {
            Timber.tag(TAG).d("Disconnecting...")
            manager?.requestGroupInfo(channel) { group ->
                if (group != null) {
                    manager?.removeGroup(channel, object : ActionListener {

                        override fun onSuccess() {
                            Timber.tag(TAG).d("removeGroup onSuccess")
                        }

                        override fun onFailure(reason: Int) {
                            Timber.tag(TAG).d("removeGroup onFailure $reason")
                        }
                    })
                } else
                    Timber.tag(TAG).d("Group is null")
            }
        }

        Timer().scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                manager?.requestDiscoveryState(channel!!) {
                    findViewById<TextView>(R.id.discover_status).setBackgroundColor(
                        if (it == WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED)
                            Color.GREEN
                        else
                            Color.RED
                    )
                }
            }
        }, 0, 1000)

        Utils.bindWifiDirectControls(
            manager!!,
            channel!!,
            findViewById(R.id.listen_start),
            findViewById(R.id.scan_start)
        )
    }

    fun setFileChooserLauncher(launcher: ActivityResultLauncher<String>){
        findViewById<Button>(R.id.main_choose_file).setOnClickListener {
            Toast.makeText(this, "Choose file", Toast.LENGTH_SHORT).show()
            launcher.launch("*/*")
        }
    }

    fun getNewFileDescriptor(): ParcelFileDescriptor {
        val exchanger = Exchanger<Uri>()

        registerForActivityResult(CreateDocument("todo/todo")) { uri ->
            exchanger.exchange(uri, 100, TimeUnit.MILLISECONDS)
        }.launch("test.txt")

        return contentResolver.openFileDescriptor(exchanger.exchange(null)!!, "r")!!
    }

    fun setWifiDirectPeers(deviceList: WifiP2pDeviceList) {
        val deviceListView = findViewById<LinearLayout>(R.id.device_list)
        val children = deviceListView.children as Sequence<WifiP2pDeviceView>
        for (device in deviceList.deviceList) {
            var deviceUpdated = false
            for (deviceView: WifiP2pDeviceView in children) {
                if (deviceView.device?.deviceAddress == device.deviceAddress) {
                    deviceView.updateDevice(device)
                    deviceUpdated = true
                    break
                }
            }
            if(deviceUpdated) continue
            val disconnectOnClick = OnClickListener {
                (it as Button).text = "▶️"
                manager?.removeGroup(channel, null)
            }
            val connectOnClick = buildConnectClickListener(device, disconnectOnClick)
            deviceListView.addView(WifiP2pDeviceView(device, this.applicationContext, connectOnClick))
        }
        deviceListView.forEach {
            if(deviceList.get((it as WifiP2pDeviceView).device!!.deviceAddress) == null){
                deviceListView.removeView(it)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun buildConnectClickListener(device: WifiP2pDevice, disconnectOnClickListener: OnClickListener): OnClickListener{
        var listener: OnClickListener? = null
        listener = OnClickListener {
            val cfg = WifiP2pConfig()
            cfg.deviceAddress = device.deviceAddress
            cfg.groupOwnerIntent = 0

            manager?.connect(channel, cfg, object : ActionListener {
                override fun onSuccess() {
                    Toast.makeText(
                        this@MainActivity.applicationContext,
                        "Connected to ${device.deviceName}",
                        Toast.LENGTH_SHORT
                    ).show()
                    it.setOnClickListener(disconnectOnClickListener)
                    (it as Button).text = "❌"
                }

                override fun onFailure(reasonCode: Int) {
                    Toast.makeText(
                        this@MainActivity.applicationContext,
                        "Failed to connect to ${device.deviceName}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })

            it.setOnClickListener{
                manager?.cancelConnect(channel, object : ActionListener {
                    override fun onSuccess() {
                        it.setOnClickListener(listener)
                    }

                    override fun onFailure(p0: Int) {
                        it.setOnClickListener(listener)
                    }
                })
            }
        }
        return listener
    }
    fun getPermissionRequestResult(): Boolean {
        return permissionRequestResults.take()!!
    }

    companion object {
        val TAG: String = MainActivity::class.simpleName!!
    }
}
