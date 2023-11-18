package com.mastik.wifidirect.tasks

import android.annotation.SuppressLint
import android.os.ParcelFileDescriptor
import androidx.core.util.Consumer
import androidx.core.util.Supplier
import com.mastik.wifidirect.tasks.Communicator.Companion.MAGIC_FILE_BYTE
import com.mastik.wifidirect.tasks.Communicator.Companion.MAGIC_STRING_BYTE
import timber.log.Timber
import java.io.DataInputStream
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.net.Socket
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.min

class SocketCommunicator() : Communicator {
    companion object {
        val TAG: String = SocketCommunicator::class.simpleName!!
    }

    private var mainOutStream: OutputStream? = null
    private var outTextStream: OutputStreamWriter? = null

    private val writeLock = ReentrantLock(true)

    private val onMessageSend: Consumer<String> = Consumer<String> { message ->
        val outStream = outTextStream
        outStream?.let {
            writeLock.lock()
            try {
                val len = message.length
                Timber.tag(TAG).d("Send message of %d bytes: %s", len, message)
                try {
                    it.write(MAGIC_STRING_BYTE)
                    it.flush()
                    for (i in 0 until Int.SIZE_BYTES) mainOutStream!!.write(len shr (i * 8))
                    it.flush()
                    it.write(message)
                    it.flush()
                } catch (e: IOException) {
                    Timber.tag(TAG).e(e, "Send message error")
                }
            } catch (_: Exception) {
            } finally {
                writeLock.unlock()
            }
        }
    }


    private val onFileSend: Consumer<FileDescriptor> = Consumer<FileDescriptor> { file ->
        Timber.tag(TAG).d("Send file")
        mainOutStream?.let {
            val fileStream = DataInputStream(FileInputStream(file))
            writeLock.lock()
            try {
                it.write(MAGIC_FILE_BYTE)
                it.flush()
                for (i in 0 until Int.SIZE_BYTES) mainOutStream!!.write(fileStream.available() shr (i * 8))
                it.flush()
                val arr = ByteArray(1024)
                while (fileStream.available() > 0) {
                    println("Available: ${fileStream.available()}")
                    val toRead = min(fileStream.available(), 1024)
                    fileStream.readFully(arr, 0, toRead)
                    it.write(arr, 0, toRead)
                }
                it.flush()
            } catch (_: Exception) {
            } finally {
                writeLock.unlock()
            }
        }
    }

    private var newMessageListener: Consumer<String>? = null
    private var newFileListener: Supplier<ParcelFileDescriptor>? = null


    @SuppressLint("NewApi")
    @Throws(IOException::class)
    fun readLoop(socket: Socket) {
        mainOutStream = socket.getOutputStream()
        outTextStream = OutputStreamWriter(socket.getOutputStream(), Charset.forName("UTF-8"))

        val rawStream = socket.getInputStream()
        val stream = InputStreamReader(socket.getInputStream())

        var messageBuff = CharBuffer.allocate(1024)
        val byteArray = ByteArray(4)

        while (socket.isConnected) {
            rawStream.read(byteArray, 0, 1)
            val magic = byteArray[0].toInt() and 0xFF
            rawStream.read(byteArray, 0, 4)
            var dataSize = 0
            for (i in 0 until Int.SIZE_BYTES) dataSize += (byteArray[i].toInt() and 0xFF) shl (i * 8)
            if (magic == MAGIC_STRING_BYTE) {
                if (messageBuff.capacity() < dataSize)
                    messageBuff = CharBuffer.allocate(dataSize)
                stream.read(messageBuff)

                val message = messageBuff.position(0).toString().substring(0, dataSize)
                Timber.tag(TAG).d("Received %d bytes: %s", dataSize, message)
                newMessageListener?.accept(message)
                messageBuff.clear()
                continue
            }
            if (magic == MAGIC_FILE_BYTE) {
                newFileListener?.get()?.let {
                    val fileStream = FileOutputStream(it.fileDescriptor)
                    val buffer = ByteArray(32768)

                    var total: Long = 0
                    val start = System.currentTimeMillis()
                    var i = 0
                    while (dataSize > 0) {
                        val toRead = min(dataSize, buffer.size)
                        dataSize -= rawStream.read(buffer, 0, toRead)
                        fileStream.write(buffer, 0, toRead)

                        total += toRead
                        if (i % 400 == 0) {
                            val cost = System.currentTimeMillis() - start
                            System.out.printf(
                                "Readed %,d bytes, speed: %,f MB/s, left: %,d bytes %n",
                                total,
                                total.toDouble() / cost / 1000,
                                dataSize
                            )
                        }
                        i++
                    }
                    fileStream.close()
                    it.close()
                }
                println("Successfully readed file")
                continue
            }
            println("Unknown magic number $magic")
        }
    }

    override fun getMessageSender(): Consumer<String> {
        return onMessageSend
    }

    override fun setOnNewMessageListener(onNewMessage: Consumer<String>) {
        newMessageListener = onNewMessage
    }

    override fun getFileSender(): Consumer<FileDescriptor> {
        return onFileSend
    }

    override fun setOnNewFileListener(onNewFile: Supplier<ParcelFileDescriptor>) {
        newFileListener = onNewFile
    }
}