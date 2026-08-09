package com.querycubix.queryshare

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.querycubix.queryshare.ui.components.BottomNavigationBar
import com.querycubix.queryshare.ui.navigation.Screen
import com.querycubix.queryshare.ui.DiscoveryScreen
import com.querycubix.queryshare.ui.screens.ChatScreen
import com.querycubix.queryshare.ui.screens.FileBrowserScreen
import com.querycubix.queryshare.ui.screens.QRScannerScreen
import com.querycubix.queryshare.ui.screens.SplashScreen
import com.querycubix.queryshare.ui.screens.TransferHistoryScreen
import com.querycubix.queryshare.ui.screens.SettingsScreen
import com.querycubix.queryshare.ui.theme.QueryshareTheme
import com.querycubix.queryshare.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            QueryshareTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    QueryShareApp()
                }
            }
        }
    }
}

@Composable
fun QueryShareApp() {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        permissions.add(Manifest.permission.CAMERA)
        permissionLauncher.launch(permissions.toTypedArray())
    }

    val navController = rememberNavController()
    val viewModel: MainViewModel = viewModel()
    val connectedServer by viewModel.connectedServer.collectAsState()
    val shouldKeepScreenAwake by viewModel.shouldKeepScreenAwake.collectAsState()

    LaunchedEffect(shouldKeepScreenAwake) {
        val window = (context as? android.app.Activity)?.window
        if (shouldKeepScreenAwake) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            Log.d("QueryShare", "KeepScreenOn enabled")
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            Log.d("QueryShare", "KeepScreenOn disabled")
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in listOf(
        Screen.FileBrowser.route,
        Screen.Chat.route,
        Screen.Transfers.route,
        Screen.Settings.route
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                BottomNavigationBar(navController)
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Splash.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Splash.route) {
                SplashScreen(onTimeout = {
                    val nextRoute = if (connectedServer == null) Screen.Discovery.route else Screen.FileBrowser.route
                    navController.navigate(nextRoute) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                })
            }
            composable(Screen.Discovery.route) {
                DiscoveryScreen(
                    viewModel = viewModel,
                    onServerSelected = { server ->
                        viewModel.connectToServer(server)
                        navController.navigate(Screen.FileBrowser.route) {
                            popUpTo(Screen.Discovery.route) { inclusive = true }
                        }
                    },
                    onScanRequested = {
                        navController.navigate(Screen.QRScanner.route)
                    }
                )
            }
            composable(Screen.QRScanner.route) {
                QRScannerScreen(
                    viewModel = viewModel,
                    onBack = { navController.navigateUp() },
                    onConnected = {
                        navController.navigate(Screen.FileBrowser.route) {
                            popUpTo(Screen.Discovery.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.FileBrowser.route) {
                FileBrowserScreen(viewModel = viewModel)
            }
            composable(Screen.Chat.route) {
                ChatScreen(viewModel = viewModel)
            }
            composable(Screen.Transfers.route) {
                TransferHistoryScreen(viewModel = viewModel)
            }
            composable(Screen.Settings.route) {
                SettingsScreen(viewModel = viewModel)
            }
        }
    }
}
