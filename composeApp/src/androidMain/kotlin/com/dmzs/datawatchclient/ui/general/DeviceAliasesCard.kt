package com.dmzs.datawatchclient.ui.general

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.transport.dto.DeviceAliasDto
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * v0.82.0 Sprint 13 — Device Aliases card.
 * GET/POST/DELETE /api/device-aliases
 */
@Composable
public fun DeviceAliasesCard(vm: DeviceAliasesViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var alias by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.load() }

    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .pwaCard(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            PwaSectionTitle(stringResource(R.string.device_aliases_title), docsAnchor = "device-aliases")

            // Add form
            OutlinedTextField(
                value = alias,
                onValueChange = { alias = it },
                label = { Text(stringResource(R.string.device_alias_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = server,
                onValueChange = { server = it },
                label = { Text(stringResource(R.string.device_alias_value)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedButton(
                onClick = {
                    if (alias.isNotBlank() && server.isNotBlank()) {
                        vm.addAlias(alias, server)
                        alias = ""; server = ""
                    }
                },
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text(stringResource(R.string.device_alias_add))
            }

            // Alias list
            if (state.aliases.isEmpty()) {
                Text(
                    stringResource(R.string.device_alias_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else {
                state.aliases.forEach { a ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${a.alias} → ${a.server}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(4.dp))
                        IconButton(onClick = { vm.deleteAlias(a.alias) }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "×",
                            )
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

public class DeviceAliasesViewModel : ViewModel() {
    public data class UiState(
        val aliases: List<DeviceAliasDto> = emptyList(),
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    public val state: StateFlow<UiState> = _state

    public fun load() {
        viewModelScope.launch {
            val transport = resolveTransport() ?: return@launch
            transport.getDeviceAliases().fold(
                onSuccess = { _state.value = _state.value.copy(aliases = it, error = null) },
                onFailure = { _state.value = _state.value.copy(error = it.message ?: it::class.simpleName) },
            )
        }
    }

    public fun addAlias(alias: String, server: String) {
        viewModelScope.launch {
            val transport = resolveTransport() ?: return@launch
            transport.createDeviceAlias(alias, server).onSuccess { load() }
        }
    }

    public fun deleteAlias(alias: String) {
        viewModelScope.launch {
            val transport = resolveTransport() ?: return@launch
            transport.deleteDeviceAlias(alias).onSuccess { load() }
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
