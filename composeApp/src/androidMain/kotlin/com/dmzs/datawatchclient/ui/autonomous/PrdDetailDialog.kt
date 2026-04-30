package com.dmzs.datawatchclient.ui.autonomous

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.transport.dto.PrdDto
import com.dmzs.datawatchclient.transport.dto.PrdStoryDto

private val EFFORT_OPTIONS = listOf("", "low", "medium", "high", "max", "quick", "normal", "thorough")

/**
 * PRD detail / review dialog — full CRUD parity with PWA v5.19.0+.
 *
 * Actions available per status (mirrors renderPRDActions in app.js):
 *   draft / revisions_asked  → Decompose, LLM, Edit, Delete
 *   needs_review / revisions_asked → Approve, Reject, Revise, LLM, Edit, Delete
 *   approved                 → Run, LLM, Edit, Delete
 *   running                  → Cancel
 *   completed/rejected/cancelled → Delete
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun PrdDetailDialog(
    prd: PrdDto,
    backends: List<String> = emptyList(),
    permissionModes: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onApprove: () -> Unit,
    onReject: (String) -> Unit,
    onDecompose: () -> Unit,
    onSetLlm: (backend: String, effort: String, model: String) -> Unit,
    onRun: () -> Unit,
    onCancel: () -> Unit,
    onRequestRevision: (note: String) -> Unit,
    onEditPrd: (title: String?, spec: String?, permissionMode: String?) -> Unit,
    onDelete: () -> Unit,
    onEditStory: (storyId: String, newTitle: String?, newDescription: String?) -> Unit,
    onEditFiles: (storyId: String, files: List<String>) -> Unit,
) {
    val status = prd.status
    val canReview = status == "needs_review" || status == "revisions_asked"
    val canEdit = status != "running"

    var rejectOpen by remember { mutableStateOf(false) }
    var rejectReason by remember { mutableStateOf("") }
    var reviseOpen by remember { mutableStateOf(false) }
    var reviseNote by remember { mutableStateOf("") }
    var llmOpen by remember { mutableStateOf(false) }
    var editPrdOpen by remember { mutableStateOf(false) }
    var prdPermissionModeOptions by remember { mutableStateOf<List<String>>(emptyList()) }
    var deleteConfirmOpen by remember { mutableStateOf(false) }
    var editingStory: PrdStoryDto? by remember { mutableStateOf(null) }
    var editingFilesFor: PrdStoryDto? by remember { mutableStateOf(null) }
    var graphOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(prd.title?.takeIf { it.isNotBlank() } ?: prd.name) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${status.replace('_', ' ')} · ${prd.stories.size} stories",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { graphOpen = true }) {
                        Text("📊 Graph", style = MaterialTheme.typography.labelSmall)
                    }
                }

                // Action row — PWA renderPRDActions parity
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (status == "draft" || status == "revisions_asked") {
                        TextButton(onClick = { onDecompose(); onDismiss() }) {
                            Text("Decompose", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (canReview) {
                        TextButton(onClick = { onApprove(); onDismiss() }) {
                            Text("Approve", color = Color(0xFF10B981), style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = { rejectOpen = true }) {
                            Text("Reject", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = { reviseOpen = true }) {
                            Text("Revise", color = Color(0xFFF59E0B), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (status == "approved") {
                        TextButton(onClick = { onRun(); onDismiss() }) {
                            Text("Run", color = Color(0xFF3B82F6), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (status == "running") {
                        TextButton(onClick = { onCancel(); onDismiss() }) {
                            Text("Cancel", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (status != "running" && status != "completed") {
                        TextButton(onClick = { llmOpen = true }) {
                            Text("LLM", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (canEdit) {
                        TextButton(onClick = { editPrdOpen = true }) {
                            Text("Edit", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    TextButton(onClick = { deleteConfirmOpen = true }) {
                        Text("Delete", color = Color(0xFF7C2D12), style = MaterialTheme.typography.labelSmall)
                    }
                }

                Spacer(Modifier.size(4.dp))

                if (prd.stories.isEmpty()) {
                    Text(
                        "No stories yet (still decomposing?).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val conflicts = buildMap<String, List<String>> {
                        val byPath = mutableMapOf<String, MutableList<String>>()
                        prd.stories
                            .filter {
                                it.status.lowercase() != "complete" &&
                                    it.status.lowercase() != "rejected"
                            }
                            .forEach { story ->
                                story.files.forEach { f ->
                                    byPath.getOrPut(f) { mutableListOf() }.add(story.id)
                                }
                            }
                        byPath.filter { it.value.size > 1 }.forEach { (path, ids) ->
                            put(path, ids)
                        }
                    }
                    prd.stories.forEach { story ->
                        StoryRow(
                            story = story,
                            canEdit = canEdit,
                            onEdit = { editingStory = story },
                            onEditFiles = { editingFilesFor = story },
                            conflicts = conflicts,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = null,
    )

    // ── Sub-dialogs ───────────────────────────────────────────────────────

    if (rejectOpen) {
        AlertDialog(
            onDismissRequest = { rejectOpen = false },
            title = { Text("Reject PRD?") },
            text = {
                OutlinedTextField(
                    value = rejectReason,
                    onValueChange = { rejectReason = it },
                    label = { Text("Reason (required)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (rejectReason.isNotBlank()) {
                            onReject(rejectReason.trim())
                            rejectOpen = false
                            onDismiss()
                        }
                    },
                    enabled = rejectReason.isNotBlank(),
                ) { Text("Reject", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { rejectOpen = false }) { Text("Cancel") } },
        )
    }

    if (reviseOpen) {
        AlertDialog(
            onDismissRequest = { reviseOpen = false },
            title = { Text("Request revisions") },
            text = {
                OutlinedTextField(
                    value = reviseNote,
                    onValueChange = { reviseNote = it },
                    label = { Text("What needs revision?") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (reviseNote.isNotBlank()) {
                            onRequestRevision(reviseNote.trim())
                            reviseOpen = false
                            onDismiss()
                        }
                    },
                    enabled = reviseNote.isNotBlank(),
                ) { Text("Send") }
            },
            dismissButton = { TextButton(onClick = { reviseOpen = false }) { Text("Cancel") } },
        )
    }

    if (llmOpen) {
        LlmOverrideDialog(
            currentBackend = prd.backend.orEmpty(),
            currentEffort = prd.effort.orEmpty(),
            currentModel = prd.model.orEmpty(),
            backends = backends,
            onDismiss = { llmOpen = false },
            onSave = { b, e, m -> onSetLlm(b, e, m); llmOpen = false },
        )
    }

    if (editPrdOpen) {
        EditPrdDialog(
            currentTitle = prd.title.orEmpty(),
            currentSpec = prd.spec.orEmpty(),
            currentPermissionMode = prd.permissionMode.orEmpty(),
            permissionModes = permissionModes,
            onDismiss = { editPrdOpen = false },
            onSave = { title, spec, pm -> onEditPrd(title, spec, pm); editPrdOpen = false },
        )
    }

    if (deleteConfirmOpen) {
        AlertDialog(
            onDismissRequest = { deleteConfirmOpen = false },
            title = { Text("Delete PRD?") },
            text = {
                Text(
                    "Permanently removes \"${prd.title ?: prd.name}\" and all child PRDs. This cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { onDelete(); deleteConfirmOpen = false; onDismiss() },
                ) { Text("Delete", color = Color(0xFF7C2D12)) }
            },
            dismissButton = { TextButton(onClick = { deleteConfirmOpen = false }) { Text("Cancel") } },
        )
    }

    editingStory?.let { story ->
        EditStoryDialog(
            story = story,
            onDismiss = { editingStory = null },
            onSave = { newTitle, newDescription ->
                onEditStory(story.id, newTitle, newDescription)
                editingStory = null
            },
        )
    }

    editingFilesFor?.let { story ->
        EditFilesDialog(
            story = story,
            onDismiss = { editingFilesFor = null },
            onSave = { files -> onEditFiles(story.id, files); editingFilesFor = null },
        )
    }

    if (graphOpen) {
        com.dmzs.datawatchclient.ui.orchestrator.OrchestratorGraphDialog(
            graphId = prd.id,
            onDismiss = { graphOpen = false },
        )
    }
}

// ── LLM override sub-dialog ───────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LlmOverrideDialog(
    currentBackend: String,
    currentEffort: String,
    currentModel: String,
    backends: List<String>,
    onDismiss: () -> Unit,
    onSave: (backend: String, effort: String, model: String) -> Unit,
) {
    var backend by remember { mutableStateOf(currentBackend) }
    var effort by remember { mutableStateOf(currentEffort) }
    var model by remember { mutableStateOf(currentModel) }
    var backendMenuOpen by remember { mutableStateOf(false) }
    var effortMenuOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set LLM override") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = backend.ifEmpty { "(inherit)" },
                        onValueChange = {},
                        label = { Text("Backend") },
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            TextButton(onClick = { backendMenuOpen = !backendMenuOpen }) { Text("▾") }
                        },
                    )
                    DropdownMenu(expanded = backendMenuOpen, onDismissRequest = { backendMenuOpen = false }) {
                        DropdownMenuItem(text = { Text("(inherit)") }, onClick = { backend = ""; backendMenuOpen = false })
                        backends.forEach { b ->
                            DropdownMenuItem(text = { Text(b) }, onClick = { backend = b; backendMenuOpen = false })
                        }
                    }
                }
                Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = effort.ifEmpty { "(inherit)" },
                        onValueChange = {},
                        label = { Text("Effort") },
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            TextButton(onClick = { effortMenuOpen = !effortMenuOpen }) { Text("▾") }
                        },
                    )
                    DropdownMenu(expanded = effortMenuOpen, onDismissRequest = { effortMenuOpen = false }) {
                        EFFORT_OPTIONS.forEach { e ->
                            DropdownMenuItem(
                                text = { Text(if (e.isEmpty()) "(inherit)" else e) },
                                onClick = { effort = e; effortMenuOpen = false },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Model (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(backend, effort, model) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ── Edit PRD title/spec sub-dialog ────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditPrdDialog(
    currentTitle: String,
    currentSpec: String = "",
    currentPermissionMode: String = "",
    permissionModes: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (title: String?, spec: String?, permissionMode: String?) -> Unit,
) {
    var title by remember { mutableStateOf(currentTitle) }
    var spec by remember { mutableStateOf(currentSpec) }
    var permissionMode by remember { mutableStateOf(currentPermissionMode) }
    var pmMenuOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit PRD") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("Title") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = spec, onValueChange = { spec = it },
                    label = { Text("Spec") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    maxLines = 8,
                )
                if (permissionModes.isNotEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        OutlinedTextField(
                            value = permissionMode.ifEmpty { "(inherit)" },
                            onValueChange = {},
                            label = { Text("Permission mode") },
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                TextButton(onClick = { pmMenuOpen = !pmMenuOpen }) { Text("▾") }
                            },
                        )
                        DropdownMenu(expanded = pmMenuOpen, onDismissRequest = { pmMenuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("(inherit)") },
                                onClick = { permissionMode = ""; pmMenuOpen = false },
                            )
                            permissionModes.forEach { pm ->
                                DropdownMenuItem(
                                    text = { Text(pm) },
                                    onClick = { permissionMode = pm; pmMenuOpen = false },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val newTitle = title.takeIf { it != currentTitle && it.isNotBlank() }
                val newSpec = spec.takeIf { it.isNotBlank() }
                val newPm = permissionMode.takeIf { it != currentPermissionMode }
                onSave(newTitle, newSpec, newPm)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ── Story row + sub-dialogs ───────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StoryRow(
    story: PrdStoryDto,
    canEdit: Boolean,
    onEdit: () -> Unit,
    onEditFiles: () -> Unit,
    conflicts: Map<String, List<String>> = emptyMap(),
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                story.title.ifBlank { story.id },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(end = 4.dp),
            )
            Spacer(Modifier.weight(1f))
            StoryStatusPill(story.status)
            if (canEdit) {
                IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit story", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        story.description?.takeIf { it.isNotBlank() }?.let { d ->
            Text(d, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        }
        if (story.files.isNotEmpty() || story.filesTouched.isNotEmpty() || canEdit) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                story.files.forEach { f ->
                    val others = conflicts[f]?.filter { it != story.id }.orEmpty()
                    FilePill(name = f, color = Color(0xFF3B82F6), conflict = others.isNotEmpty(), conflictNote = if (others.isNotEmpty()) "also in ${others.joinToString(", ")}" else null)
                }
                story.filesTouched.forEach { f -> FilePill(f, color = Color(0xFF22C55E)) }
                if (canEdit) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onEditFiles) { Text("✎ files", style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
    }
}

@Composable
private fun StoryStatusPill(status: String) {
    val color = when (status.lowercase()) {
        "complete" -> Color(0xFF3B82F6)
        "in_progress" -> Color(0xFF22C55E)
        "awaiting_approval" -> Color(0xFFF59E0B)
        "rejected" -> Color(0xFFEF4444)
        else -> Color(0xFF94A3B8)
    }
    Box(modifier = Modifier.background(color.copy(alpha = 0.18f), RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 1.dp)) {
        Text(status.lowercase().replace('_', ' '), style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun FilePill(name: String, color: Color, conflict: Boolean = false, conflictNote: String? = null) {
    val pillColor = if (conflict) Color(0xFFEF4444) else color
    Column {
        Box(modifier = Modifier.background(pillColor.copy(alpha = 0.18f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 1.dp)) {
            Text(if (conflict) "⚠ 📝 $name" else "📝 $name", style = MaterialTheme.typography.labelSmall, color = pillColor, maxLines = 1)
        }
        conflictNote?.let { note -> Text(note, style = MaterialTheme.typography.labelSmall, color = Color(0xFFEF4444), maxLines = 1) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditStoryDialog(story: PrdStoryDto, onDismiss: () -> Unit, onSave: (newTitle: String?, newDescription: String?) -> Unit) {
    var title by remember(story.id) { mutableStateOf(story.title) }
    var description by remember(story.id) { mutableStateOf(story.description.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit story") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), maxLines = 6)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(title.takeIf { it != story.title }, description.takeIf { it != story.description.orEmpty() })
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditFilesDialog(story: PrdStoryDto, onDismiss: () -> Unit, onSave: (files: List<String>) -> Unit) {
    var text by remember(story.id) { mutableStateOf(story.files.joinToString("\n")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Files for ${story.title.ifBlank { story.id }}") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("One path per line, max 50.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Files") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), maxLines = 8)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(text.lines().map { it.trim() }.filter { it.isNotEmpty() }.take(50))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
