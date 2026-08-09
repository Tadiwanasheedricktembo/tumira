package com.querycubix.queryshare.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.querycubix.queryshare.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val keepScreenAwake by viewModel.keepScreenAwakeSetting.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text("App Settings", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            ListItem(
                headlineContent = { Text("Device Name") },
                supportingContent = { Text("Android Device") },
                leadingContent = { Icon(Icons.Default.Person, contentDescription = null) }
            )
            HorizontalDivider()
            
            ListItem(
                headlineContent = { Text("Keep screen awake during transfers") },
                supportingContent = { Text("Prevents the screen from turning off while the app is in foreground") },
                leadingContent = { Icon(Icons.Default.RemoveRedEye, contentDescription = null) },
                trailingContent = {
                    Switch(
                        checked = keepScreenAwake,
                        onCheckedChange = { viewModel.setKeepScreenAwake(it) }
                    )
                }
            )
            HorizontalDivider()

            ListItem(
                headlineContent = { Text("Version") },
                supportingContent = { Text("1.0.0 (Tumira)") },
                leadingContent = { Icon(Icons.Default.Info, contentDescription = null) }
            )
        }
    }
}
