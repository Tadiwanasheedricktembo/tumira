package com.querycubix.queryshare.repository

import android.content.Context
import android.util.Log
import com.querycubix.queryshare.data.model.ServerDevice
import com.querycubix.queryshare.util.NSDHelper
import kotlinx.coroutines.flow.StateFlow

class DiscoveryRepository(context: Context) {
    private val nsdHelper = NSDHelper(context)
    
    val discoveredServers: StateFlow<Set<ServerDevice>> = nsdHelper.discoveredServers

    fun startDiscovery() {
        Log.d("DiscoveryRepository", "DISCOVERY_START")
        nsdHelper.startDiscovery()
    }

    fun stopDiscovery() {
        Log.d("DiscoveryRepository", "DISCOVERY_STOP")
        nsdHelper.stopDiscovery()
    }
}
