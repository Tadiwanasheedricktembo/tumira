package com.querycubix.queryshare.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.querycubix.queryshare.data.model.ConnectionStatus
import com.querycubix.queryshare.data.model.ServerDevice
import com.querycubix.queryshare.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryScreen(
    viewModel: MainViewModel,
    onServerSelected: (ServerDevice) -> Unit,
    onScanRequested: () -> Unit
) {
    val servers by viewModel.discoveredServers.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val connectedServer by viewModel.connectedServer.collectAsState()
    val networkGuidance by viewModel.networkGuidance.collectAsState()
    var showIpDialog by remember { mutableStateOf(false) }
    var ipAddress by remember { mutableStateOf("") }
    var portValue by remember { mutableStateOf("3000") }

    DisposableEffect(Unit) {
        viewModel.startDiscovery()
        onDispose {
            viewModel.stopDiscovery()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Discover Servers")
                        Text(
                            text = uiState.name, 
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                },
                actions = {
                        IconButton(onClick = { onScanRequested() }) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan QR")
                    }
                    IconButton(onClick = { showIpDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Connect by IP")
                    }
                    IconButton(onClick = { viewModel.startDiscovery() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        if (showIpDialog) {
            AlertDialog(
                onDismissRequest = { showIpDialog = false },
                title = { Text("Connect by IP") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = ipAddress,
                            onValueChange = { ipAddress = it },
                            label = { Text("Server IP") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = portValue,
                            onValueChange = { portValue = it.filter { char -> char.isDigit() } },
                            label = { Text("Server Port") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val port = portValue.toIntOrNull() ?: 3000
                        viewModel.connectByIp(ipAddress.trim(), port)
                        showIpDialog = false
                    }) {
                        Text("Connect")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showIpDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        Column(modifier = Modifier.padding(padding)) {
            if (uiState == MainViewModel.UIState.CONNECTING) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            networkGuidance?.let { message ->
                NetworkGuidanceCard(message = message)
            }

            connectedServer?.let { server ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 4.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .clickable(enabled = true) { onServerSelected(server) }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Paired Device", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(server.name, style = MaterialTheme.typography.bodyLarge)
                        Text("${server.ipAddress}:${server.port}", style = MaterialTheme.typography.bodyMedium)
                        ConnectionStatusBadge(ConnectionStatus.ONLINE)
                    }
                }
            }

            if (servers.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Scanning for Tumira servers...")
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(servers.toList()) { server ->
                        val isOnline = server.status == ConnectionStatus.ONLINE
                        
                        ListItem(
                            headlineContent = { Text(server.name) },
                            supportingContent = { 
                                Column {
                                    Text("${server.ipAddress}:${server.port}")
                                    ConnectionStatusBadge(server.status)
                                }
                            },
                            leadingContent = { 
                                Icon(
                                    Icons.Default.Computer, 
                                    contentDescription = null,
                                    tint = if (isOnline) MaterialTheme.colorScheme.primary else Color.Gray
                                ) 
                            },
                            trailingContent = {
                                Button(
                                    onClick = { onServerSelected(server) },
                                    enabled = isOnline && uiState != MainViewModel.UIState.CONNECTING
                                ) {
                                    Text("Connect")
                                }
                            },
                            modifier = Modifier.clickable(enabled = isOnline) { onServerSelected(server) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun NetworkGuidanceCard(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Hotspot required", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(4.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun ConnectionStatusBadge(status: ConnectionStatus) {
    val (text, color) = when (status) {
        ConnectionStatus.ONLINE -> "Online" to Color(0xFF4CAF50)
        ConnectionStatus.OFFLINE -> "Offline" to Color.Gray
        ConnectionStatus.ERROR -> "Error" to Color.Red
        ConnectionStatus.UNKNOWN -> "Checking..." to Color.Blue
    }
    
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.padding(top = 4.dp)
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}
