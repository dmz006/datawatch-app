package com.dmzs.datawatchclient.ui.autonomous

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.transport.dto.CreateTemplateRequestDto
import com.dmzs.datawatchclient.transport.dto.TemplateDto

@Composable
internal fun CreateEditTemplateSheet(
    template: TemplateDto?,
    onDismiss: () -> Unit,
    onSave: (CreateTemplateRequestDto) -> Unit,
) {
    var title by remember { mutableStateOf(template?.title ?: "") }
    var spec by remember { mutableStateOf(template?.spec ?: "") }
    var type by remember { mutableStateOf(template?.type ?: "") }
    var tags by remember { mutableStateOf(template?.tags?.joinToString(", ") ?: "") }
    var description by remember { mutableStateOf(template?.description ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (template != null) R.string.tmpl_edit else R.string.tmpl_create)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text(stringResource(R.string.tmpl_title_label)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = spec, onValueChange = { spec = it },
                    label = { Text(stringResource(R.string.tmpl_spec_label)) },
                    minLines = 3, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = type, onValueChange = { type = it },
                    label = { Text(stringResource(R.string.tmpl_type_label)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = tags, onValueChange = { tags = it },
                    label = { Text(stringResource(R.string.tmpl_tags_label)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = description, onValueChange = { description = it },
                    label = { Text(stringResource(R.string.tmpl_description_label)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsedTags = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    onSave(
                        CreateTemplateRequestDto(
                            title = title.trim(),
                            spec = spec.trim(),
                            type = type.trim().ifBlank { null },
                            tags = parsedTags,
                            description = description.trim().ifBlank { null },
                        ),
                    )
                },
                enabled = title.isNotBlank() && spec.isNotBlank(),
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
