package com.mastik.wifi_direct.tasks

import android.os.Looper
import android.os.NetworkOnMainThreadException
import com.mastik.wifi_direct.transfer.Communicator
import com.mastik.wifi_direct.transfer.FileDescriptorTransferInfo
import timber.log.Timber
import java.net.BindException
import java.net.ServerSocket
import java.util.LinkedList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.function.Consumer
import java.util.function.Function

class ServerStartTask(
    private val defaultPort: Int,
) : Communicator, Runnable {

    companion object {
        val TAG: String = ServerStartTask::class.simpleName!!

        const val MAX_PORT_OFFSET = 10

        private val isServerRunning: AtomicBoolean = AtomicBoolean(false)

        fun isServerRunning(): Boolean = isServerRunning.get()
    }

    private val communicatorsLock: ReadWriteLock = ReentrantReadWriteLock()
    private val communicators: MutableMap<String, LinkedList<Communicator>> = mutableMapOf()

    var newFileListener: Function<String, FileDescriptorTransferInfo?>? = null
    var newMessageListener: Consumer<String>? = null

    override fun run() {
        if (isServerRunning.get())
            throw IllegalStateException("Another instance of Server already running")
        if (Looper.myLooper() == Looper.getMainLooper())
            throw NetworkOnMainThreadException()


        var server: ServerSocket? = null
        var portOffset = 0
        try {
            while (server == null) {
                if (portOffset >= MAX_PORT_OFFSET) {
                    Timber.tag(TAG).e("Start socket listener error, port overflow")
                    return
                }
                try {
                    server = ServerSocket(defaultPort + portOffset++)
                } catch (e: BindException) {
                    e.printStackTrace()
                }
            }
            isServerRunning.set(!server.isClosed)
        } catch (e: IllegalArgumentException) {
            Timber.tag(TAG).e(e, "Start socket listener error, invalid port: %d", defaultPort)
            return
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Start socket listener unexpected error")
            return
        }



        try {
            while (!server.isClosed) {
                val client = server.accept()
                TaskExecutors.getCachedPool().execute {
                    val newClient = SocketCommunicator()
                    newClient.setOnNewMessageListener() {
                        newMessageListener?.accept(it)
                    }
                    newClient.setOnNewFileListener() {
                        return@setOnNewFileListener newFileListener?.apply(it)
                    }
                    communicatorsLock.writeLock().lock()
                    try{
                        communicators.getOrPut(client.inetAddress.hostAddress!!){
                            return@getOrPut LinkedList()
                        }.add(newClient)
                    } finally {
                        communicatorsLock.writeLock().unlock()
                    }
                    try {
                        newClient.readLoop(client)
                    } catch (_: Exception) {
                    } finally {
                        communicatorsLock.writeLock().lock()
                        communicators[client.inetAddress.hostAddress!!]?.remove(newClient)
                        communicatorsLock.writeLock().unlock()
                    }
                }
            }
            server.close()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isServerRunning.set(false)
        }
    }

    override fun getFileSender(): Consumer<FileDescriptorTransferInfo> =
        Consumer { transferInfo ->
            communicatorsLock.readLock().lock()
            try {
                communicators.forEach {
                    if(it.value.size <= 0)
                        return@forEach
                    it.value[0].getFileSender().accept(transferInfo)
                    it.value.addLast(it.value.removeFirst())
                }
            } finally {
                communicatorsLock.readLock().unlock()
            }
        }

    override fun getMessageSender(): Consumer<String> =
        Consumer { message ->
            communicatorsLock.readLock().lock()
            try {
                communicators.forEach {
                    if(it.value.size <= 0)
                        return@forEach
                    it.value[0].getMessageSender().accept(message)
                    it.value.addLast(it.value.removeFirst())
                }
            } finally {
                communicatorsLock.readLock().unlock()
            }
        }

    override fun setOnNewFileListener(onNewFile: Function<String, FileDescriptorTransferInfo?>) {
        newFileListener = onNewFile
    }

    override fun setOnNewMessageListener(onNewMessage: Consumer<String>) {
        newMessageListener = onNewMessage
    }
}