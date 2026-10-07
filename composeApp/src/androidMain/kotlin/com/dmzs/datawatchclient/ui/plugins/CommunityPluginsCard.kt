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
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.transport.CommunityPlugins
import com.dmzs.datawatchclient.transport.dto.CommunityPluginDto
import kotlinx.coroutines.launch
import com.dmzs.datawatchclient.ui.theme.PwaCard

/**
 * Community Plugins (web UI GH#191, datawatch v8.66): browse a registry's plugins
 * and install one. Registries come from Skill Registries; "community" by default,
 * a picker when there is more than one, and a Connect action when the registry
 * isn't connected yet. Shared rules: [CommunityPlugins].
 */
@Composable
public fun CommunityPluginsCard() {
    var registries by remember { mutableStateOf<List<String>?>(null) }
    var registry by remember { mutableStateOf<String?>(null) }
    var plugins by remember { mutableStateOf<List<CommunityPluginDto>?>(null) }
    var notConnected by remember { mutableStateOf(false) }
    var banner by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var reload by remember { mutableStateOf(0) }
    var pickerOpen by remember { mutableStateOf(false) }
    var connecting by remember { mutableStateOf(false) }
    val installed = remember { mutableStateMapOf<String, Boolean>() }
    val installing = remember { mutableStateMapOf<String, Boolean>() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val tr = com.dmzs.datawatchclient.ui.compute.resolveActiveTransport() ?: run {
            registries = emptyList()
            banner = "No enabled server." to false
            return@LaunchedEffect
        }
        tr.listSkillRegistries().fold(
            onSuccess = { list ->
                val names = list.map { it.name }
                registries = names
                registry = CommunityPlugins.pickRegistry(names, registry)
            },
            onFailure = {
                registries = emptyList()
                banner = com.dmzs.datawatchclient.transport.ErrorText.of(it, "Registries unavailable.") to false
            },
        )
    }
    LaunchedEffect(registry, reload) {
        val reg = registry ?: return@LaunchedEffect
        plugins = null
        notConnected = false
        val tr = com.dmzs.datawatchclient.ui.compute.resolveActiveTransport() ?: return@LaunchedEffect
        tr.browsePlugins(reg).fold(
            onSuccess = { plugins = it.plugins },
            onFailure = {
                val msg = com.dmzs.datawatchclient.transport.ErrorText.of(it, "Browse unavailable.")
                plugins = emptyList()
                if (CommunityPlugins.isNotConnected(msg)) notConnected = true else banner = msg to false
            },
        )
    }

    PwaCard(
        id = "community_plugins",
        title = stringResource(R.string.community_plugins_title),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            val regs = registries
            if (regs != null && regs.size > 1) {
                Box {
                    OutlinedButton(onClick = { pickerOpen = true }) { Text(registry.orEmpty()) }
                    DropdownMenu(expanded = pickerOpen, onDismissRequest = { pickerOpen = false }) {
                        regs.forEach { name ->
                            DropdownMenuItem(text = { Text(name) }, onClick = {
                                pickerOpen = false
                                banner = null
                                registry = name
                            })
                        }
                    }
                }
            }
            banner?.let { (msg, ok) ->
                Text(
                    msg,
                    modifier = Modifier.padding(vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (ok) com.dmzs.datawatchclient.ui.theme.LocalDatawatchColors.current.success else MaterialTheme.colorScheme.error,
                )
            }
            when {
                regs == null -> com.dmzs.datawatchclient.ui.common.PwaLoadingText()
                regs.isEmpty() && banner == null ->
                    Text(
                        stringResource(R.string.community_plugins_no_registries),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                notConnected -> {
                    Text(
                        stringResource(R.string.community_plugins_not_connected),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        enabled = !connecting,
                        modifier = Modifier.padding(vertical = 6.dp),
                        onClick = {
                            val reg = registry ?: return@Button
                            connecting = true
                            scope.launch {
                                val tr = com.dmzs.datawatchclient.ui.compute.resolveActiveTransport()
                                tr?.connectSkillRegistry(reg)?.fold(
                                    onSuccess = { reload++ },
                                    onFailure = { banner = com.dmzs.datawatchclient.transport.ErrorText.of(it, "Connect failed.") to false },
                                )
                                connecting = false
                            }
                        },
                    ) { Text(if (connecting) "…" else stringResource(R.string.community_plugins_connect)) }
                }
                plugins == null -> com.dmzs.datawatchclient.ui.common.PwaLoadingText()
                plugins!!.isEmpty() && banner == null ->
                    Text(
                        stringResource(R.string.community_plugins_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
            }
        }
        plugins.orEmpty().forEachIndexed { idx, plugin ->
            if (idx > 0) HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(plugin.name, style = MaterialTheme.typography.bodyMedium)
                    val sub = CommunityPlugins.subtitle(plugin.manifest.description, plugin.manifest.version)
                    if (sub.isNotBlank()) {
                        Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                val isInstalled = installed[plugin.name] == true
                val isInstalling = installing[plugin.name] == true
                Button(
                    onClick = {
                        val reg = registry ?: return@Button
                        installing[plugin.name] = true
                        scope.launch {
                            val tr = com.dmzs.datawatchclient.ui.compute.resolveActiveTransport()
                            tr?.installPlugin(reg, plugin.name)?.fold(
                                onSuccess = {
                                    installed[plugin.name] = true
                                    banner = "Installed ${plugin.name}" to true
                                },
                                onFailure = { banner = "Install failed — " + com.dmzs.datawatchclient.transport.ErrorText.of(it, "error") to false },
                            )
                            installing[plugin.name] = false
                        }
                    },
                    enabled = !isInstalled && !isInstalling,
                ) {
                    Text(
                        when {
                            isInstalled -> stringResource(R.string.community_plugins_installed)
                            isInstalling -> stringResource(R.string.community_plugins_installing)
                            else -> stringResource(R.string.community_plugins_install)
                        },
                    )
                }
            }
        }
    }
}
