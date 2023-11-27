package com.mastik.wifidirect.transfer

import androidx.core.util.Consumer
import java.io.FileDescriptor

class FileDescriptorTransferInfo(val descriptor: FileDescriptor, val name: String) {
    companion object{
        const val STATE_CREATED = 0
        const val STATE_TRANSFER_IN_PROGRESS = 1
        const val STATE_TRANSFER_COMPLETED = 2
    }
    private var transferState: Int = STATE_CREATED

    private val progressListeners = mutableListOf<Consumer<FileTransferProgressInfo>>()
    private var onTransferStartListener: Consumer<FileTransferProgressInfo>? = null
    private var onTransferEndListener: Consumer<FileTransferProgressInfo>? = null

    fun addProgressListener(progressListener: Consumer<FileTransferProgressInfo>){
        progressListeners.add(progressListener)
    }

    fun updateTransferProgress(progress: FileTransferProgressInfo){
        if(progress.bytesProgress > 0 && transferState == STATE_CREATED){
            transferState = STATE_TRANSFER_IN_PROGRESS
            onTransferStartListener?.accept(progress)
        }

        progressListeners.forEach { it.accept(progress) }

        if(progress.bytesProgress == progress.bytesTotal){
            transferState = STATE_TRANSFER_COMPLETED
            onTransferEndListener?.accept(progress)
        }
    }

    fun setOnTransferStartListener(onTransferStart: Consumer<FileTransferProgressInfo>?){
        onTransferStartListener = onTransferStart
    }

    fun setOnTransferEndListener(onTransferEnd: Consumer<FileTransferProgressInfo>?){
        onTransferEndListener = onTransferEnd

    }
}