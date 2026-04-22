package com.dmzs.datawatchclient.ui.schedules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard

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

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .pwaCard(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // v0.33.13 (B16): title matches PWA "Scheduled Events".
                // Explicit Refresh button dropped — VM polls every 15 s
                // and re-fetches on active-profile change.
                PwaSectionTitle("Scheduled Events", modifier = Modifier.weight(1f))
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
            }
            SchedulesCardBody(state = state, vm = vm, addOpen = addOpen, setAddOpen = { addOpen = it })
        }
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

@Composable
private fun ScheduleRow(
    schedule: Schedule,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                schedule.task,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
            )
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
                AssistChip(
                    onClick = {},
                    label = {
                        Text(if (schedule.enabled) "enabled" else "disabled")
                    },
                    colors =
                        AssistChipDefaults.assistChipColors(
                            labelColor =
                                if (schedule.enabled) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        ),
                )
            }
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
