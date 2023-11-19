package com.mastik.wifidirect.tasks

import androidx.core.util.Consumer
import androidx.core.util.Supplier
import java.io.FileDescriptor

interface Communicator {
    abstract fun getMessageSender(): Consumer<String>
    abstract fun setOnNewMessageListener(onNewMessage: Consumer<String>)
    abstract fun getFileSender(): Consumer<FileDescriptor>
    abstract fun setOnNewFileListener(onNewFile: Supplier<FileDescriptor>)

    companion object {
        const val MAGIC_STRING_BYTE = 0x4D
        const val MAGIC_FILE_BYTE = 0x46
    }
}