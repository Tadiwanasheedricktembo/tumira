package com.querycubix.queryshare.network

import android.content.Context
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.querycubix.queryshare.data.model.Message
import com.querycubix.queryshare.data.model.TransferDirection
import com.querycubix.queryshare.data.model.TransferHistory
import com.querycubix.queryshare.data.model.TransferStatus
import com.querycubix.queryshare.service.DownloadWorker
import io.socket.client.Ack
import io.socket.client.IO
import io.socket.client.Manager
import io.socket.client.Socket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.net.URISyntaxException

class SocketManager(context: Context) {
    private var socket: Socket? = null
    private var serverIp: String? = null
    private var serverPort: Int = 0
    private val workManager = WorkManager.getInstance(context)
    
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.DISCONNECTED)
    val connectionState = _connectionState.asStateFlow()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _transferHistory = MutableStateFlow<List<TransferHistory>>(emptyList())
    val transferHistory = _transferHistory.asStateFlow()

    private var deviceId: String? = null

    enum class ConnectionState {
        DISCONNECTED, CONNECTING, CONNECTED, FAILED
    }

    fun connect(ip: String, port: Int) {
        try {
            // Trim IP address in case of whitespace
            val cleanIp = ip.trim()
            Log.d("SocketManager", "connect() called: $cleanIp:$port")
            
            val opts = IO.Options()
            opts.reconnection = true
            opts.reconnectionDelay = 1000
            opts.reconnectionDelayMax = 5000
            opts.reconnectionAttempts = Int.MAX_VALUE
            opts.timeout = 10000
            opts.forceNew = true // Ensure new connection instance

            socket = IO.socket("http://$cleanIp:$port", opts)
            deviceId = socket?.id()
            serverIp = cleanIp
            serverPort = port
            
            Log.d("SocketManager", "SOCKET_CONNECT attempt to $cleanIp:$port")
            _connectionState.value = ConnectionState.CONNECTING

            socket?.on(Socket.EVENT_CONNECT) {
                Log.d("SocketManager", "CONNECTED to server")
                deviceId = socket?.id()
                Log.d("SocketManager", "ASSIGNED_DEVICE_ID: $deviceId")
                sendHandshake()
            }

            socket?.on(Socket.EVENT_DISCONNECT) {
                Log.d("SocketManager", "DISCONNECTED from server")
                _connectionState.value = ConnectionState.DISCONNECTED
            }

            socket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
                Log.e("SocketManager", "SOCKET_CONNECT_ERROR: ${args.getOrNull(0)}")
                _connectionState.value = ConnectionState.FAILED
            }

            socket?.io()?.on(Manager.EVENT_RECONNECT_ATTEMPT) { args ->
                Log.d("SocketManager", "RECONNECT_ATTEMPT: ${args.getOrNull(0)}")
            }

            socket?.io()?.on(Manager.EVENT_RECONNECT_ERROR) { args ->
                Log.e("SocketManager", "RECONNECT_ERROR: ${args.getOrNull(0)}")
            }

            socket?.io()?.on(Manager.EVENT_RECONNECT_FAILED) {
                Log.e("SocketManager", "RECONNECT_FAILED")
            }

            socket?.on("CONNECTION_ACKNOWLEDGED") { args ->
                val payload = args.getOrNull(0) as? JSONObject
                Log.d("SocketManager", "CONNECTION_ACKNOWLEDGED: $payload")
                val serverInfo = payload?.optJSONObject("serverInfo")
                val message = Message(
                    id = "ack-${System.currentTimeMillis()}",
                    from = "server",
                    fromName = serverInfo?.optString("name") ?: "Tumira Desktop",
                    to = null,
                    text = payload?.optString("message") ?: "Welcome to Tumira",
                    timestamp = payload?.optString("connectedAt") ?: System.currentTimeMillis().toString(),
                    isLocal = false
                )
                addMessage(message)
            }

            socket?.on("message_received") { args ->
                val payload = args.getOrNull(0) as? JSONObject
                if (payload != null) {
                    val message = Message(
                        id = payload.optString("id"),
                        from = payload.optString("from"),
                        fromName = payload.optString("fromName"),
                        to = payload.optString("to"),
                        text = payload.optString("text"),
                        timestamp = payload.optString("timestamp"),
                        isLocal = false
                    )
                    addMessage(message)
                    Log.d("SocketManager", "MESSAGE_RECEIVED: ${message.text}")
                }
            }

            socket?.on("message_sent") { args ->
                val payload = args.getOrNull(0) as? JSONObject
                if (payload != null) {
                    val message = Message(
                        id = payload.optString("id"),
                        from = payload.optString("from"),
                        fromName = payload.optString("fromName"),
                        to = payload.optString("to"),
                        text = payload.optString("text"),
                        timestamp = payload.optString("timestamp"),
                        isLocal = true
                    )
                    addMessage(message)
                    Log.d("SocketManager", "MESSAGE_SENT: ${message.text}")
                }
            }

            socket?.on("TRANSFER_START") { args ->
                val payload = args.getOrNull(0) as? JSONObject
                if (payload != null) {
                    val fileId = payload.optString("fileId")
                    val senderId = payload.optString("sender")
                    val fileName = payload.optString("fileName")
                    val fileSize = payload.optLong("size", 0L)

                    val transfer = TransferHistory(
                        id = payload.optString("sessionId", "transfer-${System.currentTimeMillis()}"),
                        fileName = fileName,
                        fileSize = fileSize,
                        direction = TransferDirection.DOWNLOAD,
                        status = TransferStatus.ONGOING,
                        progress = 0
                    )
                    updateTransferHistory(transfer)
                    Log.d("SocketManager", "TRANSFER_START: $fileName ($fileId) from $senderId")

                    if (fileId.isNotBlank() && !serverIp.isNullOrBlank()) {
                        val downloadRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
                            .addTag("download")
                            .setInputData(
                                workDataOf(
                                    "file_id" to fileId,
                                    "server_ip" to serverIp,
                                    "server_port" to serverPort
                                )
                            )
                            .build()
                        workManager.enqueue(downloadRequest)
                    } else {
                        Log.e("SocketManager", "TRANSFER_START missing fileId or server address")
                    }
                }
            }

            socket?.on("uploadStarted") { args ->
                val payload = args.getOrNull(0) as? JSONObject
                Log.d("SocketManager", "UPLOAD_STARTED: $payload")
                payload?.let {
                    val item = TransferHistory(
                        id = it.optString("uploadId", "upload-${System.currentTimeMillis()}"),
                        fileName = it.optString("filename", "Unknown file"),
                        fileSize = 0L,
                        direction = TransferDirection.UPLOAD,
                        status = TransferStatus.ONGOING,
                        progress = 0
                    )
                    updateTransferHistory(item)
                }
            }

            socket?.on("uploadCompleted") { args ->
                val payload = args.getOrNull(0) as? JSONObject
                Log.d("SocketManager", "UPLOAD_COMPLETED: $payload")
                payload?.let {
                    val item = TransferHistory(
                        id = it.optString("id", "upload-${System.currentTimeMillis()}"),
                        fileName = it.optString("filename", "Unknown file"),
                        fileSize = 0L,
                        direction = TransferDirection.UPLOAD,
                        status = TransferStatus.COMPLETED,
                        progress = 100
                    )
                    updateTransferHistory(item)
                }
            }

            socket?.on("downloadStarted") { args ->
                val payload = args.getOrNull(0) as? JSONObject
                Log.d("SocketManager", "DOWNLOAD_STARTED: $payload")
                payload?.let {
                    val item = TransferHistory(
                        id = it.optString("id", "download-${System.currentTimeMillis()}"),
                        fileName = it.optString("filename", "Unknown file"),
                        fileSize = 0L,
                        direction = TransferDirection.DOWNLOAD,
                        status = TransferStatus.ONGOING,
                        progress = 0
                    )
                    updateTransferHistory(item)
                }
            }

            socket?.on("downloadCompleted") { args ->
                val payload = args.getOrNull(0) as? JSONObject
                Log.d("SocketManager", "DOWNLOAD_COMPLETED: $payload")
                payload?.let {
                    val item = TransferHistory(
                        id = it.optString("id", "download-${System.currentTimeMillis()}"),
                        fileName = it.optString("filename", "Unknown file"),
                        fileSize = 0L,
                        direction = TransferDirection.DOWNLOAD,
                        status = TransferStatus.COMPLETED,
                        progress = 100
                    )
                    updateTransferHistory(item)
                }
            }

            socket?.connect()
        } catch (e: URISyntaxException) {
            Log.e("SocketManager", "URISyntaxException: ${e.message}")
            _connectionState.value = ConnectionState.FAILED
        }
    }

    private fun sendHandshake() {
        val handshake = JSONObject().apply {
            put("deviceName", "Android Device")
            put("deviceType", "android")
            put("appVersion", "1.0")
        }
        Log.d("SocketManager", "HANDSHAKE_SENT: $handshake")
        socket?.emit("handshake", handshake, Ack { args ->
            val response = args.getOrNull(0) as? JSONObject
            Log.d("SocketManager", "HANDSHAKE_RESPONSE: $response")
            if (response?.optBoolean("success") == true) {
                _connectionState.value = ConnectionState.CONNECTED
            } else {
                _connectionState.value = ConnectionState.FAILED
            }
        })
    }

    fun sendMessage(text: String, toDeviceId: String? = null) {
        if (socket?.connected() != true) {
            Log.w("SocketManager", "Socket not connected, cannot send message")
            return
        }

        val payload = JSONObject().apply {
            put("text", text)
            if (toDeviceId != null) {
                put("to", toDeviceId)
            }
        }

        socket?.emit("send_message", payload, Ack { args ->
            val response = args.getOrNull(0) as? JSONObject
            if (response?.optBoolean("success") == true) {
                Log.d("SocketManager", "MESSAGE_DELIVERY_CONFIRMED: ${response.optString("messageId")}")
            } else {
                Log.e("SocketManager", "MESSAGE_DELIVERY_FAILED: ${response?.optString("error")}")
            }
        })
    }

    private fun addMessage(message: Message) {
        _messages.value = _messages.value + message
    }

    private fun updateTransferHistory(transfer: TransferHistory) {
        _transferHistory.value = _transferHistory.value.toMutableList().also { list ->
            val existingIndex = list.indexOfFirst { it.id == transfer.id }
            if (existingIndex >= 0) {
                list[existingIndex] = transfer
            } else {
                list.add(transfer)
            }
        }
    }

    fun clearMessages() {
        _messages.value = emptyList()
    }

    fun disconnect() {
        socket?.disconnect()
        socket?.off()
        _connectionState.value = ConnectionState.DISCONNECTED
        deviceId = null
    }
}

