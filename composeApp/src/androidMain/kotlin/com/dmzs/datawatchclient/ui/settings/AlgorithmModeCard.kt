package com.dmzs.datawatchclient.ui.settings

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.transport.dto.AlgorithmStateDto
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val ALGORITHM_PHASES = listOf(
    "observe", "orient", "decide", "act", "measure", "learn", "improve",
)

@Composable
internal fun AlgorithmModeCard() {
    var sessions by remember { mutableStateOf<List<AlgorithmStateDto>>(emptyList()) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun loadSessions() {
        val activeId = ServiceLocator.activeServerStore.get() ?: return
        val sp = ServiceLocator.profileRepository.observeAll().first()
            .firstOrNull { it.id == activeId && it.enabled } ?: return
        ServiceLocator.transportFor(sp).algorithmList().onSuccess { sessions = it }
    }

    LaunchedEffect(Unit) { runCatching { loadSessions() } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .pwaCard()
            .padding(12.dp),
    ) {
        PwaSectionTitle(stringResource(R.string.algorithm_mode_title))

        if (sessions.isEmpty()) {
            Text(
                stringResource(R.string.algorithm_no_active),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            sessions.forEachIndexed { idx, state ->
                if (idx > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                AlgorithmSessionRow(
                    state = state,
                    expanded = expandedId == state.sessionId,
                    onToggle = { expandedId = if (expandedId == state.sessionId) null else state.sessionId },
                    onAdvance = {
                        scope.launch {
                            runCatching {
                                val activeId = ServiceLocator.activeServerStore.get() ?: return@runCatching
                                val sp = ServiceLocator.profileRepository.observeAll().first()
                                    .firstOrNull { it.id == activeId && it.enabled } ?: return@runCatching
                                ServiceLocator.transportFor(sp).algorithmAdvance(state.sessionId)
                                    .onSuccess { updated ->
                                        sessions = sessions.map { if (it.sessionId == updated.sessionId) updated else it }
                                    }
                            }
                        }
                    },
                    onAbort = {
                        scope.launch {
                            runCatching {
                                val activeId = ServiceLocator.activeServerStore.get() ?: return@runCatching
                                val sp = ServiceLocator.profileRepository.observeAll().first()
                                    .firstOrNull { it.id == activeId && it.enabled } ?: return@runCatching
                                ServiceLocator.transportFor(sp).algorithmAbort(state.sessionId)
                                    .onSuccess { updated ->
                                        sessions = sessions.map { if (it.sessionId == updated.sessionId) updated else it }
                                    }
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun AlgorithmSessionRow(
    state: AlgorithmStateDto,
    expanded: Boolean,
    onToggle: () -> Unit,
    onAdvance: () -> Unit,
    onAbort: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 4.dp),
    ) {
        // Session ID + phase strip
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                state.sessionId.take(12),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            PhaseStrip(current = state.current, aborted = state.aborted)
        }

        // Expanded detail
        if (expanded) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                // Current phase label
                Text(
                    stringResource(R.string.algorithm_current_phase) + ": ${state.current}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                // Most recent phase output
                state.history.lastOrNull()?.let { last ->
                    Text(
                        stringResource(R.string.algorithm_phase_output),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Text(
                        last.output.take(500),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                // Action buttons
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!state.aborted) {
                        FilledTonalButton(
                            onClick = onAdvance,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFF10B981).copy(alpha = 0.18f),
                                contentColor = Color(0xFF10B981),
                            ),
                        ) { Text(stringResource(R.string.algorithm_advance)) }
                        FilledTonalButton(
                            onClick = onAbort,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.18f),
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                        ) { Text(stringResource(R.string.algorithm_abort)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhaseStrip(current: String, aborted: Boolean) {
    val currentIdx = ALGORITHM_PHASES.indexOf(current).coerceAtLeast(0)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ALGORITHM_PHASES.forEachIndexed { idx, _ ->
            when {
                aborted && idx == currentIdx -> PhaseDot(color = MaterialTheme.colorScheme.error, pulse = false)
                idx < currentIdx -> PhaseDot(color = Color(0xFF10B981), pulse = false) // done: teal
                idx == currentIdx -> PhaseDot(color = Color(0xFF3B82F6), pulse = true)  // current: pulsing blue
                else -> PhaseDot(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f), pulse = false) // future: grey
            }
        }
    }
}

@Composable
private fun PhaseDot(color: Color, pulse: Boolean) {
    val alpha = if (pulse) {
        val transition = rememberInfiniteTransition(label = "pulseDot")
        val a by transition.animateFloat(
            initialValue = 0.4f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse),
            label = "dotAlpha",
        )
        a
    } else 1f

    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha)),
    )
}
