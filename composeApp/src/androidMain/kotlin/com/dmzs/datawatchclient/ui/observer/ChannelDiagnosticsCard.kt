package com.dmzs.datawatchclient.ui.observer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.prefs.ActiveServerStore
import com.dmzs.datawatchclient.ui.theme.LocalDatawatchColors
import com.dmzs.datawatchclient.ui.theme.PwaCard
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** One `/api/channel/diagnostics` session row (BL362). */
internal data class ChannelDiagRow(
    val name: String,
    val port: Int,
    val alive: Boolean,
    val error: String?,
)

/** Parsed `/api/channel/diagnostics` payload. */
internal data class ChannelDiag(
    val rows: List<ChannelDiagRow>,
    val hints: List<String>,
)

private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "null" }

/** PWA `loadChannelDiagnostics` field mapping (app.js BL362). */
internal fun parseChannelDiagnostics(d: JsonObject): ChannelDiag {
    val rows =
        (d["sessions"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.map { sd ->
            ChannelDiagRow(
                name = sd.str("session_name") ?: sd.str("session_id").orEmpty(),
                port = sd.str("channel_port")?.toIntOrNull() ?: 0,
                alive = sd.str("bridge_alive") == "true",
                error = sd.str("probe_error"),
            )
        }
    val hints = (d["hints"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content }
    return ChannelDiag(rows, hints)
}

/**
 * PWA Observer "Channel diagnostics" block (BL362): per-session MCP bridge
 * port + liveness (✓ / ✗ with probe error) and a collapsible ⚠ hints list,
 * with a refresh button.
 */
@Composable
internal fun ChannelDiagnosticsCard() {
    var diag by remember { mutableStateOf<ChannelDiag?>(null) }
    var failed by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var hintsOpen by remember { mutableStateOf(false) }
    LaunchedEffect(reload) {
        val profiles = ServiceLocator.profileRepository.observeAll().first()
        val activeId = ServiceLocator.activeServerStore.get()
        val profile =
            profiles.firstOrNull { it.id == activeId && it.enabled && activeId != ActiveServerStore.SENTINEL_ALL_SERVERS }
                ?: profiles.firstOrNull { it.enabled } ?: return@LaunchedEffect
        ServiceLocator.transportFor(profile).fetchChannelDiagnostics().fold(
            onSuccess = {
                diag = parseChannelDiagnostics(it)
                failed = false
            },
            onFailure = { failed = true },
        )
    }
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    val small = MaterialTheme.typography.bodySmall
    PwaCard(
        id = "channel_diag",
        title = stringResource(R.string.channel_diag_title),
        docsAnchor = "channel-diagnostics",
        headerActions = { TextButton(onClick = { reload++ }) { Text("↻") } },
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            val d = diag
            when {
                failed -> Text(stringResource(R.string.channel_diag_unavailable), style = small, color = dim)
                d == null -> com.dmzs.datawatchclient.ui.common.PwaLoadingText()
                d.rows.isEmpty() -> Text(stringResource(R.string.channel_diag_no_sessions), style = small, color = dim)
                else ->
                    d.rows.forEach { r ->
                        val port = if (r.port > 0) "port ${r.port}" else "port –"
                        val err = r.error?.let { " — $it" }.orEmpty()
                        Text(
                            (if (r.alive) "✓ " else "✗ ") + r.name + "  " + port + if (r.alive) "" else err,
                            style = small,
                            color = if (r.alive) MaterialTheme.colorScheme.onSurface else LocalDatawatchColors.current.warning,
                        )
                    }
            }
            if (d != null && d.hints.isNotEmpty()) {
                Text(
                    "⚠ " + pluralHints(d.hints.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalDatawatchColors.current.warning,
                    modifier = Modifier.padding(top = 6.dp).clickable { hintsOpen = !hintsOpen },
                )
                if (hintsOpen) {
                    d.hints.forEach { h ->
                        Text("• $h", style = MaterialTheme.typography.labelSmall, color = dim, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        }
    }
}

private fun pluralHints(n: Int): String = if (n == 1) "1 hint" else "$n hints"
