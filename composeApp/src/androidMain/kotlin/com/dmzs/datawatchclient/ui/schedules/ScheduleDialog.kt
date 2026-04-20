package com.dmzs.datawatchclient.ui.schedules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Create-schedule dialog. Task + cron text fields + enabled switch.
 *
 * Cron is a free-form string — the parent server validates it; the client
 * only does a non-blank check. Hint text shows common cron patterns so
 * users don't have to google them for routine cases.
 *
 * Reused from Settings → Schedules ("New schedule" FAB) and session-detail
 * overflow ("Schedule reply") — the latter pre-seeds the task from the
 * current session's prompt via [initialTask].
 */
@Composable
public fun ScheduleDialog(
    onConfirm: (task: String, cron: String, enabled: Boolean) -> Unit,
    onDismiss: () -> Unit,
    initialTask: String = "",
    initialCron: String = "0 9 * * *",
    title: String = "New schedule",
) {
    var task by remember { mutableStateOf(initialTask) }
    var cron by remember { mutableStateOf(initialCron) }
    var enabled by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    "Task",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                OutlinedTextField(
                    value = task,
                    onValueChange = { task = it },
                    placeholder = { Text("e.g. new: nightly deploy") },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 96.dp),
                    maxLines = 5,
                )
                Text(
                    "Cron",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp),
                )
                OutlinedTextField(
                    value = cron,
                    onValueChange = { cron = it },
                    singleLine = true,
                    placeholder = { Text("0 9 * * *") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    // Intentionally simple — the server is authoritative on
                    // syntax. Don't re-implement cron parsing client-side.
                    "Examples: `0 9 * * *` daily 9 AM · `*/15 * * * *` every 15 min · `0 */6 * * *` every 6 h",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Enabled", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(task.trim(), cron.trim(), enabled) },
                enabled = task.isNotBlank() && cron.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
