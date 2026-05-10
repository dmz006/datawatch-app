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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.transport.dto.SessionTemplateDto
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * v0.82.0 Sprint 13 — Session Templates card.
 * GET/POST/DELETE /api/templates
 */
@Composable
public fun SessionTemplatesCard(vm: SessionTemplatesViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var name by remember { mutableStateOf("") }
    var backend by remember { mutableStateOf("") }
    var projectDir by remember { mutableStateOf("") }
    var effort by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.load() }

    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .pwaCard(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            PwaSectionTitle(stringResource(R.string.session_templates_title))

            // Add form
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.session_template_name_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = backend,
                onValueChange = { backend = it },
                label = { Text(stringResource(R.string.session_template_backend_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = projectDir,
                onValueChange = { projectDir = it },
                label = { Text(stringResource(R.string.session_template_dir_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = effort,
                onValueChange = { effort = it },
                label = { Text(stringResource(R.string.session_template_effort_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.session_template_desc_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedButton(
                onClick = {
                    if (name.isNotBlank()) {
                        vm.addTemplate(
                            SessionTemplateDto(
                                name = name,
                                backend = backend,
                                projectDir = projectDir,
                                effort = effort,
                                description = description,
                            ),
                        )
                        name = ""; backend = ""; projectDir = ""; effort = ""; description = ""
                    }
                },
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text(stringResource(R.string.session_template_add))
            }

            // Template list
            if (state.templates.isEmpty()) {
                Text(
                    stringResource(R.string.session_template_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else {
                state.templates.forEach { t ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(t.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            val meta = listOfNotNull(
                                t.backend.takeIf { it.isNotBlank() },
                                t.effort.takeIf { it.isNotBlank() },
                                t.description.takeIf { it.isNotBlank() },
                            ).joinToString(" · ")
                            if (meta.isNotBlank()) {
                                Text(
                                    meta,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.width(4.dp))
                        IconButton(onClick = { vm.deleteTemplate(t.name) }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.session_template_delete),
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

public class SessionTemplatesViewModel : ViewModel() {
    public data class UiState(
        val templates: List<SessionTemplateDto> = emptyList(),
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    public val state: StateFlow<UiState> = _state

    public fun load() {
        viewModelScope.launch {
            val transport = resolveTransport() ?: return@launch
            transport.getSessionTemplates().fold(
                onSuccess = { _state.value = _state.value.copy(templates = it, error = null) },
                onFailure = { _state.value = _state.value.copy(error = it.message ?: it::class.simpleName) },
            )
        }
    }

    public fun addTemplate(template: SessionTemplateDto) {
        viewModelScope.launch {
            val transport = resolveTransport() ?: return@launch
            transport.createSessionTemplate(template).onSuccess { load() }
        }
    }

    public fun deleteTemplate(name: String) {
        viewModelScope.launch {
            val transport = resolveTransport() ?: return@launch
            transport.deleteSessionTemplate(name).onSuccess { load() }
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
