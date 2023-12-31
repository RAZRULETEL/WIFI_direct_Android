package com.mastik.wifi_direct.views

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.view.View.OnClickListener
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class WifiP2pDeviceView(context: Context): LinearLayout(context) {
    private val deviceName: TextView
    private val deviceStatus: TextView
    private val connectButton: Button

    lateinit var device: WifiP2pDevice
        private set

    private var disconnectOnClickListener: OnClickListener? = null

    constructor(device: WifiP2pDevice, context: Context): this(context){
        updateDevice(device)
    }

    init {
        this.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

        val infoLayout = LinearLayout(context)
        infoLayout.orientation = VERTICAL
        infoLayout.layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)

        deviceName = TextView(context)
        deviceStatus = TextView(context)

        connectButton = Button(context)
        connectButton.text = "▶️"
        val buttonParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        buttonParams.rightMargin = 50
        connectButton.layoutParams = buttonParams

        infoLayout.addView(deviceName)
        infoLayout.addView(deviceStatus)

        this.addView(infoLayout)
        this.addView(connectButton)
    }

    fun updateDevice(device: WifiP2pDevice){
        this.device = device
        this.post {
            deviceName.text = device.deviceName
            deviceStatus.text = STATUS_MAP[device.status]
            if(device.status == WifiP2pDevice.CONNECTED){
                connectButton.setOnClickListener(disconnectOnClickListener)
                connectButton.text = "❌"
            }
        }

    }

    @SuppressLint("MissingPermission")
    fun setUpActionListener(manager: WifiP2pManager, channel: WifiP2pManager.Channel){
        var listener: OnClickListener? = null

        disconnectOnClickListener = OnClickListener {
            (it as Button).text = "▶️"
            it.setOnClickListener(listener)
            manager.removeGroup(channel, null)
        }

        listener = OnClickListener {
            val cfg = WifiP2pConfig()
            cfg.deviceAddress = device.deviceAddress
            cfg.groupOwnerIntent = 0

            manager.connect(channel, cfg, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    (it as Button).text = "⏹️"
                    it.setOnClickListener{
                        manager.cancelConnect(channel, object : WifiP2pManager.ActionListener {
                            override fun onSuccess() {
                                it.setOnClickListener(listener)
                                (it as Button).text = "▶️"
                            }

                            override fun onFailure(p0: Int) {
                                it.setOnClickListener(listener)
                                (it as Button).text = "▶️"
                            }
                        })
                    }
                }

                override fun onFailure(reasonCode: Int) {
                    it.setOnClickListener(listener)
                    (it as Button).text = "▶️"
                }
            })
        }
        connectButton.setOnClickListener(listener)
    }

    companion object{
        val STATUS_MAP = mapOf(
            WifiP2pDevice.AVAILABLE to "Available",
            WifiP2pDevice.INVITED to "Invited",
            WifiP2pDevice.CONNECTED to "Connected",
            WifiP2pDevice.FAILED to "Failed",
            WifiP2pDevice.UNAVAILABLE to "Unavailable"
        )
    }
}