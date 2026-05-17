package com.dmzs.datawatchclient.wear

import android.app.Activity
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * BL303-W3 — Single-confirmation approve screen for a guardrail block.
 *
 * Launched from the guardrail block notification action. Shows the block
 * summary, a "Confirm Approve" button, and a 10-second countdown that
 * auto-dismisses if the user takes no action. On confirm, sends
 * `/datawatch/approveGate` via MessageClient so the phone's WearSyncService
 * can call the approve transport without the watch holding credentials.
 */
public class WearApproveScreen : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID).orEmpty()
        val summary = intent.getStringExtra(EXTRA_BLOCK_SUMMARY).orEmpty()

        setContent {
            MaterialTheme {
                ApproveContent(
                    summary = summary,
                    onConfirm = {
                        sendApproveMessage(sessionId)
                        setResult(Activity.RESULT_OK)
                        finish()
                    },
                    onTimeout = { finish() },
                )
            }
        }
    }

    private fun sendApproveMessage(sessionId: String) {
        val payload = sessionId.toByteArray(Charsets.UTF_8)
        val client = Wearable.getMessageClient(this)
        val nodeClient = Wearable.getNodeClient(this)
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob()).launch {
            runCatching {
                val nodes: List<Node> = Tasks.await(nodeClient.connectedNodes)
                nodes.forEach { node ->
                    Tasks.await(client.sendMessage(node.id, APPROVE_GATE_PATH, payload))
                }
                Log.d(TAG, "approveGate sent sessionId=$sessionId nodes=${nodes.size}")
            }.onFailure { Log.w(TAG, "approveGate send FAILED", it) }
        }
    }

    public companion object {
        public const val EXTRA_SESSION_ID: String = "sessionId"
        public const val EXTRA_BLOCK_SUMMARY: String = "blockSummary"
        public const val APPROVE_GATE_PATH: String = "/datawatch/approveGate"
        private const val TAG: String = "WearApprove"
        public const val AUTO_DISMISS_SECONDS: Int = 10
    }
}

@Composable
private fun ApproveContent(
    summary: String,
    onConfirm: () -> Unit,
    onTimeout: () -> Unit,
) {
    val blockColor = Color(0xFFEF4444)
    var countdown by remember { mutableIntStateOf(WearApproveScreen.AUTO_DISMISS_SECONDS) }
    var confirmed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        repeat(WearApproveScreen.AUTO_DISMISS_SECONDS) {
            delay(1_000L)
            countdown--
        }
        if (!confirmed) onTimeout()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F14)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Text(
                "Guardrail Blocked",
                style = MaterialTheme.typography.title3,
                color = blockColor,
                textAlign = TextAlign.Center,
            )

            if (summary.isNotBlank()) {
                Text(
                    summary.take(80),
                    style = MaterialTheme.typography.caption1,
                    color = Color(0xFF9AA7B3),
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                )
            }

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = {
                    confirmed = true
                    onConfirm()
                },
                modifier = Modifier.fillMaxWidth(0.75f),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Color(0xFF00E5A0),
                    contentColor = Color(0xFF00140B),
                ),
            ) {
                Text("Confirm Approve", style = MaterialTheme.typography.button)
            }

            Text(
                "Auto-dismiss in ${countdown}s",
                style = MaterialTheme.typography.caption2,
                color = Color(0xFF9AA7B3),
            )
        }
    }
}
