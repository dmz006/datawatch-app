package com.dmzs.datawatchclient.ui.autonomous

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.transport.sse.DecomposeLiveState
import com.dmzs.datawatchclient.ui.theme.LocalDatawatchColors

/**
 * PWA `.prd-story-profile-pill` / `.prd-task-llm-badge` / `.prd-task-spawn-badge`:
 * small bordered pill used for the story prof/LLM pills and task LLM / ↳ spawn badges.
 */
@Composable
internal fun PrdMiniPill(
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Box(
        modifier =
            Modifier
                .padding(start = 4.dp)
                .background(color.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
                .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerField(
    label: String,
    value: String,
    options: List<String>,
    inheritLabel: String,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }, modifier = modifier) {
        OutlinedTextField(
            value = value.ifEmpty { inheritLabel },
            onValueChange = {},
            label = { Text(label) },
            readOnly = true,
            singleLine = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(inheritLabel) }, onClick = {
                onPick("")
                open = false
            })
            options.filter { it.isNotEmpty() }.forEach { o ->
                DropdownMenuItem(text = { Text(o) }, onClick = {
                    onPick(o)
                    open = false
                })
            }
        }
    }
}

/**
 * PWA openPRDSetStoryLLMModal (🤖) and openPRDEditTaskModal (✎ spec + LLM).
 * [spec] non-null → the task variant with the spec editor on top.
 * [onSave] gets the new spec (null = unchanged / story) and the LLM triple
 * (null = unchanged).
 */
@Composable
internal fun PrdItemLlmDialog(
    title: String,
    hint: String,
    spec: String?,
    currentBackend: String,
    currentEffort: String,
    currentModel: String,
    backends: List<String>,
    modelsFor: (String) -> List<String>,
    onDismiss: () -> Unit,
    onSave: (newSpec: String?, llm: Triple<String, String, String>?) -> Unit,
) {
    var specText by remember { mutableStateOf(spec.orEmpty()) }
    var backend by remember { mutableStateOf(currentBackend) }
    var effort by remember { mutableStateOf(currentEffort) }
    var model by remember { mutableStateOf(currentModel) }
    val inherit = stringResource(R.string.new_prd_inherit)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (spec != null) {
                    OutlinedTextField(
                        value = specText,
                        onValueChange = { specText = it },
                        label = { Text(stringResource(R.string.prd_detail_task_spec_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 8,
                    )
                }
                Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PickerField(
                    label = stringResource(R.string.prd_new_backend_label),
                    value = backend,
                    options = (backends + listOf(currentBackend)).distinct(),
                    inheritLabel = inherit,
                    onPick = {
                        if (it != backend) model = ""
                        backend = it
                    },
                )
                PickerField(
                    label = stringResource(R.string.prd_new_effort_label),
                    value = effort,
                    options = (EFFORT_OPTIONS + listOf(currentEffort)).distinct(),
                    inheritLabel = inherit,
                    onPick = { effort = it },
                )
                val models = modelsFor(backend)
                if (backend.isNotEmpty() && models.isNotEmpty()) {
                    PickerField(
                        label = stringResource(R.string.prd_new_model_label),
                        value = model,
                        options = (models + listOf(model)).distinct(),
                        inheritLabel = inherit,
                        onPick = { model = it },
                    )
                } else if (backend.isNotEmpty()) {
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it.trim() },
                        label = { Text(stringResource(R.string.prd_new_model_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val newSpec = if (spec != null && specText.isNotBlank() && specText != spec) specText else null
                val llmChanged = backend != currentBackend || effort != currentEffort || model != currentModel
                if (newSpec != null || llmChanged) onSave(newSpec, if (llmChanged) Triple(backend, effort, model) else null)
                onDismiss()
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** PWA openPRDSetStoryProfileModal (⚙): "(inherit automaton default)" + project profiles. */
@Composable
internal fun PrdStoryProfileDialog(
    storyId: String,
    current: String,
    profiles: List<String>,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var picked by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.prd_set_story_profile_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Story $storyId", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PickerField(
                    label = stringResource(R.string.prd_set_story_profile_title),
                    value = picked,
                    options = (profiles + listOf(current)).distinct(),
                    inheritLabel = stringResource(R.string.prd_story_profile_inherit),
                    onPick = { picked = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.prd_set_story_profile_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (picked != current) onSave(picked)
                onDismiss()
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** PWA openPRDInstantiateModal: `k=v,k=v` vars prompt → POST …/instantiate. */
@Composable
internal fun PrdInstantiateTemplateDialog(
    onDismiss: () -> Unit,
    onSubmit: (Map<String, String>) -> Unit,
) {
    var csv by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.prd_action_instantiate)) },
        text = {
            OutlinedTextField(
                value = csv,
                onValueChange = { csv = it },
                label = { Text(stringResource(R.string.prd_instantiate_vars_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onSubmit(parsePrdTemplateVars(csv))
                onDismiss()
            }) { Text(stringResource(R.string.prd_action_instantiate)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** PWA openPRDInstantiateModal parsing: split on `,`, first `=` separates key/value, blank keys dropped. */
internal fun parsePrdTemplateVars(csv: String): Map<String, String> =
    csv.split(',').mapNotNull { kv ->
        val i = kv.indexOf('=')
        if (i <= 0) return@mapNotNull null
        val k = kv.substring(0, i).trim()
        if (k.isEmpty()) null else k to kv.substring(i + 1).trim()
    }.toMap()

/**
 * PWA `#decompose-progress-<id>` box (accent left border): spinner + "Decomposing
 * Automaton… (done/total)" with each story title as it streams in, then
 * ✓ "N stories generated" / ✗ "Decompose failed: …".
 */
@Composable
internal fun DecomposeLiveCard(state: DecomposeLiveState) {
    val dw = LocalDatawatchColors.current
    val edge =
        when {
            state.error != null -> MaterialTheme.colorScheme.error
            state.finished -> dw.success
            else -> Color(0xFF6366F1)
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .background(Color(0xFF6366F1).copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                .drawBehind {
                    drawRect(color = edge, topLeft = Offset.Zero, size = Size(3.dp.toPx(), size.height))
                },
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            when {
                state.error != null ->
                    Text(
                        "✗ " + stringResource(R.string.decompose_error) + ": " + state.error,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                state.finished ->
                    Text(
                        "✓ " + stringResource(R.string.decompose_story_count, state.storyCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = dw.success,
                    )
                else ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                        Text(
                            stringResource(R.string.decompose_in_progress) +
                                if (state.total > 0) " (${state.done}/${state.total})" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
            }
            if (state.isActive) {
                state.stories.forEach { s ->
                    Text(
                        "+ " + s.title.ifBlank { "Story ${s.index + 1}" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 2.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
