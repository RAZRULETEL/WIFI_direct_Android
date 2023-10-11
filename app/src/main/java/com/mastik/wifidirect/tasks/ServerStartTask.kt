package com.mastik.wifidirect.tasks

import android.os.Looper
import android.os.NetworkOnMainThreadException
import androidx.core.util.Consumer
import timber.log.Timber
import java.net.BindException
import java.net.ServerSocket

class ServerStartTask(
    private val defaultPort: Int,
    ) : Communicator, Runnable {

    private var communicator: SocketCommunicator? = null

    override fun run() {
        if(Looper.myLooper() == Looper.getMainLooper())
            throw NetworkOnMainThreadException()



        var server: ServerSocket? = null
        var portOffset = 0
        try {
            while (server == null) {
                if (portOffset > 10) {
                    Timber.tag(TAG).e("Start socket listener error, port overflow")
                    return
                }
                try {
                    server = ServerSocket(defaultPort + portOffset++)
                } catch (_: BindException) {}
            }
        } catch (e: IllegalArgumentException) {
            Timber.tag(TAG).e(e, "Start socket listener error, invalid port: %d", defaultPort)
            return
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Start socket listener unexpected error")
            return
        }



        try {
            communicator = SocketCommunicator(server.accept())

            server.close()

            communicator!!.readLoop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        communicator = null
    }

    override fun getMessageSender(): Consumer<String>?{
        return communicator?.getMessageSender()
    }

    override fun setOnNewMessageListener(onNewMessage: Consumer<String>) {
        communicator?.setOnNewMessageListener(onNewMessage)
    }

    companion object {
        val TAG: String = Companion::class.java.simpleName
    }
}