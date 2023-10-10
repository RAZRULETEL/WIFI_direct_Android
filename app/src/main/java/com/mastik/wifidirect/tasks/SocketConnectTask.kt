package com.mastik.wifidirect.tasks

import com.mastik.wifidirect.R
import java.net.InetSocketAddress
import java.net.Socket

class SocketConnectTask(
    val host: String,
    val defaultPort: Int,
): Runnable {


    override fun run() {
        val client = Socket()
        try {
            client.connect(InetSocketAddress(host, R.integer.port), R.integer.connect_timeout)
//            statusText.post { statusTex2t.setBackgroundColor(Color.GREEN) }
        }
        catch (e: Exception){
//            statusText.post { statusText.setBackgroundColor(Color.RED) }
            e.printStackTrace()
        }finally {
            client.close()
        }
    }
}