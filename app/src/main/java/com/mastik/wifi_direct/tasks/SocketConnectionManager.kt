package com.mastik.wifi_direct.tasks

import android.net.wifi.p2p.WifiP2pInfo
import com.mastik.wifi_direct.MainActivity
import com.mastik.wifi_direct.transfer.Communicator
import com.mastik.wifi_direct.transfer.FileDescriptorTransferInfo
import timber.log.Timber
import java.util.function.Consumer
import java.util.function.Function

object SocketConnectionManager : Communicator {
    val TAG: String = SocketConnectionManager::class.simpleName!!

    private lateinit var p2pNetworkInfo: WifiP2pInfo

    private var connectManagers: MutableMap<String, MultiConnectTask> = mutableMapOf()
    private var serverTask: ServerStartTask? = ServerStartTask(MainActivity.DEFAULT_P2P_SERVER_PORT)

    init {
        TaskExecutors.getCachedPool().execute(serverTask)
    }

    private var onNewFile: Function<String, FileDescriptorTransferInfo?>? = null
    private var onNewMessage: Consumer<String>? = null

    private val newClientListener = Consumer<String> {
        Timber.tag(TAG).d("newClientListener $it")
        connectManagers.getOrPut(it) { MultiConnectTask(it, MainActivity.DEFAULT_P2P_SERVER_PORT) }
    }

    fun updateNetworkInfo(p2pNetworkInfo: WifiP2pInfo) {
        Timber.tag(TAG).d("updateNetworkInfo $p2pNetworkInfo")
        this.p2pNetworkInfo = p2pNetworkInfo
        if (p2pNetworkInfo.groupFormed) {
            if (!ServerStartTask.isServerRunning()) {
                serverTask = ServerStartTask(MainActivity.DEFAULT_P2P_SERVER_PORT)
                TaskExecutors.getCachedPool().execute(serverTask)
            }

            if (!p2pNetworkInfo.isGroupOwner)
                connectManagers[p2pNetworkInfo.groupOwnerAddress.hostAddress!!] = MultiConnectTask(
                    p2pNetworkInfo.groupOwnerAddress.hostAddress!!,
                    MainActivity.DEFAULT_P2P_SERVER_PORT
                )

            serverTask!!.setOnNewClientListener(newClientListener)
            serverTask!!.setOnNewMessageListener {
                onNewMessage?.accept(it)
            }
            serverTask!!.setOnNewFileListener {
                return@setOnNewFileListener onNewFile?.apply(it)
            }
        } else {
            connectManagers.values.forEach { e -> e.destroy() }
            connectManagers.clear()
        }
    }

    /**
     * @return consumer that accepts file descriptor and synchronously sends file to all clients
     */
    override fun getFileSender(): Consumer<FileDescriptorTransferInfo> = Consumer { transferInfo ->
        Timber.d("Sending file to ${connectManagers.size} clients")

        connectManagers.values.forEach { e ->
            try {
                e.getFileSender().accept(transferInfo)
            } catch (e: Exception) {
                Timber.e(e, "Failed to send file: ")
            }
        }

    }

    override fun getMessageSender(): Consumer<String> = Consumer { message ->
        connectManagers.values.forEach { e -> e.getMessageSender().accept(message) }
    }

    override fun setOnNewFileListener(onNewFile: Function<String, FileDescriptorTransferInfo?>) {
        this.onNewFile = onNewFile
    }

    override fun setOnNewMessageListener(onNewMessage: Consumer<String>) {
        this.onNewMessage = onNewMessage
    }
}