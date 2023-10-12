package com.mastik.wifidirect.tasks

import androidx.core.util.Consumer

interface Communicator {
    abstract fun getMessageSender(): Consumer<String>
    abstract fun setOnNewMessageListener(onNewMessage: Consumer<String>)
}