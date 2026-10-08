package com.dmzs.datawatchclient.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.push.NotificationChannels
import com.dmzs.datawatchclient.push.NtfyFallbackService
import com.dmzs.datawatchclient.push.PushRegistrationCoordinator
import com.dmzs.datawatchclient.push.UnifiedPushSseService
import com.dmzs.datawatchclient.ui.alerts.AlertDockOverlay
import com.dmzs.datawatchclient.ui.alerts.AlertsScreen
import com.dmzs.datawatchclient.ui.alerts.AlertsViewModel
import com.dmzs.datawatchclient.ui.dashboard.DashboardScreen
import com.dmzs.datawatchclient.ui.gesture.threeFingerSwipeUp
import com.dmzs.datawatchclient.ui.monitoring.FederatedPeersViewModel
import com.dmzs.datawatchclient.ui.onboarding.OnboardingScreen
import com.dmzs.datawatchclient.ui.servers.AddServerScreen
import com.dmzs.datawatchclient.ui.servers.EditServerScreen
import com.dmzs.datawatchclient.ui.servers.ServerPickerSheet
import com.dmzs.datawatchclient.ui.sessions.NewSessionScreen
import com.dmzs.datawatchclient.ui.sessions.SessionDetailScreen
import com.dmzs.datawatchclient.ui.sessions.SessionsScreen
import com.dmzs.datawatchclient.ui.settings.SettingsScreen
import com.dmzs.datawatchclient.ui.shell.AlertDockChannel
import com.dmzs.datawatchclient.ui.shell.BottomNavBar
import com.dmzs.datawatchclient.ui.shell.Destinations
import com.dmzs.datawatchclient.ui.shell.LastViewStore
import com.dmzs.datawatchclient.ui.shell.SessionsNavChannel
import com.dmzs.datawatchclient.ui.shell.SettingsNavChannel
import com.dmzs.datawatchclient.ui.splash.MatrixSplashScreen
import com.dmzs.datawatchclient.ui.splash.SplashGate
import com.dmzs.datawatchclient.ui.theme.DatawatchTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Top-level composable. Cold-launch lands on Splash. After a minimum splash
 * dwell and once the encrypted DB has emitted its profile list, Splash goes to
 * Home (parity D86c: no onboarding page — Sessions shows the no-server state).
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
            // Defer service startup to allow system to settle after app init
            delay(500)
            try {
                NtfyFallbackService.start(context)
            } catch (e: Throwable) {
                android.util.Log.w("AppRoot", "NtfyFallbackService start failed: ${e.message}")
            }
            try {
                UnifiedPushSseService.start(context)
            } catch (e: Throwable) {
                android.util.Log.w("AppRoot", "UnifiedPushSseService start failed: ${e.message}")
            }
        }

        // v0.36.2 — screen-unlock lifecycle observer. On every
        // ON_RESUME (activity becomes visible again — including the
        // post-keyguard unlock case), re-probe every enabled
        // profile so the reachability dot reflects current state
        // instead of whatever was true when the screen turned off.
        // BL-T14-1 fix: SessionsScreen now has its own ON_RESUME observer
        // that calls vm.refresh() directly; this observer handles the
        // reachability dot only.
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
                navController.navigate(Destinations.sessionDetail(DeepLinks.shortSessionId(sessionId)))
            }
        }

        // Alert deep link (datawatch://alert/<id>): surface the Home shell (pop any
        // full-screen destination such as session detail); HomeShell then switches
        // to the Alerts tab and consumes the target.
        val pendingAlert by DeepLinks.pendingAlertTarget.collectAsState()
        LaunchedEffect(pendingAlert) {
            if (pendingAlert == null) return@LaunchedEffect
            if (navController.currentDestination?.route != Destinations.Home) {
                navController.popBackStack(Destinations.Home, inclusive = false)
            }
        }

        // Activity-scoped so the Home shell, the root alert dock and the live
        // WS alert feed share one poller.
        val alertsVm: AlertsViewModel = viewModel()
        val alertsState by alertsVm.state.collectAsState()
        LiveAlertFeed()

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
                alertsVm = alertsVm,
            )
            // Parity D41a — the alert dock is hosted at the root so client-side
            // messages (the former toasts) are visible on every screen, including
            // full-screen session detail and New Session.
            val dockOpen by AlertDockChannel.open.collectAsState()
            val dockEntries by AlertDockChannel.entries.collectAsState()
            if (dockOpen) {
                AlertDockOverlay(
                    alerts = alertsState.active.flatMap { it.alerts },
                    entries = dockEntries,
                    onDismiss = { AlertDockChannel.dismiss() },
                    onMute = { AlertDockChannel.mute() },
                    modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding(),
                )
            }
            if (pickerOpen) {
                ServerPickerSheet(
                    onDismiss = { pickerOpen = false },
                    onAdd = { navController.navigate(Destinations.AddServer) },
                )
            }
        }
    }
}

/** CSS `ease` — cubic-bezier(0.25, 0.1, 0.25, 1). */
private val SplashFadeEasing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

@Composable
private fun Nav(
    navController: NavHostController,
    startDestination: String,
    profiles: List<ServerProfile>?,
    alertsVm: AlertsViewModel,
) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(
            Destinations.Splash,
            exitTransition = { fadeOut(tween(durationMillis = 600, easing = SplashFadeEasing)) },
        ) {
            val splashContext = LocalContext.current
            // Parity D37a — splash only on first launch, app version change,
            // or >24 h since last shown; otherwise go straight in.
            val splashGate =
                remember {
                    SplashGate.consume(splashContext, com.dmzs.datawatchclient.Version.VERSION)
                }
            val showSplash = rememberSaveable { splashGate }
            var splashStatus by remember { mutableStateOf("unlocking vault…") }
            if (showSplash) {
                MatrixSplashScreen(
                    replay = false,
                    autoAdvance = false,
                    statusText = splashStatus,
                    onFinished = { /* managed by LaunchedEffect below */ },
                )
            } else {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
            }
            LaunchedEffect(profiles) {
                if (showSplash) {
                    // Cycle status messages timed to what's actually happening:
                    // SQLCipher unwrap → profile query → service startup → navigate.
                    delay(800L)
                    splashStatus = "loading profiles…"
                    delay(1200L)
                    splashStatus = "starting services…"
                    // Wait out the remaining dwell to reach the 3200ms brand moment.
                    delay(1200L)
                }
                var resolved = profiles
                var waited = 0L
                val maxWaitMs = if (showSplash) 2000L else 5000L
                while (resolved == null && waited < maxWaitMs) {
                    delay(100L)
                    waited += 100L
                    resolved = profiles
                }
                if (showSplash) {
                    splashStatus = "ready"
                    delay(180L) // brief flash so "ready" is visible
                }
                // Parity D86c — PWA-style minimal first run: always land on
                // Home; with no server the Sessions tab shows "No server
                // connected" + Add server instead of a separate onboarding page.
                navController.navigate(Destinations.Home) {
                    popUpTo(Destinations.Splash) { inclusive = true }
                    launchSingleTop = true
                }
                // Parity D40a — reopen the session that was open at shutdown
                // (PWA `cs_active_session`), unless a deep link is pending.
                val lastSession = LastViewStore.lastSession(splashContext)
                if (lastSession != null && resolved?.isNotEmpty() == true &&
                    DeepLinks.pendingSessionTarget.replayCache.isEmpty() &&
                    DeepLinks.pendingAlertTarget.value == null
                ) {
                    navController.navigate(Destinations.sessionDetail(DeepLinks.shortSessionId(lastSession)))
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
        composable(
            Destinations.Home,
            // Parity: PWA splash `.fade-out` (opacity 0.6 s ease) reveals the app.
            enterTransition = {
                if (initialState.destination.route == Destinations.Splash) {
                    fadeIn(tween(durationMillis = 600, easing = SplashFadeEasing))
                } else {
                    null
                }
            },
        ) {
            HomeShell(
                alertsVm = alertsVm,
                onAddServer = { navController.navigate(Destinations.AddServer) },
                onEditServer = { id -> navController.navigate(Destinations.editServer(id)) },
                onOpenSession = { id ->
                    navController.navigate(Destinations.sessionDetail(id))
                },
                onExpandSession = { id ->
                    navController.navigate(Destinations.sessionDetail(id, statusMode = true))
                },
                onNewSession = { navController.navigate(Destinations.NewSession) },
            )
        }
        composable(Destinations.NewSession) {
            NewSessionScreen(
                onStarted = { sessionId ->
                    navController.navigate(Destinations.sessionDetail(sessionId, isNew = true)) {
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
                    androidx.navigation.navArgument("isNew") {
                        type = androidx.navigation.NavType.BoolType
                        defaultValue = false
                    },
                    androidx.navigation.navArgument("statusMode") {
                        type = androidx.navigation.NavType.BoolType
                        defaultValue = false
                    },
                ),
        ) { entry ->
            val id = entry.arguments?.getString("sessionId") ?: return@composable
            val isNew = entry.arguments?.getBoolean("isNew") ?: false
            val openInStatusMode = entry.arguments?.getBoolean("statusMode") ?: false
            val context = LocalContext.current
            LaunchedEffect(id) { LastViewStore.setLastSession(context, id) }
            SessionDetailScreen(
                sessionId = id,
                isNew = isNew,
                openInStatusMode = openInStatusMode,
                onBack = {
                    LastViewStore.setLastSession(context, null)
                    navController.popBackStack()
                },
                onOpenSession = { other -> navController.navigate(Destinations.sessionDetail(other)) },
                onNavigateToSettings = { tab ->
                    LastViewStore.setLastSession(context, null)
                    context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
                        .edit().putString("settings_active_tab", tab).apply()
                    SettingsNavChannel.request(tab)
                    navController.navigate(Destinations.Home) {
                        popUpTo(Destinations.Home) { inclusive = false }
                        launchSingleTop = true
                    }
                },
            )
        }
    }
}

@Composable
private fun HomeShell(
    alertsVm: AlertsViewModel,
    onAddServer: () -> Unit,
    onEditServer: (String) -> Unit,
    onOpenSession: (String) -> Unit,
    onExpandSession: (String) -> Unit = {},
    onNewSession: () -> Unit,
) {
    val tabNav = rememberNavController()
    val alertsState by alertsVm.state.collectAsState()
    // S6-2 (#74): observe federated peer stale state for Settings nav badge.
    val federatedPeersVm: FederatedPeersViewModel = viewModel()
    val federatedPeersState by federatedPeersVm.state.collectAsState()
    // PWA updatePeerStaleBadge: polled every 30 s independently of the Observer tab.
    LaunchedEffect(federatedPeersVm) {
        while (true) {
            federatedPeersVm.refresh()
            kotlinx.coroutines.delay(30_000)
        }
    }

    // BL7 — foldable / large-screen two-pane. On MEDIUM+ width (≥600 dp,
    // covers unfolded foldables and tablets), sessions list and session detail
    // render side-by-side. On narrow phones, existing full-screen nav applies.
    val isWide = LocalConfiguration.current.screenWidthDp >= 600
    var selectedSessionId by remember { mutableStateOf<String?>(null) }

    // v0.42.5 — probe whether the active server exposes the
    // autonomous surface (`/api/autonomous/prds`). Local-only setups
    // and older daemons return 404 / never have the route; in that
    // case the PRDs tab disappears from the bottom nav so it doesn't
    // dead-end the user. Probe re-runs whenever the active server
    // changes. Federated "All servers" mode keeps the tab visible —
    // any one of the fanned-out profiles may have it.
    val activeId by ServiceLocator.activeServerStore.observe()
        .collectAsState(initial = null)
    // Default true — show the tab immediately; probe corrects to false only when the
    // active server explicitly has autonomous.enabled=false.  Avoids the 10-15 s delay
    // users see while waiting for profileRepository + fetchConfig() to settle.
    var prdsSupported by remember { mutableStateOf(true) }
    var dashboardEnabled by remember { mutableStateOf(true) }

    // v0.42.9 — probe `autonomous.enabled` from /api/config on
    // active-server change AND on every successful config save
    // (ConfigSaveBus). Event-driven instead of polling — user
    // direction 2026-04-28: no timed refresh; the queue / event
    // mechanism (`ConfigSaveBus`) flips the PRDs nav tab as soon
    // as the user toggles autonomous in Settings.
    suspend fun probeAutonomous() {
        val id = activeId
        if (id == com.dmzs.datawatchclient.prefs.ActiveServerStore.SENTINEL_ALL_SERVERS) {
            prdsSupported = true
            dashboardEnabled = true
            return
        }
        // When no explicit active server is stored, mirror SessionsViewModel: fall back
        // to the first enabled profile so the Autonomous tab appears consistently with
        // what the Sessions tab is already showing.
        val profile =
            if (id == null) {
                kotlinx.coroutines.withTimeoutOrNull(10_000) {
                    ServiceLocator.profilesWithProxied()
                        .first { list -> list.any { it.enabled } }
                        .filter { it.enabled }
                        .firstOrNull()
                }
            } else {
                kotlinx.coroutines.withTimeoutOrNull(10_000) {
                    ServiceLocator.profilesWithProxied()
                        .first { list -> list.any { it.id == id && it.enabled } }
                        .firstOrNull { it.id == id && it.enabled }
                }
            }
        profile ?: return
        ServiceLocator.transportFor(profile).fetchAutonomousEnabled()
            .onSuccess { enabled ->
                prdsSupported = enabled
                dashboardEnabled = enabled
            }
            .onFailure { e ->
            }
    }

    LaunchedEffect(activeId) {
        probeAutonomous()
    }
    LaunchedEffect(Unit) {
        com.dmzs.datawatchclient.events.ConfigSaveBus.events.collect {
            probeAutonomous()
        }
    }

    val context = LocalContext.current
    // Parity D40a — start on the last tab (PWA `cs_active_view`) and record
    // every tab change.
    val startTab = remember { LastViewStore.lastTab(context) }
    LaunchedEffect(tabNav) {
        tabNav.currentBackStackEntryFlow.collect { entry ->
            LastViewStore.setLastTab(context, entry.destination.route)
        }
    }
    // A restored Automata / Dashboard tab must not outlive its gate.
    LaunchedEffect(prdsSupported, dashboardEnabled) {
        val route = tabNav.currentBackStackEntry?.destination?.route
        val hidden =
            (route == Destinations.Tabs.Autonomous && !prdsSupported) ||
                (route == Destinations.Tabs.Dashboard && !dashboardEnabled)
        if (hidden) {
            tabNav.navigate(Destinations.Tabs.Sessions) {
                popUpTo(tabNav.graph.startDestinationId) { inclusive = true }
                launchSingleTop = true
            }
        }
    }
    val pendingSessionsFilter by SessionsNavChannel.pendingFilter.collectAsState()
    LaunchedEffect(pendingSessionsFilter) {
        pendingSessionsFilter ?: return@LaunchedEffect
        tabNav.navigate(Destinations.Tabs.Sessions) {
            popUpTo(Destinations.Tabs.Sessions) { inclusive = false }
            launchSingleTop = true
        }
        // filter text is consumed by SessionsScreen itself via SessionsNavChannel
    }

    // Alert deep link (datawatch://alert/<id>) → Alerts tab focused on that alert.
    val pendingAlertTarget by DeepLinks.pendingAlertTarget.collectAsState()
    LaunchedEffect(pendingAlertTarget) {
        val target = pendingAlertTarget ?: return@LaunchedEffect
        // Cold start: wait for the tab NavHost to set its graph before navigating.
        tabNav.currentBackStackEntryFlow.first()
        tabNav.navigate(Destinations.Tabs.Alerts) {
            popUpTo(tabNav.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
        if (target.isNotBlank()) alertsVm.focusAlert(target)
        DeepLinks.consumeAlertTarget()
    }

    val pendingSettingsTab by SettingsNavChannel.pendingTab.collectAsState()
    LaunchedEffect(pendingSettingsTab) {
        val tab = pendingSettingsTab ?: return@LaunchedEffect
        context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
            .edit().putString("settings_active_tab", tab).apply()
        tabNav.navigate(Destinations.Tabs.Settings) {
            popUpTo(Destinations.Tabs.Sessions) { saveState = true }
            launchSingleTop = true
            restoreState = false
        }
        SettingsNavChannel.consume()
    }

    val mainPane: @Composable (Modifier) -> Unit = { mod ->
        Scaffold(
            modifier = mod,
            // Each tab's TopAppBar already pads for the status bar and the
            // NavigationBar for the nav bar. Since enableEdgeToEdge (build 283)
            // the default systemBars insets here added the status bar a second
            // time — an empty band above every tab title.
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
            bottomBar = {
                BottomNavBar(
                    tabNav,
                    alertsBadge = alertsState.watchedAlertCount,
                    prdsSupported = prdsSupported,
                    dashboardEnabled = dashboardEnabled,
                    stalePeerCount = federatedPeersState.stalePeerCount,
                    onStalePeerBadgeClick = {
                        // PWA navigateToStalePeer: Observer → scroll to Federated Peers + flash.
                        com.dmzs.datawatchclient.ui.shell.ObserverNavChannel.requestStalePeers()
                        tabNav.navigate(Destinations.Tabs.Observer) {
                            launchSingleTop = true
                            restoreState = true
                            popUpTo(Destinations.Home) { saveState = true }
                        }
                    },
                )
            },
        ) { inner ->
            Box(modifier = Modifier.fillMaxSize().padding(inner)) {
                NavHost(
                    navController = tabNav,
                    startDestination = startTab,
                    modifier = Modifier.widthIn(max = 840.dp).fillMaxHeight().align(Alignment.TopCenter),
                ) {
                    composable(Destinations.Tabs.Sessions) {
                        SessionsScreen(
                            onOpenSession = if (isWide) { id -> selectedSessionId = id } else onOpenSession,
                            onEditServer = onEditServer,
                            onAddServer = onAddServer,
                            onNewSession = onNewSession,
                            onExpandSession = if (isWide) { id -> selectedSessionId = id } else onExpandSession,
                        )
                    }
                    composable(Destinations.Tabs.Autonomous) {
                        com.dmzs.datawatchclient.ui.autonomous.AutonomousScreen()
                    }
                    composable(Destinations.Tabs.Alerts) {
                        AlertsScreen(
                            onOpenSession = if (isWide) { id -> selectedSessionId = id } else onOpenSession,
                            vm = alertsVm,
                        )
                    }
                    composable(Destinations.Tabs.Observer) {
                        com.dmzs.datawatchclient.ui.observer.ObserverScreen()
                    }
                    composable(Destinations.Tabs.Dashboard) {
                        DashboardScreen(
                            onOpenSession = if (isWide) { id -> selectedSessionId = id } else onOpenSession,
                            onExpandSession = if (isWide) { id -> selectedSessionId = id } else onExpandSession,
                        )
                    }
                    composable(Destinations.Tabs.Settings) {
                        SettingsScreen(
                            onAddServer = onAddServer,
                            onEditServer = onEditServer,
                            onNavigateToObserver = {
                                tabNav.navigate(Destinations.Tabs.Observer) {
                                    popUpTo(Destinations.Tabs.Settings) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = false
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (isWide) {
        Row(modifier = Modifier.fillMaxSize()) {
            mainPane(Modifier.width(360.dp).fillMaxHeight())
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                val sid = selectedSessionId
                if (sid != null) {
                    SessionDetailScreen(
                        sessionId = sid,
                        isNew = false,
                        onBack = { selectedSessionId = null },
                        onOpenSession = { other -> selectedSessionId = other },
                        onNavigateToSettings = { tab ->
                            context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
                                .edit().putString("settings_active_tab", tab).apply()
                            tabNav.navigate(Destinations.Tabs.Settings) {
                                popUpTo(Destinations.Tabs.Sessions) { saveState = true }
                                launchSingleTop = true
                                restoreState = false
                            }
                        },
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.monitor_no_session_selected),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    } else {
        mainPane(Modifier.fillMaxSize())
    }
}

/**
 * Parity D51a — live WS `alert` frames become dock entries ("badge + in-app
 * toast" in the PWA, where toasts are the dock). Duplicate frames for the same
 * alert id (several sockets open) are posted once.
 */
@Composable
private fun LiveAlertFeed() {
    LaunchedEffect(Unit) {
        val seen = ArrayDeque<String>()
        com.dmzs.datawatchclient.transport.ws.AlertsHub.flow.collect { push ->
            val id = push.id
            if (id != null) {
                if (id in seen) return@collect
                seen.addLast(id)
                if (seen.size > 200) seen.removeFirst()
            }
            val title = if (push.title.length > 60) push.title.take(57) + "…" else push.title
            val level =
                when (push.level.lowercase()) {
                    "error", "warn", "warning" -> com.dmzs.datawatchclient.ui.shell.DockLevel.Error
                    else -> com.dmzs.datawatchclient.ui.shell.DockLevel.Info
                }
            AlertDockChannel.post(title, level, fromServerAlert = true)
        }
    }
}
