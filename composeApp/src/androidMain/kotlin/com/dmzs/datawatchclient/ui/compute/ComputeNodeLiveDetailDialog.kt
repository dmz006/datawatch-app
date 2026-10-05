package com.dmzs.datawatchclient.ui.compute

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.transport.ComputeNodeLiveDetail
import com.dmzs.datawatchclient.transport.ComputeNodeLiveDetailLoader
import com.dmzs.datawatchclient.transport.TransportClient
import kotlinx.coroutines.delay

/**
 * PWA `computeShowDetail` (Settings › Compute › node row 📡): modal with the
 * pretty-printed `/api/compute/nodes/{name}/detail` JSON, re-polled every 1 s
 * while open; on failure the title becomes "📡 name — detail unavailable" with
 * the server reason + the monitoring_endpoint fix hint, and polling stops.
 */
@Composable
internal fun ComputeNodeLiveDetailDialog(
    name: String,
    resolveTransport: suspend () -> TransportClient?,
    onDismiss: () -> Unit,
) {
    var detail by remember(name) { mutableStateOf<ComputeNodeLiveDetail?>(null) }
    LaunchedEffect(name) {
        val t = resolveTransport() ?: return@LaunchedEffect
        while (true) {
            val d = ComputeNodeLiveDetailLoader.load(t, name)
            detail = d
            if (d.error != null) break
            delay(ComputeNodeLiveDetailLoader.POLL_INTERVAL_MS)
        }
    }
    val failed = detail?.error != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "📡 $name — " +
                    if (failed) {
                        stringResource(R.string.compute_detail_unavailable)
                    } else {
                        stringResource(R.string.compute_detail_title)
                    },
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            val d = detail
            val err: String? = d?.error
            when {
                d == null -> Text("…", style = MaterialTheme.typography.bodySmall)
                err != null -> ComputeDetailError(err)
                else -> ComputeDetailJson(d.json.orEmpty())
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

@Composable
private fun ComputeDetailJson(json: String) {
    Column {
        SelectionContainer {
            Text(
                json,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .background(MaterialTheme.colorScheme.background, RoundedCornerShape(4.dp))
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                        .padding(8.dp),
            )
        }
        Text(
            stringResource(R.string.compute_detail_polling),
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun ComputeDetailError(reason: String) {
    Column {
        Text(reason, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
        Text(
            stringResource(R.string.compute_detail_fix_label),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            stringResource(R.string.compute_detail_fix_hint),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
