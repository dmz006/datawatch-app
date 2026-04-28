package com.dmzs.datawatchclient.ui.memory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.transport.dto.SpellcheckSuggestionDto
import com.dmzs.datawatchclient.transport.dto.SvoTripleDto
import com.dmzs.datawatchclient.ui.settings.Section
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Mempalace operator surfaces — datawatch v5.27.0 / issue #21.
 *
 * Three small actions live on this card so they don't clutter
 * `MemoryCard` (which is already a busy list+search surface):
 *
 * - **Sweep stale** — operator-driven similarity-decay eviction with
 *   a dry-run toggle (default on). Reports the count without removing
 *   anything until the toggle is switched off.
 * - **Spellcheck** — Levenshtein suggestions for a free-text input.
 *   Daemon never rewrites; UI shows the suggestions inline.
 * - **Extract facts** — heuristic SVO triple extractor for a free-text
 *   input. Returns `[{subject, verb, object}]` rendered as a list.
 *
 * `pin` and `wakeup` are not on this card — pin folds into a per-row
 * action on `MemoryCard` (planned v0.37.1) and wakeup is a session-
 * lifecycle helper rather than an operator UI.
 */
@Composable
public fun MempalaceActionsCard(vm: MempalaceActionsViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    Section(title = "Mempalace") {
        Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            SweepRow(state, vm)
            Spacer(Modifier.height(12.dp))
            SpellcheckRow(state, vm)
            Spacer(Modifier.height(12.dp))
            ExtractFactsRow(state, vm)
            state.banner?.let { msg ->
                Text(
                    msg,
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun SweepRow(
    state: MempalaceActionsViewModel.UiState,
    vm: MempalaceActionsViewModel,
) {
    Text(
        "Sweep stale",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = state.sweepDays,
            onValueChange = vm::setSweepDays,
            label = { Text("Older than (days)") },
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
        Spacer(Modifier.padding(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = state.sweepDryRun,
                onCheckedChange = vm::setSweepDryRun,
            )
            Text("dry-run", style = MaterialTheme.typography.labelSmall)
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        OutlinedButton(
            onClick = { vm.runSweep() },
            enabled = state.sweepDays.toIntOrNull() != null && !state.busy,
        ) {
            Text(if (state.sweepDryRun) "Estimate" else "Sweep now")
        }
    }
    state.sweepResult?.let { count ->
        Text(
            if (state.sweepDryRun) "$count entries would be removed"
            else "$count entries removed",
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SpellcheckRow(
    state: MempalaceActionsViewModel.UiState,
    vm: MempalaceActionsViewModel,
) {
    Text(
        "Spellcheck",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    OutlinedTextField(
        value = state.spellcheckText,
        onValueChange = vm::setSpellcheckText,
        label = { Text("Text") },
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        maxLines = 3,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(
            onClick = { vm.runSpellcheck() },
            enabled = state.spellcheckText.isNotBlank() && !state.busy,
        ) { Text("Check") }
    }
    state.spellcheckResult?.let { suggestions ->
        if (suggestions.isEmpty()) {
            Text(
                "No misspellings found.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            suggestions.forEach { s ->
                Text(
                    "${s.word} → ${s.suggestions.joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ExtractFactsRow(
    state: MempalaceActionsViewModel.UiState,
    vm: MempalaceActionsViewModel,
) {
    Text(
        "Extract facts",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    OutlinedTextField(
        value = state.factsText,
        onValueChange = vm::setFactsText,
        label = { Text("Text") },
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        maxLines = 4,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(
            onClick = { vm.runExtractFacts() },
            enabled = state.factsText.isNotBlank() && !state.busy,
        ) { Text("Extract") }
    }
    state.factsResult?.let { triples ->
        if (triples.isEmpty()) {
            Text(
                "No SVO triples extracted.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            triples.forEach { t ->
                Text(
                    "${t.subject} — ${t.verb} → ${t.obj}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

public class MempalaceActionsViewModel : ViewModel() {
    public data class UiState(
        val sweepDays: String = "30",
        val sweepDryRun: Boolean = true,
        val sweepResult: Int? = null,
        val spellcheckText: String = "",
        val spellcheckResult: List<SpellcheckSuggestionDto>? = null,
        val factsText: String = "",
        val factsResult: List<SvoTripleDto>? = null,
        val busy: Boolean = false,
        val banner: String? = null,
    )
    private val _state = MutableStateFlow(UiState())
    public val state: StateFlow<UiState> = _state

    public fun setSweepDays(v: String) {
        _state.value = _state.value.copy(sweepDays = v.filter { it.isDigit() })
    }
    public fun setSweepDryRun(v: Boolean) {
        _state.value = _state.value.copy(sweepDryRun = v)
    }
    public fun setSpellcheckText(v: String) {
        _state.value = _state.value.copy(spellcheckText = v)
    }
    public fun setFactsText(v: String) {
        _state.value = _state.value.copy(factsText = v)
    }

    public fun runSweep() {
        val days = _state.value.sweepDays.toIntOrNull() ?: return
        viewModelScope.launch {
            val profile = activeProfile() ?: return@launch
            _state.value = _state.value.copy(busy = true, banner = null)
            ServiceLocator.transportFor(profile).memorySweepStale(
                olderThanDays = days,
                dryRun = _state.value.sweepDryRun,
            ).fold(
                onSuccess = { count ->
                    _state.value = _state.value.copy(busy = false, sweepResult = count)
                },
                onFailure = { err ->
                    _state.value = _state.value.copy(
                        busy = false,
                        banner = "Sweep failed — ${err.message ?: err::class.simpleName}",
                    )
                },
            )
        }
    }

    public fun runSpellcheck() {
        val text = _state.value.spellcheckText.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            val profile = activeProfile() ?: return@launch
            _state.value = _state.value.copy(busy = true, banner = null)
            ServiceLocator.transportFor(profile)
                .memorySpellcheck(text = text, extraWords = emptyList()).fold(
                    onSuccess = { sug ->
                        _state.value = _state.value.copy(busy = false, spellcheckResult = sug)
                    },
                    onFailure = { err ->
                        _state.value = _state.value.copy(
                            busy = false,
                            banner = "Spellcheck failed — ${err.message ?: err::class.simpleName}",
                        )
                    },
                )
        }
    }

    public fun runExtractFacts() {
        val text = _state.value.factsText.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            val profile = activeProfile() ?: return@launch
            _state.value = _state.value.copy(busy = true, banner = null)
            ServiceLocator.transportFor(profile).memoryExtractFacts(text = text).fold(
                onSuccess = { triples ->
                    _state.value = _state.value.copy(busy = false, factsResult = triples)
                },
                onFailure = { err ->
                    _state.value = _state.value.copy(
                        busy = false,
                        banner = "Extract failed — ${err.message ?: err::class.simpleName}",
                    )
                },
            )
        }
    }

    private suspend fun activeProfile(): com.dmzs.datawatchclient.domain.ServerProfile? {
        val activeId = ServiceLocator.activeServerStore.get() ?: return null
        return ServiceLocator.profileRepository.observeAll().first()
            .firstOrNull { it.id == activeId && it.enabled }
    }
}
