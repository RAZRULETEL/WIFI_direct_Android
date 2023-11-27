package com.mastik.wifidirect

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.graphics.Color
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.ActionListener
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import com.mastik.wifidirect.util.Utils
import com.mastik.wifidirect.views.WifiP2pDeviceListView
import timber.log.Timber
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.Exchanger
import java.util.concurrent.TimeUnit


class MainActivity : ComponentActivity() {

    private val intentFilter = IntentFilter()

    private val manager: WifiP2pManager? by lazy(LazyThreadSafetyMode.NONE) {
        getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager?
    }

    private var channel: WifiP2pManager.Channel? = null
    var receiver: BroadcastReceiver? = null

    val permissionRequestResultExchanger = Exchanger<Map<String, Boolean>>()
    lateinit var requestPermissions: ActivityResultLauncher<Array<String>>

    private val fileExchanger = Exchanger<ParcelFileDescriptor>()
    private lateinit var createFileUri: ActivityResultLauncher<String>

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_layout)

        Timber.plant(Timber.DebugTree())

        requestPermissions =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
                Timber.tag(TAG).d("Permission granted: $granted")
                permissionRequestResultExchanger.exchange(granted, 100, TimeUnit.MILLISECONDS)
            }
        createFileUri = registerForActivityResult(CreateDocument("todo/todo")) { uri ->
            if (uri != null)
                fileExchanger.exchange(
                    contentResolver.openFileDescriptor(uri, "w"),
                    100,
                    TimeUnit.MILLISECONDS
                )
            else
                fileExchanger.exchange(null)
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
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





        Utils.checkWifiDirectPermissions(this)
    }

    fun setFileChooserLauncher(launcher: ActivityResultLauncher<String>) {
        findViewById<Button>(R.id.main_choose_file).setOnClickListener {
            Toast.makeText(this, "Choose file", Toast.LENGTH_SHORT).show()
            launcher.launch("*/*")
        }
    }

    fun getNewFileDescriptor(fileName: String): ParcelFileDescriptor {
        createFileUri.launch(fileName)
        return fileExchanger.exchange(null)!!
    }

    fun setWifiDirectPeers(deviceList: WifiP2pDeviceList) {
        val deviceListView = findViewById<WifiP2pDeviceListView>(R.id.device_list)
        for (device in deviceList.deviceList)
            deviceListView.addOrUpdateDevice(device, manager!!, channel!!)

    }

    companion object {
        val TAG: String = MainActivity::class.simpleName!!
    }
}
