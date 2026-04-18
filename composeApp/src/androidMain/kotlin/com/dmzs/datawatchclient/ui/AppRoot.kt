package com.dmzs.datawatchclient.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.ui.onboarding.OnboardingScreen
import com.dmzs.datawatchclient.ui.servers.AddServerScreen
import com.dmzs.datawatchclient.ui.sessions.SessionsScreen
import com.dmzs.datawatchclient.ui.settings.SettingsScreen
import com.dmzs.datawatchclient.ui.shell.BottomNavBar
import com.dmzs.datawatchclient.ui.shell.Destinations
import com.dmzs.datawatchclient.ui.shell.PlaceholderTabScreen
import com.dmzs.datawatchclient.ui.splash.MatrixSplashScreen
import com.dmzs.datawatchclient.ui.theme.DatawatchTheme

/**
 * Top-level composable. Decides between onboarding and the home shell based on
 * whether any server profiles exist in the encrypted DB.
 */
@Composable
public fun AppRoot() {
    DatawatchTheme {
        val profiles by ServiceLocator.profileRepository
            .observeAll()
            .collectAsState(initial = emptyList())
        val navController = rememberNavController()
        // Cold-launch always lands on Splash. After Splash finishes it picks the
        // next destination based on whether any server profiles exist.
        Nav(
            navController = navController,
            startDestination = Destinations.Splash,
            hasProfiles = profiles.isNotEmpty(),
        )
    }
}

@Composable
private fun Nav(
    navController: NavHostController,
    startDestination: String,
    hasProfiles: Boolean,
) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Destinations.Splash) {
            MatrixSplashScreen(
                replay = false,
                onFinished = {
                    val next = if (hasProfiles) Destinations.Home else Destinations.Onboarding
                    navController.navigate(next) {
                        popUpTo(Destinations.Splash) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(Destinations.SplashReplay) {
            MatrixSplashScreen(
                replay = true,
                onFinished = { navController.popBackStack() },
            )
        }
        composable(Destinations.Onboarding) {
            OnboardingScreen(onGetStarted = { navController.navigate(Destinations.AddServer) })
        }
        composable(Destinations.AddServer) {
            AddServerScreen(
                onAdded = {
                    navController.navigate(Destinations.Home) {
                        popUpTo(Destinations.Onboarding) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onCancel = { navController.popBackStack() },
            )
        }
        composable(Destinations.Home) {
            HomeShell(
                onReplaySplash = { navController.navigate(Destinations.SplashReplay) },
            )
        }
    }
}

@Composable
private fun HomeShell(onReplaySplash: () -> Unit) {
    val tabNav = rememberNavController()
    Scaffold(bottomBar = { BottomNavBar(tabNav) }) { inner ->
        NavHost(
            navController = tabNav,
            startDestination = Destinations.Tabs.Sessions,
            modifier = Modifier.padding(inner),
        ) {
            composable(Destinations.Tabs.Sessions) { SessionsScreen() }
            composable(Destinations.Tabs.Channels) {
                PlaceholderTabScreen("Channels", "Sprint 2 wires the messaging backends tab.")
            }
            composable(Destinations.Tabs.Stats) {
                PlaceholderTabScreen("Stats", "Sprint 1 Phase 4 adds the live stats dashboard.")
            }
            composable(Destinations.Tabs.Settings) {
                SettingsScreen(onReplaySplash = onReplaySplash)
            }
        }
    }
}
