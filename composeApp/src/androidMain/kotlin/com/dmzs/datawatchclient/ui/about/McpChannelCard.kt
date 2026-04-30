package com.dmzs.datawatchclient.ui.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.prefs.ActiveServerStore
import com.dmzs.datawatchclient.ui.theme.LocalDatawatchColors
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull

/**
 * Settings → About: MCP channel bridge status card.
 * Mirrors GET /api/channel/info — shows bridge kind (Go/JS),
 * bridge path, ready badge, and collapsible stale-.mcp.json list.
 */
@Composable
public fun McpChannelCard() {
    var kind by remember { mutableStateOf<String?>(null) }
    var ready by remember { mutableStateOf<Boolean?>(null) }
    var path by remember { mutableStateOf<String?>(null) }
    var stale by remember { mutableStateOf<List<String>>(emptyList()) }
    var banner by remember { mutableStateOf<String?>(null) }
    var staleExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val profiles = ServiceLocator.profileRepository.observeAll().first()
        val activeId = ServiceLocator.activeServerStore.get()
        val profile =
            profiles.firstOrNull {
                it.id == activeId && it.enabled && activeId != ActiveServerStore.SENTINEL_ALL_SERVERS
            } ?: profiles.firstOrNull { it.enabled } ?: run {
                banner = "No enabled server."
                return@LaunchedEffect
            }
        ServiceLocator.transportFor(profile).fetchChannelInfo().fold(
            onSuccess = { el ->
                val obj = el as? JsonObject ?: run {
                    banner = "Unexpected response."
                    return@fold
                }
                kind = (obj["kind"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                ready = (obj["ready"] as? JsonPrimitive)?.booleanOrNull
                path = (obj["path"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                stale = (obj["stale"] as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
                    ?: emptyList()
            },
            onFailure = { banner = "Channel info unavailable — ${it.message ?: it::class.simpleName}" },
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).pwaCard(),
    ) {
        PwaSectionTitle("MCP Channel Bridge")
        banner?.let {
            Text(
                it,
                modifier = Modifier.padding(horizontal = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (banner == null) {
            if (kind == null && ready == null) {
                Text(
                    stringResource(R.string.common_loading),
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val dw = LocalDatawatchColors.current
                // Kind row
                kind?.let { k ->
                    val kindLabel = if (k.equals("go", ignoreCase = true)) "Go ✓" else "JS ⚠"
                    val kindColor = if (k.equals("go", ignoreCase = true)) dw.success else MaterialTheme.colorScheme.error
                    BridgeInfoRow("Kind", kindLabel, kindColor)
                }
                // Ready badge
                ready?.let { r ->
                    val readyLabel = if (r) "Ready" else "Not ready"
                    val readyColor = if (r) dw.success else MaterialTheme.colorScheme.error
                    BridgeInfoRow("Status", readyLabel, readyColor)
                }
                // Path
                path?.let { p ->
                    if (p.isNotBlank()) BridgeInfoRow("Path", p, MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // Stale list (collapsible)
                if (stale.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { staleExpanded = !staleExpanded }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Stale .mcp.json files (${stale.size})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            if (staleExpanded) "▲" else "▼",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (staleExpanded) {
                        stale.forEach { stalePath ->
                            Text(
                                stalePath,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
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

@Composable
private fun BridgeInfoRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(56.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = valueColor,
            modifier = Modifier.weight(1f),
        )
    }
}
