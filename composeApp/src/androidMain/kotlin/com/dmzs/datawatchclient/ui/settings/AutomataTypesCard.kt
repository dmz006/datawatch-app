package com.dmzs.datawatchclient.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.transport.dto.AutomataTypeDto
import com.dmzs.datawatchclient.transport.dto.AutomataTypeRequestDto
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
internal fun AutomataTypesCard() {
    val scope = rememberCoroutineScope()
    var types by remember { mutableStateOf<List<AutomataTypeDto>>(emptyList()) }
    var createOpen by remember { mutableStateOf(false) }

    suspend fun loadTypes() {
        val activeId = ServiceLocator.activeServerStore.get()
        val sp = ServiceLocator.profileRepository.observeAll()
            .first { list -> list.any { it.enabled } }
            .let { list ->
                if (activeId == null) list.firstOrNull { it.enabled }
                else list.firstOrNull { it.id == activeId && it.enabled }
                    ?: list.firstOrNull { it.enabled }
            } ?: return
        ServiceLocator.transportFor(sp).listAutomataTypes().onSuccess { types = it }
    }

    LaunchedEffect(Unit) { runCatching { loadTypes() } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .pwaCard()
            .padding(12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PwaSectionTitle(stringResource(R.string.automata_type_registry_title), modifier = Modifier.weight(1f), docsAnchor = "automata")
            IconButton(onClick = { createOpen = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.automata_type_create))
            }
        }
        if (types.isEmpty()) {
            Text(stringResource(R.string.automata_type_create) + "…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        } else {
            types.forEach { dt ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(dt.label.ifBlank { dt.id }, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text(dt.id, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 4.dp))
                    IconButton(onClick = {
                        scope.launch {
                            runCatching {
                                val activeId = ServiceLocator.activeServerStore.get()
                                val sp = ServiceLocator.profileRepository.observeAll()
                                    .first { list -> list.any { it.enabled } }
                                    .let { list ->
                                        if (activeId == null) list.firstOrNull { it.enabled }
                                        else list.firstOrNull { it.id == activeId && it.enabled }
                                            ?: list.firstOrNull { it.enabled }
                                    } ?: return@runCatching
                                ServiceLocator.transportFor(sp).deleteAutomataType(dt.id).onSuccess { loadTypes() }
                            }
                        }
                    }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                    }
                }
            }
        }
    }

    if (createOpen) {
        CreateTypeDialog(
            onDismiss = { createOpen = false },
            onCreate = { req ->
                scope.launch {
                    runCatching {
                        val activeId = ServiceLocator.activeServerStore.get()
                        val sp = ServiceLocator.profileRepository.observeAll()
                            .first { list -> list.any { it.enabled } }
                            .let { list ->
                                if (activeId == null) list.firstOrNull { it.enabled }
                                else list.firstOrNull { it.id == activeId && it.enabled }
                                    ?: list.firstOrNull { it.enabled }
                            } ?: return@runCatching
                        ServiceLocator.transportFor(sp).registerAutomataType(req).onSuccess { loadTypes() }
                    }
                }
                createOpen = false
            },
        )
    }
}

@Composable
private fun CreateTypeDialog(onDismiss: () -> Unit, onCreate: (AutomataTypeRequestDto) -> Unit) {
    var id by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var color by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.automata_type_create)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(value = id, onValueChange = { id = it }, label = { Text(stringResource(R.string.automata_type_id_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text(stringResource(R.string.automata_type_label_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                OutlinedTextField(value = color, onValueChange = { color = it }, label = { Text(stringResource(R.string.automata_type_color_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(AutomataTypeRequestDto(id = id.trim(), label = label.trim(), color = color.trim().ifBlank { null })) },
                enabled = id.isNotBlank() && label.isNotBlank(),
            ) { Text(stringResource(R.string.action_create)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
