package com.mastik.wifidirect.tasks

import android.os.Looper
import android.os.NetworkOnMainThreadException
import androidx.core.util.Consumer
import timber.log.Timber
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

class ConnectTask(
    private val host: String,
    private val defaultPort: Int,
    private val connectDelay: Long = 0
): Communicator, Runnable {

    private var communicator: SocketCommunicator? = null

    override fun run() {
        if(Looper.myLooper() == Looper.getMainLooper())
            throw NetworkOnMainThreadException()

        Thread.sleep(connectDelay)

        val client = Socket()
        var portOffset = 0

        while (!client.isConnected) {
            if(portOffset > 10){
                Timber.tag(TAG).e("Start socket listener error, port overflow")
                return
            }
            try {
                client.connect(
                    InetSocketAddress(host, defaultPort + portOffset++),
                    3000//R.integer.connect_timeout
                )
            } catch (e: SocketTimeoutException) { e.printStackTrace()
            } catch (e: IOException) { e.printStackTrace()
            } catch (e: IllegalArgumentException) {
                Timber.tag(TAG).e(e, "Start socket listener error, invalid port or host")
                return
            }
        }



        try {
            communicator = SocketCommunicator(client)
            communicator!!.readLoop()

            if(!client.isConnected) client.close()
        } catch (e: Exception){
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

    companion object{
        val TAG: String = ConnectTask::class.simpleName!!
    }
}