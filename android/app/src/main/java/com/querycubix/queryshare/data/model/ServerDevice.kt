package com.querycubix.queryshare.data.model

enum class ConnectionStatus {
    UNKNOWN, ONLINE, OFFLINE, ERROR
}

data class ServerDevice(
    val name: String,
    val ipAddress: String,
    val port: Int,
    val status: ConnectionStatus = ConnectionStatus.UNKNOWN
)
