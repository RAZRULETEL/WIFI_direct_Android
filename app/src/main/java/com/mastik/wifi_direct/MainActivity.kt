package com.mastik.wifi_direct

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.graphics.Color
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.ActionListener
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.text.TextUtils
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import com.mastik.wifi_direct.transfer.FileDescriptorTransferInfo
import com.mastik.wifi_direct.util.Utils
import com.mastik.wifi_direct.views.WifiP2pDeviceListView
import timber.log.Timber
import java.util.concurrent.Exchanger
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
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
    private lateinit var createFileUri: ActivityResultLauncher<String>

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

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Timber.plant(Timber.DebugTree())

        requestPermissions =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
                Timber.tag(TAG).d("Permission granted: $granted")
                permissionRequestResultExchanger.exchange(granted)
            }
        createFileUri = registerForActivityResult(CreateDocument("todo/todo")) { uri ->
            if (uri != null)
                fileExchanger.exchange(
                    contentResolver.openFileDescriptor(uri, "w"),
                    100,
                    TimeUnit.MILLISECONDS
                )
            else
                fileExchanger.exchange(null, 100, TimeUnit.MILLISECONDS)
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
    }

    fun setFileChooserLauncher(launcher: ActivityResultLauncher<String>) {
        findViewById<Button>(R.id.main_choose_file).setOnClickListener {
            Toast.makeText(this, "Choose file", Toast.LENGTH_SHORT).show()
            launcher.launch("*/*")
        }
    }

    fun getNewFileDescriptor(fileName: String): FileDescriptorTransferInfo? {
        createFileUri.launch(fileName)
        val newFile = fileExchanger.exchange(null)
        return newFile?.let {
            val layout = LinearLayout(this)

            val nameText = TextView(this)
            nameText.text = fileName
            nameText.maxLines = 1
            nameText.ellipsize = TextUtils.TruncateAt.END
            nameText.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)


            val progressBar = ProgressBar(
                this, null,
                android.R.attr.progressBarStyleHorizontal
            )
            progressBar.max = 100
            progressBar.scaleY = 2f
            val progressParams = LinearLayout.LayoutParams(
                convertDpToPixel(70f, this),
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            progressParams.leftMargin = convertDpToPixel(5f, this)
            progressParams.rightMargin = convertDpToPixel(5f, this)
            progressBar.layoutParams = progressParams

            val etaText = TextView(this)
            etaText.text = "ETA: 00:00"
            etaText.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { params -> params.rightMargin = convertDpToPixel(5f, this) }

            val speedText = TextView(this)
            speedText.text = "Speed: 0 KB/s"

            layout.addView(nameText)
            layout.addView(progressBar)
            layout.addView(etaText)
            layout.addView(speedText)

            runOnUiThread {
                findViewById<LinearLayout>(R.id.main_layout).addView(layout, 2)
            }

            val transferInfo = FileDescriptorTransferInfo(it.fileDescriptor, fileName)
            transferInfo.addProgressListener{
                runOnUiThread {
                    progressBar.progress = ((it.bytesProgress.toDouble() / it.bytesTotal) * 100).toInt()
                    etaText.text = "ETA: ${(it.ETA / 60).toInt()}:${(it.ETA % 60).toInt()}"
                    speedText.text = "Speed: ${(it.currentSpeed / 1024).toInt()} KB/s"
                }
            }

            transferInfo.onTransferEndListener = Consumer {
                runOnUiThread {
                    findViewById<LinearLayout>(R.id.main_layout).removeView(layout)
                }
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
    }
}
