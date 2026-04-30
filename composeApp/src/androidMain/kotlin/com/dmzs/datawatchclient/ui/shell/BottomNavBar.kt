package com.dmzs.datawatchclient.ui.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import com.dmzs.datawatchclient.ui.theme.LocalDatawatchColors

@Composable
internal fun BottomNavBar(
    navController: NavController,
    alertsBadge: Int = 0,
    prdsSupported: Boolean = true,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.hierarchy?.firstOrNull()?.route

    // PWA bottom nav has 4 tabs: Sessions / New / Alerts / Settings.
    // Mobile uses a FAB on Sessions for New (tighter on a phone), and
    // keeps 3 bottom tabs. Stats used to be here; moved into
    // Settings/Monitor per PWA structure. Channels used to be here but
    // the PWA has no such tab — backend picker is under Settings/LLM.
    // v0.38.0 — Autonomous tab added between Sessions and Alerts to
    // surface the PRD lifecycle (#11–13, #18, #19).
    //
    // v0.42.5 user direction 2026-04-28: render the literal 🤖 emoji
    // (U+1F916) for the PRDs tab so it matches the PWA's full-color
    // robot exactly. v0.41.1's `Icons.Filled.SmartToy` was a flat
    // single-color outline; the system emoji renders in colour at the
    // same size on every Android target.
    val items =
        buildList {
            add(BottomNavItem(Destinations.Tabs.Sessions, "Sessions", icon = Icons.Filled.Chat))
            // v0.42.5 — only render PRDs when the active server
            // actually exposes the autonomous surface. Local-only
            // setups + older daemons would otherwise dead-end into
            // an empty list.
            if (prdsSupported) {
                add(BottomNavItem(Destinations.Tabs.Autonomous, "Autonomous", emoji = "🤖"))
            }
            add(BottomNavItem(Destinations.Tabs.Alerts, "Alerts", icon = Icons.Filled.NotificationsActive))
            add(BottomNavItem(Destinations.Tabs.Settings, "Settings", icon = Icons.Filled.Settings))
        }

    val dw = LocalDatawatchColors.current
    // PWA `.nav-btn.active` — color + border-top + rgba(168,85,247,0.08) bg.
    // Material3 NavigationBarItem indicator is the tinted pill; we colour
    // it to match the PWA active tint.
    val itemColors =
        NavigationBarItemDefaults.colors(
            selectedIconColor = dw.accent2,
            selectedTextColor = dw.accent2,
            indicatorColor = dw.accent2.copy(alpha = 0.12f),
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )

    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
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
                            NavGlyph(item)
                        }
                    } else {
                        NavGlyph(item)
                    }
                },
                label = { Text(item.label) },
                colors = itemColors,
            )
        }
    }
}

/**
 * v0.42.5 — bottom-nav item carries either a Material vector
 * [icon] or a literal [emoji] string. The PRDs tab uses the 🤖
 * emoji so it renders in the same full colour the PWA shows; the
 * other tabs stay on Material vectors which inherit the
 * NavigationBarItem selected/unselected tint.
 */
private data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    val emoji: String? = null,
)

@Composable
private fun NavGlyph(item: BottomNavItem) {
    when {
        item.emoji != null ->
            Text(
                item.emoji,
                style = TextStyle(fontSize = 22.sp),
            )
        item.icon != null ->
            Icon(item.icon, contentDescription = item.label)
    }
}
