package com.mastik.wifidirect

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.ActionListener
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import com.mastik.wifidirect.util.Utils
import timber.log.Timber
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.SynchronousQueue


class MainActivity : ComponentActivity() {

    private val intentFilter = IntentFilter()

    val manager: WifiP2pManager? by lazy(LazyThreadSafetyMode.NONE) {
        getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager?
    }

    var channel: WifiP2pManager.Channel? = null
    var receiver: BroadcastReceiver? = null

    private val permissionRequestResults: SynchronousQueue<Boolean> = SynchronousQueue(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_layout)

        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
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

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                var perms = arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    perms += Manifest.permission.NEARBY_WIFI_DEVICES
                requestPermissions(
                    perms, 0
                )
            }
        }

        findViewById<ToggleButton>(R.id.listen_start).setOnCheckedChangeListener { button, checked ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (checked) {
                    manager?.startListening(channel!!, object : ActionListener {
                        override fun onSuccess() {
                            findViewById<TextView>(R.id.listen_status).setBackgroundColor(Color.GREEN)
                        }

                        override fun onFailure(p0: Int) {
                            findViewById<TextView>(R.id.listen_status).setBackgroundColor(Color.RED)
                        }
                    })
                } else {
                    manager?.stopListening(channel!!, object : ActionListener {
                        override fun onSuccess() {
                            findViewById<TextView>(R.id.listen_status).setBackgroundColor(Color.RED)
                        }

                        override fun onFailure(p0: Int) {

                        }
                    })
                }
            }
        }

        findViewById<Button>(R.id.connect_cancel).setOnClickListener {
            Timber.tag(TAG).d("Disconnecting...")
            val config = WifiP2pConfig()
            config.deviceAddress = device!!.deviceAddress
            manager?.cancelConnect(channel, object : ActionListener {
                override fun onSuccess() {
                    Toast.makeText(
                        applicationContext,
                        "Successfully disconnected",
                        Toast.LENGTH_LONG
                    ).show()
                }

                override fun onFailure(p0: Int) {
                    Toast.makeText(applicationContext, "Disconnection fail $p0", Toast.LENGTH_LONG)
                        .show()
                }

            })
        }

        findViewById<Button>(R.id.connect).setOnClickListener {
            Timber.tag(TAG).d("Connecting...")
            val config = WifiP2pConfig()
            config.deviceAddress = device!!.deviceAddress
            manager?.connect(channel, config, object : ActionListener {
                override fun onSuccess() {
                    Timber.tag(TAG).d("Connected !!!")
                    findViewById<TextView>(R.id.textView).text = "Connected !!!"
                }

                override fun onFailure(reasonCode: Int) {
                    Timber.tag(TAG).d("Failed to connect %s", reasonCode)
                    findViewById<TextView>(R.id.textView).text = "Failed to connect $reasonCode"
                }
            })
        }


        Timer().scheduleAtFixedRate(object : TimerTask() {
            @SuppressLint("MissingPermission")
            override fun run() {
                Utils.checkWifiDirectPermissions(this@MainActivity)
                manager?.discoverPeers(channel, object : ActionListener {
                    override fun onSuccess() {
                        findViewById<TextView>(R.id.scan_status).setBackgroundColor(Color.GREEN)
                    }

                    override fun onFailure(reasonCode: Int) {
                        Timber.tag("WIFI P2P SCAN").d("Failure $reasonCode")
                        findViewById<TextView>(R.id.scan_status).setBackgroundColor(Color.RED)
                    }
                })
            }
        }, 0, 1000)

    }

    private var device: WifiP2pDevice? = null

    fun setDevice(device: WifiP2pDevice) {
        this.device = device
    }

    fun getPermissionRequestResult(): Boolean {
        return permissionRequestResults.poll()!!
    }

    companion object {
        val TAG = Companion::class.java.simpleName
    }
}
