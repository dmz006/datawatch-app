package com.dmzs.datawatchclient.ui.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState

@Composable
internal fun BottomNavBar(
    navController: NavController,
    alertsBadge: Int = 0,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.hierarchy?.firstOrNull()?.route

    val items = listOf(
        BottomNavItem(Destinations.Tabs.Sessions, "Sessions", Icons.Filled.Chat),
        BottomNavItem(Destinations.Tabs.Alerts, "Alerts", Icons.Filled.NotificationsActive),
        BottomNavItem(Destinations.Tabs.Channels, "Channels", Icons.Filled.Forum),
        BottomNavItem(Destinations.Tabs.Stats, "Stats", Icons.Filled.Insights),
        BottomNavItem(Destinations.Tabs.Settings, "Settings", Icons.Filled.Settings),
    )

    NavigationBar {
        items.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.route,
                onClick = {
                    if (currentRoute != item.route) {
                        navController.navigate(item.route) {
                            launchSingleTop = true
                            restoreState = true
                            popUpTo(Destinations.Home) { saveState = true }
                        }
                    }
                },
                icon = {
                    if (item.route == Destinations.Tabs.Alerts && alertsBadge > 0) {
                        BadgedBox(badge = { Badge { Text(alertsBadge.toString()) } }) {
                            Icon(item.icon, contentDescription = item.label)
                        }
                    } else {
                        Icon(item.icon, contentDescription = item.label)
                    }
                },
                label = { Text(item.label) },
            )
        }
    }
}

private data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)
