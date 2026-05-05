package com.dmzs.datawatchclient.ui.autonomous

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.toMutableStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.transport.dto.InstantiateTemplateRequestDto
import com.dmzs.datawatchclient.transport.dto.TemplateDto

private val VAR_REGEX = Regex("""\{\{(\w+)\}\}""")

@Composable
internal fun InstantiateTemplateDialog(
    template: TemplateDto,
    onDismiss: () -> Unit,
    onInstantiate: (InstantiateTemplateRequestDto) -> Unit,
) {
    val varNames = remember(template.spec) {
        VAR_REGEX.findAll(template.spec).map { it.groupValues[1] }.toSortedSet().toList()
    }
    val varValues: SnapshotStateMap<String, String> = remember(varNames) {
        varNames.associateWith { "" }.entries.map { it.toPair() }.toMutableStateMap()
    }
    var projectDir by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${stringResource(R.string.tmpl_instantiate)}: ${template.title}") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = projectDir, onValueChange = { projectDir = it },
                    label = { Text(stringResource(R.string.new_prd_project_dir_label)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                if (varNames.isNotEmpty()) {
                    Text(
                        stringResource(R.string.tmpl_vars_title),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    )
                    varNames.forEach { varName ->
                        OutlinedTextField(
                            value = varValues[varName] ?: "",
                            onValueChange = { varValues[varName] = it },
                            label = { Text(varName) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onInstantiate(
                        InstantiateTemplateRequestDto(
                            projectDir = projectDir.trim().ifBlank { null },
                            vars = varValues.toMap(),
                        ),
                    )
                },
            ) { Text(stringResource(R.string.tmpl_instantiate)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
