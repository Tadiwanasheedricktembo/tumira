package com.querycubix.queryshare.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.querycubix.queryshare.data.model.ServerDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class NSDHelper(private val context: Context) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val _discoveredServers = MutableStateFlow<Set<ServerDevice>>(emptySet())
    val discoveredServers = _discoveredServers.asStateFlow()

    private val SERVICE_TYPE = "_queryshare._tcp."
    private val resolveQueue = ArrayDeque<NsdServiceInfo>()
    private val resolvedServiceNames = mutableSetOf<String>()
    private var multicastLock: WifiManager.MulticastLock? = null
    private var isResolving = false
    private var isDiscovering = false
    private var activeResolveId = 0
    private val handler = Handler(Looper.getMainLooper())
    private var resolveWatchdog: Runnable? = null

    init {
        Log.d("NSDHelper", "NSDHelper initialized")
    }

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(regType: String) {
            isDiscovering = true
            Log.d("NSDHelper", "DISCOVERY_START")
        }

        override fun onServiceFound(service: NsdServiceInfo) {
            Log.d("NSDHelper", "SERVICE_FOUND: ${service.serviceName}")
            if (service.serviceType == SERVICE_TYPE || service.serviceType == "$SERVICE_TYPE.") {
                enqueueResolve(service)
            }
        }

        override fun onServiceLost(service: NsdServiceInfo) {
            Log.d("NSDHelper", "Service lost: ${service.serviceName}")
            _discoveredServers.value = _discoveredServers.value.filter { it.name != service.serviceName }.toSet()
        }

        override fun onDiscoveryStopped(serviceType: String) {
            isDiscovering = false
            Log.d("NSDHelper", "Discovery stopped")
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            isDiscovering = false
            Log.e("NSDHelper", "Start discovery failed: $errorCode")
            nsdManager.stopServiceDiscovery(this)
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            isDiscovering = false
            Log.e("NSDHelper", "Stop discovery failed: $errorCode")
            nsdManager.stopServiceDiscovery(this)
        }
    }

    private fun enqueueResolve(service: NsdServiceInfo) {
        handler.post {
            val serviceName = service.serviceName
            if (serviceName in resolvedServiceNames || resolveQueue.any { it.serviceName == serviceName }) {
                return@post
            }
            resolveQueue.addLast(service)
            drainResolveQueue()
        }
    }

    private fun drainResolveQueue() {
        if (isResolving) return

        val service = resolveQueue.removeFirstOrNull() ?: return
        Log.d("NSDHelper", "RESOLVE_START for ${service.serviceName}")
        isResolving = true
        val resolveId = ++activeResolveId

        resolveWatchdog = Runnable {
            if (isResolving && resolveId == activeResolveId) {
                Log.e("NSDHelper", "RESOLVE_TIMEOUT for ${service.serviceName}")
                finishResolve(resolveId)
            }
        }
        handler.postDelayed(resolveWatchdog!!, 15000)

        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e("NSDHelper", "RESOLVE_FAILED: ${serviceInfo.serviceName} code=$errorCode")
                finishResolve(resolveId)
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                Log.d("NSDHelper", "RESOLVE_SUCCESS: ${serviceInfo.serviceName}")
                try {
                    val hostAddress = serviceInfo.host?.hostAddress
                    if (hostAddress != null && serviceInfo.port > 0) {
                        val device = ServerDevice(
                            name = serviceInfo.serviceName,
                            ipAddress = hostAddress,
                            port = serviceInfo.port
                        )
                        resolvedServiceNames += serviceInfo.serviceName
                        _discoveredServers.value += device
                    }
                } catch (e: Exception) {
                    Log.e("NSDHelper", "Error processing resolved service", e)
                } finally {
                    finishResolve(resolveId)
                }
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                nsdManager.resolveService(service, { it.run() }, resolveListener)
            } else {
                @Suppress("DEPRECATION")
                nsdManager.resolveService(service, resolveListener)
            }
        } catch (e: Exception) {
            Log.e("NSDHelper", "Error calling resolveService", e)
            finishResolve(resolveId)
        }
    }

    private fun finishResolve(resolveId: Int) {
        if (resolveId != activeResolveId) return
        resolveWatchdog?.let { handler.removeCallbacks(it) }
        resolveWatchdog = null
        isResolving = false
        drainResolveQueue()
    }

    private fun acquireMulticastLock() {
        if (multicastLock?.isHeld == true) return
        multicastLock = wifiManager.createMulticastLock("QueryShareNsdLock").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseMulticastLock() {
        multicastLock?.let { lock -> if (lock.isHeld) lock.release() }
        multicastLock = null
    }

    fun startDiscovery() {
        _discoveredServers.value = emptySet()
        resolvedServiceNames.clear()
        resolveQueue.clear()
        acquireMulticastLock()
        if (!isDiscovering) {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        }
    }

    fun stopDiscovery() {
        resolveWatchdog?.let { handler.removeCallbacks(it) }
        resolveWatchdog = null
        resolveQueue.clear()
        isResolving = false

        if (isDiscovering) {
            try {
                nsdManager.stopServiceDiscovery(discoveryListener)
            } catch (e: Exception) {
                isDiscovering = false
            }
        }
        releaseMulticastLock()
    }
}
