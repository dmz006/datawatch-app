package com.dmzs.datawatchclient.ui.observer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.dmzs.datawatchclient.R

/**
 * Observer tab — mirrors the PWA's Observer surface.
 * Aggregates monitoring cards: system stats, eBPF, cluster,
 * federated peers, plugins, memory, schedules, daemon log, observer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun ObserverScreen() {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.nav_observer),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
            )
        },
    ) { innerPadding ->
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
            com.dmzs.datawatchclient.ui.memory.MemoryCard()
            com.dmzs.datawatchclient.ui.memory.MempalaceActionsCard()
            com.dmzs.datawatchclient.ui.schedules.SchedulesCard()
            com.dmzs.datawatchclient.ui.ops.DaemonLogCard()
            com.dmzs.datawatchclient.ui.settings.ObserverCard()
        }
    }
}
