package com.mastik.wifi_direct

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.NetworkInfo
import android.net.Uri
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.ActionListener
import android.os.ParcelFileDescriptor
import android.os.Parcelable
import android.provider.OpenableColumns
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContracts
import com.mastik.wifi_direct.tasks.ConnectTask
import com.mastik.wifi_direct.tasks.ServerStartTask
import com.mastik.wifi_direct.tasks.TaskExecutors
import com.mastik.wifi_direct.transfer.Communicator
import com.mastik.wifi_direct.transfer.FileDescriptorTransferInfo
import com.mastik.wifi_direct.util.Utils
import timber.log.Timber
import java.io.FileDescriptor

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

    val getContent : ActivityResultRegistry = activity.activityResultRegistry

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION == action) {
            val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)

            if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                Timber.tag(TAG).d("Wifi Direct mode is enabled")
            } else {
                Timber.tag(TAG).d("Wifi Direct mode is not enabled")
            }

            Timber.tag(TAG).d("P2P state changed - $state")
        } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION == action) {
            if (Utils.checkWifiDirectPermissionsSoft(activity))
                manager?.requestPeers(
                    channel
                ) { deviceList ->
                    Timber.tag(TAG).d(deviceList.toString())
                    activity.setWifiDirectPeers(deviceList)
                }
            Timber.tag(TAG).d("P2P peers changed")
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
                        Timber.tag(TAG).d("onConnectionInfoAvailable: $info")
                        if (info.groupFormed) {
                            var task: Communicator? = null
                            if (!info.isGroupOwner) {
                                if (info.groupOwnerAddress.hostAddress != null)
                                    task = ConnectTask(
                                        info.groupOwnerAddress.hostAddress!!,
                                        MainActivity.DEFAULT_P2P_SERVER_PORT
                                    )
                                else
                                    Timber.tag(TAG).e("Group owner address is null")
                            } else {
                                if(!ServerStartTask.isServerRunning())
                                    task = ServerStartTask(MainActivity.DEFAULT_P2P_SERVER_PORT)
                            }



                            task?.let {communicator ->
                                communicator.setOnNewMessageListener() { message ->
                                    activity.findViewById<TextView>(R.id.socket_status).post {
                                        activity.findViewById<TextView>(R.id.socket_status).text = message
                                    }
                                }

                                communicator.setOnNewFileListener(activity::getNewFileDescriptor)

                                activity.findViewById<ImageButton>(R.id.message_send)
                                    .setOnClickListener {
                                        TaskExecutors.getCachedPool().execute {
                                            communicator.getMessageSender().accept(
                                                activity.findViewById<TextView>(R.id.message_text).text.toString()
                                            )
                                        }
                                    }

                                activity.setFileChooserLauncher(getContent.register("open file", ActivityResultContracts.GetContent()) { uri: Uri? ->
                                    uri?.let {
                                        TaskExecutors.getCachedPool().execute {
                                            val parcelFileDescriptor: ParcelFileDescriptor =
                                                activity.contentResolver.openFileDescriptor(
                                                    uri,
                                                    "r"
                                                )!!

                                            val fileDescriptor: FileDescriptor =
                                                parcelFileDescriptor.fileDescriptor

                                            communicator.getFileSender().accept(
                                                FileDescriptorTransferInfo(fileDescriptor, uri.getName(activity.applicationContext)))

                                            parcelFileDescriptor.close()
                                        }
                                    }
                                })



                                TaskExecutors.getCachedPool().execute(communicator as Runnable)
                            }
                        }
                    })
            } else {
                // It's a disconnect
            }
        } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION == action) {
            Timber.tag(TAG)
                .d(
                    "THIS_DEVICE_CHANGED ${
                        intent.getParcelableExtra<Parcelable>(
                            WifiP2pManager.EXTRA_WIFI_P2P_DEVICE
                        ) as WifiP2pDevice?
                    }"
                )
        } else if (WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION == action) {
            val state = intent.getIntExtra(WifiP2pManager.EXTRA_DISCOVERY_STATE, -1)

            Timber.tag(TAG).d("P2P Discovery state changed - $state")
            activity.updateDiscoveryState(state)

            if(state == WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED){

            } else if (state == WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED) {
                manager?.discoverPeers(channel, object : ActionListener {
                    override fun onSuccess() {}

                    override fun onFailure(p0: Int) {}
                })
            }
        }
    }

    fun Uri.getName(context: Context): String {
        val returnCursor = context.contentResolver.query(this, null, null, null, null)!!
        val nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        returnCursor.moveToFirst()
        val fileName = returnCursor.getString(nameIndex)
        returnCursor.close()
        return fileName
    }

    companion object {
        const val TAG = "BroadcastReceiver"
    }
}