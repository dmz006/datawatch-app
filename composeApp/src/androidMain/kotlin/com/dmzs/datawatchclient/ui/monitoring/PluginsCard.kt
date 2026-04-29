package com.dmzs.datawatchclient.ui.monitoring

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dmzs.datawatchclient.transport.dto.PluginDto
import com.dmzs.datawatchclient.ui.settings.Section
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Settings → Monitor → Plugins card. Mirrors PWA
 * `loadPluginsStatus()` (datawatch v4.2.0+ / B41).
 *
 * Renders subprocess plugins and native subsystems
 * (datawatch-observer + future native bridges) in the same list,
 * with a small kind tag (`subprocess` vs `native`) so the operator
 * sees both kinds without confusing them.
 *
 * v0.36.0 — issue #5.
 */
@Composable
public fun PluginsCard(vm: PluginsCardViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    LaunchedEffect(Unit) { vm.refresh() }
    if (state.plugins.isEmpty() && state.native.isEmpty() && !state.loading) return

    Section(title = "Plugins") {
        Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            (state.native + state.plugins).forEach { plugin ->
                PluginRow(plugin = plugin)
            }
        }
    }
}

@Composable
private fun PluginRow(plugin: PluginDto) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .background(
                        if (plugin.enabled) Color(0xFF22C55E) else Color(0xFF94A3B8),
                        CircleShape,
                    ),
        )
        Spacer(Modifier.size(8.dp))
        Column(modifier = Modifier.padding(end = 8.dp)) {
            Text(
                plugin.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val sub =
                buildString {
                    plugin.description?.takeIf { it.isNotBlank() }?.let { append(it) }
                    plugin.version?.takeIf { it.isNotBlank() }?.let {
                        if (isNotEmpty()) append(" · ")
                        append("v$it")
                    }
                    plugin.message?.takeIf { it.isNotBlank() }?.let {
                        if (isNotEmpty()) append(" · ")
                        append(it)
                    }
                }
            if (sub.isNotEmpty()) {
                Text(
                    sub,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        KindBadge(plugin.kind)
    }
}

@Composable
private fun KindBadge(kind: String) {
    val (label, color) =
        when (kind.lowercase()) {
            "native" -> "native" to Color(0xFF7C3AED)
            else -> "subprocess" to Color(0xFF3B82F6)
        }
    Box(
        modifier =
            Modifier
                .background(color.copy(alpha = 0.18f), RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

public class PluginsCardViewModel(
    private val resolver: com.dmzs.datawatchclient.ui.common.ProfileResolver =
        com.dmzs.datawatchclient.ui.common.ProfileResolver.Default,
) : ViewModel() {
    public data class UiState(
        val loading: Boolean = true,
        val plugins: List<PluginDto> = emptyList(),
        val native: List<PluginDto> = emptyList(),
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    public val state: StateFlow<UiState> = _state

    public fun refresh() {
        viewModelScope.launch {
            val (_, transport) = resolver.resolve() ?: return@launch
            transport.listPlugins().fold(
                onSuccess = { dto ->
                    _state.value =
                        _state.value.copy(
                            loading = false,
                            plugins = dto.plugins,
                            native = dto.native,
                            error = null,
                        )
                },
                onFailure = { err ->
                    _state.value =
                        _state.value.copy(
                            loading = false,
                            plugins = emptyList(),
                            native = emptyList(),
                            error = err.message,
                        )
                },
            )
        }
    }
}
