package com.dmzs.datawatchclient.ui.about

import androidx.compose.foundation.layout.Column
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * In-app MCP tools catalogue viewer. Reads `/api/mcp/docs` and
 * renders whichever shape the parent emits — either a flat
 * array of `{name, description, …}` or an object of categories
 * each holding such an array.
 */
@Composable
public fun McpToolsCard() {
    var tools by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var categories by remember { mutableStateOf<Map<String, List<Pair<String, String>>>>(emptyMap()) }
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
        ServiceLocator.transportFor(profile).fetchMcpDocs().fold(
            onSuccess = { root ->
                when (root) {
                    is JsonArray -> tools = root.extractTools()
                    is JsonObject -> {
                        // Either nested categories (object of arrays) or
                        // a top-level object with a `tools` array.
                        val nested =
                            root.entries.mapNotNull { (k, v) ->
                                (v as? JsonArray)?.extractTools()?.takeIf { it.isNotEmpty() }
                                    ?.let { k to it }
                            }
                        if (nested.isNotEmpty()) {
                            categories = nested.toMap()
                        } else {
                            tools = (root["tools"] as? JsonArray)?.extractTools() ?: emptyList()
                        }
                    }
                    else -> banner = "Unexpected response shape."
                }
            },
            onFailure = { banner = "MCP docs unavailable — ${it.message ?: it::class.simpleName}" },
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).pwaCard(),
    ) {
        PwaSectionTitle("MCP tools")
        banner?.let {
            Text(
                it,
                modifier = Modifier.padding(horizontal = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            if (categories.isNotEmpty()) {
                categories.forEach { (cat, list) ->
                    Text(
                        cat,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                    list.forEach { ToolRow(it.first, it.second) }
                }
            } else {
                tools.forEach { ToolRow(it.first, it.second) }
                if (tools.isEmpty() && banner == null) {
                    Text(
                        "Loading…",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolRow(name: String, desc: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(name, style = MaterialTheme.typography.bodyMedium)
        if (desc.isNotBlank()) {
            Text(
                desc,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    HorizontalDivider()
}

private fun JsonArray.extractTools(): List<Pair<String, String>> =
    mapNotNull { el ->
        (el as? JsonObject)?.let { obj ->
            val name =
                (obj["name"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                    ?: return@mapNotNull null
            val desc =
                (obj["description"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                    ?: (obj["summary"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                    ?: ""
            name to desc
        }
    }
