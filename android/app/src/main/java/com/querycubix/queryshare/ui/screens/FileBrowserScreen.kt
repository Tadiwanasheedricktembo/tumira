package com.querycubix.queryshare.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.querycubix.queryshare.data.model.FileItem
import com.querycubix.queryshare.viewmodel.MainViewModel
import com.querycubix.queryshare.util.formatFileSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    viewModel: MainViewModel
) {
    val files by viewModel.serverFiles.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val connectedServer by viewModel.connectedServer.collectAsState()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.uploadFile(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(connectedServer?.name ?: "File Browser") },
                actions = {
                    IconButton(onClick = { launcher.launch("*/*") }) {
                        Icon(Icons.Default.FileUpload, contentDescription = "Upload")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { launcher.launch("*/*") }) {
                Icon(Icons.Default.FileUpload, contentDescription = "Upload")
            }
        }
    ) { padding ->
        if (files.isEmpty() && !isRefreshing) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No files found on server")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                items(files) { file ->
                    FileListItem(file = file, onDownloadClick = { viewModel.downloadFile(file.id) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun FileListItem(file: FileItem, onDownloadClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(file.name) },
        supportingContent = { Text(formatFileSize(file.size)) },
        leadingContent = { Icon(Icons.Default.InsertDriveFile, contentDescription = null) },
        trailingContent = {
            IconButton(onClick = onDownloadClick) {
                Icon(Icons.Default.Download, contentDescription = "Download")
            }
        }
    )
}
