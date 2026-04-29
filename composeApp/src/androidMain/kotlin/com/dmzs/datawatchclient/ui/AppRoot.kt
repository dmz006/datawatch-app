package com.dmzs.datawatchclient.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.push.NotificationChannels
import com.dmzs.datawatchclient.push.NtfyFallbackService
import com.dmzs.datawatchclient.push.PushRegistrationCoordinator
import com.dmzs.datawatchclient.ui.alerts.AlertsScreen
import com.dmzs.datawatchclient.ui.alerts.AlertsViewModel
import com.dmzs.datawatchclient.ui.gesture.threeFingerSwipeUp
import com.dmzs.datawatchclient.ui.onboarding.OnboardingScreen
import com.dmzs.datawatchclient.ui.servers.AddServerScreen
import com.dmzs.datawatchclient.ui.servers.EditServerScreen
import com.dmzs.datawatchclient.ui.servers.ServerPickerSheet
import com.dmzs.datawatchclient.ui.sessions.NewSessionScreen
import com.dmzs.datawatchclient.ui.sessions.SessionDetailScreen
import com.dmzs.datawatchclient.ui.sessions.SessionsScreen
import com.dmzs.datawatchclient.ui.settings.SettingsScreen
import com.dmzs.datawatchclient.ui.shell.BottomNavBar
import com.dmzs.datawatchclient.ui.shell.Destinations
import com.dmzs.datawatchclient.ui.splash.MatrixSplashScreen
import com.dmzs.datawatchclient.ui.theme.DatawatchTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
        var pickerOpen by remember { mutableStateOf(false) }
        val context = LocalContext.current

        // One-shot bootstrap: register notification channels, attempt push
        // registration against every enabled profile, and start the ntfy
        // fallback service. The coordinator is idempotent so re-runs are safe.
        LaunchedEffect(Unit) {
            NotificationChannels.ensureRegistered(context)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                PushRegistrationCoordinator(context).registerAll()
            }
            NtfyFallbackService.start(context)
        }

        // v0.36.2 — screen-unlock lifecycle observer. On every
        // ON_RESUME (activity becomes visible again — including the
        // post-keyguard unlock case), re-probe every enabled
        // profile so the reachability dot reflects current state
        // instead of whatever was true when the screen turned off.
        // Polling resumes naturally via SessionsViewModel's
        // 5-second loop; this just removes the "first poll lag"
        // operators were seeing on unlock.
        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
        androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
            val observer =
                androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        kotlinx.coroutines.GlobalScope.launch(
                            kotlinx.coroutines.Dispatchers.IO,
                        ) {
                            runCatching {
                                val list =
                                    ServiceLocator.profileRepository
                                        .observeAll()
                                        .first()
                                list.filter { it.enabled }.forEach { p ->
                                    runCatching { ServiceLocator.transportFor(p).ping() }
                                }
                            }
                        }
                    }
                }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        // Deep-link consumer: pop any pending session id off the SharedFlow and
        // navigate when the nav graph is ready (Home destination present).
        LaunchedEffect(Unit) {
            DeepLinks.pendingSessionTarget.collect { sessionId ->
                navController.navigate(Destinations.sessionDetail(sessionId))
            }
        }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .threeFingerSwipeUp(onFired = { pickerOpen = true }),
        ) {
            Nav(
                navController = navController,
                startDestination = Destinations.Splash,
                profiles = profiles,
            )
            if (pickerOpen) {
                ServerPickerSheet(
                    onDismiss = { pickerOpen = false },
                    onAdd = { navController.navigate(Destinations.AddServer) },
                )
            }
        }
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
                val next =
                    if (resolved?.isNotEmpty() == true) {
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
                    val poppedToHome =
                        navController.popBackStack(
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
                onEditServer = { id -> navController.navigate(Destinations.editServer(id)) },
                onOpenSession = { id ->
                    navController.navigate(Destinations.sessionDetail(id))
                },
                onNewSession = { navController.navigate(Destinations.NewSession) },
            )
        }
        composable(Destinations.NewSession) {
            NewSessionScreen(
                onStarted = { sessionId ->
                    navController.navigate(Destinations.sessionDetail(sessionId)) {
                        popUpTo(Destinations.NewSession) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onCancel = { navController.popBackStack() },
            )
        }
        composable(
            route = Destinations.EditServer,
            arguments =
                listOf(
                    androidx.navigation.navArgument("profileId") {
                        type = androidx.navigation.NavType.StringType
                    },
                ),
        ) { entry ->
            val id = entry.arguments?.getString("profileId") ?: return@composable
            EditServerScreen(
                profileId = id,
                onSaved = { navController.popBackStack() },
                onDeleted = { navController.popBackStack() },
                onCancel = { navController.popBackStack() },
            )
        }
        composable(
            route = Destinations.SessionDetail,
            arguments =
                listOf(
                    androidx.navigation.navArgument("sessionId") {
                        type = androidx.navigation.NavType.StringType
                    },
                ),
        ) { entry ->
            val id = entry.arguments?.getString("sessionId") ?: return@composable
            SessionDetailScreen(
                sessionId = id,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

@Composable
private fun HomeShell(
    onAddServer: () -> Unit,
    onEditServer: (String) -> Unit,
    onOpenSession: (String) -> Unit,
    onNewSession: () -> Unit,
) {
    val tabNav = rememberNavController()
    val alertsVm: AlertsViewModel = viewModel()
    val alertsState by alertsVm.state.collectAsState()

    // v0.42.5 — probe whether the active server exposes the
    // autonomous surface (`/api/autonomous/prds`). Local-only setups
    // and older daemons return 404 / never have the route; in that
    // case the PRDs tab disappears from the bottom nav so it doesn't
    // dead-end the user. Probe re-runs whenever the active server
    // changes. Federated "All servers" mode keeps the tab visible —
    // any one of the fanned-out profiles may have it.
    val activeId by ServiceLocator.activeServerStore.observe()
        .collectAsState(initial = null)
    var prdsSupported by remember { mutableStateOf(false) }

    // v0.42.9 — probe `autonomous.enabled` from /api/config on
    // active-server change AND on every successful config save
    // (ConfigSaveBus). Event-driven instead of polling — user
    // direction 2026-04-28: no timed refresh; the queue / event
    // mechanism (`ConfigSaveBus`) flips the PRDs nav tab as soon
    // as the user toggles autonomous in Settings.
    suspend fun probeAutonomous() {
        val id = activeId
        if (id == null) {
            prdsSupported = false
            return
        }
        if (id == com.dmzs.datawatchclient.prefs.ActiveServerStore.SENTINEL_ALL_SERVERS) {
            prdsSupported = true
            return
        }
        val profile =
            ServiceLocator.profileRepository.observeAll().first()
                .firstOrNull { it.id == id && it.enabled } ?: return
        ServiceLocator.transportFor(profile).fetchConfig().onSuccess { cfg ->
            val auto = cfg.raw["autonomous"] as? kotlinx.serialization.json.JsonObject
            val enabled =
                (auto?.get("enabled") as? kotlinx.serialization.json.JsonPrimitive)
                    ?.content?.lowercase() == "true"
            prdsSupported = enabled
        }
    }

    LaunchedEffect(activeId) {
        prdsSupported = false
        probeAutonomous()
    }
    LaunchedEffect(activeId) {
        com.dmzs.datawatchclient.events.ConfigSaveBus.events.collect {
            probeAutonomous()
        }
    }

    Scaffold(
        bottomBar = {
            BottomNavBar(
                tabNav,
                alertsBadge = alertsState.count,
                prdsSupported = prdsSupported,
            )
        },
    ) { inner ->
        NavHost(
            navController = tabNav,
            startDestination = Destinations.Tabs.Sessions,
            modifier = Modifier.padding(inner),
        ) {
            composable(Destinations.Tabs.Sessions) {
                SessionsScreen(
                    onOpenSession = onOpenSession,
                    onEditServer = onEditServer,
                    onAddServer = onAddServer,
                    onNewSession = onNewSession,
                )
            }
            composable(Destinations.Tabs.Autonomous) {
                com.dmzs.datawatchclient.ui.autonomous.AutonomousScreen()
            }
            composable(Destinations.Tabs.Alerts) {
                AlertsScreen(onOpenSession = onOpenSession, vm = alertsVm)
            }
            composable(Destinations.Tabs.Settings) {
                SettingsScreen(
                    onAddServer = onAddServer,
                    onEditServer = onEditServer,
                )
            }
        }
    }
}
