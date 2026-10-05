package com.dmzs.datawatchclient.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.transport.CouncilLivePhase
import com.dmzs.datawatchclient.transport.CouncilLiveReducer
import com.dmzs.datawatchclient.transport.CouncilLiveReply
import com.dmzs.datawatchclient.transport.CouncilLiveRound
import com.dmzs.datawatchclient.transport.CouncilLiveState
import com.dmzs.datawatchclient.transport.CouncilReplyStatus
import com.dmzs.datawatchclient.transport.TransportClient
import com.dmzs.datawatchclient.transport.watchCouncilRun
import kotlinx.coroutines.launch

// PWA councilOpenLiveWatch palette.
private val CouncilGreen = Color(0xFF22C55E)
private val CouncilAccent = Color(0xFF6366F1)
private val CouncilAmber = Color(0xFFF59E0B)
private val CouncilPurple = Color(0xFFA855F7)
private val CouncilRed = Color(0xFFEF4444)

/**
 * Live council run (PWA `councilOpenLiveWatch`) or replay of a past run
 * (PWA `councilViewRun`). When [live] is true the sheet subscribes to
 * `/api/council/runs/{id}/events` via [watchCouncilRun]; otherwise it loads
 * `GET /api/council/runs/{id}` once. [onFinished] fires when a live run
 * reaches a terminal phase so the caller can refresh its recent-runs list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CouncilLiveRunSheet(
    transport: TransportClient,
    initial: CouncilLiveState,
    live: Boolean,
    onDismiss: () -> Unit,
    onFinished: () -> Unit = {},
) {
    var state by remember(initial.runId) { mutableStateOf(initial) }
    var confirmCancel by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(initial.runId, live) {
        if (live) {
            var notified = false
            transport.watchCouncilRun(initial).collect { s ->
                state = s
                if (s.isTerminal && !notified) {
                    notified = true
                    onFinished()
                }
            }
        } else {
            transport.councilGetRun(initial.runId).onSuccess { run ->
                state = CouncilLiveReducer.fromRun(run)
            }
        }
    }

    val replyCount = state.rounds.sumOf { r -> r.replies.count { it.status == CouncilReplyStatus.DONE } }
    LaunchedEffect(replyCount, state.phase) {
        if (live && !state.isTerminal) {
            val total = listState.layoutInfo.totalItemsCount
            if (total > 0) listState.animateScrollToItem(total - 1)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            CouncilLiveHeader(
                state = state,
                onCancel = { confirmCancel = true },
            )
            if (state.proposal.isNotBlank()) {
                Text(
                    state.proposal,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 6,
                    modifier = Modifier.padding(top = 6.dp, bottom = 6.dp),
                )
            }
            HorizontalDivider()
        }
        SelectionContainer {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                items(state.rounds, key = { "round-${it.index}" }) { round ->
                    CouncilLiveRoundBlock(round = round, roundsTotal = state.roundsTotal)
                }
                item(key = "footer") { CouncilLiveFooter(state) }
            }
        }
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text(stringResource(R.string.council_cancel_confirm, state.runId.take(8))) },
            confirmButton = {
                TextButton(onClick = {
                    confirmCancel = false
                    scope.launch { transport.councilStopRun(state.runId) }
                }) { Text(stringResource(R.string.council_cancel_btn_label)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancel = false }) { Text(stringResource(R.string.action_close)) }
            },
        )
    }
}

@Composable
private fun CouncilLiveHeader(
    state: CouncilLiveState,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CouncilPhaseChip(state.phase)
        Text(
            state.runId.take(8),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
        if (state.mode.isNotBlank()) {
            Text(
                state.mode,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.personas.isNotEmpty()) {
            Text(
                "${state.personas.size} " + stringResource(R.string.council_personas_count),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(modifier = Modifier.weight(1f))
        if (!state.isTerminal && state.phase != CouncilLivePhase.CONNECTING) {
            TextButton(onClick = onCancel) {
                Text("✕ " + stringResource(R.string.council_cancel_btn_label), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
internal fun CouncilPhaseChip(phase: String) {
    val (label, color) =
        when (phase) {
            CouncilLivePhase.CONNECTING -> stringResource(R.string.council_phase_connecting) to MaterialTheme.colorScheme.onSurfaceVariant
            CouncilLivePhase.RUNNING -> stringResource(R.string.council_phase_running) to CouncilGreen
            CouncilLivePhase.SYNTHESIZING -> stringResource(R.string.council_phase_synthesizing) to CouncilPurple
            CouncilLivePhase.COMPLETED -> stringResource(R.string.council_phase_completed) to CouncilAccent
            CouncilLivePhase.CANCELLED -> stringResource(R.string.council_phase_cancelled) to CouncilRed
            else -> stringResource(R.string.council_phase_error) to CouncilRed
        }
    Box(
        modifier =
            Modifier
                .background(color.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun CouncilLiveRoundBlock(
    round: CouncilLiveRound,
    roundsTotal: Int,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Text(
            if (roundsTotal > 0) {
                stringResource(R.string.council_live_round, round.index, maxOf(roundsTotal, round.index))
            } else {
                stringResource(R.string.council_round_label) + " ${round.index}"
            },
            style = MaterialTheme.typography.labelMedium,
            color = CouncilAccent,
            fontWeight = FontWeight.SemiBold,
        )
        round.replies.forEach { reply -> CouncilLiveReplyRow(reply) }
    }
}

@Composable
private fun CouncilLiveReplyRow(reply: CouncilLiveReply) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val marker =
                when (reply.status) {
                    CouncilReplyStatus.DONE -> "✓" to CouncilGreen
                    CouncilReplyStatus.ERROR -> "✗" to CouncilRed
                    CouncilReplyStatus.RESPONDING -> "⏳" to CouncilAmber
                    else -> "·" to MaterialTheme.colorScheme.onSurfaceVariant
                }
            Text(marker.first, style = MaterialTheme.typography.labelMedium, color = marker.second)
            Text(
                reply.persona,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = marker.second,
            )
            when (reply.status) {
                CouncilReplyStatus.RESPONDING -> {
                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = CouncilAmber)
                    Text(
                        stringResource(R.string.council_live_responding),
                        style = MaterialTheme.typography.labelSmall,
                        color = CouncilAmber,
                    )
                }
                CouncilReplyStatus.WAITING ->
                    Text(
                        stringResource(R.string.council_live_waiting),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                else -> Unit
            }
        }
        if (reply.text.isNotBlank()) {
            if (reply.status == CouncilReplyStatus.ERROR) {
                Text(
                    reply.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = CouncilRed,
                    modifier = Modifier.padding(start = 18.dp, top = 2.dp),
                )
            } else {
                // Persona replies are markdown (operator 2026-10-05: render on all three).
                com.dmzs.datawatchclient.ui.autonomous.MarkdownView(
                    text = reply.text,
                    modifier = Modifier.padding(start = 18.dp, top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun CouncilLiveFooter(state: CouncilLiveState) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 24.dp)) {
        if (state.phase == CouncilLivePhase.CONNECTING || (state.phase == CouncilLivePhase.RUNNING && state.rounds.isEmpty())) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp)
                Text(
                    stringResource(R.string.council_running_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (state.phase == CouncilLivePhase.SYNTHESIZING) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp, color = CouncilPurple)
                Text(
                    "◆ " + stringResource(R.string.council_live_synthesizing),
                    style = MaterialTheme.typography.labelMedium,
                    color = CouncilPurple,
                )
            }
        }
        if (state.consensus.isNotBlank()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            Text(
                stringResource(R.string.council_consensus),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = CouncilGreen,
            )
            com.dmzs.datawatchclient.ui.autonomous.MarkdownView(text = state.consensus, modifier = Modifier.padding(top = 2.dp))
        }
        if (state.dissent.isNotBlank()) {
            Text(
                stringResource(R.string.council_dissent_label),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = CouncilAmber,
                modifier = Modifier.padding(top = 8.dp),
            )
            com.dmzs.datawatchclient.ui.autonomous.MarkdownView(text = state.dissent, modifier = Modifier.padding(top = 2.dp))
        }
        if (state.phase == CouncilLivePhase.ERROR) {
            Text(
                "⚠ " + stringResource(R.string.council_live_lost),
                style = MaterialTheme.typography.labelSmall,
                color = CouncilRed,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
