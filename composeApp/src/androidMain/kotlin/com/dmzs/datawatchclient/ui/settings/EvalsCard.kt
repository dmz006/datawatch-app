package com.dmzs.datawatchclient.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.transport.dto.EvalRunHistoryDto
import com.dmzs.datawatchclient.transport.dto.EvalRunResultDto
import com.dmzs.datawatchclient.transport.dto.EvalSuiteDto
import com.dmzs.datawatchclient.transport.TransportError
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
internal fun EvalsCard() {
    var suites by remember { mutableStateOf<List<EvalSuiteDto>>(emptyList()) }
    var visible by remember { mutableStateOf(false) }
    var runningId by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<Map<String, EvalRunResultDto>>(emptyMap()) }
    // issue #131 — alpha.68: GET /api/evals returns completed run history
    var evalRuns by remember { mutableStateOf<List<EvalRunHistoryDto>>(emptyList()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        runCatching {
            val activeId = ServiceLocator.activeServerStore.get()
            val sp = ServiceLocator.profileRepository.observeAll()
                .first { list -> list.any { it.enabled } }
                .let { list ->
                    if (activeId == null) list.filter { it.enabled }.firstOrNull()
                    else list.firstOrNull { it.id == activeId && it.enabled }
                } ?: return@runCatching
            val transport = ServiceLocator.transportFor(sp)
            transport.evalsList().onSuccess { list ->
                suites = list
                visible = true
            }.onFailure { err ->
                visible = err !is TransportError.NotFound
            }
            // Fetch run history from /api/evals (alpha.68+); graceful on 404
            transport.listEvalRuns().onSuccess { runs -> evalRuns = runs }
        }
    }

    if (!visible) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .pwaCard()
            .padding(12.dp),
    ) {
        PwaSectionTitle(stringResource(R.string.evals_title), docsAnchor = "evals")

        if (suites.isEmpty()) {
            Text(
                stringResource(R.string.evals_no_suites),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            suites.forEachIndexed { idx, suite ->
                if (idx > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                EvalSuiteRow(
                    suite = suite,
                    running = runningId == suite.effectiveId,
                    result = results[suite.effectiveId],
                    onRun = {
                        runningId = suite.effectiveId
                        scope.launch {
                            runCatching {
                                val activeId = ServiceLocator.activeServerStore.get()
                                val sp = ServiceLocator.profileRepository.observeAll()
                                    .first { list -> list.any { it.enabled } }
                                    .let { list ->
                                        if (activeId == null) list.filter { it.enabled }.firstOrNull()
                                        else list.firstOrNull { it.id == activeId && it.enabled }
                                    } ?: return@runCatching
                                ServiceLocator.transportFor(sp).evalsRun(suite.effectiveId)
                                    .onSuccess { r -> results = results + (suite.effectiveId to r) }
                            }
                            runningId = null
                        }
                    },
                )
            }
        }

        // issue #131 — alpha.68: Recent eval runs from GET /api/evals
        if (evalRuns.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            PwaSectionTitle(stringResource(R.string.evals_runs_title))
            evalRuns.take(5).forEachIndexed { idx, run ->
                if (idx > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                EvalRunHistoryRow(run)
            }
        }
    }
}

@Composable
private fun EvalRunHistoryRow(run: EvalRunHistoryDto) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val (dot, color) = if (run.status == "pass") "✓" to Color(0xFF10B981) else "✗" to Color(0xFFEF4444)
        Text(dot, style = MaterialTheme.typography.labelSmall, color = color)
        Column(modifier = Modifier.weight(1f)) {
            Text(run.name, style = MaterialTheme.typography.bodySmall)
            if (run.createdAt.isNotBlank()) {
                Text(run.createdAt, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        ScoreBadge(run.score)
    }
}

@Composable
private fun EvalSuiteRow(
    suite: EvalSuiteDto,
    running: Boolean,
    result: EvalRunResultDto?,
    onRun: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(suite.name, style = MaterialTheme.typography.bodySmall)
                suite.lastRun?.let { lr ->
                    Text(lr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            suite.lastScore?.let { score ->
                ScoreBadge(score)
            }
            if (running) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                FilledTonalButton(
                    onClick = onRun,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFF3B82F6).copy(alpha = 0.18f),
                        contentColor = Color(0xFF3B82F6),
                    ),
                ) { Text(stringResource(R.string.evals_run)) }
            }
        }

        // Result display after run
        result?.let { r ->
            Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ScoreBadge(r.score)
                Text(
                    stringResource(R.string.evals_passed) + ": ${r.passed}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF10B981),
                )
                Text(
                    stringResource(R.string.evals_failed) + ": ${r.failed}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (r.failed > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ScoreBadge(score: Double) {
    val (bg, text) = when {
        score >= 0.8 -> Color(0xFF10B981) to Color(0xFF10B981)
        score >= 0.5 -> Color(0xFFF59E0B) to Color(0xFFF59E0B)
        else -> Color(0xFFEF4444) to Color(0xFFEF4444)
    }
    Box(
        modifier = Modifier
            .background(bg.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            "%.0f%%".format(score * 100),
            style = MaterialTheme.typography.labelSmall,
            color = text,
        )
    }
}
