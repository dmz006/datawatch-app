package com.dmzs.datawatchclient.ui.observer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dmzs.datawatchclient.ui.alerts.AlertsViewModel
import com.dmzs.datawatchclient.ui.common.AlertsBellAction
import com.dmzs.datawatchclient.ui.common.DocsLinkAction
import com.dmzs.datawatchclient.ui.common.ReachabilityDot
import com.dmzs.datawatchclient.ui.common.SingleServerPickerTitle

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
    var pickerOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    SingleServerPickerTitle(
                        active = state.activeProfile,
                        open = pickerOpen,
                        onToggle = { pickerOpen = !pickerOpen },
                        onDismiss = { pickerOpen = false },
                        profiles = state.allProfiles,
                        onSelect = { vm.selectProfile(it); pickerOpen = false },
                    )
                },
                actions = {
                    DocsLinkAction("datawatch-definitions.md#observer")
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
        if (state.loading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(36.dp))
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState()),
            ) {
                com.dmzs.datawatchclient.ui.stats.StatsScreenContent()
                com.dmzs.datawatchclient.ui.monitoring.EBpfStatusCard()
                com.dmzs.datawatchclient.ui.monitoring.EBpfNetworkCard()
                com.dmzs.datawatchclient.ui.monitoring.ClusterNodesCard()
                com.dmzs.datawatchclient.ui.monitoring.FederatedPeersCard()
                com.dmzs.datawatchclient.ui.monitoring.PluginsCard()
                com.dmzs.datawatchclient.ui.about.McpChannelCard()
                CommBackendsCard()
                MatrixStatusCard()
                com.dmzs.datawatchclient.ui.memory.MemoryCard()
                com.dmzs.datawatchclient.ui.memory.MempalaceActionsCard()
                com.dmzs.datawatchclient.ui.schedules.SchedulesCard()
                com.dmzs.datawatchclient.ui.monitoring.CooldownCard()
                com.dmzs.datawatchclient.ui.monitoring.SessionAnalyticsCard()
                com.dmzs.datawatchclient.ui.monitoring.AuditLogCard()
                KnowledgeGraphCard()
                com.dmzs.datawatchclient.ui.ops.DaemonLogCard()
            }
        }
    }
}
