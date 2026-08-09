package com.querycubix.queryshare.repository

import android.content.Context
import com.querycubix.queryshare.network.SocketManager
import kotlinx.coroutines.flow.StateFlow

class ConnectionRepository(context: Context) {
    private val socketManager = SocketManager(context)
    
    val connectionState: StateFlow<SocketManager.ConnectionState> = socketManager.connectionState
    val messages = socketManager.messages
    val transferHistory = socketManager.transferHistory

    fun connect(ip: String, port: Int) {
        socketManager.connect(ip, port)
    }

    fun disconnect() {
        socketManager.disconnect()
    }

    fun sendMessage(text: String, toDeviceId: String? = null) {
        socketManager.sendMessage(text, toDeviceId)
    }
}
