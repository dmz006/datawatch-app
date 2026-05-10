package com.dmzs.datawatchclient.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.transport.dto.DocsPendingSourceDto
import com.dmzs.datawatchclient.transport.dto.DocsSearchResultDto
import com.dmzs.datawatchclient.transport.dto.DocsTrustBulkRequest
import com.dmzs.datawatchclient.transport.dto.DocsTrustedSourceDto
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * v0.75.0 S6-4 (#84, #85) — Documentation Search card.
 *
 * Full-text search via GET /api/docs/search with index_kind badge
 * (vector=teal, bm25=grey). Pending trust queue with bulk accept/dismiss
 * (GET/POST /api/docs/trust/pending, /accept, /dismiss). Trusted sources
 * collapsible list with remove (GET /api/docs/trust, DELETE /api/docs/trust/{path}).
 *
 * Placed in Settings → General after NotificationsCard.
 */
@Composable
public fun DocsSearchCard(vm: DocsSearchViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var query by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.loadPendingAndTrusted()
    }

    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .pwaCard(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            PwaSectionTitle(stringResource(R.string.docs_search_title))

            // Search input
            OutlinedTextField(
                value = query,
                onValueChange = { q ->
                    query = q
                    if (q.length >= 2) vm.search(q)
                    else if (q.isEmpty()) vm.clearResults()
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
                        // S6-4 (#85): index_kind badge — vector=teal, bm25=grey.
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

            // Pending trust queue (shown if non-empty)
            if (state.pending.isNotEmpty()) {
                PwaSectionTitle(stringResource(R.string.docs_trust_pending_title))
                val allSelected =
                    state.selected.size == state.pending.size && state.pending.isNotEmpty()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = allSelected,
                        onCheckedChange = { vm.selectAll(it) },
                    )
                    Text(stringResource(R.string.docs_trust_select_all))
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { vm.trustSelected() }) {
                        Text(stringResource(R.string.docs_trust_accept))
                    }
                    TextButton(onClick = { vm.dismissSelected() }) {
                        Text(stringResource(R.string.docs_trust_dismiss))
                    }
                }
                state.pending.forEach { source ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = source.path in state.selected,
                            onCheckedChange = { vm.toggle(source.path, it) },
                        )
                        Text(
                            source.path,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        source.reason?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Trusted sources (collapsible)
            if (state.trusted.isNotEmpty()) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(stringResource(R.string.docs_trusted_sources, state.trusted.size))
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                    )
                }
                if (expanded) {
                    state.trusted.forEach { source ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                source.path,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            IconButton(onClick = { vm.removeTrusted(source.path) }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.docs_trust_remove),
                                )
                            }
                        }
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
}

public class DocsSearchViewModel : ViewModel() {
    public data class UiState(
        val results: List<DocsSearchResultDto> = emptyList(),
        val pending: List<DocsPendingSourceDto> = emptyList(),
        val trusted: List<DocsTrustedSourceDto> = emptyList(),
        val selected: Set<String> = emptySet(),
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    public val state: StateFlow<UiState> = _state

    public fun loadPendingAndTrusted() {
        viewModelScope.launch {
            val transport = resolveTransport() ?: return@launch
            transport.docsPendingList().onSuccess { _state.value = _state.value.copy(pending = it, selected = emptySet()) }
            transport.docsTrustedList().onSuccess { _state.value = _state.value.copy(trusted = it) }
        }
    }

    public fun search(q: String) {
        viewModelScope.launch {
            val transport = resolveTransport() ?: return@launch
            transport.docsSearch(q, limit = 10).fold(
                onSuccess = { _state.value = _state.value.copy(results = it, error = null) },
                onFailure = { _state.value = _state.value.copy(error = it.message ?: it::class.simpleName) },
            )
        }
    }

    public fun clearResults() {
        _state.value = _state.value.copy(results = emptyList())
    }

    public fun selectAll(select: Boolean) {
        _state.value = _state.value.copy(
            selected = if (select) _state.value.pending.map { it.path }.toSet() else emptySet(),
        )
    }

    public fun toggle(path: String, checked: Boolean) {
        val current = _state.value.selected.toMutableSet()
        if (checked) current.add(path) else current.remove(path)
        _state.value = _state.value.copy(selected = current)
    }

    public fun trustSelected() {
        val paths = _state.value.selected.toList()
        if (paths.isEmpty()) return
        viewModelScope.launch {
            val transport = resolveTransport() ?: return@launch
            transport.docsTrustAccept(paths).onSuccess { loadPendingAndTrusted() }
        }
    }

    public fun dismissSelected() {
        val paths = _state.value.selected.toList()
        if (paths.isEmpty()) return
        viewModelScope.launch {
            val transport = resolveTransport() ?: return@launch
            transport.docsTrustDismiss(paths).onSuccess { loadPendingAndTrusted() }
        }
    }

    public fun removeTrusted(path: String) {
        viewModelScope.launch {
            val transport = resolveTransport() ?: return@launch
            transport.docsTrustRemove(path).onSuccess { loadPendingAndTrusted() }
        }
    }

    private suspend fun resolveTransport(): com.dmzs.datawatchclient.transport.TransportClient? {
        val activeId = ServiceLocator.activeServerStore.get()
        val profile =
            ServiceLocator.profileRepository.observeAll().first()
                .firstOrNull { it.id == activeId && it.enabled } ?: return null
        return ServiceLocator.transportFor(profile)
    }
}
