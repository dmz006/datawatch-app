package com.dmzs.datawatchclient.ui.alerts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.domain.Alert
import com.dmzs.datawatchclient.domain.AlertSeverity

/**
 * Floating alert dock — appears top-right when 2+ active alerts exist,
 * matching PWA v7.0.0-alpha.29 consolidated dock behaviour (#271).
 *
 * - Collapsed: count pill + per-category badges + expand/dismiss/mute buttons.
 * - Expanded: scrolling list of last 100 alerts (timestamp + type + message).
 * - ✕ dismisses the header (dock re-appears on next alert batch above threshold).
 * - 🔕 mutes; caller's [onMute] callback suppresses future dock renders for the session.
 *
 * Single alert: caller should NOT render this; pass through a slim toast instead.
 */
@Composable
public fun AlertDockOverlay(
    alerts: List<Alert>,
    onDismiss: () -> Unit,
    onMute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val chevronAngle by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")

    val errorCount = alerts.count { it.severity == AlertSeverity.Error }
    val waitingCount = alerts.count { it.type == "waiting_input" || it.type == "needs_input" }
    val total = alerts.size

    Box(
        modifier = modifier
            .wrapContentWidth(Alignment.End)
            .padding(end = 8.dp, top = 8.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.97f),
                shape = RoundedCornerShape(10.dp),
            ),
    ) {
        Column(modifier = Modifier.widthIn(min = 220.dp, max = 340.dp)) {
            // Header row: pill + categories + chevron + dismiss + mute
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Count pill
                Box(
                    modifier = Modifier
                        .background(Color(0xFFD97706).copy(alpha = 0.20f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = if (total == 1) stringResource(R.string.alert_dock_one)
                               else stringResource(R.string.alert_dock_many, total),
                        style = MaterialTheme.typography.labelSmall,
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
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.rotate(chevronAngle).size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Dismiss ✕ — hides dock header; re-appears on next batch
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.alert_dock_dismiss), modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // Mute 🔕 — suppresses for session
                IconButton(onClick = onMute, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.NotificationsOff, contentDescription = stringResource(R.string.alert_dock_mute), modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Expanded: scrolling alert list (last 100)
            AnimatedVisibility(visible = expanded) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp).padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(alerts.take(100)) { alert ->
                        DockAlertRow(alert)
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryPill(label: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun DockAlertRow(alert: Alert) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        val (dotColor) = when (alert.severity) {
            AlertSeverity.Error -> Pair(Color(0xFFEF4444), Unit)
            AlertSeverity.Warning -> Pair(Color(0xFFF59E0B), Unit)
            else -> Pair(Color(0xFF22C55E), Unit)
        }
        Box(
            modifier = Modifier.size(6.dp).padding(top = 5.dp)
                .background(dotColor, androidx.compose.foundation.shape.CircleShape),
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
