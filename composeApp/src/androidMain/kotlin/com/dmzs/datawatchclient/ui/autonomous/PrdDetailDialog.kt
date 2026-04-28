package com.dmzs.datawatchclient.ui.autonomous

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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

/**
 * PRD detail / review dialog.
 *
 * v0.38.1 closes #12 (story description rendering + ✎ edit button +
 * modal), #18 (Approve / Reject buttons when PRD is in needs_review),
 * partial #19 (file pills rendered + ✎ edit modal for the files
 * list — conflict-detection across stories deferred to v0.38.2).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PrdDetailDialog(
    prd: PrdDto,
    onDismiss: () -> Unit,
    onApprove: () -> Unit,
    onReject: (String) -> Unit,
    onEditStory: (storyId: String, newTitle: String?, newDescription: String?) -> Unit,
    onEditFiles: (storyId: String, files: List<String>) -> Unit,
) {
    val canEdit = prd.status == "needs_review" || prd.status == "revisions_asked"
    var rejectOpen by remember { mutableStateOf(false) }
    var rejectReason by remember { mutableStateOf("") }
    var editingStory: PrdStoryDto? by remember { mutableStateOf(null) }
    var editingFilesFor: PrdStoryDto? by remember { mutableStateOf(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(prd.title?.takeIf { it.isNotBlank() } ?: prd.name) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "${prd.status.replace('_', ' ')} · ${prd.stories.size} stories",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(8.dp))
                if (prd.stories.isEmpty()) {
                    Text(
                        "No stories yet (still decomposing?).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    prd.stories.forEach { story ->
                        StoryRow(
                            story = story,
                            canEdit = canEdit,
                            onEdit = { editingStory = story },
                            onEditFiles = { editingFilesFor = story },
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (prd.status == "needs_review" || prd.status == "revisions_asked") {
                Row {
                    TextButton(onClick = { rejectOpen = true }) {
                        Text("Reject", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = onApprove) { Text("Approve") }
                }
            } else {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
        dismissButton = {
            if (prd.status == "needs_review" || prd.status == "revisions_asked") {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )

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
                        }
                    },
                    enabled = rejectReason.isNotBlank(),
                ) {
                    Text("Reject", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { rejectOpen = false }) { Text("Cancel") }
            },
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
            onSave = { files ->
                onEditFiles(story.id, files)
                editingFilesFor = null
            },
        )
    }
}

@Composable
private fun StoryRow(
    story: PrdStoryDto,
    canEdit: Boolean,
    onEdit: () -> Unit,
    onEditFiles: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
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
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "Edit story",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        story.description?.takeIf { it.isNotBlank() }?.let { d ->
            Text(
                d,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (story.files.isNotEmpty() || story.filesTouched.isNotEmpty() || canEdit) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                story.files.forEach { f -> FilePill(f, color = Color(0xFF3B82F6)) }
                story.filesTouched.forEach { f -> FilePill(f, color = Color(0xFF22C55E)) }
                if (canEdit) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onEditFiles) {
                        Text("✎ files", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun StoryStatusPill(status: String) {
    val color =
        when (status.lowercase()) {
            "complete" -> Color(0xFF3B82F6)
            "in_progress" -> Color(0xFF22C55E)
            "awaiting_approval" -> Color(0xFFF59E0B)
            "rejected" -> Color(0xFFEF4444)
            else -> Color(0xFF94A3B8)
        }
    Box(
        modifier =
            Modifier
                .background(color.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
                .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(
            status.lowercase().replace('_', ' '),
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

@Composable
private fun FilePill(name: String, color: Color) {
    Box(
        modifier =
            Modifier
                .background(color.copy(alpha = 0.18f), RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(
            "📝 $name",
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
        )
    }
}

@Composable
private fun EditStoryDialog(
    story: PrdStoryDto,
    onDismiss: () -> Unit,
    onSave: (newTitle: String?, newDescription: String?) -> Unit,
) {
    var title by remember(story.id) { mutableStateOf(story.title) }
    var description by remember(story.id) { mutableStateOf(story.description.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit story") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    maxLines = 6,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val newTitle = title.takeIf { it != story.title }
                    val newDesc = description.takeIf { it != story.description.orEmpty() }
                    onSave(newTitle, newDesc)
                },
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun EditFilesDialog(
    story: PrdStoryDto,
    onDismiss: () -> Unit,
    onSave: (files: List<String>) -> Unit,
) {
    var text by remember(story.id) { mutableStateOf(story.files.joinToString("\n")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Files for ${story.title.ifBlank { story.id }}") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "One path per line, max 50.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Files") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    maxLines = 8,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val files =
                        text.lines()
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .take(50)
                    onSave(files)
                },
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
