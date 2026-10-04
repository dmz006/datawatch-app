package com.dmzs.datawatchclient.ui.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.domain.Alert
import com.dmzs.datawatchclient.domain.AlertSeverity
import com.dmzs.datawatchclient.ui.shell.AlertDockChannel
import com.dmzs.datawatchclient.ui.shell.DockEntry
import com.dmzs.datawatchclient.ui.shell.DockLevel

/**
 * Floating alert dock — appears top-right when 2+ active alerts exist,
 * matching PWA v7.0.0-alpha.29 consolidated dock behaviour (#271).
 *
 * - Collapsed: count pill + per-category badges + expand/dismiss/mute buttons.
 * - Expanded: scrolling list of last 100 alerts (timestamp + type + message).
 * - ✕ clears the dock's client-side entries and closes it (PWA `dismissAlertDock`).
 * - 🔕 mutes for the app session (PWA `muteAlertDock`, parity D47a).
 * - [entries] are the client-side messages that replaced Android toasts
 *   (parity D41a) plus live WS alert frames (D51a); [alerts] are server alerts.
 * - No expand/collapse animation — the PWA dock is static (parity D36b).
 */
@Composable
public fun AlertDockOverlay(
    alerts: List<Alert>,
    onDismiss: () -> Unit,
    onMute: () -> Unit,
    modifier: Modifier = Modifier,
    entries: List<DockEntry> = emptyList(),
) {
    var expanded by remember { mutableStateOf(entries.isNotEmpty()) }

    val errorCount =
        alerts.count { it.severity == AlertSeverity.Error } +
            entries.filter { it.level == DockLevel.Error }.sumOf { it.count }
    val waitingCount = alerts.count { it.type == "waiting_input" || it.type == "needs_input" }
    val total = alerts.size + AlertDockChannel.localCount(entries)

    Box(
        modifier =
            modifier
                .wrapContentWidth(Alignment.End)
                .padding(end = 8.dp, top = 8.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.97f),
                    shape = RoundedCornerShape(10.dp),
                ),
    ) {
        Column(modifier = Modifier.widthIn(min = 220.dp, max = 420.dp)) {
            // Header row: pill + categories + chevron + dismiss + mute
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Count pill
                Box(
                    modifier =
                        Modifier
                            .background(Color(0xFFD97706).copy(alpha = 0.20f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text =
                            if (total == 1) {
                                stringResource(R.string.alert_dock_one)
                            } else {
                                stringResource(R.string.alert_dock_many, total)
                            },
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 14.sp),
                        color = Color(0xFFD97706),
                    )
                }
                Spacer(Modifier.width(6.dp))
                // Per-category counters
                if (waitingCount > 0) {
                    CategoryPill("needs-input ×$waitingCount", Color(0xFF8B5CF6))
                    Spacer(Modifier.width(4.dp))
                }
                if (errorCount > 0) {
                    CategoryPill("err ×$errorCount", Color(0xFFEF4444))
                    Spacer(Modifier.width(4.dp))
                }
                Spacer(Modifier.weight(1f))
                // Expand chevron
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Dismiss ✕ — hides dock header; re-appears on next batch
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.alert_dock_dismiss),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Mute 🔕 — suppresses for session
                IconButton(onClick = onMute, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.NotificationsOff,
                        contentDescription = stringResource(R.string.alert_dock_mute),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Expanded: scrolling alert list (last 100)
            if (expanded) {
                LazyColumn(
                    modifier =
                        Modifier.fillMaxWidth().heightIn(
                            max = 280.dp,
                        ).padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(entries, key = { "e${it.id}" }) { entry ->
                        DockEntryRow(entry, onRemove = { AlertDockChannel.remove(entry.id) })
                    }
                    items(alerts.take(100), key = { "a${it.id}" }) { alert ->
                        DockAlertRow(alert)
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryPill(
    label: String,
    color: Color,
) {
    Box(
        modifier =
            Modifier
                .background(color.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp), color = color)
    }
}

@Composable
private fun DockEntryRow(
    entry: DockEntry,
    onRemove: () -> Unit,
) {
    val railColor =
        when (entry.level) {
            DockLevel.Error -> Color(0xFFEF4444)
            DockLevel.Warning -> Color(0xFFF59E0B)
            DockLevel.Success -> Color(0xFF10B981)
            DockLevel.Info -> MaterialTheme.colorScheme.secondary
        }
    val time =
        java.time.LocalTime
            .ofInstant(java.time.Instant.ofEpochMilli(entry.tsMs), java.time.ZoneId.systemDefault())
            .let { "%02d:%02d:%02d".format(it.hour, it.minute, it.second) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier =
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(railColor, RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (entry.count > 1) "$time  ×${entry.count}" else time,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                entry.message,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 4,
            )
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.alert_dock_dismiss),
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DockAlertRow(alert: Alert) {
    val railColor =
        when (alert.severity) {
            AlertSeverity.Error -> Color(0xFFEF4444)
            AlertSeverity.Warning -> Color(0xFFF59E0B)
            else -> Color(0xFF10B981)
        }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Per-type color rail — left edge stripe (Sprint 27 alpha.33)
        Box(
            modifier =
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(railColor, androidx.compose.foundation.shape.RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(6.dp))
        Column {
            Text(
                alert.title.ifBlank { alert.type },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (alert.message.isNotBlank()) {
                Text(
                    alert.message,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
    }
}
