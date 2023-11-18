package com.mastik.wifidirect

import android.content.Context
import android.net.wifi.p2p.WifiP2pDevice
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class WifiP2pDeviceView(private val context: Context): LinearLayout(context) {
    private val deviceName: TextView
    private val deviceStatus: TextView
    private val connectButton: Button

    var device: WifiP2pDevice? = null
        private set

    constructor(device: WifiP2pDevice, context: Context): this(context){
        updateDevice(device)
    }

    constructor(device: WifiP2pDevice, context: Context, connectClickListener: OnClickListener): this(context){
        updateDevice(device)
        setOnConnectButtonClickListener(connectClickListener)
    }

    init {
        val infoLayout = LinearLayout(context)
        infoLayout.orientation = LinearLayout.VERTICAL

        deviceName = TextView(context)
        deviceStatus = TextView(context)

        connectButton = Button(context)
        connectButton.text = "▶️"

        infoLayout.addView(deviceName)
        infoLayout.addView(deviceStatus)

        this.addView(infoLayout)
        this.addView(connectButton)
    }

    fun updateDevice(device: WifiP2pDevice){
        this.device = device
        deviceName.text = device.deviceName
        deviceStatus.text = STATUS_MAP[device.status]
    }

    fun setOnConnectButtonClickListener(clickListener: OnClickListener){
        connectButton.setOnClickListener(clickListener)
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