package com.dmzs.datawatchclient.ui.tailscale

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.transport.dto.TailscaleStatusDto
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * v0.80.0 Sprint 11 — Tailscale mesh status card (GET /api/tailscale/status).
 * Shows enabled/disabled badge, coordinator URL, and nodes list with
 * online/offline indicators.
 */
@Composable
public fun TailscaleMeshCard() {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<TailscaleStatusDto?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val profiles = ServiceLocator.profileRepository.observeAll().first()
        val activeId = ServiceLocator.activeServerStore.get()
        val profile = profiles.firstOrNull { it.id == activeId && it.enabled }
            ?: profiles.firstOrNull { it.enabled } ?: return@LaunchedEffect
        ServiceLocator.transportFor(profile).getTailscaleStatus().fold(
            onSuccess = { status = it; loadError = null },
            onFailure = { loadError = it.message },
        )
    }

    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .pwaCard(),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            PwaSectionTitle(stringResource(R.string.tailscale_section_status), docsAnchor = "mesh-status")
            if (loadError != null) {
                Text(
                    loadError ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                return@Column
            }
            val s = status ?: run {
                Text(
                    stringResource(R.string.tailscale_loading),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                return@Column
            }

            // Status badge row
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.tailscale_status_label) + ":",
                    style = MaterialTheme.typography.labelSmall,
                )
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (s.enabled) Color(0xFF1B5E20) else MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        if (s.enabled) stringResource(R.string.tailscale_enabled) else stringResource(R.string.tailscale_disabled),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (s.enabled) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                if (s.backend.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            s.backend,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            // Coordinator URL
            if (s.coordinatorUrl.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.tailscale_coordinator_label) + ":",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(s.coordinatorUrl, style = MaterialTheme.typography.bodySmall)
                }
            }

            // Nodes
            Text(
                stringResource(R.string.tailscale_nodes_label) + ":",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
            )
            if (s.nodes.isEmpty()) {
                Text(
                    stringResource(R.string.tailscale_no_nodes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                s.nodes.forEach { node ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Online/offline dot
                        Text(
                            if (node.online) "●" else "○",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (node.online) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            node.name,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f),
                        )
                        if (node.ip.isNotEmpty()) {
                            Text(
                                node.ip,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (node.tags.isNotEmpty()) {
                            Spacer(Modifier.width(4.dp))
                            Text(
                                node.tags.joinToString(", "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
