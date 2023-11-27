package com.mastik.wifidirect.tasks

import android.os.Looper
import android.os.NetworkOnMainThreadException
import androidx.core.util.Consumer
import androidx.core.util.Function
import com.mastik.wifidirect.transfer.Communicator
import com.mastik.wifidirect.transfer.FileDescriptorTransferInfo
import timber.log.Timber
import java.net.BindException
import java.net.ServerSocket

class ServerStartTask(
    private val defaultPort: Int,
    ) : Communicator, Runnable {

    companion object {
        val TAG: String = ServerStartTask::class.simpleName!!

        const val MAX_PORT_OFFSET = 10
    }

    private var communicator: SocketCommunicator = SocketCommunicator()

    override fun run() {
        if(Looper.myLooper() == Looper.getMainLooper())
            throw NetworkOnMainThreadException()



        var server: ServerSocket? = null
        var portOffset = 0
        try {
            while (server == null) {
                if (portOffset >= MAX_PORT_OFFSET) {
                    Timber.tag(TAG).e("Start socket listener error, port overflow")
                    return
                }
                try {
                    server = ServerSocket(defaultPort + portOffset++)
                } catch (e: BindException) {e.printStackTrace()}
            }
        } catch (e: IllegalArgumentException) {
            Timber.tag(TAG).e(e, "Start socket listener error, invalid port: %d", defaultPort)
            return
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Start socket listener unexpected error")
            return
        }



        try {
            val client = server.accept()

            server.close()

            communicator.readLoop(client)
        } catch (e: Exception) {
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