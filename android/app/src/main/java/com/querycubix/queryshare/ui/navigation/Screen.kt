package com.querycubix.queryshare.ui.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Home : Screen("home")
    object Discovery : Screen("discovery")
    object QRScanner : Screen("qr_scanner")
    object FileBrowser : Screen("file_browser")
    object Chat : Screen("chat")
    object Transfers : Screen("transfers")
    object Settings : Screen("settings")
}
