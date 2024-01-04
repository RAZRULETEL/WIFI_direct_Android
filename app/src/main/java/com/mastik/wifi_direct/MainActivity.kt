package com.mastik.wifi_direct

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.graphics.Color
import android.net.Uri
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.ActionListener
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.webkit.MimeTypeMap
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import com.mastik.wifi_direct.tasks.SocketConnectionManager
import com.mastik.wifi_direct.tasks.TaskExecutors
import com.mastik.wifi_direct.transfer.FileDescriptorTransferInfo
import com.mastik.wifi_direct.util.Utils
import com.mastik.wifi_direct.views.IndicatorPanelView
import com.mastik.wifi_direct.views.WifiP2pDeviceListView
import timber.log.Timber
import trikita.log.Log
import java.io.FileDescriptor
import java.util.concurrent.Exchanger
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt


class MainActivity : ComponentActivity() {

    private val intentFilter = IntentFilter()

    private val manager: WifiP2pManager? by lazy(LazyThreadSafetyMode.NONE) {
        getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager?
    }

    private var channel: WifiP2pManager.Channel? = null
    private var receiver: BroadcastReceiver? = null

    val permissionRequestResultExchanger = Exchanger<Map<String, Boolean>>()
    lateinit var requestPermissions: ActivityResultLauncher<Array<String>>

    private val fileExchanger = Exchanger<ParcelFileDescriptor>()

    private var discoveryState: Int = WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED

    private val errorReporter: ActionListener = object : ActionListener {
        override fun onSuccess() {}

        override fun onFailure(e: Int) {
            Toast.makeText(
                applicationContext,
                "Error: ${P2P_ERROR_MAP.getOrDefault(e, "Unknown error")}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_layout)

        Log.i("MainActivity", "onCreate")
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Timber.plant(Timber.DebugTree())

        requestPermissions =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
                Timber.tag(TAG).d("Permission granted: $granted")
                permissionRequestResultExchanger.exchange(granted)
            }

        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION)

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

        Utils.checkWifiDirectPermissions(this)

        findViewById<ImageButton>(R.id.scan_restart).setOnClickListener {
            it.animate().rotationBy(360f).setDuration(700)
                .setInterpolator(AccelerateDecelerateInterpolator()).start()
            if (discoveryState == WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED)
                manager!!.discoverPeers(channel, errorReporter)
            else
                manager!!.stopPeerDiscovery(channel!!, object : ActionListener {
                    override fun onSuccess() {
                        updateDiscoveryState(WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED)
                    }

                    override fun onFailure(e: Int) {
                        Toast.makeText(
                            applicationContext,
                            "Error: ${P2P_ERROR_MAP.getOrDefault(e, "Unknown error")}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                })
        }

        SocketConnectionManager.setOnNewFileListener(this::getNewFileDescriptor)
        setFileChooserLauncher(
            activityResultRegistry.register(
                "open file",
                ActivityResultContracts.GetContent()
            ) { uri: Uri? ->
                uri?.let {
                    TaskExecutors.getCachedPool().execute {
                        val parcelFileDescriptor: ParcelFileDescriptor =
                            contentResolver.openFileDescriptor(
                                uri,
                                "r"
                            )!!

                        val fileDescriptor: FileDescriptor =
                            parcelFileDescriptor.fileDescriptor

                        val transferInfo = FileDescriptorTransferInfo(fileDescriptor, uri.getName(applicationContext))

                        findViewById<IndicatorPanelView>(R.id.transfer_indicator_panel).addOutgoingTransferIndicator(uri.getName(applicationContext), transferInfo)

                        SocketConnectionManager.getFileSender().accept(transferInfo)

                        parcelFileDescriptor.close() // If we lost link to parcel, then it close the descriptor
                    }
                }
            })

        manager!!.requestConnectionInfo(channel
        ) { info ->
            Timber.tag(WiFiDirectBroadcastReceiver.TAG).d("onConnectionInfoAvailable: $info")
            SocketConnectionManager.updateNetworkInfo(info)
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

    fun setFileChooserLauncher(launcher: ActivityResultLauncher<String>) {
        findViewById<Button>(R.id.main_choose_file).setOnClickListener {
            Toast.makeText(this, "Choose file", Toast.LENGTH_SHORT).show()
            launcher.launch("*/*")
        }
    }

    val desc = mutableListOf<ParcelFileDescriptor>()

    fun getNewFileDescriptor(fileName: String): FileDescriptorTransferInfo? {
//        createFileUri.launch(fileName)
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileName.substringAfterLast("."))
        Timber.tag(TAG).d("Creating new file $fileName, mime: $mimeType")
        var fileCreator: ActivityResultLauncher<String>? = null
        fileCreator = activityResultRegistry.register(
            "Create new file",
            CreateDocument(mimeType?: "*/*")
        )
        {uri ->
            if (uri != null)
                fileExchanger.exchange(
                    contentResolver.openFileDescriptor(uri, "w"),
                    100,
                    TimeUnit.MILLISECONDS
                )
            else
                fileExchanger.exchange(null, 100, TimeUnit.MILLISECONDS)
            fileCreator?.unregister()
        }

        fileCreator.launch(fileName)

        val newFile = fileExchanger.exchange(null)

        return newFile?.let{
            val transferInfo = FileDescriptorTransferInfo(it.fileDescriptor, fileName)

            findViewById<IndicatorPanelView>(R.id.transfer_indicator_panel).addIngoingTransferIndicator(fileName, transferInfo)

            transferInfo.addTransferEndListener { _ ->
                it.close()
            }

            transferInfo
        }
    }

    fun setWifiDirectPeers(deviceList: WifiP2pDeviceList) {
        val deviceListView = findViewById<WifiP2pDeviceListView>(R.id.device_list)
        for (device in deviceList.deviceList)
            deviceListView.addOrUpdateDevice(device, manager!!, channel!!)
        deviceListView.getDevices().forEach { device ->
            if (!deviceList.deviceList.any { e -> e.deviceAddress == device.deviceAddress })
                deviceListView.removeDevice(device)
        }
    }

    fun updateDiscoveryState(state: Int) {
        discoveryState = state
        findViewById<TextView>(R.id.discover_status).setBackgroundColor(
            if (state == WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED)
                Color.GREEN
            else
                Color.RED
        )
    }

    companion object {
        const val DEFAULT_P2P_SERVER_PORT = 50_001

        val TAG: String = MainActivity::class.simpleName!!

        val P2P_ERROR_MAP = mapOf(
            WifiP2pManager.ERROR to "Unknown WiFi p2p error",
            WifiP2pManager.P2P_UNSUPPORTED to "WiFi Direct is not supported by your device",
            WifiP2pManager.BUSY to "WiFi p2p service busy"
        )

        fun convertDpToPixel(dp: Float, context: Context): Int {
            return (dp * (context.resources.displayMetrics.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT)).roundToInt()
        }

        fun convertDpToPixel(dp: Int, context: Context): Int {
            return (dp * (context.resources.displayMetrics.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT)).roundToInt()
        }
    }
}
