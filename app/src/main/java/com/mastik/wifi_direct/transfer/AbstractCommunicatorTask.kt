package com.mastik.wifi_direct.transfer

import androidx.core.util.Consumer
import androidx.core.util.Function
import com.mastik.wifi_direct.tasks.SocketCommunicator

abstract class AbstractCommunicatorTask: Runnable, Communicator {
    protected val communicator: SocketCommunicator = SocketCommunicator()


    override fun getMessageSender() = communicator.getMessageSender()
    override fun getFileSender() = communicator.getFileSender()

    override fun setOnNewMessageListener(onNewMessage: Consumer<String>) = communicator.setOnNewMessageListener(onNewMessage)
    override fun setOnNewFileListener(onNewFile: Function<String, FileDescriptorTransferInfo>) = communicator.setOnNewFileListener(onNewFile)
}