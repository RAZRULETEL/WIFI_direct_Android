package com.mastik.wifidirect.tasks

import android.os.Looper
import android.os.NetworkOnMainThreadException
import androidx.core.util.Consumer
import com.mastik.wifidirect.R
import timber.log.Timber
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

class ConnectTask(
    private val host: String,
    private val defaultPort: Int,
): Communicator, Runnable {

    private var communicator: SocketCommunicator? = null

    override fun run() {
        if(Looper.myLooper() == Looper.getMainLooper())
            throw NetworkOnMainThreadException()



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
                    R.integer.connect_timeout
                )
            } catch (_: SocketTimeoutException) {
            } catch (_: IOException) {
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
        val TAG: String = Companion::class.java.simpleName
    }
}