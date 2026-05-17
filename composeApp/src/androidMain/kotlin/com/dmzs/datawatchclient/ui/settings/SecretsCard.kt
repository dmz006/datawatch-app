package com.dmzs.datawatchclient.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.transport.dto.AddSecretDto
import com.dmzs.datawatchclient.transport.dto.SecretDto
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * v0.82.0 Sprint 13 — Secrets Store card (full CRUD).
 * GET/POST/DELETE /api/secrets
 * Placed in Settings → General below SecretsStatusCard.
 */
@Composable
public fun SecretsCard(vm: SecretsCardViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var name by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var scopes by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.load() }

    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .pwaCard(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            PwaSectionTitle(stringResource(R.string.secrets_section_store), docsAnchor = "secrets-store")

            // Add form
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.secrets_name_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(stringResource(R.string.secrets_value_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            OutlinedTextField(
                value = desc,
                onValueChange = { desc = it },
                label = { Text(stringResource(R.string.secrets_desc_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = tags,
                onValueChange = { tags = it },
                label = { Text(stringResource(R.string.secrets_tags_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = scopes,
                onValueChange = { scopes = it },
                label = { Text(stringResource(R.string.secrets_scopes_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedButton(
                onClick = {
                    if (name.isNotBlank() && value.isNotBlank()) {
                        vm.addSecret(
                            AddSecretDto(
                                name = name,
                                value = value,
                                description = desc,
                                tags = tags.split(",").map { it.trim() }.filter { it.isNotBlank() },
                                scopes = scopes.split(",").map { it.trim() }.filter { it.isNotBlank() },
                            ),
                        )
                        name = ""; value = ""; desc = ""; tags = ""; scopes = ""
                    }
                },
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text(stringResource(R.string.secrets_add_btn))
            }

            // Secret list
            if (state.secrets.isEmpty()) {
                Text(
                    stringResource(R.string.secrets_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else {
                state.secrets.forEach { s ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    s.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                )
                                if (s.tags.isNotEmpty()) {
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "[${s.tags.joinToString(", ")}]",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (s.description.isNotBlank()) {
                                Text(
                                    s.description,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (s.scopes.isNotEmpty()) {
                                Row {
                                    s.scopes.forEach { scope ->
                                        Surface(
                                            color = Color(0xFF00695C),
                                            shape = RoundedCornerShape(4.dp),
                                            modifier = Modifier.padding(end = 4.dp),
                                        ) {
                                            Text(
                                                scope,
                                                fontSize = 10.sp,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.width(4.dp))
                        IconButton(onClick = { vm.deleteSecret(s.name) }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.secrets_delete_btn),
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

public class SecretsCardViewModel : ViewModel() {
    public data class UiState(
        val secrets: List<SecretDto> = emptyList(),
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    public val state: StateFlow<UiState> = _state

    public fun load() {
        viewModelScope.launch {
            val transport = resolveTransport() ?: return@launch
            transport.getSecrets().fold(
                onSuccess = { _state.value = _state.value.copy(secrets = it.secrets, error = null) },
                onFailure = { _state.value = _state.value.copy(error = it.message ?: it::class.simpleName) },
            )
        }
    }

    public fun addSecret(secret: AddSecretDto) {
        viewModelScope.launch {
            val transport = resolveTransport() ?: return@launch
            transport.addSecret(secret).onSuccess { load() }
        }
    }

    public fun deleteSecret(name: String) {
        viewModelScope.launch {
            val transport = resolveTransport() ?: return@launch
            transport.deleteSecret(name).onSuccess { load() }
        }
    }

    private suspend fun resolveTransport(): com.dmzs.datawatchclient.transport.TransportClient? {
        val activeId = ServiceLocator.activeServerStore.get()
        val profiles = ServiceLocator.profileRepository.observeAll().first()
        val profile = if (activeId == null) profiles.firstOrNull { it.enabled }
            else profiles.firstOrNull { it.id == activeId && it.enabled }
                ?: profiles.firstOrNull { it.enabled }
        return profile?.let { ServiceLocator.transportFor(it) }
    }
}
