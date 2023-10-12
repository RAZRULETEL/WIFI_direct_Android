package com.mastik.wifidirect.tasks

import androidx.core.util.Consumer
import timber.log.Timber
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import java.nio.CharBuffer
import java.nio.charset.Charset

class SocketCommunicator (): Communicator {
    private var outTextStream: OutputStreamWriter? = null

    private val onMessageSend: Consumer<String> = Consumer<String>{ message ->
        outTextStream?.let {
            Timber.tag(TAG).d("Send message: %s", message)

            val len = message.length
            try {
                for (i in 0 until 4) it.write(len shr (i * 8))

                it.write(message)
                it.flush()
            } catch (e: IOException) {
                Timber.tag(TAG).e(e, "Send message error")
            }
        }
    }

    private var newMessageListener: Consumer<String>? = null

    @Throws(IOException::class)
    fun readLoop(socket: Socket) {
        outTextStream = OutputStreamWriter(socket.getOutputStream(), Charset.forName("UTF-8"))

        val stream = InputStreamReader(socket.getInputStream())
        val buff = CharBuffer.allocate(8192)

        while (socket.isConnected) {
            val dataSize = stream.read(buff)
            val message = buff.position(0).toString().substring(0, dataSize)
            Timber.tag(TAG).d("Received %d bytes: %s", dataSize, message)
            newMessageListener?.accept(message)
            buff.clear()
        }
    }

    override fun getMessageSender(): Consumer<String> {
        return onMessageSend
    }

    override fun setOnNewMessageListener(onNewMessage: Consumer<String>) {
        newMessageListener = onNewMessage
    }

    companion object{
        val TAG: String = SocketCommunicator::class.simpleName!!
    }
}