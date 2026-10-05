package com.dmzs.datawatchclient.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.transport.DocsTrustEntry
import com.dmzs.datawatchclient.transport.dto.DocsSearchResultDto
import com.dmzs.datawatchclient.ui.theme.LocalDatawatchColors
import com.dmzs.datawatchclient.ui.theme.PwaCard
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Datawatch inline docs: search, how-to guides, trust management.
 * Placed in Settings → General.
 */
@Composable
public fun DocsSearchCard(vm: DocsSearchViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var query by remember { mutableStateOf("") }
    var addSourceText by remember { mutableStateOf("") }
    var addSourceExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.loadAll()
    }

    PwaCard(
        id = "docs_search",
        title = stringResource(R.string.docs_search_title),
        docsAnchor = "docs-search",
        innerPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        // Search input
        OutlinedTextField(
            value = query,
            onValueChange = { q ->
                query = q
                if (q.length >= 2) {
                    vm.search(q)
                } else if (q.isEmpty()) {
                    vm.clearResults()
                }
            },
            label = { Text(stringResource(R.string.docs_search_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        // Search results
        state.results.forEach { result ->
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        result.title,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    val badgeColor =
                        if (result.indexKind == "vector") Color(0xFF00ACC1) else Color(0xFF757575)
                    Surface(color = badgeColor, shape = RoundedCornerShape(4.dp)) {
                        Text(
                            result.indexKind,
                            fontSize = 10.sp,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }
                }
                Text(
                    result.excerpt,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
        }

        // Pending trust queue (PWA loadDocsTrustPanel): title always shown,
        // "none" when empty, else select-all toolbar + per-row Trust / Dismiss.
        PwaSectionTitle(stringResource(R.string.docs_trust_pending_title))
        if (state.pending.isEmpty()) {
            DocsTrustNone(loaded = state.loaded)
        } else {
            val allSelected = state.selected.size == state.pending.size
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = allSelected,
                    onCheckedChange = { vm.selectAll(it) },
                )
                Text(stringResource(R.string.docs_trust_select_all), style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { vm.decideSelected(accept = true) }) {
                    Text(
                        stringResource(R.string.docs_trust_accept),
                        color = LocalDatawatchColors.current.success,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                TextButton(onClick = { vm.decideSelected(accept = false) }) {
                    Text(stringResource(R.string.docs_trust_dismiss), style = MaterialTheme.typography.labelSmall)
                }
            }
            HorizontalDivider()
            state.pending.forEach { entry ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = entry.source in state.selected,
                        onCheckedChange = { vm.toggle(entry.source, it) },
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            entry.source,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                        if (entry.detail.isNotEmpty()) {
                            Text(
                                entry.detail,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    TextButton(onClick = { vm.decide(listOf(entry.source), accept = true) }) {
                        Text(
                            stringResource(R.string.docs_trust_row_accept),
                            color = LocalDatawatchColors.current.success,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    TextButton(onClick = { vm.decide(listOf(entry.source), accept = false) }) {
                        Text(stringResource(R.string.docs_trust_row_dismiss), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // Trusted sources (PWA: source · granted_by · × unless `core`).
        PwaSectionTitle(stringResource(R.string.docs_trusted_sources))
        if (state.trusted.isEmpty()) {
            DocsTrustNone(loaded = state.loaded)
        }
        state.trusted.forEach { entry ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entry.source,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    entry.detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (entry.source != "core") {
                    IconButton(onClick = { vm.removeTrusted(entry.source) }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.docs_trust_remove),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }

        // Add source
        TextButton(onClick = { addSourceExpanded = !addSourceExpanded }) {
            Text(stringResource(R.string.docs_trust_add_source))
            Icon(
                if (addSourceExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
            )
        }
        if (addSourceExpanded) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = addSourceText,
                    onValueChange = { addSourceText = it },
                    label = { Text(stringResource(R.string.docs_trust_add_source_hint)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        if (addSourceText.isNotBlank()) {
                            vm.addSource(addSourceText.trim())
                            addSourceText = ""
                            addSourceExpanded = false
                        }
                    },
                    enabled = addSourceText.isNotBlank(),
                ) {
                    Text(stringResource(R.string.docs_trust_row_accept))
                }
            }
        }

        // Error hint
        state.error?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** PWA renders an italic, muted "none" for an empty pending / trusted list. */
@Composable
private fun DocsTrustNone(loaded: Boolean) {
    Text(
        if (loaded) stringResource(R.string.docs_trust_none) else stringResource(R.string.common_loading),
        style = MaterialTheme.typography.bodySmall,
        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

public class DocsSearchViewModel : ViewModel() {
    public data class UiState(
        val results: List<DocsSearchResultDto> = emptyList(),
        val pending: List<DocsTrustEntry> = emptyList(),
        val trusted: List<DocsTrustEntry> = emptyList(),
        val selected: Set<String> = emptySet(),
        val loaded: Boolean = false,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    public val state: StateFlow<UiState> = _state

    /** PWA loadDocsTrustPanel: `{pending:[…]}` + `{trusted:[…]}`. */
    public fun loadAll() {
        viewModelScope.launch {
            val transport = resolveTransport() ?: return@launch
            transport.docsTrustPendingEntries()
                .onSuccess { list ->
                    val keep = _state.value.selected.intersect(list.map { it.source }.toSet())
                    _state.value = _state.value.copy(pending = list, selected = keep)
                }.onFailure { reportError(it) }
            transport.docsTrustedEntries()
                .onSuccess { _state.value = _state.value.copy(trusted = it) }
                .onFailure { reportError(it) }
            _state.value = _state.value.copy(loaded = true)
        }
    }

    public fun search(q: String) {
        viewModelScope.launch {
            val transport = resolveTransport() ?: return@launch
            transport.docsSearch(q, limit = 10).fold(
                onSuccess = { _state.value = _state.value.copy(results = it, error = null) },
                onFailure = { reportError(it) },
            )
        }
    }

    public fun clearResults() {
        _state.value = _state.value.copy(results = emptyList())
    }

    public fun selectAll(select: Boolean) {
        _state.value =
            _state.value.copy(
                selected = if (select) _state.value.pending.map { it.source }.toSet() else emptySet(),
            )
    }

    public fun toggle(
        source: String,
        checked: Boolean,
    ) {
        val current = _state.value.selected.toMutableSet()
        if (checked) current.add(source) else current.remove(source)
        _state.value = _state.value.copy(selected = current)
    }

    /** PWA docsTrustBulkAccept / docsTrustBulkDismiss. */
    public fun decideSelected(accept: Boolean) {
        val sources = _state.value.selected.toList()
        if (sources.isEmpty()) {
            _state.value = _state.value.copy(error = "Select one or more sources first")
            return
        }
        decide(sources, accept)
    }

    /** POST /api/docs/trust/{accept|dismiss} `{sources:[…]}`. */
    public fun decide(
        sources: List<String>,
        accept: Boolean,
    ) {
        if (sources.isEmpty()) return
        viewModelScope.launch {
            val transport = resolveTransport() ?: return@launch
            transport.docsTrustDecide(sources, accept)
                .onSuccess {
                    _state.value = _state.value.copy(selected = _state.value.selected - sources.toSet(), error = null)
                    loadAll()
                }.onFailure { reportError(it) }
        }
    }

    /** DELETE /api/docs/trust/{source} — `core` is never offered for removal. */
    public fun removeTrusted(source: String) {
        if (source == "core") return
        viewModelScope.launch {
            val transport = resolveTransport() ?: return@launch
            transport.docsTrustRemove(source)
                .onSuccess {
                    _state.value = _state.value.copy(error = null)
                    loadAll()
                }.onFailure { reportError(it) }
        }
    }

    public fun addSource(source: String) {
        viewModelScope.launch {
            val transport = resolveTransport() ?: return@launch
            transport.docsTrustAdd(source).onSuccess { loadAll() }
                .onFailure { _state.value = _state.value.copy(error = "Add failed: ${it.message}") }
        }
    }

    private fun reportError(t: Throwable) {
        _state.value = _state.value.copy(error = t.message ?: t::class.simpleName)
    }

    private suspend fun resolveTransport(): com.dmzs.datawatchclient.transport.TransportClient? {
        val activeId = ServiceLocator.activeServerStore.get()
        val profiles = ServiceLocator.profileRepository.observeAll().first()
        val enabled = profiles.filter { it.enabled }
        // Fall back to first enabled profile when in all-servers mode or no match
        val profile = enabled.firstOrNull { it.id == activeId } ?: enabled.firstOrNull() ?: return null
        return ServiceLocator.transportFor(profile)
    }
}
