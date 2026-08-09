package com.querycubix.queryshare.viewmodel

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.querycubix.queryshare.data.model.ConnectionStatus
import com.querycubix.queryshare.data.model.FileItem
import com.querycubix.queryshare.data.model.Message
import com.querycubix.queryshare.data.model.QrPayload
import com.querycubix.queryshare.data.model.ServerDevice
import com.querycubix.queryshare.data.model.TransferHistory
import com.querycubix.queryshare.data.repository.FileRepository
import com.querycubix.queryshare.data.repository.SettingsRepository
import com.querycubix.queryshare.network.SocketManager
import com.querycubix.queryshare.repository.ConnectionRepository
import com.querycubix.queryshare.repository.DiscoveryRepository
import com.querycubix.queryshare.service.DownloadWorker
import com.querycubix.queryshare.service.UploadWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val discoveryRepository = DiscoveryRepository(application)
    private val connectionRepository = ConnectionRepository(application)
    private val repository = FileRepository()
    private val settingsRepository = SettingsRepository(application)
    private val workManager = WorkManager.getInstance(application)

    enum class UIState { SCANNING, DISCOVERED, CONNECTING, CONNECTED, FAILED }

    private val _uiState = MutableStateFlow(UIState.SCANNING)
    val uiState = _uiState.asStateFlow()

    private val _verifiedServers = MutableStateFlow<Map<String, ServerDevice>>(emptyMap())
    val discoveredServers = _verifiedServers.asStateFlow().combine(MutableStateFlow(emptySet<ServerDevice>())) { verified, _ ->
        verified.values.toSet()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    
    val onlineServers = _verifiedServers.asStateFlow().combine(MutableStateFlow(emptySet<ServerDevice>())) { verified, _ ->
        verified.values.filter { it.status == ConnectionStatus.ONLINE }.toSet()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val keepScreenAwakeSetting = settingsRepository.keepScreenAwake

    private val _manualConnectionState = MutableStateFlow<ManualConnectionState>(ManualConnectionState.Idle)
    val manualConnectionState = _manualConnectionState.asStateFlow()

    private val _networkGuidance = MutableStateFlow<String?>(null)
    val networkGuidance = _networkGuidance.asStateFlow()

    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError = _connectionError.asStateFlow()

    fun getLastConnection() = Pair(settingsRepository.getLastIp(), settingsRepository.getLastPort())

    sealed class ManualConnectionState {
        object Idle : ManualConnectionState()
        object Connecting : ManualConnectionState()
        object Success : ManualConnectionState()
        data class Error(val message: String) : ManualConnectionState()
    }

    // Track active transfers
    val isTransferActive = combine(
        workManager.getWorkInfosByTagLiveData("upload").asFlow(),
        workManager.getWorkInfosByTagLiveData("download").asFlow()
    ) { uploads, downloads ->
        val activeUploads = uploads?.any { !it.state.isFinished } ?: false
        val activeDownloads = downloads?.any { !it.state.isFinished } ?: false
        activeUploads || activeDownloads
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val shouldKeepScreenAwake = combine(keepScreenAwakeSetting, isTransferActive) { setting, transferActive ->
        setting || transferActive
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _connectedServer = MutableStateFlow<ServerDevice?>(null)
    private val _pendingServer = MutableStateFlow<ServerDevice?>(null)
    private var pendingManualConnection = false
    val connectedServer = _connectedServer.asStateFlow()

    private val _serverFiles = MutableStateFlow<List<FileItem>>(emptyList())
    val serverFiles = _serverFiles.asStateFlow()

    val messages = connectionRepository.messages

    val transferHistory = connectionRepository.transferHistory

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    init {
        // ensure notification channel exists for pairing notifications
        createNotificationChannel()

        discoveryRepository.discoveredServers.onEach { servers ->
            if (servers.isNotEmpty() && _uiState.value == UIState.SCANNING) {
                _uiState.value = UIState.DISCOVERED
            } else if (servers.isEmpty()) {
                _uiState.value = UIState.SCANNING
            }

            servers.forEach { device ->
                if (!_verifiedServers.value.containsKey(device.name)) {
                    verifyConnectivity(device)
                }
            }
            // Remove servers that are no longer discovered
            val discoveredNames = servers.map { it.name }.toSet()
            _verifiedServers.update { current ->
                current.filterKeys { it in discoveredNames }
            }
        }.launchIn(viewModelScope)

        connectionRepository.connectionState.onEach { state ->
            when (state) {
                SocketManager.ConnectionState.CONNECTING -> _uiState.value = UIState.CONNECTING
                SocketManager.ConnectionState.CONNECTED -> {
                    _uiState.value = UIState.CONNECTED
                    _connectionError.value = null
                    _pendingServer.value?.let { connected ->
                        _connectedServer.value = connected
                        settingsRepository.saveLastConnection(connected.ipAddress, connected.port)
                        refreshFiles()
                        // notify user that pairing succeeded
                        try {
                            sendPairNotification(connected.name)
                        } catch (e: Exception) {
                            Log.w("MainViewModel", "Failed to send notification", e)
                        }
                    }
                    pendingManualConnection = false
                    _pendingServer.value = null
                }
                SocketManager.ConnectionState.FAILED -> {
                    _uiState.value = UIState.FAILED
                    _connectionError.value = _networkGuidance.value
                        ?: "Connection failed. Make sure your phone is connected to the laptop hotspot and the QR uses the matching hotspot IP address."
                    if (pendingManualConnection) {
                        _manualConnectionState.value = ManualConnectionState.Error("Connection failed")
                    }
                    pendingManualConnection = false
                    _pendingServer.value = null
                }
                SocketManager.ConnectionState.DISCONNECTED -> {
                    if (discoveredServers.value.isNotEmpty()) _uiState.value = UIState.DISCOVERED
                    else _uiState.value = UIState.SCANNING
                }
            }
        }.launchIn(viewModelScope)
    }

    private fun verifyConnectivity(device: ServerDevice) {
        viewModelScope.launch {
            Log.d("MainViewModel", "Verifying connectivity for ${device.name}")
            val tempRepo = FileRepository()
            tempRepo.updateBaseUrl(device.ipAddress, device.port)
            
            val pingResponse = tempRepo.ping()
            
            val status = if (pingResponse?.isSuccessful == true) {
                ConnectionStatus.ONLINE
            } else if (pingResponse != null) {
                ConnectionStatus.ERROR
            } else {
                ConnectionStatus.OFFLINE
            }
            
            _verifiedServers.update { it + (device.name to device.copy(status = status)) }
        }
    }

    fun connectByIp(ip: String, port: Int) {
        viewModelScope.launch {
            _manualConnectionState.value = ManualConnectionState.Connecting
            pendingManualConnection = true
            connectToTarget(ServerDevice(name = "Manual: $ip", ipAddress = ip, port = port, status = ConnectionStatus.ONLINE))
        }
    }

    fun resetManualConnectionState() {
        _manualConnectionState.value = ManualConnectionState.Idle
    }

    fun setKeepScreenAwake(enabled: Boolean) {
        settingsRepository.setKeepScreenAwake(enabled)
    }

    fun startDiscovery() {
        refreshNetworkGuidance()
        discoveryRepository.startDiscovery()
    }

    fun stopDiscovery() {
        discoveryRepository.stopDiscovery()
    }

    fun connectToServer(server: ServerDevice) {
        viewModelScope.launch {
            Log.d("MainViewModel", "CONNECTING to ${server.name}")
            connectToTarget(server)
        }
    }

    fun connectWithQrPayload(payload: QrPayload) {
        Log.d("MainViewModel", "connectWithQrPayload called: deviceName=${payload.deviceName}, ip=${payload.ip}, alternates=${payload.alternateIps}, port=${payload.port}")
        viewModelScope.launch {
            Log.d("MainViewModel", "CONNECTING_VIA_QR to ${payload.deviceName}")
            val candidateIps = (listOf(payload.ip) + payload.alternateIps).distinct()
            for (candidateIp in candidateIps) {
                val connected = connectToTarget(
                    ServerDevice(
                        name = payload.deviceName,
                        ipAddress = candidateIp,
                        port = payload.port,
                        status = ConnectionStatus.ONLINE
                    ),
                    failOnUnreachable = false
                )
                if (connected) return@launch
            }

            _connectionError.value = _networkGuidance.value
                ?: "Could not reach any QR address: ${candidateIps.joinToString()}. In the desktop Hotspot screen, select the IP address for the network your phone is connected to, then scan again."
            _pendingServer.value = null
            _uiState.value = UIState.FAILED
        }
    }

    private suspend fun connectToTarget(server: ServerDevice, failOnUnreachable: Boolean = true): Boolean {
        refreshNetworkGuidance()
        Log.d("MainViewModel", "connectToTarget: ${server.name} (${server.ipAddress}:${server.port})")
        _uiState.value = UIState.CONNECTING
        _connectionError.value = null
        _pendingServer.value = server
        
        repository.updateBaseUrl(server.ipAddress, server.port)
        Log.d("MainViewModel", "BASE_URL_UPDATED: ${server.ipAddress}:${server.port}")

        // Try to ping the server first - with retry logic
        var pingSuccess = false
        for (attempt in 1..3) {
            if (repository.checkConnection()) {
                pingSuccess = true
                Log.d("MainViewModel", "PING_SUCCESS on attempt $attempt")
                break
            } else {
                Log.w("MainViewModel", "PING_FAILED attempt $attempt/3")
                if (attempt < 3) {
                    kotlinx.coroutines.delay(500) // Wait before retry
                }
            }
        }

        if (!pingSuccess) {
            Log.e("MainViewModel", "PING_FAILED_FINAL for ${server.name} after retries")
            if (failOnUnreachable) {
                _connectionError.value = _networkGuidance.value
                    ?: "Could not reach ${server.ipAddress}:${server.port}. In the desktop Hotspot screen, select the IP address for the network your phone is connected to, then scan again."
                _uiState.value = UIState.FAILED
                if (pendingManualConnection) {
                    _manualConnectionState.value = ManualConnectionState.Error(_networkGuidance.value ?: "Connection failed: server unreachable")
                }
                pendingManualConnection = false
                _pendingServer.value = null
            }
            return false
        }

        Log.d("MainViewModel", "PING_SUCCESS, initiating socket connection")
        connectionRepository.disconnect()
        connectionRepository.connect(server.ipAddress, server.port)
        Log.d("MainViewModel", "SOCKET_CONNECT_INITIATED for ${server.name}")
        return true
    }

    fun refreshFiles() {
        viewModelScope.launch {
            _isRefreshing.value = true
            repository.getFiles().onSuccess {
                _serverFiles.value = it
            }.onFailure {
                Log.e("MainViewModel", "Refresh failed", it)
            }
            _isRefreshing.value = false
        }
    }

    private fun createNotificationChannel() {
        try {
            val channelId = "queryshare_pair_channel"
            val name = "Pairing"
            val descriptionText = "Notifications when a device pairs with Tumira"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager = getApplication<Application>().getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        } catch (e: Exception) {
            Log.w("MainViewModel", "Could not create notification channel", e)
        }
    }

    private fun sendPairNotification(serverName: String) {
        try {
            val context = getApplication<Application>().applicationContext
            val channelId = "queryshare_pair_channel"
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Paired with $serverName")
                .setContentText("Your device is now connected to $serverName")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setSound(soundUri)

            with(NotificationManagerCompat.from(context)) {
                notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), builder.build())
            }
        } catch (e: Exception) {
            Log.w("MainViewModel", "Failed to send pairing notification", e)
        }
    }

    fun uploadFile(uri: Uri) {
        val server = _connectedServer.value ?: return
        val uploadRequest = OneTimeWorkRequestBuilder<UploadWorker>()
            .addTag("upload")
            .setInputData(workDataOf(
                "file_uri" to uri.toString(),
                "server_ip" to server.ipAddress,
                "server_port" to server.port
            ))
            .build()
        
        workManager.enqueue(uploadRequest)
    }

    fun downloadFile(fileId: String) {
        val server = _connectedServer.value ?: return
        val downloadRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .addTag("download")
            .setInputData(workDataOf(
                "file_id" to fileId,
                "server_ip" to server.ipAddress,
                "server_port" to server.port
            ))
            .build()
        
        workManager.enqueue(downloadRequest)
    }

    fun sendMessage(text: String) {
        connectionRepository.sendMessage(text)
    }

    private fun refreshNetworkGuidance() {
        _networkGuidance.value = getNetworkGuidance()
    }

    private fun getNetworkGuidance(): String? {
        val context = getApplication<Application>().applicationContext
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return null
        val network = connectivityManager.activeNetwork
            ?: return "No network connection detected. Connect to the laptop hotspot or the same Wi-Fi as the desktop app."
        val capabilities = connectivityManager.getNetworkCapabilities(network)
            ?: return null

        val onWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val onCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)

        return when {
            onWifi -> null
            onCellular -> "Your phone is using mobile data. Connect it to the laptop hotspot or the same Wi-Fi as the desktop app before pairing."
            else -> "For direct transfers, connect this phone to the laptop hotspot or the same Wi-Fi as the desktop app."
        }
    }

    override fun onCleared() {
        super.onCleared()
        connectionRepository.disconnect()
        discoveryRepository.stopDiscovery()
    }
}
