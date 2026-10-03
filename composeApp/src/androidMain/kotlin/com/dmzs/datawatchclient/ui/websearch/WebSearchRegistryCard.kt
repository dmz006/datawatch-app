@file:Suppress("MagicNumber")

package com.dmzs.datawatchclient.ui.websearch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.prefs.ActiveServerStore
import com.dmzs.datawatchclient.transport.dto.WebSearchProviderDto
import com.dmzs.datawatchclient.ui.common.DatawatchLoadingContent
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private suspend fun resolveTransport() =
    ServiceLocator.profileRepository.observeAll().first().let { profiles ->
        val activeId = ServiceLocator.activeServerStore.get()
        (
            profiles.firstOrNull {
                it.id == activeId && it.enabled && activeId != ActiveServerStore.SENTINEL_ALL_SERVERS
            } ?: profiles.firstOrNull { it.enabled }
        )?.let { ServiceLocator.transportFor(it) }

    }

/**
 * BL391 — Web Search Providers registry card for Settings → Compute tab.
 *
 * Shows the named provider list (SearXNG / Brave) with enable/disable
 * toggle, Test, Edit, Delete, and an Add button. Mirrors the LlmRegistryCard
 * pattern; replaces the old single-provider ConfigFieldsPanel(WebSearch).
 *
 * Endpoints: GET/POST /api/websearch/providers,
 *            PATCH/DELETE /api/websearch/providers/{name},
 *            POST .../enable|disable|test
 */
@Composable
public fun WebSearchRegistryCard() {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var providers by remember { mutableStateOf<List<WebSearchProviderDto>>(emptyList()) }
    var banner by remember { mutableStateOf<String?>(null) }
    var refreshTick by remember { mutableStateOf(0) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editProvider by remember { mutableStateOf<WebSearchProviderDto?>(null) }
    var deleteProvider by remember { mutableStateOf<WebSearchProviderDto?>(null) }

    LaunchedEffect(refreshTick) {
        loading = true
        val transport = resolveTransport() ?: run {
            banner = "No enabled server."
            loading = false
            return@LaunchedEffect
        }
        transport.listWebSearchProviders().fold(
            onSuccess = { providers = it; banner = null },
            onFailure = { banner = "Unavailable — ${it.message ?: it::class.simpleName}" },
        )
        loading = false
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).pwaCard(),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PwaSectionTitle(
                stringResource(R.string.ws_registry_title),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { editProvider = null; showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.ws_registry_add))
            }
        }

        banner?.let {
            Text(
                it,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (loading) {
            DatawatchLoadingContent(modifier = Modifier.padding(horizontal = 12.dp))
        } else if (providers.isEmpty() && banner == null) {
            Text(
                stringResource(R.string.ws_registry_empty),
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            providers.forEachIndexed { idx, p ->
                if (idx > 0) HorizontalDivider()
                WebSearchProviderRow(
                    provider = p,
                    onToggle = { enabled ->
                        scope.launch {
                            resolveTransport()?.enableWebSearchProvider(p.name, enabled)?.fold(
                                onSuccess = { refreshTick++ },
                                onFailure = { banner = "Toggle failed — ${it.message}" },
                            )
                        }
                    },
                    onEdit = { editProvider = p; showAddDialog = true },
                    onDelete = { deleteProvider = p },
                    onTest = {
                        scope.launch {
                            banner = "Testing ${p.name}…"
                            resolveTransport()?.testWebSearchProvider(p.name)?.fold(
                                onSuccess = { result ->
                                    banner = if (result.ok) {
                                        "✓ ${p.name}: ${result.resultCount} results"
                                    } else {
                                        "✗ ${p.name}: ${result.error ?: "test failed"}"
                                    }
                                },
                                onFailure = { banner = "Test error — ${it.message}" },
                            )
                        }
                    },
                )
            }
        }
    }

    if (showAddDialog) {
        WebSearchProviderDialog(
            existing = editProvider,
            onDismiss = { showAddDialog = false },
            onSave = { dto ->
                scope.launch {
                    val transport = resolveTransport() ?: return@launch
                    val result = if (editProvider == null) {
                        transport.createWebSearchProvider(dto)
                    } else {
                        transport.updateWebSearchProvider(editProvider!!.name, dto)
                    }
                    result.fold(
                        onSuccess = { showAddDialog = false; banner = null; refreshTick++ },
                        onFailure = { banner = "Save failed — ${it.message}" },
                    )
                }
            },
        )
    }

    deleteProvider?.let { p ->
        AlertDialog(
            onDismissRequest = { deleteProvider = null },
            title = { Text(stringResource(R.string.ws_registry_delete_title)) },
            text = { Text(stringResource(R.string.ws_registry_delete_confirm, p.name)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        resolveTransport()?.deleteWebSearchProvider(p.name)?.fold(
                            onSuccess = { deleteProvider = null; refreshTick++ },
                            onFailure = { banner = "Delete failed — ${it.message}" },
                        )
                        deleteProvider = null
                    }
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteProvider = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun WebSearchProviderRow(
    provider: WebSearchProviderDto,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onTest: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(provider.name, style = MaterialTheme.typography.bodyMedium)
                TypeChip(provider.type)
                if (provider.priority > 0) {
                    AssistChip(
                        onClick = {},
                        label = { Text("#${provider.priority}", style = MaterialTheme.typography.labelSmall) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    )
                }
            }
            val detail = when (provider.type) {
                "searxng" -> provider.url.ifBlank { null }?.let { "$it · ${provider.engine.ifBlank { "default" }}" }
                "brave" -> if (provider.apiKey.isNotBlank()) "API key set" else "No API key"
                else -> null
            }
            detail?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = provider.enabled,
            onCheckedChange = onToggle,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        IconButton(onClick = { menuExpanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = null)
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.ws_registry_test)) },
                onClick = { menuExpanded = false; onTest() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.edit)) },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                onClick = { menuExpanded = false; onEdit() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                onClick = { menuExpanded = false; onDelete() },
            )
        }
    }
}

@Composable
private fun TypeChip(type: String) {
    val (label, containerColor) = when (type) {
        "searxng" -> "SearXNG" to Color(0xFF1565C0)
        "brave" -> "Brave" to Color(0xFFBF360C)
        else -> type to Color(0xFF37474F)
    }
    AssistChip(
        onClick = {},
        label = { Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White) },
        colors = AssistChipDefaults.assistChipColors(containerColor = containerColor),
    )
}

@Composable
private fun WebSearchProviderDialog(
    existing: WebSearchProviderDto?,
    onDismiss: () -> Unit,
    onSave: (WebSearchProviderDto) -> Unit,
) {
    var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
    var type by remember(existing) { mutableStateOf(existing?.type ?: "searxng") }
    var enabled by remember(existing) { mutableStateOf(existing?.enabled ?: true) }
    var priority by remember(existing) { mutableStateOf(existing?.priority?.toString() ?: "1") }
    var url by remember(existing) { mutableStateOf(existing?.url ?: "") }
    var engine by remember(existing) { mutableStateOf(existing?.engine ?: "bing") }
    var apiKey by remember(existing) { mutableStateOf("") }
    var numResults by remember(existing) { mutableStateOf(existing?.numResults?.toString() ?: "10") }
    var typeDropdown by remember { mutableStateOf(false) }
    var apiKeyVisible by remember { mutableStateOf(false) }

    val isSearxng = type == "searxng"
    val isBrave = type == "brave"
    val isEdit = existing != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) stringResource(R.string.ws_registry_edit) else stringResource(R.string.ws_registry_add)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.ws_registry_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isEdit,
                )
                // Type picker
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.ws_registry_type_label),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { if (!isEdit) typeDropdown = true }, enabled = !isEdit) { Text(type) }
                    DropdownMenu(expanded = typeDropdown, onDismissRequest = { typeDropdown = false }) {
                        listOf("searxng", "brave").forEach { t ->
                            DropdownMenuItem(text = { Text(t) }, onClick = { type = t; typeDropdown = false })
                        }
                    }
                }
                // SearXNG fields
                if (isSearxng) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text(stringResource(R.string.ws_registry_url_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = engine,
                        onValueChange = { engine = it },
                        label = { Text(stringResource(R.string.ws_registry_engine_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                // Brave fields
                if (isBrave) {
                    val existingKeyNote = if (isEdit && existing?.apiKey?.isNotBlank() == true) {
                        stringResource(R.string.ws_registry_api_key_set)
                    } else null
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text(stringResource(R.string.ws_registry_api_key_label)) },
                        placeholder = existingKeyNote?.let { { Text(it) } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                                Text(if (apiKeyVisible) stringResource(R.string.hide) else stringResource(R.string.show))
                            }
                        },
                    )
                }
                // Common fields
                OutlinedTextField(
                    value = numResults,
                    onValueChange = { numResults = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.ws_registry_num_results_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = priority,
                    onValueChange = { priority = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.ws_registry_priority_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.ws_registry_enabled_label),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val dto = WebSearchProviderDto(
                    name = name.trim(),
                    type = type,
                    enabled = enabled,
                    priority = priority.toIntOrNull() ?: 1,
                    url = if (isSearxng) url.trim() else "",
                    engine = if (isSearxng) engine.trim() else "",
                    apiKey = if (isBrave && apiKey.isNotBlank()) apiKey else (if (isEdit) existing?.apiKey ?: "" else ""),
                    numResults = numResults.toIntOrNull() ?: 10,
                )
                onSave(dto)
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
