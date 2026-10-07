package com.dmzs.datawatchclient.ui.about

import androidx.compose.foundation.layout.padding
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
import com.dmzs.datawatchclient.transport.ChannelBridgeFormat
import com.dmzs.datawatchclient.transport.ChannelBridgeLine
import com.dmzs.datawatchclient.ui.theme.LocalDatawatchColors
import com.dmzs.datawatchclient.ui.theme.PwaCard
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject

/**
 * MCP channel bridge status card (Observer + Settings › About).
 * `GET /api/channel/info` rendered as the web UI's `loadChannelBridge` lines
 * (shared [ChannelBridgeFormat], same as iOS): bridge kind + ready mark,
 * path, not-ready hint, JS node path, `MCP: stdio + SSE`, and the stale
 * `.mcp.json` list with the cleanup command.
 */
@Composable
public fun McpChannelCard() {
    var lines by remember { mutableStateOf<List<ChannelBridgeLine>?>(null) }
    var banner by remember { mutableStateOf<String?>(null) }

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
                val obj = el as? JsonObject
                if (obj == null) banner = "unavailable" else lines = ChannelBridgeFormat.lines(obj)
            },
            onFailure = { banner = "unavailable" },
        )
    }

    PwaCard(
        id = "mcp_channel",
        title = "MCP Channel Bridge",
    ) {
        val dw = LocalDatawatchColors.current
        val current = lines
        when {
            banner != null ->
                Text(
                    banner.orEmpty(),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            current == null -> com.dmzs.datawatchclient.ui.common.PwaLoadingText()
            else ->
                current.forEach { l ->
                    Text(
                        l.text,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color =
                            when (l.tone) {
                                "success" -> dw.success
                                "warning" -> dw.warning
                                "muted" -> MaterialTheme.colorScheme.onSurfaceVariant
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                    )
                }
        }
    }
}
