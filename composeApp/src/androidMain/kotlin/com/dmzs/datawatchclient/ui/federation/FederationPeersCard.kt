package com.dmzs.datawatchclient.ui.federation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.prefs.ActiveServerStore
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Read-only list of the active server's federation peers
 * (GET /api/servers). Adding / editing peers is done via the
 * server's own config UI today — mobile surfaces this just so
 * users can verify which peers the active server talks to.
 */
@Composable
public fun FederationPeersCard() {
    var peers by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var banner by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val profiles = ServiceLocator.profileRepository.observeAll().first()
        val activeId = ServiceLocator.activeServerStore.get()
        val profile =
            profiles.firstOrNull {
                it.id == activeId && it.enabled && activeId != ActiveServerStore.SENTINEL_ALL_SERVERS
            } ?: profiles.firstOrNull { it.enabled } ?: return@LaunchedEffect
        ServiceLocator.transportFor(profile).listRemoteServers().fold(
            onSuccess = { peers = it },
            onFailure = { banner = "Peers unavailable — ${it.message ?: it::class.simpleName}" },
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).pwaCard(),
    ) {
        PwaSectionTitle("Federated peers")
        banner?.let {
            Text(
                it,
                modifier = Modifier.padding(horizontal = 12.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (peers.isEmpty() && banner == null) {
            Text(
                "No federated peers configured.",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        peers.forEach { p ->
            val name = p.stringField("name") ?: "?"
            val url = p.stringField("url") ?: ""
            val enabled = p.boolField("enabled") ?: true
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        url,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    if (enabled) "enabled" else "disabled",
                    style = MaterialTheme.typography.labelSmall,
                    color =
                        if (enabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
            HorizontalDivider()
        }
    }
}

private fun JsonObject.stringField(key: String): String? = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonObject.boolField(key: String): Boolean? = (get(key) as? JsonPrimitive)?.content?.toBooleanStrictOrNull()
