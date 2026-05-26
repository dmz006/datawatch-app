package com.dmzs.datawatchclient.ui.monitoring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.prefs.ActiveServerStore
import com.dmzs.datawatchclient.transport.dto.AuditEntryDto
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Observer tab — Audit Log card. Mirrors PWA audit section:
 * filterable by actor/action, shows ts/action/actor/sessionId/details.
 */
@Composable
public fun AuditLogCard() {
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<AuditEntryDto>>(emptyList()) }
    var actorFilter by remember { mutableStateOf("") }
    var actionFilter by remember { mutableStateOf("") }
    var banner by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        val profiles = ServiceLocator.profileRepository.observeAll().first()
        val activeId = ServiceLocator.activeServerStore.get()
        val profile = if (activeId == ActiveServerStore.SENTINEL_ALL_SERVERS) {
            profiles.firstOrNull { it.enabled }
        } else {
            profiles.firstOrNull { it.id == activeId && it.enabled }
                ?: profiles.firstOrNull { it.enabled }
        }
        profile?.let {
            ServiceLocator.transportFor(it).getAuditLog(
                actor = actorFilter.takeIf { a -> a.isNotBlank() },
                action = actionFilter.takeIf { a -> a.isNotBlank() },
                limit = 20,
            ).onSuccess { list -> entries = list.entries; banner = null }
             .onFailure { e -> banner = e.message }
        }
    }

    LaunchedEffect(Unit) { reload() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .pwaCard()
            .padding(12.dp),
    ) {
        PwaSectionTitle("Audit Log", docsAnchor = "audit-log")

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = actorFilter,
                onValueChange = { actorFilter = it },
                label = { Text("Actor") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = actionFilter,
                onValueChange = { actionFilter = it },
                label = { Text("Action") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { scope.launch { reload() } }) { Text("Load") }
        }

        banner?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        if (entries.isEmpty()) {
            Text(
                "No audit entries.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            entries.forEach { entry ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        entry.ts?.let {
                            Text(
                                it.take(16),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            entry.action,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                        entry.actor?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        entry.sessionId?.let {
                            Text(
                                it.takeLast(8),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    entry.details?.takeIf { it.isNotEmpty() }?.let {
                        Text(
                            it.toString().take(120),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
