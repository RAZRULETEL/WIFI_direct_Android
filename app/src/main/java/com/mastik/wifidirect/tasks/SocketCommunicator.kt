package com.mastik.wifidirect.tasks

import androidx.core.util.Consumer
import timber.log.Timber
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import java.nio.CharBuffer
import java.nio.charset.Charset

class SocketCommunicator (private val socket: Socket) {
    private val outTextStream =
        OutputStreamWriter(socket.getOutputStream(), Charset.forName("UTF-8"))

    private val onMessageSend: Consumer<String> = Consumer<String>{ message ->
        val outTextStream = this.outTextStream

        Timber.tag(TAG).d("Sending...")
        val len = message.length
        try {
            for (i in 0 until 4) outTextStream.write(len shl i * 8)

            outTextStream.write(message)
            outTextStream.flush()
        } catch (e: IOException) {
            Timber.tag(TAG).e(e, "Send message error")
        }
    }

    fun readLoop(newMessageListener: Consumer<String>) {
        val stream = InputStreamReader(socket.getInputStream())
        val buff = CharBuffer.allocate(8192)

        while (socket.isConnected) {
            val dataSize = stream.read(buff)
            newMessageListener.accept(buff.toString())
            buff.clear()
        }
    }

    fun getMessageSender(): Consumer<String> {
        return onMessageSend
    }

    companion object{
        val TAG: String = Companion::class.java.simpleName
    }
}