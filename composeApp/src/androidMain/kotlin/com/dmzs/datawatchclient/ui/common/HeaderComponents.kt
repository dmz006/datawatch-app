package com.dmzs.datawatchclient.ui.common

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.prefs.ActiveServerStore
import com.dmzs.datawatchclient.ui.shell.AlertDockChannel

/**
 * Docs / help link — plain "?" text button that opens [docsPath] in an in-app WebView sheet.
 * [docsPath] is relative to the server's diagrams page, e.g. "datawatch-definitions.md#sessions-list".
 * The full URL becomes "$baseUrl/diagrams.html#docs/$docsPath".
 * Hidden entirely when no active server is configured.
 * Placed leftmost in the TopAppBar actions block (appears left of filter, alerts, status).
 */
@Composable
internal fun DocsLinkAction(docsPath: String) {
    val profiles by ServiceLocator.profileRepository.observeAll().collectAsState(initial = emptyList())
    val activeId by ServiceLocator.activeServerStore.observe().collectAsState(initial = null)
    val activeProfile =
        remember(profiles, activeId) {
            val enabled = profiles.filter { it.enabled }
            if (activeId == ActiveServerStore.SENTINEL_ALL_SERVERS) {
                enabled.firstOrNull()
            } else {
                (enabled.firstOrNull { it.id == activeId } ?: enabled.firstOrNull())
            }
        }
    val baseUrl = activeProfile?.baseUrl
    val allowSelfSigned = activeProfile?.trustAnchorSha256 == ServiceLocator.TRUST_ALL_SENTINEL
    var showDocs by remember { mutableStateOf(false) }

    if (baseUrl != null) {
        val url = "$baseUrl/diagrams.html#docs/$docsPath"
        Box(
            modifier =
                Modifier
                    .clickable { showDocs = true }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "?",
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (showDocs) {
            DocsViewerSheet(
                url = url,
                onDismiss = { showDocs = false },
                allowSelfSigned = allowSelfSigned,
                pinSha256 = ServiceLocator.pinFor(activeProfile),
            )
        }
    }
}

/**
 * Alerts pill button matching the PWA headerAlertPill style.
 * Shows "🔔 {count}" or "🔕 muted". Blue border/bg when alerts > 0,
 * dimmed when 0, gray when muted. Clicking toggles [AlertDockChannel].
 */
@Composable
internal fun AlertsBellAction(
    alertsBadge: Int,
    alertsMuted: Boolean = false,
) {
    // Parity D41a/D47a: client-side dock entries (former toasts) add to the
    // count; the dock mute lives in AlertDockChannel.
    val dockEntries by AlertDockChannel.entries.collectAsState()
    val dockMuted by AlertDockChannel.muted.collectAsState()
    val muted = alertsMuted || dockMuted
    val count = alertsBadge + AlertDockChannel.localCount(dockEntries)
    AlertsBellPill(count, muted)
}

@Composable
private fun AlertsBellPill(
    alertsBadge: Int,
    alertsMuted: Boolean,
) {
    val (bg, border, alpha) =
        when {
            alertsMuted ->
                Triple(
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    0.55f,
                )
            alertsBadge > 0 ->
                // Parity D3a — PWA pill: blue-tint background, accent2 (purple) border.
                Triple(
                    Color(0xFF60A5FA).copy(alpha = 0.18f),
                    com.dmzs.datawatchclient.ui.theme.LocalDatawatchColors.current.accent2,
                    1f,
                )
            else ->
                Triple(
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f),
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    0.55f,
                )
        }
    val label = if (alertsMuted) "🔕 muted" else "🔔 $alertsBadge"
    Box(
        modifier =
            Modifier
                .clickable { AlertDockChannel.toggle() }
                .background(color = bg, shape = RoundedCornerShape(10.dp))
                .border(width = 1.dp, color = border.copy(alpha = alpha), shape = RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .graphicsLayer { this.alpha = alpha },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            color = if (!alertsMuted && alertsBadge > 0) MaterialTheme.colorScheme.onSurface else border,
        )
    }
}

/**
 * Animated reachability dot shown at the far right of TopAppBar actions.
 * Green = reachable; red = unreachable or not yet probed (PWA .status-dot).
 * Tap opens a bottom sheet with last-probe time and a retry button.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun ReachabilityDot(
    reachable: Boolean?,
    lastProbeEpochMs: Long?,
    onRetry: () -> Unit,
) {
    var sheetOpen by remember { mutableStateOf(false) }
    val reconnectMsg = stringResource(R.string.status_dot_reconnecting)
    // PWA .status-dot: red until connected, green when connected — no
    // separate probing colour (operator 2026-10-05). 300 ms colour
    // transition mirrors `transition: background 0.3s ease`.
    val color by androidx.compose.animation.animateColorAsState(
        targetValue = if (reachable == true) Color(0xFF10B981) else Color(0xFFEF4444),
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 300),
        label = "status-dot",
    )
    val description =
        when (reachable) {
            true -> stringResource(R.string.sessions_server_online)
            false -> stringResource(R.string.sessions_server_unreachable)
            null -> stringResource(R.string.sessions_probing)
        }
    Box(
        modifier =
            Modifier
                .padding(start = 8.dp)
                .size(24.dp)
                // Parity D38a: tap opens the status sheet; long-press
                // force-refreshes the connection (PWA forceRefreshConnection).
                .combinedClickable(
                    onClick = { sheetOpen = true },
                    onLongClick = { forceRefreshConnection(reconnectMsg, onRetry) },
                ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = color,
            modifier =
                Modifier.size(12.dp),
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
                val relLabel =
                    lastProbeEpochMs?.let { relativeTimeLabel(it) }
                        ?: stringResource(R.string.sessions_never)
                Text(
                    "Last successful probe: $relLabel",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                OutlinedButton(
                    onClick = {
                        onRetry()
                        sheetOpen = false
                    },
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
            modifier =
                Modifier
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
                                    modifier =
                                        Modifier
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

/**
 * Parity D38a — PWA `forceRefreshConnection`: drop and reopen the WS
 * connection ([com.dmzs.datawatchclient.events.ReconnectBus]) and re-probe
 * the server ([onRetry]). The PWA also re-checks its service worker for a new
 * bundle; app updates come from the store, so that half is n/a here.
 */
internal fun forceRefreshConnection(
    message: String,
    onRetry: () -> Unit,
) {
    AlertDockChannel.post(message)
    com.dmzs.datawatchclient.events.ReconnectBus.request()
    onRetry()
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
