package com.dmzs.datawatchclient.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.Version
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.ui.splash.MatrixLogoAnimated
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Settings — mirrors the PWA's Settings structure (Servers / Comms / About)
 * at the card level. Animated logo sits in About as a live visual, not behind
 * a "replay" action — matches user feedback that the splash should always be
 * shown, not an opt-in.
 *
 * Feature-parity with PWA is tracked upstream in
 * [dmz006/datawatch#4](https://github.com/dmz006/datawatch/issues/4).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun SettingsScreen(
    onAddServer: () -> Unit = {},
    onEditServer: (String) -> Unit = {},
) {
    val profiles by ServiceLocator.profileRepository.observeAll()
        .collectAsState(initial = emptyList())

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .fillMaxWidth(),
        ) {
            ServersCard(
                profiles = profiles,
                onAddServer = onAddServer,
                onEditServer = onEditServer,
                onDelete = { profile ->
                    GlobalScope.launch(Dispatchers.IO) {
                        if (profile.bearerTokenRef.isNotBlank()) {
                            ServiceLocator.tokenVault.remove(profile.bearerTokenRef)
                        }
                        ServiceLocator.profileRepository.delete(profile.id)
                    }
                },
            )
            CommsCard()
            AboutCard()
        }
    }
}

@Composable
private fun ServersCard(
    profiles: List<com.dmzs.datawatchclient.domain.ServerProfile>,
    onAddServer: () -> Unit,
    onEditServer: (String) -> Unit,
    onDelete: (com.dmzs.datawatchclient.domain.ServerProfile) -> Unit,
) {
    SectionWithAction(title = "Servers", actionIcon = Icons.Filled.Add,
                      actionDescription = "Add server", onAction = onAddServer) {
        if (profiles.isEmpty()) {
            Text(
                "No servers yet — tap + above to add one.",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            profiles.forEach { p ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEditServer(p.id) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(p.displayName, style = MaterialTheme.typography.titleSmall)
                        Text(
                            p.baseUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val badges = buildList {
                            if (p.bearerTokenRef.isBlank()) add("no auth")
                            if (p.trustAnchorSha256 == ServiceLocator.TRUST_ALL_SENTINEL) {
                                add("trust-all TLS")
                            }
                        }
                        if (badges.isNotEmpty()) {
                            Text(
                                badges.joinToString("  ·  "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    IconButton(onClick = { onDelete(p) }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete server",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun CommsCard() {
    Section(title = "Comms") {
        Text(
            "Messaging channel configuration will land in Sprint 3 (see " +
                "docs/plans/README.md F3). This card will mirror the PWA's " +
                "Settings → Comms tab — Signal / Telegram / Slack / ntfy / Matrix etc.",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AboutCard() {
    Section(title = "About") {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            // Live animated logo (matrix rain + eye + arcs + tablet frame).
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                    MatrixLogoAnimated(modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(24.dp)))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("App version", style = MaterialTheme.typography.bodyMedium)
                Text(Version.VERSION, style = MaterialTheme.typography.bodyMedium,
                     color = MaterialTheme.colorScheme.primary)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Package", style = MaterialTheme.typography.bodyMedium)
                Text("com.dmzs.datawatchclient",
                     style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("License", style = MaterialTheme.typography.bodyMedium)
                Text("Polyform Noncommercial 1.0.0",
                     style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Source", style = MaterialTheme.typography.bodyMedium)
                Text("github.com/dmz006/datawatch-app",
                     style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.primary)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Parent project", style = MaterialTheme.typography.bodyMedium)
                Text("github.com/dmz006/datawatch",
                     style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Text(
        title,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
    content()
    HorizontalDivider()
}

@Composable
private fun SectionWithAction(
    title: String,
    actionIcon: androidx.compose.ui.graphics.vector.ImageVector,
    actionDescription: String,
    onAction: () -> Unit,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f).padding(vertical = 12.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        IconButton(onClick = onAction) {
            Icon(actionIcon, contentDescription = actionDescription,
                 tint = MaterialTheme.colorScheme.primary)
        }
    }
    content()
    HorizontalDivider()
}
