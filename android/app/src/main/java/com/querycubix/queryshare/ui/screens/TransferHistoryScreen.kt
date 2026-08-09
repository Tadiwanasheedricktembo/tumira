package com.querycubix.queryshare.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.querycubix.queryshare.data.model.TransferDirection
import com.querycubix.queryshare.data.model.TransferHistory
import com.querycubix.queryshare.viewmodel.MainViewModel
import com.querycubix.queryshare.util.formatFileSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferHistoryScreen(viewModel: MainViewModel) {
    val history by viewModel.transferHistory.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Transfer History") })
        }
    ) { padding ->
        if (history.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("No transfers yet")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(history) { item ->
                    TransferItem(item)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun TransferItem(item: TransferHistory) {
    ListItem(
        headlineContent = { Text(item.fileName) },
        supportingContent = { Text("${formatFileSize(item.fileSize)} • ${item.status}") },
        leadingContent = {
            Icon(
                if (item.direction == TransferDirection.UPLOAD) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                contentDescription = null
            )
        },
        trailingContent = {
            if (item.progress in 1..99) {
                CircularProgressIndicator(
                    progress = { item.progress / 100f },
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    )
}
