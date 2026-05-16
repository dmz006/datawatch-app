package com.dmzs.datawatchclient.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.transport.dto.AvailableSkillDto
import com.dmzs.datawatchclient.transport.dto.SkillDto
import com.dmzs.datawatchclient.transport.dto.SkillRegistryDto
import com.dmzs.datawatchclient.transport.dto.SkillRegistryRequestDto
import com.dmzs.datawatchclient.transport.dto.SkillRegistryUpdateDto
import com.dmzs.datawatchclient.transport.dto.SyncSkillsRequestDto
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
internal fun SkillRegistriesCard() {
    val scope = rememberCoroutineScope()
    var registries by remember { mutableStateOf<List<SkillRegistryDto>>(emptyList()) }
    var syncedSkills by remember { mutableStateOf<List<SkillDto>>(emptyList()) }
    var addOpen by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<SkillRegistryDto?>(null) }
    var browseTarget by remember { mutableStateOf<SkillRegistryDto?>(null) }
    var connectingName by remember { mutableStateOf<String?>(null) }

    suspend fun loadAll() {
        val activeId = ServiceLocator.activeServerStore.get()
        val sp = ServiceLocator.profileRepository.observeAll()
            .first { list -> list.any { it.enabled } }
            .let { list ->
                if (activeId == null) list.filter { it.enabled }.firstOrNull()
                else list.firstOrNull { it.id == activeId && it.enabled }
            } ?: return
        val t = ServiceLocator.transportFor(sp)
        t.listSkillRegistries().onSuccess { registries = it }
        t.listSyncedSkills().onSuccess { syncedSkills = it }
    }

    LaunchedEffect(Unit) { runCatching { loadAll() } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .pwaCard()
            .padding(12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PwaSectionTitle(
                stringResource(R.string.skills_section_title),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = {
                scope.launch {
                    runCatching {
                        val activeId = ServiceLocator.activeServerStore.get()
                        val sp = ServiceLocator.profileRepository.observeAll()
                            .first { list -> list.any { it.enabled } }
                            .let { list ->
                                if (activeId == null) list.filter { it.enabled }.firstOrNull()
                                else list.firstOrNull { it.id == activeId && it.enabled }
                            } ?: return@runCatching
                        ServiceLocator.transportFor(sp).addDefaultSkillRegistry().onSuccess { loadAll() }
                    }
                }
            }) { Text(stringResource(R.string.skills_btn_add_default), style = MaterialTheme.typography.labelSmall) }
            IconButton(onClick = { addOpen = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.skills_btn_add))
            }
        }

        if (registries.isEmpty()) {
            Text(
                stringResource(R.string.skills_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            registries.forEach { reg ->
                RegistryRow(
                    reg = reg,
                    connecting = connectingName == reg.name,
                    onConnect = {
                        scope.launch {
                            runCatching {
                                connectingName = reg.name
                                val activeId = ServiceLocator.activeServerStore.get()
                                val sp = ServiceLocator.profileRepository.observeAll()
                                    .first { list -> list.any { it.enabled } }
                                    .let { list ->
                                        if (activeId == null) list.filter { it.enabled }.firstOrNull()
                                        else list.firstOrNull { it.id == activeId && it.enabled }
                                    } ?: return@runCatching
                                ServiceLocator.transportFor(sp).connectSkillRegistry(reg.name).onSuccess { loadAll() }
                            }
                            connectingName = null
                        }
                    },
                    onBrowse = { browseTarget = reg },
                    onEdit = { editTarget = reg },
                    onDelete = {
                        scope.launch {
                            runCatching {
                                val activeId = ServiceLocator.activeServerStore.get()
                                val sp = ServiceLocator.profileRepository.observeAll()
                                    .first { list -> list.any { it.enabled } }
                                    .let { list ->
                                        if (activeId == null) list.filter { it.enabled }.firstOrNull()
                                        else list.firstOrNull { it.id == activeId && it.enabled }
                                    } ?: return@runCatching
                                ServiceLocator.transportFor(sp).deleteSkillRegistry(reg.name).onSuccess { loadAll() }
                            }
                        }
                    },
                )
            }
        }

        if (syncedSkills.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                stringResource(R.string.skills_synced_section),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            syncedSkills.forEach { sk ->
                SyncedSkillRow(sk)
            }
        }
    }

    if (addOpen) {
        AddEditRegistryDialog(
            existing = null,
            onDismiss = { addOpen = false },
            onSave = { req ->
                scope.launch {
                    runCatching {
                        val activeId = ServiceLocator.activeServerStore.get()
                        val sp = ServiceLocator.profileRepository.observeAll()
                            .first { list -> list.any { it.enabled } }
                            .let { list ->
                                if (activeId == null) list.filter { it.enabled }.firstOrNull()
                                else list.firstOrNull { it.id == activeId && it.enabled }
                            } ?: return@runCatching
                        ServiceLocator.transportFor(sp).createSkillRegistry(req).onSuccess { loadAll() }
                    }
                }
                addOpen = false
            },
        )
    }

    editTarget?.let { reg ->
        AddEditRegistryDialog(
            existing = reg,
            onDismiss = { editTarget = null },
            onSave = { req ->
                scope.launch {
                    runCatching {
                        val activeId = ServiceLocator.activeServerStore.get()
                        val sp = ServiceLocator.profileRepository.observeAll()
                            .first { list -> list.any { it.enabled } }
                            .let { list ->
                                if (activeId == null) list.filter { it.enabled }.firstOrNull()
                                else list.firstOrNull { it.id == activeId && it.enabled }
                            } ?: return@runCatching
                        ServiceLocator.transportFor(sp).updateSkillRegistry(
                            reg.name,
                            SkillRegistryUpdateDto(url = req.url, branch = req.branch),
                        ).onSuccess { loadAll() }
                    }
                }
                editTarget = null
            },
        )
    }

    browseTarget?.let { reg ->
        BrowseSkillsDialog(
            registry = reg,
            onDismiss = { browseTarget = null },
            onSync = { selected ->
                scope.launch {
                    runCatching {
                        val activeId = ServiceLocator.activeServerStore.get()
                        val sp = ServiceLocator.profileRepository.observeAll()
                            .first { list -> list.any { it.enabled } }
                            .let { list ->
                                if (activeId == null) list.filter { it.enabled }.firstOrNull()
                                else list.firstOrNull { it.id == activeId && it.enabled }
                            } ?: return@runCatching
                        ServiceLocator.transportFor(sp).syncSkills(reg.name, SyncSkillsRequestDto(selected)).onSuccess { loadAll() }
                    }
                }
                browseTarget = null
            },
        )
    }
}

@Composable
private fun RegistryRow(
    reg: SkillRegistryDto,
    connecting: Boolean,
    onConnect: () -> Unit,
    onBrowse: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .padding(end = 0.dp),
            ) {
                Surface(
                    modifier = Modifier.size(8.dp),
                    shape = MaterialTheme.shapes.extraSmall,
                    color = if (reg.status == "connected") Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    content = {},
                )
            }
            Spacer(Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(reg.name, style = MaterialTheme.typography.bodySmall)
                Text(
                    reg.url.take(48),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (reg.builtin) {
                Text(
                    stringResource(R.string.skills_badge_builtin),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            if (connecting) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp).padding(end = 4.dp), strokeWidth = 2.dp)
            } else {
                TextButton(onClick = onConnect) { Text(stringResource(R.string.skills_btn_connect), style = MaterialTheme.typography.labelSmall) }
            }
            TextButton(onClick = onBrowse) { Text(stringResource(R.string.skills_btn_browse), style = MaterialTheme.typography.labelSmall) }
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.skills_btn_edit), modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete), modifier = Modifier.size(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SyncedSkillRow(skill: SkillDto) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(skill.name, style = MaterialTheme.typography.bodySmall)
            skill.description?.let {
                Text(it.take(60), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            skill.registry,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
    if (skill.tags.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(bottom = 2.dp)) {
            skill.tags.forEach { tag ->
                AssistChip(onClick = {}, label = { Text(tag, style = MaterialTheme.typography.labelSmall) })
            }
        }
    }
}

@Composable
private fun AddEditRegistryDialog(
    existing: SkillRegistryDto?,
    onDismiss: () -> Unit,
    onSave: (SkillRegistryRequestDto) -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var url by remember { mutableStateOf(existing?.url ?: "") }
    var branch by remember { mutableStateOf(existing?.branch ?: "main") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (existing == null) stringResource(R.string.skills_dialog_add_title)
                else stringResource(R.string.skills_dialog_edit_title),
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (existing == null) name = it },
                    label = { Text(stringResource(R.string.skills_field_name)) },
                    singleLine = true,
                    enabled = existing == null,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.skills_field_url)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = branch,
                    onValueChange = { branch = it },
                    label = { Text(stringResource(R.string.skills_field_branch)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(SkillRegistryRequestDto(name = name.trim(), url = url.trim(), branch = branch.trim().ifBlank { "main" })) },
                enabled = name.isNotBlank() && url.isNotBlank(),
            ) { Text(stringResource(if (existing == null) R.string.action_create else R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun BrowseSkillsDialog(
    registry: SkillRegistryDto,
    onDismiss: () -> Unit,
    onSync: (List<String>) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var available by remember { mutableStateOf<List<AvailableSkillDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(registry.name) {
        runCatching {
            val activeId = ServiceLocator.activeServerStore.get()
            val sp = ServiceLocator.profileRepository.observeAll()
                .first { list -> list.any { it.enabled } }
                .let { list ->
                    if (activeId == null) list.filter { it.enabled }.firstOrNull()
                    else list.firstOrNull { it.id == activeId && it.enabled }
                } ?: return@runCatching
            ServiceLocator.transportFor(sp).listAvailableSkills(registry.name).onSuccess {
                available = it
                selected = it.filter { s -> s.synced }.map { s -> s.name }.toSet()
            }
        }
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.skills_dialog_browse_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().height(320.dp).verticalScroll(rememberScrollState())) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                } else if (available.isEmpty()) {
                    Text(stringResource(R.string.skills_browse_empty), style = MaterialTheme.typography.bodySmall)
                } else {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { selected = available.map { it.name }.toSet() }) {
                            Text(stringResource(R.string.skills_select_all), style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = { selected = emptySet() }) {
                            Text(stringResource(R.string.skills_select_none), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    available.forEach { sk ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = sk.name in selected,
                                onCheckedChange = { chk ->
                                    selected = if (chk) selected + sk.name else selected - sk.name
                                },
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(sk.name, style = MaterialTheme.typography.bodySmall)
                                sk.description?.let {
                                    Text(it.take(50), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSync(selected.toList()) },
                enabled = selected.isNotEmpty(),
            ) { Text(stringResource(R.string.skills_btn_sync)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
