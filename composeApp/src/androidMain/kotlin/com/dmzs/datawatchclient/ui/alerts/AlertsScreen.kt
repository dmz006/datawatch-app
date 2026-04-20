package com.dmzs.datawatchclient.ui.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dmzs.datawatchclient.domain.Session
import kotlin.math.absoluteValue

/**
 * Alerts tab — surfaces sessions that need user input. The list is the same
 * filtered view that drives the bottom-nav badge counter, so the badge always
 * agrees with what's renderable here.
 *
 * Tap a row to jump straight into that session's detail screen (where the
 * reply composer is pre-focused if the prompt is current). Swipe **left** on
 * a row to dismiss it — dismissal mutes the underlying session, which drops
 * it from the `needsInput && !muted` projection without destroying the
 * session itself. Matches Gmail / Discord left-swipe conventions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun AlertsScreen(
    onOpenSession: (String) -> Unit,
    vm: AlertsViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Alerts") }) }) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (state.alerts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "No sessions need input. You're caught up.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn {
                    items(state.alerts, key = { it.id }) { session ->
                        AlertRow(
                            session = session,
                            onClick = { onOpenSession(session.id) },
                            onDismiss = { vm.dismiss(session.id) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertRow(
    session: Session,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 80.dp.toPx() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(session.id) {
                var dx = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dx = 0f },
                    onDragEnd = {
                        // Left swipe (negative dx) past threshold → dismiss.
                        // Right swipe is reserved — matches SessionsScreen's
                        // swipe-to-mute convention on the opposite direction.
                        if (dx < -swipeThresholdPx) onDismiss()
                    },
                    onDragCancel = { dx = 0f },
                ) { _, delta -> dx += delta }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .clickable(onClick = onClick)
                .padding(16.dp),
        ) {
            Column {
                Text(session.id, style = MaterialTheme.typography.titleSmall)
                Text(
                    session.taskSummary ?: "(no summary)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    "Waiting on input · " + (session.hostnamePrefix ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
