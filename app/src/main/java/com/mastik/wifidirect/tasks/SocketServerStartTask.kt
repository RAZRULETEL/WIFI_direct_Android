package com.mastik.wifidirect.tasks

import android.os.Looper
import android.os.NetworkOnMainThreadException
import android.widget.ImageButton
import android.widget.TextView
import timber.log.Timber
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.BindException
import java.net.ServerSocket
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.util.function.Consumer

class SocketServerStartTask(
    private val defaultPort: Int,
    private val messageText: TextView,
    private val newMessageListener: Consumer<String>,
    private val sendButton: ImageButton,

    ) : Runnable {

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
            val client = server.accept()

            server.close()

            val outTextStream =
                OutputStreamWriter(client.getOutputStream(), Charset.forName("UTF-8"))

            sendButton.setOnClickListener {
                TaskExecutors.getCachedPool().execute {
                    Timber.tag(TAG).d("Sending...")
                    val len = messageText.text.length
                    for (i in 0 until 4) outTextStream.write(len shl i * 8)

                    outTextStream.write(messageText.text.toString())
                    outTextStream.flush()
                }
            }

            val stream = InputStreamReader(client.getInputStream())
            val buff = CharBuffer.allocate(8192)
            while (client.isConnected) {
                val dataSize = stream.read(buff)
                newMessageListener.accept(buff.toString())
                buff.clear()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        val TAG: String = Companion::class.java.simpleName
    }
}