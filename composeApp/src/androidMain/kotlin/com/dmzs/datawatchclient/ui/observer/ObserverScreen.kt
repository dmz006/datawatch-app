package com.dmzs.datawatchclient.ui.observer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dmzs.datawatchclient.ui.alerts.AlertsViewModel
import com.dmzs.datawatchclient.ui.common.AlertsBellAction
import com.dmzs.datawatchclient.ui.common.DocsLinkAction
import com.dmzs.datawatchclient.ui.common.ReachabilityDot
import com.dmzs.datawatchclient.ui.common.ServerPickerBar

/**
 * Observer tab — aggregates monitoring cards: system stats, eBPF, cluster,
 * federated peers, plugins, memory, schedules, daemon log, observer.
 * Previously duplicated in Settings → Monitor tab (removed in v0.x.x).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun ObserverScreen(
    vm: ObserverViewModel = viewModel(),
    alertsVm: AlertsViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val reachable by vm.reachable.collectAsState()
    val lastProbeEpochMs by vm.lastProbeEpochMs.collectAsState()
    val alertsState by alertsVm.state.collectAsState()
    // PWA navigateToStalePeer: scroll Federated Peers (last card) into view.
    val scrollState = rememberScrollState()
    val staleFlash by com.dmzs.datawatchclient.ui.shell.ObserverNavChannel.staleFlash.collectAsState()
    androidx.compose.runtime.LaunchedEffect(staleFlash) {
        if (staleFlash != null) {
            kotlinx.coroutines.delay(600)
            scrollState.animateScrollTo(scrollState.maxValue)
            kotlinx.coroutines.delay(3_400)
            com.dmzs.datawatchclient.ui.shell.ObserverNavChannel.consume()
        }
    }

    Scaffold(
        // Tab screens sit inside the app shell Scaffold, which already
        // reserves the system bars + bottom nav; the default systemBars
        // insets here doubled the nav-bar gap above the bottom menu.
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(com.dmzs.datawatchclient.R.string.nav_observer)) },
                actions = {
                    DocsLinkAction(com.dmzs.datawatchclient.docs.DocsLinks.forKey("view_observer"))
                    AlertsBellAction(alertsBadge = alertsState.watchedAlertCount)
                    if (state.activeProfile != null) {
                        ReachabilityDot(
                            reachable = reachable,
                            lastProbeEpochMs = lastProbeEpochMs,
                            onRetry = {},
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        // PWA: no full-screen spinner — each card shows its own "Loading…" placeholder.
        run {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(scrollState),
            ) {
                ServerPickerBar(
                    profiles = state.allProfiles,
                    activeId = state.activeProfile?.id,
                    allMode = false,
                    onSelect = vm::selectProfile,
                )
                // #236.2 — a proxied remote that can't be reached shows the real error
                // (and a way back) instead of an endless loader (PWA v8.73.5+).
                val fedStatus =
                    com.dmzs.datawatchclient.ui.common.rememberFedConnStatus(
                        state.activeProfile?.id,
                        false,
                    )
                if (fedStatus?.phase == com.dmzs.datawatchclient.transport.FedConnPhase.ERROR) {
                    com.dmzs.datawatchclient.ui.common.FedConnStatusPane(fedStatus)
                    return@Column
                }
                // Parity D28a — PWA Observer order: one "System Statistics"
                // block (grid + stats panel + eBPF + plugins + peers + cluster +
                // channel + comm sub-blocks), then the standalone cards, with
                // Federated Peers last. Pipelines and Identity live in Settings
                // only (PWA has no Observer copy).
                // #236.7 (PWA v8.73.7) — most cards load once from the active
                // server; keying them on it makes a server switch refetch every
                // card immediately instead of after leaving and re-entering the tab.
                androidx.compose.runtime.key(state.activeProfile?.id) {
                    ObserverStatsBlock()
                    com.dmzs.datawatchclient.ui.memory.MemoryCard()
                    com.dmzs.datawatchclient.ui.memory.MempalaceActionsCard()
                    com.dmzs.datawatchclient.ui.schedules.SchedulesCard()
                    com.dmzs.datawatchclient.ui.monitoring.CooldownCard()
                    com.dmzs.datawatchclient.ui.monitoring.SessionAnalyticsCard()
                    com.dmzs.datawatchclient.ui.monitoring.AuditLogCard()
                    KnowledgeGraphCard()
                    com.dmzs.datawatchclient.ui.ops.DaemonLogCard()
                    com.dmzs.datawatchclient.ui.monitoring.FederatedPeersCard()
                }
            }
        }
    }
}

/**
 * Parity D28a — the PWA "System Statistics" section is one collapsible block
 * holding every stats sub-panel; collapsing it hides them all (key `stats`).
 */
@Composable
private fun ObserverStatsBlock() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val collapsed = com.dmzs.datawatchclient.ui.theme.PwaCardCollapseStore.isCollapsed(context, "stats")
    com.dmzs.datawatchclient.ui.theme.PwaCardHeader(
        title = androidx.compose.ui.res.stringResource(com.dmzs.datawatchclient.R.string.observer_system_statistics),
        collapsed = collapsed,
        onToggle = { com.dmzs.datawatchclient.ui.theme.PwaCardCollapseStore.toggle(context, "stats") },
        docsPath = com.dmzs.datawatchclient.docs.DocsLinks.forKey("stats"),
        headerActions = null,
    )
    if (collapsed) return
    com.dmzs.datawatchclient.ui.monitoring.SystemStatsGridCard()
    com.dmzs.datawatchclient.ui.stats.StatsScreenContent()
    com.dmzs.datawatchclient.ui.monitoring.EBpfStatusCard()
    com.dmzs.datawatchclient.ui.monitoring.EBpfNetworkCard()
    com.dmzs.datawatchclient.ui.monitoring.PluginsCard()
    com.dmzs.datawatchclient.ui.monitoring.PeerResourcesCard()
    com.dmzs.datawatchclient.ui.monitoring.ClusterNodesCard()
    com.dmzs.datawatchclient.ui.about.McpChannelCard()
    ChannelDiagnosticsCard()
    CommBackendsCard()
}
