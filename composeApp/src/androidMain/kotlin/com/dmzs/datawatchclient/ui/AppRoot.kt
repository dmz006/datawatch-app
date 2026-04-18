package com.dmzs.datawatchclient.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.ui.onboarding.OnboardingScreen
import com.dmzs.datawatchclient.ui.servers.AddServerScreen
import com.dmzs.datawatchclient.ui.sessions.SessionsScreen
import com.dmzs.datawatchclient.ui.settings.SettingsScreen
import com.dmzs.datawatchclient.ui.shell.BottomNavBar
import com.dmzs.datawatchclient.ui.shell.Destinations
import com.dmzs.datawatchclient.ui.shell.PlaceholderTabScreen
import com.dmzs.datawatchclient.ui.splash.MatrixSplashScreen
import com.dmzs.datawatchclient.ui.theme.DatawatchTheme
import kotlinx.coroutines.delay

/**
 * Top-level composable. Cold-launch lands on Splash. After a minimum splash
 * dwell and once the encrypted DB has emitted its profile list, Splash picks
 * between Onboarding and Home.
 *
 * The initial value of `profiles` is `null` (not empty-list) so we can tell the
 * "still loading" state apart from the "confirmed zero profiles" state — that
 * prevents a race where the splash 3.2 s timer beats SQLCipher unwrap + the
 * first DB query and sends the user to Onboarding even though profiles exist.
 */
@Composable
public fun AppRoot() {
    DatawatchTheme {
        val profiles by ServiceLocator.profileRepository
            .observeAll()
            .collectAsState(initial = null)
        val navController = rememberNavController()
        Nav(
            navController = navController,
            startDestination = Destinations.Splash,
            profiles = profiles,
        )
    }
}

@Composable
private fun Nav(
    navController: NavHostController,
    startDestination: String,
    profiles: List<ServerProfile>?,
) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Destinations.Splash) {
            MatrixSplashScreen(
                replay = false,
                autoAdvance = false, // we gate exit on profiles emission below
                onFinished = { /* no-op: managed by the LaunchedEffect */ },
            )
            LaunchedEffect(profiles) {
                // Minimum dwell for brand moment; cap extra wait at 2 s more so
                // a stuck DB layer doesn't soft-lock the app.
                delay(3200L)
                var resolved = profiles
                var waited = 0L
                while (resolved == null && waited < 2000L) {
                    delay(100L)
                    waited += 100L
                    resolved = profiles
                }
                val next = if (resolved?.isNotEmpty() == true) {
                    Destinations.Home
                } else {
                    Destinations.Onboarding
                }
                navController.navigate(next) {
                    popUpTo(Destinations.Splash) { inclusive = true }
                    launchSingleTop = true
                }
            }
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
                    // Try to fall back to an existing Home in the stack first
                    // (Home/Settings → AddServer path). If Home isn't there,
                    // we came from Onboarding — push Home and pop Onboarding
                    // in a single transactional navigate(). Previous impl
                    // used inclusive-pop which briefly emptied the back stack
                    // and rendered a blank composition.
                    val poppedToHome = navController.popBackStack(
                        route = Destinations.Home,
                        inclusive = false,
                    )
                    if (!poppedToHome) {
                        navController.navigate(Destinations.Home) {
                            popUpTo(Destinations.Onboarding) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                },
                onCancel = { navController.popBackStack() },
            )
        }
        composable(Destinations.Home) {
            HomeShell(
                onAddServer = { navController.navigate(Destinations.AddServer) },
            )
        }
    }
}

@Composable
private fun HomeShell(onAddServer: () -> Unit) {
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
                SettingsScreen(
                    onAddServer = onAddServer,
                    onEditServer = { /* TODO Sprint 2: edit profile */ },
                )
            }
        }
    }
}
