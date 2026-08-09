package com.querycubix.queryshare.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.querycubix.queryshare.ui.navigation.Screen

@Composable
fun BottomNavigationBar(navController: NavController) {
    val items = listOf(
        NavigationItem("Files", Screen.FileBrowser.route, Icons.Default.Home),
        NavigationItem("Messages", Screen.Chat.route, Icons.Default.Chat),
        NavigationItem("History", Screen.Transfers.route, Icons.Default.History),
        NavigationItem("Settings", Screen.Settings.route, Icons.Default.Settings)
    )

    NavigationBar {
        val navBackStackEntry = navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry.value?.destination?.route

        items.forEach { item ->
            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = item.title) },
                label = { Text(item.title) },
                selected = currentRoute == item.route,
                onClick = {
                    navController.navigate(item.route) {
                        navController.graph.startDestinationRoute?.let { route ->
                            popUpTo(route) { saveState = true }
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}

data class NavigationItem(val title: String, val route: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
