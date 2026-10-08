package com.dmzs.datawatchclient.ui.channels

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.prefs.ActiveServerStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Signal device linking — mirrors the PWA `startLinking` / `streamLinkEvents`
 * flow: `POST /api/link/start {device_name}` returns a `stream_id`, then the
 * SSE stream `GET /api/link/stream?id=` emits `qr` (a `sgnl://linkdevice…`
 * URI), `linked`, or `error`. (The previous implementation called `/api/link/qr`
 * without the required id and a non-existent `/api/link/cancel`.)
 *
 * The app has no QR encoder, so the link URI is shown the way the PWA's
 * no-QRCode.js fallback does (white box, selectable text), with Copy and an
 * "Open in Signal" intent for a Signal install on this device. Dismissing just
 * closes the stream — the server has no cancel route.
 */
@Composable
public fun SignalLinkingDialog(
    onDismiss: () -> Unit,
    onLinked: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val strScanPrompt = stringResource(R.string.signal_link_scan_prompt)
    val strNoServer = stringResource(R.string.servers_none_available)
    var linkUri by remember { mutableStateOf<String?>(null) }
    var linked by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }

    suspend fun activeProfile(): ServerProfile? {
        val profiles = ServiceLocator.profilesWithProxied().first()
        val activeId = ServiceLocator.activeServerStore.get()
        return profiles.firstOrNull {
            it.id == activeId && it.enabled && activeId != ActiveServerStore.SENTINEL_ALL_SERVERS
        } ?: profiles.firstOrNull { it.enabled }
    }

    LaunchedEffect(Unit) {
        val profile =
            activeProfile() ?: run {
                error = strNoServer
                return@LaunchedEffect
            }
        val transport = ServiceLocator.transportFor(profile)
        job =
            scope.launch {
                val streamId =
                    transport.startSignalLink(deviceName = "").getOrElse { e ->
                        error = e.message ?: "Failed to start linking"
                        return@launch
                    }
                transport.signalLinkEvents(streamId)
                    .catch { e -> if (!linked) error = e.message ?: "Stream error" }
                    .collect { ev ->
                        when (ev.event) {
                            "qr" -> linkUri = ev.data.trim()
                            "linked" -> {
                                ServiceLocator.profileRepository.setSignalLinked(profile.id, true)
                                linked = true
                                onLinked()
                            }
                            "error" -> error = ev.data.ifBlank { "unknown error" }
                        }
                    }
            }
    }

    DisposableEffect(Unit) {
        onDispose { job?.cancel() }
    }

    AlertDialog(
        onDismissRequest = {
            job?.cancel()
            onDismiss()
        },
        title = { Text(stringResource(R.string.signal_link_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when {
                    linked ->
                        Text(
                            stringResource(R.string.signal_link_success),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    error != null ->
                        Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    linkUri == null -> {
                        Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                            com.dmzs.datawatchclient.ui.common.DatawatchLoadingContent(
                                label = null,
                                eyeSize = 48.dp,
                                verticalPadding = 0.dp,
                            )
                        }
                        Text(
                            stringResource(R.string.signal_link_waiting),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> {
                        val uri = linkUri.orEmpty()
                        val qr = remember(uri) { signalQrBitmap(uri) }
                        if (qr != null) {
                            androidx.compose.foundation.Image(
                                bitmap = qr,
                                contentDescription = stringResource(R.string.signal_link_title),
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .background(Color.White)
                                        .padding(8.dp),
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                        SelectionContainer {
                            Text(
                                uri,
                                fontSize = 10.sp,
                                color = Color.Black,
                                maxLines = if (qr != null) 2 else Int.MAX_VALUE,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .background(Color.White)
                                        .padding(8.dp),
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row {
                            OutlinedButton(onClick = { clipboard.setText(AnnotatedString(uri)) }) {
                                Text(stringResource(R.string.signal_link_copy))
                            }
                            Spacer(Modifier.size(8.dp))
                            OutlinedButton(onClick = {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
                                } catch (_: ActivityNotFoundException) {
                                    error = context.getString(R.string.signal_link_no_app)
                                }
                            }) { Text(stringResource(R.string.signal_link_open)) }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            strScanPrompt,
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
                    job?.cancel()
                    onDismiss()
                }) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}


/** Render the `sgnl://linkdevice…` URI as a QR code (ZXing core), black on white. */
internal fun signalQrBitmap(uri: String): androidx.compose.ui.graphics.ImageBitmap? {
    if (uri.isBlank()) return null
    return runCatching {
        val size = 512
        val matrix =
            com.google.zxing.qrcode.QRCodeWriter().encode(
                uri,
                com.google.zxing.BarcodeFormat.QR_CODE,
                size,
                size,
                mapOf(com.google.zxing.EncodeHintType.MARGIN to 1),
            )
        val pixels = IntArray(size * size) { i -> if (matrix.get(i % size, i / size)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        android.graphics.Bitmap.createBitmap(pixels, size, size, android.graphics.Bitmap.Config.ARGB_8888)
    }.getOrNull()?.asImageBitmap()
}
