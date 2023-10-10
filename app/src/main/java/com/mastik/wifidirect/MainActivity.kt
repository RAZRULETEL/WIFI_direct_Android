package com.mastik.wifidirect

import AsyncSocketTask
import android.Manifest
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
import android.util.Log
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import java.util.Timer
import java.util.TimerTask


class MainActivity : ComponentActivity() {

    private val intentFilter = IntentFilter()

    val manager: WifiP2pManager? by lazy(LazyThreadSafetyMode.NONE) {
        getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager?
    }

    var channel: WifiP2pManager.Channel? = null
    var receiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_layout)

        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        channel = manager?.initialize(applicationContext, mainLooper, null)
        channel?.also { channel ->
            receiver = WiFiDirectBroadcastReceiver(manager, channel, this)
        }

        registerReceiver(receiver, intentFilter);

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
                        perms, 12412
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
            Log.d(WiFiDirectBroadcastReceiver.TAG, "Disconnecting...")
            val config = WifiP2pConfig()
            config.deviceAddress = device!!.deviceAddress
            manager?.cancelConnect(channel, object : ActionListener {
                override fun onSuccess() {
                    Toast.makeText(applicationContext, "Successfully disconnected", Toast.LENGTH_LONG).show();
                }

                override fun onFailure(p0: Int) {
                    Toast.makeText(applicationContext, "Disconnection fail $p0", Toast.LENGTH_LONG).show();
                }

            })
        }

        findViewById<Button>(R.id.connect).setOnClickListener {
            Log.d(WiFiDirectBroadcastReceiver.TAG, "Connecting...")
            val config = WifiP2pConfig()
            config.deviceAddress = device!!.deviceAddress
            manager?.connect(channel, config, object : ActionListener {
                override fun onSuccess() {
                    Log.d(WiFiDirectBroadcastReceiver.TAG, "Connected !!!")
                    findViewById<TextView>(R.id.textView).text = "Connected !!!"
                }

                override fun onFailure(reasonCode: Int) {
                    Log.d(WiFiDirectBroadcastReceiver.TAG, "Failed to connect $reasonCode")
                    findViewById<TextView>(R.id.textView).text = "Failed to connect $reasonCode"
                }

            })
        }

            findViewById<Button>(R.id.socket_connect).setOnClickListener {
                findViewById<TextView>(R.id.socket_status).setBackgroundColor(Color.YELLOW)
                AsyncSocketTask(
                    findViewById<TextView>(R.id.socket_status),
                    findViewById<TextView>(R.id.message_text),
                    findViewById<ImageButton>(R.id.message_send)
                ).execute()
            }

            Timer().scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    manager?.discoverPeers(channel, object : ActionListener {
                        override fun onSuccess() {
                            findViewById<TextView>(R.id.scan_status).setBackgroundColor(Color.GREEN)
                        }

                        override fun onFailure(reasonCode: Int) {
                            Log.d("WIFI P2P SCAN", "onFailure $reasonCode")
                            findViewById<TextView>(R.id.scan_status).setBackgroundColor(Color.RED)
                        }
                    })
                }
                }, 0, 1000)

        }

        private var device: WifiP2pDevice? = null

        public fun setDevice(device: WifiP2pDevice){
            this.device = device
        }
        }
