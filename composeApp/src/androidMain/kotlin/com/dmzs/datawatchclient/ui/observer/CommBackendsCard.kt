package com.dmzs.datawatchclient.ui.observer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.transport.dto.MatrixStatusDto
import com.dmzs.datawatchclient.ui.common.PwaLoadingText
import com.dmzs.datawatchclient.ui.shell.AlertDockChannel
import com.dmzs.datawatchclient.ui.shell.DockLevel
import com.dmzs.datawatchclient.ui.theme.LocalDatawatchColors
import com.dmzs.datawatchclient.ui.theme.PwaCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** PWA `loadCommBackendsStatus` service list, in PWA order. */
internal val COMM_BACKENDS: List<String> =
    listOf(
        "telegram", "discord", "slack", "matrix", "ntfy",
        "email", "twilio", "github_webhook", "webhook", "dns_channel",
    )

/** PWA label: underscores → spaces, CSS `text-transform: capitalize` (each word). */
internal fun commBackendLabel(key: String): String =
    key.split('_').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

/** Enabled services from `/api/config`, in PWA order. */
internal fun enabledCommBackends(raw: Map<String, kotlinx.serialization.json.JsonElement>): List<String> =
    COMM_BACKENDS.filter { key ->
        val section = raw[key] as? JsonObject ?: return@filter false
        (section["enabled"] as? JsonPrimitive)?.booleanOrNull == true
    }

private sealed interface MatrixLive {
    data object Checking : MatrixLive

    data class Loaded(val status: MatrixStatusDto) : MatrixLive

    data object Unavailable : MatrixLive
}

/**
 * PWA `commBackendsBlock` (BL241): enabled communication backends from
 * `/api/config`, each with a green dot; the Matrix row carries its live status
 * (`/api/matrix/status`) inline plus a "Test" button (POST /api/matrix/test →
 * toast, here the alert dock). Matrix is not a separate card.
 */
@Composable
internal fun CommBackendsCard() {
    var enabledBackends by remember { mutableStateOf<List<String>?>(null) }
    var unavailable by remember { mutableStateOf(false) }
    var matrix by remember { mutableStateOf<MatrixLive>(MatrixLive.Checking) }
    var profileRef by remember { mutableStateOf<ServerProfile?>(null) }
    val scope = rememberCoroutineScope()
    val sentMsg = stringResource(R.string.matrix_test_sent)

    LaunchedEffect(Unit) {
        val id = ServiceLocator.activeServerStore.get()
        val profiles = ServiceLocator.profileRepository.observeAll().first().filter { it.enabled }
        val profile =
            profiles.firstOrNull { it.id == id } ?: profiles.firstOrNull() ?: run {
                unavailable = true
                return@LaunchedEffect
            }
        profileRef = profile
        val transport = ServiceLocator.transportFor(profile)
        transport.fetchConfig().fold(
            onSuccess = { cfg ->
                val enabled = enabledCommBackends(cfg.raw)
                enabledBackends = enabled
                if ("matrix" in enabled) {
                    transport.fetchMatrixStatus().fold(
                        onSuccess = { matrix = MatrixLive.Loaded(it) },
                        onFailure = { matrix = MatrixLive.Unavailable },
                    )
                }
            },
            onFailure = { unavailable = true },
        )
    }

    PwaCard(
        id = "comm_backends",
        title = "Communication Backends",
        innerPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
    ) {
        val list = enabledBackends
        when {
            unavailable -> MutedText("Unavailable")
            list == null -> PwaLoadingText()
            list.isEmpty() -> MutedText("No communication backends enabled.")
            else ->
                list.forEach { backend ->
                    if (backend == "matrix") {
                        MatrixRow(
                            live = matrix,
                            onTest = {
                                val p = profileRef ?: return@MatrixRow
                                scope.launch {
                                    ServiceLocator.transportFor(p).sendMatrixTest().fold(
                                        onSuccess = { AlertDockChannel.post(sentMsg) },
                                        onFailure = {
                                            AlertDockChannel.post(
                                                it.message ?: it::class.simpleName.orEmpty(),
                                                DockLevel.Error,
                                            )
                                        },
                                    )
                                }
                            },
                        )
                    } else {
                        BackendRow(backend)
                    }
                }
        }
    }
}

@Composable
private fun MutedText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun EnabledDot() {
    Surface(shape = CircleShape, color = Color(0xFF22C55E), modifier = Modifier.size(7.dp)) {}
}

@Composable
private fun BackendRow(backend: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        EnabledDot()
        Text(commBackendLabel(backend), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun MatrixRow(
    live: MatrixLive,
    onTest: () -> Unit,
) {
    val dw = LocalDatawatchColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        EnabledDot()
        Text(commBackendLabel("matrix"), style = MaterialTheme.typography.bodySmall)
        val (text, color) =
            when (live) {
                MatrixLive.Checking -> "checking…" to MaterialTheme.colorScheme.onSurfaceVariant
                MatrixLive.Unavailable -> "unavailable" to MaterialTheme.colorScheme.onSurfaceVariant
                is MatrixLive.Loaded ->
                    if (live.status.connected) {
                        "● connected" to dw.success
                    } else {
                        "● disconnected" to MaterialTheme.colorScheme.error
                    }
            }
        Text(
            text,
            fontSize = 10.sp,
            color = color,
            modifier = Modifier.padding(start = 2.dp).weight(1f),
        )
        OutlinedButton(
            onClick = onTest,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            modifier = Modifier.padding(0.dp),
        ) { Text(stringResource(R.string.matrix_test_btn), fontSize = 10.sp) }
    }
}
