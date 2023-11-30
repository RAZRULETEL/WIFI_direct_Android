package com.mastik.wifi_direct.tasks

import android.os.Looper
import android.os.NetworkOnMainThreadException
import timber.log.Timber
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

class ConnectTask(
    private val host: String,
    private val defaultPort: Int,
    private val connectDelay: Long = 1_000L
): SocketCommunicator(), Runnable {
    companion object{
        val TAG: String = ConnectTask::class.simpleName!!

        private const val CONNECT_TIMEOUT: Int = 3_000
    }

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
            readLoop(client)
            if(!client.isConnected) client.close()
        } catch (e: Exception){
            e.printStackTrace()
        }
    }
}