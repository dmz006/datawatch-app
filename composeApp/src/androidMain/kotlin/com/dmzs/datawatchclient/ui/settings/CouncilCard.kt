package com.dmzs.datawatchclient.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import com.dmzs.datawatchclient.transport.dto.CouncilConfigDto
import com.dmzs.datawatchclient.transport.dto.CouncilPersonaDto
import com.dmzs.datawatchclient.transport.dto.CouncilRunDto
import com.dmzs.datawatchclient.transport.dto.StartCouncilRunRequest
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CouncilCard() {
    var personas by remember { mutableStateOf<List<CouncilPersonaDto>>(emptyList()) }
    var runs by remember { mutableStateOf<List<CouncilRunDto>>(emptyList()) }
    var config by remember { mutableStateOf(CouncilConfigDto()) }
    var proposal by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("debate") }
    var selectedPersonas by remember { mutableStateOf<Set<String>>(emptySet()) }
    var expandedRunId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun loadAll() {
        val activeId = ServiceLocator.activeServerStore.get() ?: return
        val sp = ServiceLocator.profileRepository.observeAll().first()
            .firstOrNull { it.id == activeId && it.enabled } ?: return
        val t = ServiceLocator.transportFor(sp)
        t.councilListPersonas().onSuccess { personas = it }
        t.councilListRuns().onSuccess { runs = it }
        t.councilGetConfig().onSuccess { config = it }
    }

    LaunchedEffect(Unit) { runCatching { loadAll() } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .pwaCard()
            .padding(12.dp),
    ) {
        PwaSectionTitle(stringResource(R.string.council_title))

        // ── PERSONAS section ──────────────────────────────────────────────
        if (personas.isNotEmpty()) {
            Text(
                stringResource(R.string.council_personas_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                personas.forEach { persona ->
                    FilterChip(
                        selected = persona.enabled && persona.name in selectedPersonas,
                        onClick = {
                            selectedPersonas = if (persona.name in selectedPersonas) {
                                selectedPersonas - persona.name
                            } else {
                                selectedPersonas + persona.name
                            }
                        },
                        label = { Text(persona.name, style = MaterialTheme.typography.labelSmall) },
                        enabled = persona.enabled,
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        // ── FIREHOSE TOGGLE (#97) ─────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.council_firehose_label),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = config.commFirehose,
                onCheckedChange = { newVal ->
                    val updated = config.copy(commFirehose = newVal)
                    config = updated
                    scope.launch {
                        runCatching {
                            val activeId = ServiceLocator.activeServerStore.get() ?: return@runCatching
                            val sp = ServiceLocator.profileRepository.observeAll().first()
                                .firstOrNull { it.id == activeId && it.enabled } ?: return@runCatching
                            ServiceLocator.transportFor(sp).councilUpdateConfig(updated)
                                .onSuccess { config = it }
                        }
                    }
                },
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // ── START RUN form ────────────────────────────────────────────────
        OutlinedTextField(
            value = proposal,
            onValueChange = { proposal = it },
            label = { Text(stringResource(R.string.council_run_proposal)) },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            minLines = 2,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = mode == "debate",
                onClick = { mode = "debate" },
                label = { Text(stringResource(R.string.council_mode_debate)) },
            )
            FilterChip(
                selected = mode == "quick",
                onClick = { mode = "quick" },
                label = { Text(stringResource(R.string.council_mode_quick)) },
            )
        }
        FilledTonalButton(
            onClick = {
                if (proposal.isNotBlank()) {
                    scope.launch {
                        runCatching {
                            val activeId = ServiceLocator.activeServerStore.get() ?: return@runCatching
                            val sp = ServiceLocator.profileRepository.observeAll().first()
                                .firstOrNull { it.id == activeId && it.enabled } ?: return@runCatching
                            val req = StartCouncilRunRequest(
                                proposal = proposal.trim(),
                                mode = mode,
                                personas = selectedPersonas.toList(),
                            )
                            ServiceLocator.transportFor(sp).councilStartRun(req)
                                .onSuccess { run ->
                                    runs = runs + run
                                    proposal = ""
                                }
                        }
                    }
                }
            },
            enabled = proposal.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.council_run_btn)) }

        // ── ACTIVE RUNS section ───────────────────────────────────────────
        if (runs.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                stringResource(R.string.council_results_title),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            runs.forEachIndexed { idx, run ->
                if (idx > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                CouncilRunRow(
                    run = run,
                    expanded = expandedRunId == run.id,
                    onToggle = { expandedRunId = if (expandedRunId == run.id) null else run.id },
                    onCancel = {
                        scope.launch {
                            runCatching {
                                val activeId = ServiceLocator.activeServerStore.get() ?: return@runCatching
                                val sp = ServiceLocator.profileRepository.observeAll().first()
                                    .firstOrNull { it.id == activeId && it.enabled } ?: return@runCatching
                                ServiceLocator.transportFor(sp).councilStopRun(run.id)
                                    .onSuccess { runs = runs.filter { it.id != run.id } }
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun CouncilRunRow(
    run: CouncilRunDto,
    expanded: Boolean,
    onToggle: () -> Unit,
    onCancel: () -> Unit,
) {
    val isActive = run.status in listOf("running", "pending", "deliberating")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                run.proposal.take(60),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            CouncilStatusBadge(run.status)
            Text(
                stringResource(R.string.council_round_label) + " ${run.round}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (expanded) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                // Milestone timeline: started / round / consensus / cancel
                run.startedAt?.let { t ->
                    MilestoneEntry(label = "Started", value = t)
                }
                if (run.round > 0) {
                    MilestoneEntry(label = stringResource(R.string.council_round_label), value = run.round.toString())
                }
                run.consensus?.let { c ->
                    Text(
                        stringResource(R.string.council_consensus),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF10B981),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(c, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
                }
                if (isActive) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.padding(top = 8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) { Text(stringResource(R.string.action_cancel)) }
                }
            }
        }
    }
}

@Composable
private fun CouncilStatusBadge(status: String) {
    val color = when (status) {
        "running", "deliberating" -> Color(0xFF3B82F6)
        "completed" -> Color(0xFF10B981)
        "cancelled", "aborted" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(status, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun MilestoneEntry(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelSmall)
    }
}
