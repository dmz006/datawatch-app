package com.dmzs.datawatchclient.ui.general

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.transport.dto.ToolingBackendDto
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * v0.82.0 Sprint 13 — Backend Artifact Lifecycle (Tooling) card.
 * GET /api/tooling/status, POST /api/tooling/gitignore and /cleanup
 */
@Composable
public fun ToolingCard(vm: ToolingViewModel = viewModel()) {
    val state by vm.state.collectAsState()

    LaunchedEffect(Unit) { vm.load() }

    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .pwaCard(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                PwaSectionTitle(stringResource(R.string.tooling_title), Modifier.weight(1f), docsAnchor = "backend-artifact-lifecycle")
                TextButton(onClick = { vm.load() }) {
                    Text(stringResource(R.string.tooling_refresh))
                }
            }

            if (state.backends.isEmpty()) {
                Text(
                    stringResource(R.string.tooling_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else {
                state.backends.forEach { b ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                b.backend,
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                if (b.ignored) "✓" else "⚠",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (b.ignored) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            )
                        }
                        if (b.present.isNotEmpty()) {
                            Text(
                                b.present.joinToString(", "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row {
                            OutlinedButton(
                                onClick = { vm.gitignore(b.backend) },
                                modifier = Modifier.padding(end = 4.dp),
                            ) {
                                Text(stringResource(R.string.tooling_gitignore))
                            }
                            Spacer(Modifier.width(4.dp))
                            OutlinedButton(onClick = { vm.cleanup(b.backend) }) {
                                Text(stringResource(R.string.tooling_cleanup))
                            }
                        }
                    }
                }
            }

            state.error?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

public class ToolingViewModel : ViewModel() {
    public data class UiState(
        val backends: List<ToolingBackendDto> = emptyList(),
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    public val state: StateFlow<UiState> = _state

    public fun load() {
        viewModelScope.launch {
            val transport = resolveTransport() ?: return@launch
            transport.getToolingStatus().fold(
                onSuccess = { _state.value = _state.value.copy(backends = it.backends, error = null) },
                onFailure = { _state.value = _state.value.copy(error = it.message ?: it::class.simpleName) },
            )
        }
    }

    public fun gitignore(backend: String) {
        viewModelScope.launch {
            val transport = resolveTransport() ?: return@launch
            transport.toolingGitignore(backend).onSuccess { load() }
        }
    }

    public fun cleanup(backend: String) {
        viewModelScope.launch {
            val transport = resolveTransport() ?: return@launch
            transport.toolingCleanup(backend).onSuccess { load() }
        }
    }

    private suspend fun resolveTransport(): com.dmzs.datawatchclient.transport.TransportClient? {
        val activeId = ServiceLocator.activeServerStore.get()
        val profile =
            ServiceLocator.profileRepository.observeAll().first()
                .firstOrNull { it.id == activeId && it.enabled } ?: return null
        return ServiceLocator.transportFor(profile)
    }
}
