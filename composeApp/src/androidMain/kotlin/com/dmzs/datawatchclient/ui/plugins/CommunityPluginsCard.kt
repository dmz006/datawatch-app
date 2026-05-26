package com.dmzs.datawatchclient.ui.plugins

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.prefs.ActiveServerStore
import com.dmzs.datawatchclient.transport.dto.CommunityPluginDto
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
public fun CommunityPluginsCard() {
    var plugins by remember { mutableStateOf<List<CommunityPluginDto>>(emptyList()) }
    var banner by remember { mutableStateOf<String?>(null) }
    val installed = remember { mutableStateMapOf<String, Boolean>() }
    val installing = remember { mutableStateMapOf<String, Boolean>() }
    val scope = rememberCoroutineScope()

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
        ServiceLocator.transportFor(profile).browsePlugins("community").fold(
            onSuccess = { plugins = it.plugins },
            onFailure = { banner = "Browse unavailable — ${it.message ?: it::class.simpleName}" },
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).pwaCard(),
    ) {
        PwaSectionTitle("Community Plugins", docsAnchor = "skill-registries")
        banner?.let {
            Text(
                it,
                modifier = Modifier.padding(horizontal = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (plugins.isEmpty() && banner == null) {
            Text(
                "Loading…",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        plugins.forEachIndexed { idx, plugin ->
            if (idx > 0) HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(plugin.name, style = MaterialTheme.typography.bodyMedium)
                    val desc = plugin.manifest.description.ifBlank { plugin.manifest.version }
                    if (desc.isNotBlank()) {
                        Text(
                            desc,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                val isInstalled = installed[plugin.name] == true
                val isInstalling = installing[plugin.name] == true
                Button(
                    onClick = {
                        installing[plugin.name] = true
                        scope.launch {
                            val profiles = ServiceLocator.profileRepository.observeAll().first()
                            val activeId = ServiceLocator.activeServerStore.get()
                            val profile =
                                profiles.firstOrNull {
                                    it.id == activeId && it.enabled && activeId != ActiveServerStore.SENTINEL_ALL_SERVERS
                                } ?: profiles.firstOrNull { it.enabled }
                            if (profile == null) {
                                banner = "No enabled server."
                                installing[plugin.name] = false
                                return@launch
                            }
                            ServiceLocator.transportFor(profile)
                                .installPlugin("community", plugin.name)
                                .fold(
                                    onSuccess = {
                                        installed[plugin.name] = true
                                        banner = "Installed ${plugin.name}"
                                    },
                                    onFailure = { banner = "Install failed — ${it.message ?: it::class.simpleName}" },
                                )
                            installing[plugin.name] = false
                        }
                    },
                    enabled = !isInstalled && !isInstalling,
                ) {
                    Text(when {
                        isInstalled -> "✓ Installed"
                        isInstalling -> "…"
                        else -> "Install"
                    })
                }
            }
        }
    }
}
