package com.dmzs.datawatchclient.ui.plugins

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.transport.dto.PluginDto
import com.dmzs.datawatchclient.transport.dto.PluginsDto
import com.dmzs.datawatchclient.ui.common.ProfileResolver
import com.dmzs.datawatchclient.ui.shell.AlertDockChannel
import com.dmzs.datawatchclient.ui.shell.DockLevel
import com.dmzs.datawatchclient.ui.theme.LocalDatawatchColors
import com.dmzs.datawatchclient.ui.theme.PwaCard
import kotlinx.coroutines.launch

/**
 * PWA Settings › Plugins "Plugin Manager" (`loadPluginsPanel`, BL238):
 * Reload plugins, then Native and Subprocess lists; each subprocess plugin has
 * an Enable / Disable action (POST /api/plugins/{name}/{enable|disable}).
 * Results go to the alert dock (the PWA's toasts, D41a).
 */
@Composable
public fun InstalledPluginsCard() {
    var data by remember { mutableStateOf<PluginsDto?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(reload) {
        val (_, transport) = ProfileResolver.Default.resolve() ?: return@LaunchedEffect
        transport.listPlugins().fold(
            onSuccess = {
                data = it
                error = null
            },
            onFailure = { error = it.message ?: it::class.simpleName },
        )
    }
    PwaCard(id = "plugins_list", title = stringResource(R.string.plugins_manager_title), docsAnchor = "plugins") {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            OutlinedButton(onClick = {
                scope.launch {
                    val (_, transport) = ProfileResolver.Default.resolve() ?: return@launch
                    transport.reloadPlugins().fold(
                        onSuccess = { n -> AlertDockChannel.post("Reloaded: $n plugin(s)") },
                        onFailure = { e -> AlertDockChannel.post(e.message ?: e::class.simpleName.orEmpty(), DockLevel.Error) },
                    )
                    reload++
                }
            }) { Text(stringResource(R.string.plugins_reload)) }
            val d = data
            when {
                error != null -> Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                d == null -> com.dmzs.datawatchclient.ui.common.PwaLoadingText()
                else -> {
                    if (d.native.isNotEmpty()) {
                        PluginGroupLabel(stringResource(R.string.plugins_native))
                        d.native.forEach { p -> PluginRow(p, native = true, onToggle = {}) }
                    }
                    PluginGroupLabel(stringResource(R.string.plugins_subprocess))
                    if (d.plugins.isEmpty()) {
                        Text(
                            stringResource(R.string.plugins_none_subprocess),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    d.plugins.forEach { p ->
                        PluginRow(p, native = false, onToggle = {
                            val action = pluginToggleAction(p.enabled)
                            scope.launch {
                                val (_, transport) = ProfileResolver.Default.resolve() ?: return@launch
                                transport.pluginAction(p.name, action).fold(
                                    onSuccess = { AlertDockChannel.post("Plugin ${p.name} ${action}d") },
                                    onFailure = { e -> AlertDockChannel.post(e.message ?: e::class.simpleName.orEmpty(), DockLevel.Error) },
                                )
                                reload++
                            }
                        })
                    }
                }
            }
        }
    }
}

/** PWA button: an enabled plugin offers Disable, a disabled one Enable. */
internal fun pluginToggleAction(enabled: Boolean): String = if (enabled) "disable" else "enable"

@Composable
private fun PluginGroupLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
    )
}

@Composable
private fun PluginRow(
    p: PluginDto,
    native: Boolean,
    onToggle: () -> Unit,
) {
    HorizontalDivider()
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .background(
                        if (p.enabled) LocalDatawatchColors.current.success else MaterialTheme.colorScheme.onSurfaceVariant,
                        CircleShape,
                    ),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
            Text(
                p.name + (if (native) "  · native" else "") + (p.version?.let { "  v$it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )
            p.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!native) {
            TextButton(onClick = onToggle) {
                Text(stringResource(if (p.enabled) R.string.plugins_disable else R.string.plugins_enable))
            }
        }
    }
}
