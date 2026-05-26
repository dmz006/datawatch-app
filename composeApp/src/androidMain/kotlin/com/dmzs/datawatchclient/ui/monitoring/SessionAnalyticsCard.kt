package com.dmzs.datawatchclient.ui.monitoring

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.prefs.ActiveServerStore
import com.dmzs.datawatchclient.transport.dto.AnalyticsDto
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.coroutines.flow.first

/**
 * Observer tab — Session Analytics card. Mirrors PWA analytics section.
 * Shows bucketed session counts with ok/err bar for the last N days.
 */
@Composable
public fun SessionAnalyticsCard() {
    var data by remember { mutableStateOf<AnalyticsDto?>(null) }
    var rangeDays by remember { mutableStateOf(7) }
    var banner by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(rangeDays) {
        val profiles = ServiceLocator.profileRepository.observeAll().first()
        val activeId = ServiceLocator.activeServerStore.get()
        val profile = if (activeId == ActiveServerStore.SENTINEL_ALL_SERVERS) {
            profiles.firstOrNull { it.enabled }
        } else {
            profiles.firstOrNull { it.id == activeId && it.enabled }
                ?: profiles.firstOrNull { it.enabled }
        }
        profile?.let {
            ServiceLocator.transportFor(it).getAnalytics(rangeDays)
                .onSuccess { d -> data = d; banner = null }
                .onFailure { e -> banner = e.message }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .pwaCard()
            .padding(12.dp),
    ) {
        PwaSectionTitle("Session Analytics", docsAnchor = "session-analytics")

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Range:", style = MaterialTheme.typography.labelSmall)
            listOf(7, 14, 30, 90).forEach { days ->
                FilterChip(
                    selected = rangeDays == days,
                    onClick = { rangeDays = days },
                    label = { Text("${days}d", style = MaterialTheme.typography.labelSmall) },
                )
            }
            data?.successRate?.let { rate ->
                Spacer(Modifier.weight(1f))
                Text(
                    "%.1f%% success".format(rate * 100),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        banner?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        val d = data
        if (d == null) {
            Text(
                "Loading…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        if (d.buckets.isEmpty()) {
            Text(
                "No sessions in range.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        val maxTotal = d.buckets.maxOfOrNull { it.sessionCount } ?: 1
        d.buckets.forEach { bucket ->
            val errors = bucket.failed + bucket.killed
            val ok = bucket.sessionCount - errors
            val errPct = if (bucket.sessionCount > 0) errors.toFloat() / bucket.sessionCount else 0f
            val barColor = when {
                errPct > 0.20f -> Color(0xFFEF4444)
                errPct > 0.05f -> Color(0xFFF59E0B)
                else -> Color(0xFF10B981)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    bucket.date,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(72.dp),
                )
                Text(
                    "${bucket.sessionCount}",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(32.dp),
                )
                Text(
                    "$ok",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF10B981),
                    modifier = Modifier.width(32.dp),
                )
                Text(
                    "$errors",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (errors > 0) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(32.dp),
                )
                val barFraction = if (maxTotal > 0) bucket.sessionCount.toFloat() / maxTotal else 0f
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(barFraction)
                            .background(barColor),
                    )
                }
            }
        }
    }
}
