package com.dmzs.datawatchclient.ui.common

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.domain.ServerProfile

/**
 * Docs / help link — HelpOutline icon that opens [url] in the system browser.
 * Placed leftmost in the TopAppBar actions block (appears left of filter, alerts, status).
 * Uses explicit Intent(ACTION_VIEW) so it works on all Android devices regardless of UriHandler config.
 */
@Composable
internal fun DocsLinkAction(url: String) {
    val ctx = LocalContext.current
    IconButton(onClick = {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }) {
        Icon(
            Icons.Filled.HelpOutline,
            contentDescription = stringResource(R.string.sessions_help_link),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Alerts bell with badge count. Shows [alertsBadge] as a red badge when > 0,
 * a dimmed badge when 0. Bell icon dims when muted.
 * Placed second-from-right in the TopAppBar actions block.
 */
@Composable
internal fun AlertsBellAction(alertsBadge: Int, alertsMuted: Boolean = false) {
    BadgedBox(
        badge = {
            if (alertsBadge > 0 || alertsMuted) {
                Badge(
                    containerColor = if (alertsMuted || alertsBadge == 0) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                ) {
                    val label = when {
                        alertsMuted -> "🔕"
                        alertsBadge > 0 -> alertsBadge.toString()
                        else -> ""
                    }
                    if (label.isNotEmpty()) {
                        Text(label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        },
        modifier = Modifier.padding(end = 4.dp),
    ) {
        Icon(
            if (alertsBadge > 0 && !alertsMuted) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsNone,
            contentDescription = stringResource(R.string.nav_alerts),
            tint = if (alertsMuted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                   else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Animated reachability dot shown at the far right of TopAppBar actions.
 * Green = reachable, red = unreachable, amber (pulsing) = probing.
 * Tap opens a bottom sheet with last-probe time and a retry button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReachabilityDot(
    reachable: Boolean?,
    lastProbeEpochMs: Long?,
    onRetry: () -> Unit,
) {
    var sheetOpen by remember { mutableStateOf(false) }
    val color = when (reachable) {
        true -> Color(0xFF22C55E)
        false -> Color(0xFFEF4444)
        null -> Color(0xFFF59E0B)
    }
    val description = when (reachable) {
        true -> stringResource(R.string.sessions_server_online)
        false -> stringResource(R.string.sessions_server_unreachable)
        null -> stringResource(R.string.sessions_probing)
    }
    val infinite = rememberInfiniteTransition(label = "probe-pulse")
    val scale by infinite.animateFloat(
        initialValue = 1f,
        targetValue = if (reachable == null) 1.4f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "probe-pulse-scale",
    )
    Box(
        modifier = Modifier
            .padding(start = 8.dp)
            .size(24.dp)
            .clickable(onClick = { sheetOpen = true }),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = color,
            modifier = Modifier
                .size(12.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale },
            shape = CircleShape,
        ) {}
    }

    if (sheetOpen) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { sheetOpen = false },
            sheetState = sheetState,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(description, style = MaterialTheme.typography.titleMedium)
                val relLabel = lastProbeEpochMs?.let { relativeTimeLabel(it) }
                    ?: stringResource(R.string.sessions_never)
                Text(
                    "Last successful probe: $relLabel",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                OutlinedButton(
                    onClick = { onRetry(); sheetOpen = false },
                    modifier = Modifier.padding(top = 16.dp),
                ) { Text(stringResource(R.string.sessions_retry_now)) }
            }
        }
    }
}

/**
 * Server picker title for screens where "All servers" is NOT an option
 * (Observer, Dashboard, Settings). Shows the active profile name with a
 * dropdown arrow; tapping opens a menu of enabled profiles.
 */
@Composable
internal fun SingleServerPickerTitle(
    active: ServerProfile?,
    open: Boolean,
    onToggle: () -> Unit,
    onDismiss: () -> Unit,
    profiles: List<ServerProfile>,
    onSelect: (String) -> Unit,
) {
    Box {
        Row(
            modifier = Modifier
                .clickable(onClick = onToggle)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(active?.displayName ?: stringResource(R.string.sessions_no_server))
            Icon(
                Icons.Filled.ArrowDropDown,
                contentDescription = stringResource(R.string.sessions_switch_server),
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = onDismiss) {
            if (profiles.isEmpty()) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sessions_no_servers)) },
                    onClick = onDismiss,
                    enabled = false,
                )
            } else {
                profiles.forEach { p ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                HeaderProfileDot(enabled = p.enabled)
                                Column(
                                    modifier = Modifier
                                        .padding(start = 12.dp)
                                        .weight(1f),
                                ) {
                                    Text(p.displayName, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        p.baseUrl,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (p.id == active?.id) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = "Active",
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        },
                        onClick = { onSelect(p.id) },
                    )
                }
            }
        }
    }
}

/** 8dp status dot for picker dropdown rows. */
@Composable
internal fun HeaderProfileDot(enabled: Boolean) {
    val color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Surface(color = color, modifier = Modifier.size(8.dp), shape = CircleShape) {}
}

internal fun relativeTimeLabel(epochMs: Long): String {
    val deltaMs = System.currentTimeMillis() - epochMs
    val seconds = deltaMs / 1000
    return when {
        seconds < 5 -> "just now"
        seconds < 60 -> "${seconds}s ago"
        seconds < 3600 -> "${seconds / 60}m ago"
        seconds < 86400 -> "${seconds / 3600}h ago"
        else -> "${seconds / 86400}d ago"
    }
}
