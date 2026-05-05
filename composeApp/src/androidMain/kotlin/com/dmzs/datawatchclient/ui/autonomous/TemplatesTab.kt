package com.dmzs.datawatchclient.ui.autonomous

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.transport.dto.TemplateDto
import com.dmzs.datawatchclient.transport.dto.UpdateTemplateRequestDto
import com.dmzs.datawatchclient.ui.theme.pwaCard

@Composable
internal fun TemplatesTab(
    vm: TemplatesViewModel,
    createOpen: Boolean,
    onCreateDismiss: () -> Unit,
) {
    val state by vm.state.collectAsState()
    var editTemplate by remember { mutableStateOf<TemplateDto?>(null) }
    var instantiateTemplate by remember { mutableStateOf<TemplateDto?>(null) }
    var deleteTarget by remember { mutableStateOf<TemplateDto?>(null) }

    LaunchedEffect(Unit) { vm.refresh() }

    Box(modifier = Modifier.fillMaxSize()) {
        state.banner?.let { banner ->
            Text(
                banner,
                modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (state.templates.isEmpty() && !state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.tmpl_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.templates, key = { it.id }) { tmpl ->
                    TemplateRow(tmpl, onUse = { instantiateTemplate = tmpl }, onEdit = { editTemplate = tmpl }, onDelete = { deleteTarget = tmpl })
                }
            }
        }
    }

    if (createOpen) {
        CreateEditTemplateSheet(template = null, onDismiss = onCreateDismiss, onSave = { req -> vm.createTemplate(req); onCreateDismiss() })
    }
    editTemplate?.let { tmpl ->
        CreateEditTemplateSheet(
            template = tmpl,
            onDismiss = { editTemplate = null },
            onSave = { req ->
                vm.updateTemplate(tmpl.id, UpdateTemplateRequestDto(req.title, req.spec, req.type, req.tags, req.description))
                editTemplate = null
            },
        )
    }
    instantiateTemplate?.let { tmpl ->
        InstantiateTemplateDialog(
            template = tmpl,
            onDismiss = { instantiateTemplate = null },
            onInstantiate = { req -> vm.instantiateTemplate(tmpl.id, req) { _ -> }; instantiateTemplate = null },
        )
    }
    deleteTarget?.let { tmpl ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.tmpl_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = { vm.deleteTemplate(tmpl.id); deleteTarget = null }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun TemplateRow(
    template: TemplateDto,
    onUse: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .pwaCard()
            .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                template.title.ifBlank { template.id },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp),
            ) {
                template.type?.takeIf { it.isNotBlank() }?.let { type ->
                    Box(
                        Modifier
                            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    ) {
                        Text(type, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
                template.tags.take(3).forEach { tag ->
                    Text("#$tag", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            template.description?.takeIf { it.isNotBlank() }?.let { desc ->
                Text(
                    desc.take(80),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        TextButton(onClick = onUse) { Text(stringResource(R.string.tmpl_use)) }
        IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit)) }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete)) }
    }
}
