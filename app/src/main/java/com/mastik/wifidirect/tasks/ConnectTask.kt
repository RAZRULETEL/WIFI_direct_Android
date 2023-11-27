package com.mastik.wifidirect.tasks

import android.os.Looper
import android.os.NetworkOnMainThreadException
import androidx.core.util.Consumer
import androidx.core.util.Function
import com.mastik.wifidirect.transfer.Communicator
import com.mastik.wifidirect.transfer.FileDescriptorTransferInfo
import timber.log.Timber
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

class ConnectTask(
    private val host: String,
    private val defaultPort: Int,
    private val connectDelay: Long = 1_000L
): Communicator, Runnable {
    companion object{
        val TAG: String = ConnectTask::class.simpleName!!

        private const val CONNECT_TIMEOUT: Int = 3_000
    }

    private var communicator: SocketCommunicator = SocketCommunicator()

    override fun run() {
        if(Looper.myLooper() == Looper.getMainLooper())
            throw NetworkOnMainThreadException()

        Thread.sleep(connectDelay)

        val client = Socket()
        var portOffset = 0

        while (!client.isConnected) {
            if(portOffset >= ServerStartTask.MAX_PORT_OFFSET){
                Timber.tag(TAG).e("Start socket listener error, port overflow")
                return
            }
            try {
                client.connect(
                    InetSocketAddress(host, defaultPort + portOffset++),
                    CONNECT_TIMEOUT
                )
            } catch (_: SocketTimeoutException) {
            } catch (_: IOException) {
            } catch (e: IllegalArgumentException) {
                Timber.tag(TAG).e(e, "Start socket listener error, invalid port or host")
                return
            }
        }



        try {
            communicator.readLoop(client)
            if(!client.isConnected) client.close()
        } catch (e: Exception){
            e.printStackTrace()
        }
    }

    override fun getMessageSender(): Consumer<String>{
        return communicator.getMessageSender()
    }

    override fun setOnNewMessageListener(onNewMessage: Consumer<String>) {
        communicator.setOnNewMessageListener(onNewMessage)
    }

    override fun getFileSender(): Consumer<FileDescriptorTransferInfo> {
        return communicator.getFileSender()
    }

    override fun setOnNewFileListener(onNewFile: Function<String, FileDescriptorTransferInfo>) {
        communicator.setOnNewFileListener(onNewFile)
    }
}