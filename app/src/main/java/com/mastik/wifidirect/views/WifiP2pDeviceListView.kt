package com.mastik.wifidirect.views

import android.content.Context
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.children
import java.util.concurrent.CopyOnWriteArrayList

class WifiP2pDeviceListView(context: Context, attributes: AttributeSet? = null): LinearLayout(context, attributes) {

    private val deviceList = CopyOnWriteArrayList<WifiP2pDevice>()

    override fun addView(child: View, index: Int, params: ViewGroup.LayoutParams) {
        if(child !is WifiP2pDeviceView)
            throw IllegalArgumentException("Only ${WifiP2pDeviceView::class.simpleName} elements can be added")
        super.addView(child, index, params)
    }

    fun addDevice(device: WifiP2pDevice, manager: WifiP2pManager, channel: WifiP2pManager.Channel){
        deviceList.add(device)
        val devView = WifiP2pDeviceView(device, context)
        devView.setUpActionListener(manager, channel)
        addView(devView)
    }

    fun addOrUpdateDevice(device: WifiP2pDevice, manager: WifiP2pManager, channel: WifiP2pManager.Channel){
        if(deviceList.contains(device))
            (children.find { e -> (e as WifiP2pDeviceView).device.deviceAddress == device.deviceAddress } as WifiP2pDeviceView?)?.updateDevice(device)
        else
            addDevice(device, manager, channel)
    }

    fun removeDevice(device: WifiP2pDevice){
        if(deviceList.remove(device)){
            val deviceView = children.find { e -> (e as WifiP2pDeviceView).device.deviceAddress == device.deviceAddress }
            deviceView?.let {removeView(it)}
        }
    }

    fun getDevices(): Sequence<WifiP2pDeviceView>{
        return children.map { e -> if(e is WifiP2pDeviceView) e else throw IllegalStateException("Device list contains illegal child") }
    }
}