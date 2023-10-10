package com.mastik.wifidirect.tasks

import android.os.Looper
import android.os.NetworkOnMainThreadException
import androidx.core.util.Consumer
import timber.log.Timber
import java.net.BindException
import java.net.ServerSocket

class SocketServerStartTask(
    private val defaultPort: Int,
    private val newMessageListener: Consumer<String>,
    ) : Runnable {

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

            communicator!!.readLoop(newMessageListener)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        communicator = null
    }

    fun getMessageSender(): Consumer<String>?{
        return communicator?.getMessageSender()
    }

    companion object {
        val TAG: String = Companion::class.java.simpleName
    }
}