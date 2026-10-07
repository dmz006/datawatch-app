package com.dmzs.datawatchclient.ui.schedules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dmzs.datawatchclient.domain.Schedule
import com.dmzs.datawatchclient.ui.theme.LocalDatawatchColors
import com.dmzs.datawatchclient.ui.theme.PwaCard
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.dmzs.datawatchclient.R
import androidx.compose.material.icons.filled.Edit

private const val SCHEDULES_PAGE_SIZE = 10

/**
 * Settings → Schedules card. Lists `/api/schedule` entries for the active
 * server profile. Each row shows task + cron + enabled state with a delete
 * icon; a "+ Add" action opens [ScheduleDialog] to create a new schedule.
 *
 * v0.12 keeps this read + create + delete; toggling enabled without a full
 * edit dialog is a v0.13 follow-up (parent exposes the shape as
 * `{enabled: Boolean}` on /api/schedule but mobile doesn't expose a UI for
 * mid-row toggling yet to keep the surface minimal).
 */
@Composable
public fun SchedulesCard(vm: SchedulesViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var addOpen by remember { mutableStateOf(false) }

    // v0.33.13 (B16): title matches PWA "Scheduled Events".
    // Loaded one-shot (D54b) and on active-profile change.
    PwaCard(
        id = "schedules",
        title = "Scheduled Events",
        headerActions = {
            if (state.refreshing) {
                androidx.compose.material3.CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.padding(horizontal = 8.dp).size(16.dp),
                )
            }
            IconButton(onClick = { addOpen = true }, enabled = state.supported) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "New schedule",
                    tint =
                        if (state.supported) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        },
    ) {
        SchedulesCardBody(state = state, vm = vm, addOpen = addOpen, setAddOpen = { addOpen = it })
    }
}

@Composable
private fun SchedulesCardBody(
    state: SchedulesViewModel.UiState,
    vm: SchedulesViewModel,
    addOpen: Boolean,
    setAddOpen: (Boolean) -> Unit,
) {
    state.banner?.let { banner ->
        Surface(color = MaterialTheme.colorScheme.errorContainer) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    banner,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = vm::dismissBanner) { Text("Dismiss") }
            }
        }
    }

    if (state.schedules.isEmpty() && state.supported) {
        Text(
            "No schedules yet — tap + above to create one.",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        // v0.33.13 (B17): paginate 10 per page. PWA's Scheduled
        // Events does the same — render-all was eating the entire
        // Settings scroll on servers with many entries.
        var pageState by remember(state.schedules.size) { mutableStateOf(0) }
        // PWA select-all checkbox + "Delete selected" (with confirm).
        var selected by remember(state.schedules) { mutableStateOf<Set<String>>(emptySet()) }
        var confirmBulk by remember { mutableStateOf(false) }
        var editTarget by remember { mutableStateOf<Schedule?>(null) }
        var editCommand by remember { mutableStateOf<String?>(null) }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val allIds = state.schedules.map { it.id }.toSet()
            androidx.compose.material3.Checkbox(
                checked = allIds.isNotEmpty() && selected.containsAll(allIds),
                onCheckedChange = { on -> selected = if (on) allIds else emptySet() },
            )
            Text(
                stringResource(R.string.schedules_select_all),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { confirmBulk = true }, enabled = selected.isNotEmpty()) {
                Text(stringResource(R.string.schedules_delete_selected), color = MaterialTheme.colorScheme.error)
            }
        }
        if (confirmBulk) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { confirmBulk = false },
                text = { Text(stringResource(R.string.schedules_delete_selected_confirm, selected.size)) },
                confirmButton = {
                    TextButton(onClick = {
                        vm.deleteMany(selected)
                        selected = emptySet()
                        confirmBulk = false
                    }) { Text(stringResource(R.string.action_yes)) }
                },
                dismissButton = { TextButton(onClick = { confirmBulk = false }) { Text(stringResource(R.string.action_no)) } },
            )
        }
        // Parity D56b — the PWA edits a schedule with two prompt()s:
        // "Edit command:" then "New time (ISO, or empty to keep):".
        editTarget?.let { target ->
            val cmd = editCommand
            if (cmd == null) {
                SchedulePromptDialog(
                    message = stringResource(R.string.schedules_edit_command_prompt),
                    initial = target.task,
                    onCancel = { editTarget = null },
                    onOk = { editCommand = it },
                )
            } else {
                SchedulePromptDialog(
                    message = stringResource(R.string.schedules_edit_time_prompt),
                    initial = target.runAt?.toString().orEmpty(),
                    onCancel = {
                        editTarget = null
                        editCommand = null
                    },
                    onOk = { time ->
                        vm.update(target.id, target.task, cmd, time)
                        editTarget = null
                        editCommand = null
                    },
                )
            }
        }
        val pageSize = SCHEDULES_PAGE_SIZE
        val total = state.schedules.size
        val lastPage = ((total - 1).coerceAtLeast(0)) / pageSize
        val page = pageState.coerceIn(0, lastPage)
        val slice = state.schedules.drop(page * pageSize).take(pageSize)
        slice.forEachIndexed { idx, schedule ->
            if (idx > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ScheduleRow(
                schedule = schedule,
                onDelete = { vm.delete(schedule.id) },
                checked = schedule.id in selected,
                onCheckedChange = { on -> selected = if (on) selected + schedule.id else selected - schedule.id },
                onEdit = {
                    editCommand = null
                    editTarget = schedule
                },
            )
        }
        if (total > pageSize) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { pageState = (page - 1).coerceAtLeast(0) }, enabled = page > 0) {
                    Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "Previous page")
                }
                Text(
                    "Page ${page + 1} of ${lastPage + 1}  ·  $total total",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(
                    onClick = { pageState = (page + 1).coerceAtMost(lastPage) },
                    enabled = page < lastPage,
                ) {
                    Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "Next page")
                }
            }
        }
    }

    if (addOpen) {
        ScheduleDialog(
            onConfirm = { task, cron, enabled ->
                vm.create(task, cron, enabled)
                setAddOpen(false)
            },
            onDismiss = { setAddOpen(false) },
        )
    }
}

private fun isDeferredSession(schedule: Schedule): Boolean =
    schedule.type == "new_session" && !schedule.deferredSessionName.isNullOrBlank()

/**
 * PWA loadSchedulesList label: `NEW: <name>` for deferred-session launches,
 * else `<session_name or session_id> [<schedule_name>]: <command>`.
 */
internal fun scheduleRowLabel(schedule: Schedule): String {
    if (isDeferredSession(schedule)) return "NEW: ${schedule.deferredSessionName}"
    val ref = (schedule.sessionName ?: schedule.sessionId)?.takeIf { it.isNotBlank() }
    val schedRef = schedule.scheduleName?.takeIf { it.isNotBlank() }?.let { " [$it]" }.orEmpty()
    return if (ref != null) "$ref$schedRef: ${schedule.task}" else schedule.task
}

@Composable
private fun ScheduleRow(
    schedule: Schedule,
    onDelete: () -> Unit,
    checked: Boolean = false,
    onCheckedChange: (Boolean) -> Unit = {},
    onEdit: () -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    scheduleRowLabel(schedule),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                // PWA cron badge: shown when the server sent `cron_expr` (not for NEW: rows).
                if (!schedule.cron.isNullOrBlank() && !isDeferredSession(schedule)) {
                    Surface(
                        color = LocalDatawatchColors.current.bg2,
                        shape = RoundedCornerShape(2.dp),
                    ) {
                        Text(
                            "cron",
                            fontSize = 9.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    schedule.cron ?: schedule.runAt?.toString() ?: "on input",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // PWA loadSchedulesList: the row shows the schedule `state`
                // as 10px bold uppercase text — pending = warning,
                // done = success, anything else text2. Older servers
                // without `state` fall back to enabled/disabled.
                val dw = LocalDatawatchColors.current
                val stateText = schedule.state?.takeIf { it.isNotBlank() }
                    ?: if (schedule.enabled) "enabled" else "disabled"
                Text(
                    stateText.uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color =
                        when (stateText) {
                            "pending" -> dw.warning
                            "done" -> dw.success
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        }
        IconButton(onClick = onEdit) {
            Icon(
                androidx.compose.material.icons.Icons.Filled.Edit,
                contentDescription = stringResource(R.string.schedules_edit),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Delete schedule",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** A browser-`prompt()` equivalent: message, one prefilled field, Cancel / OK. */
@Composable
private fun SchedulePromptDialog(
    message: String,
    initial: String,
    onCancel: () -> Unit,
    onOk: (String) -> Unit,
) {
    var text by remember(message) { mutableStateOf(initial) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onCancel,
        text = {
            Column {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                androidx.compose.material3.OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onOk(text) }) { Text(stringResource(R.string.action_ok)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) } },
    )
}
