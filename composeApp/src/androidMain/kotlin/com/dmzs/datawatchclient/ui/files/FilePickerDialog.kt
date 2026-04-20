package com.dmzs.datawatchclient.ui.files

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dmzs.datawatchclient.domain.FileEntry

/**
 * Server-side directory picker for the active datawatch profile.
 * - Breadcrumb at the top shows the current absolute path (server-resolved).
 * - `..` row appears when the current path isn't root.
 * - Tap a folder to descend; tap a file to select and return the file path.
 * - "Pick this folder" returns the current directory path.
 *
 * Callers get back either a directory path (Pick this folder) or a file
 * path (tap a file). `onPicked(null)` signals Cancel.
 *
 * Reused from [com.dmzs.datawatchclient.ui.sessions.NewSessionScreen]'s
 * working-dir field and from the schedule-reply flow.
 */
@Composable
public fun FilePickerDialog(
    onPicked: (String?) -> Unit,
    pickerMode: PickerMode = PickerMode.FolderOrFile,
    vm: FilePickerViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()

    AlertDialog(
        onDismissRequest = { onPicked(null) },
        title = {
            Column {
                Text("Browse server", style = MaterialTheme.typography.titleMedium)
                Text(
                    state.path ?: "…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                state.banner?.let { banner ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    ) {
                        Text(
                            banner,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                if (state.loading) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 240.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                    }
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        if (state.path != null && state.path != "/" && state.path!!.isNotBlank()) {
                            item {
                                ParentRow(onClick = vm::goUp)
                                HorizontalDivider()
                            }
                        }
                        items(state.entries, key = { it.path }) { entry ->
                            FileRow(
                                entry = entry,
                                selectable =
                                    pickerMode != PickerMode.FolderOnly ||
                                        entry.isDirectory,
                                onClick = {
                                    if (entry.isDirectory) {
                                        vm.browse(entry.path)
                                    } else if (pickerMode != PickerMode.FolderOnly) {
                                        onPicked(entry.path)
                                    }
                                },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (pickerMode != PickerMode.FileOnly) {
                TextButton(
                    onClick = { onPicked(state.path) },
                    enabled = !state.loading && state.path != null,
                ) { Text("Pick this folder") }
            }
        },
        dismissButton = {
            TextButton(onClick = { onPicked(null) }) { Text("Cancel") }
        },
    )
}

/** Controls whether files, folders, or both are selectable. */
public enum class PickerMode { FolderOnly, FileOnly, FolderOrFile }

@Composable
private fun ParentRow(onClick: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Filled.ArrowUpward, contentDescription = null)
        Text("..", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun FileRow(
    entry: FileEntry,
    selectable: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = selectable, onClick = onClick)
                .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            if (entry.isDirectory) {
                Icons.Filled.Folder
            } else {
                Icons.AutoMirrored.Filled.InsertDriveFile
            },
            contentDescription = null,
            tint =
                if (selectable) {
                    if (entry.isDirectory) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                },
        )
        Text(
            entry.name,
            style = MaterialTheme.typography.bodyMedium,
            color =
                if (selectable) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                },
        )
    }
}
