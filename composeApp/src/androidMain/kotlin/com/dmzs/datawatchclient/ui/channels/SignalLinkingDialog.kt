package com.dmzs.datawatchclient.ui.channels

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.res.stringResource
import com.dmzs.datawatchclient.R
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.prefs.ActiveServerStore
import com.dmzs.datawatchclient.transport.dto.LinkQrFrameDto
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Modal dialog that renders live QR frames streamed from /api/link/qr (SSE).
 * When Signal completes pairing the status flips to linked — the dialog surfaces
 * that state and persists it to the local profile row (migration 6).
 */
@Composable
public fun SignalLinkingDialog(
    onDismiss: () -> Unit,
    onLinked: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val strWaiting = stringResource(R.string.signal_link_waiting)
    val strScanPrompt = stringResource(R.string.signal_link_scan_prompt)
    val strSuccess = stringResource(R.string.signal_link_success)
    val strNoServer = stringResource(R.string.servers_none_available)
    var qrFrame by remember { mutableStateOf<LinkQrFrameDto?>(null) }
    var status by remember { mutableStateOf(strWaiting) }
    var linked by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var sseJob by remember { mutableStateOf<Job?>(null) }

    suspend fun activeTransport() =
        ServiceLocator.profileRepository.observeAll().first().let { profiles ->
            val activeId = ServiceLocator.activeServerStore.get()
            (profiles.firstOrNull {
                it.id == activeId && it.enabled && activeId != ActiveServerStore.SENTINEL_ALL_SERVERS
            } ?: profiles.firstOrNull { it.enabled })?.let { ServiceLocator.transportFor(it) }
        }

    suspend fun checkLinked(profile: ServerProfile): Boolean {
        val transport = ServiceLocator.transportFor(profile)
        return transport.getSignalLinkStatus()
            .getOrNull()?.linked == true
    }

    LaunchedEffect(Unit) {
        val transport = activeTransport() ?: run {
            error = strNoServer
            return@LaunchedEffect
        }
        sseJob = scope.launch {
            transport.startSignalLinking()
                .catch { e -> error = e.message ?: "Stream error" }
                .collect { frame ->
                    qrFrame = frame
                    status = strScanPrompt
                    // Check if pairing completed after each frame
                    val profiles = ServiceLocator.profileRepository.observeAll().first()
                    val activeId = ServiceLocator.activeServerStore.get()
                    val profile = profiles.firstOrNull {
                        it.id == activeId && it.enabled && activeId != ActiveServerStore.SENTINEL_ALL_SERVERS
                    } ?: profiles.firstOrNull { it.enabled } ?: return@collect
                    if (checkLinked(profile)) {
                        ServiceLocator.profileRepository.setSignalLinked(profile.id, true)
                        linked = true
                        status = strSuccess
                        onLinked()
                    }
                }
        }
    }

    DisposableEffect(Unit) {
        onDispose { sseJob?.cancel() }
    }

    AlertDialog(
        onDismissRequest = {
            sseJob?.cancel()
            if (!linked) {
                scope.launch { activeTransport()?.cancelSignalLink() }
            }
            onDismiss()
        },
        title = { Text(stringResource(R.string.signal_link_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (linked) {
                    Text(
                        stringResource(R.string.signal_link_success),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    } ?: run {
                        val frame = qrFrame
                        if (frame != null) {
                            QrImageView(frame.imageBase64)
                        } else {
                            Box(
                                modifier = Modifier.size(200.dp),
                                contentAlignment = Alignment.Center,
                            ) { CircularProgressIndicator() }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            status,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (linked) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
            }
        },
        dismissButton = {
            if (!linked) {
                TextButton(onClick = {
                    sseJob?.cancel()
                    scope.launch { activeTransport()?.cancelSignalLink() }
                    onDismiss()
                }) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

@Composable
private fun QrImageView(imageBase64: String) {
    val bitmap = remember(imageBase64) {
        runCatching {
            val bytes = Base64.decode(imageBase64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }
    if (bitmap != null) {
        Box(
            modifier = Modifier
                .size(200.dp)
                .background(Color.White)
                .padding(8.dp),
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.signal_link_title),
                modifier = Modifier.size(184.dp),
            )
        }
    } else {
        Box(modifier = Modifier.size(200.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.signal_link_qr_unavailable), style = MaterialTheme.typography.bodySmall)
        }
    }
}
