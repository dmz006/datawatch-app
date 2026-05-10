package com.dmzs.datawatchclient.ui.autonomous

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.transport.dto.AutomataTypeDto
import com.dmzs.datawatchclient.transport.dto.AutomataTypeRequestDto
import com.dmzs.datawatchclient.transport.dto.NewPrdRequestDto
import com.dmzs.datawatchclient.transport.dto.PrdDto
import com.dmzs.datawatchclient.transport.dto.RuleProposalDto
import com.dmzs.datawatchclient.transport.dto.ScanResultDto
import com.dmzs.datawatchclient.ui.common.ProfileResolver
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Backs [AutonomousScreen]. Reads /api/autonomous/prds against the
 * active server profile.
 */
@OptIn(ExperimentalCoroutinesApi::class)
public class AutonomousViewModel(
    private val resolver: ProfileResolver = ProfileResolver.Default,
) : ViewModel() {
    public data class UiState(
        val loading: Boolean = true,
        val prds: List<PrdDto> = emptyList(),
        val banner: String? = null,
        /** Backend names from /api/backends — used for LLM dropdowns. */
        val backends: List<String> = emptyList(),
        /** Permission modes from /api/llm/claude/permission_modes (v5.27.5+; empty on older daemons). */
        val permissionModes: List<String> = emptyList(),
        /** Latest scan result for the open PRD (v0.62.0). */
        val scanResult: ScanResultDto? = null,
        val scanLoading: Boolean = false,
        /** Proposed rules from proposeRules (v0.62.0). */
        val proposedRules: RuleProposalDto? = null,
        /** Type registry (v0.63.0). */
        val automataTypes: List<AutomataTypeDto> = emptyList(),
        /** Multi-select set for bulk actions (v0.76.0). */
        val selectedIds: Set<String> = emptySet(),
    )

    private val _state = MutableStateFlow(UiState())
    public val state: StateFlow<UiState> = _state

    /** Fetch PRD list and backends list together. */
    public fun refresh() {
        viewModelScope.launch {
            val (_, transport) =
                resolver.resolve() ?: run {
                    _state.value = UiState(loading = false, banner = "No enabled server.")
                    return@launch
                }
            transport.listPrds().fold(
                onSuccess = { dto ->
                    val backends = transport.listBackends()
                        .getOrNull()?.llm.orEmpty()
                    val permModes = transport.listClaudePermissionModes().getOrElse { emptyList() }
                    _state.value = UiState(loading = false, prds = dto.prds, backends = backends, permissionModes = permModes)
                },
                onFailure = { err ->
                    _state.value =
                        UiState(
                            loading = false,
                            prds = emptyList(),
                            banner = "Load failed — ${err.message ?: err::class.simpleName}",
                        )
                },
            )
        }
    }

    private fun prdOp(label: String, block: suspend (com.dmzs.datawatchclient.transport.TransportClient) -> Result<Unit>) {
        viewModelScope.launch {
            val (_, transport) = resolver.resolve() ?: return@launch
            block(transport).fold(
                onSuccess = { refresh() },
                onFailure = { err ->
                    _state.value = _state.value.copy(
                        banner = "$label failed — ${err.message ?: err::class.simpleName}",
                    )
                },
            )
        }
    }

    public fun create(req: NewPrdRequestDto) {
        viewModelScope.launch {
            val (_, transport) = resolver.resolve() ?: return@launch
            transport.createPrd(req).fold(
                onSuccess = { _ -> refresh() },
                onFailure = { err ->
                    _state.value =
                        _state.value.copy(
                            banner = "Create failed — ${err.message ?: err::class.simpleName}",
                        )
                },
            )
        }
    }

    public fun approve(prdId: String) {
        prdOp("Approve") { it.prdAction(prdId, "approve") }
    }

    public fun reject(prdId: String, reason: String) {
        val body = buildJsonObject { put("reason", JsonPrimitive(reason)) }
        prdOp("Reject") { it.prdAction(prdId, "reject", body) }
    }

    public fun decompose(prdId: String) {
        prdOp("Decompose") { it.prdAction(prdId, "decompose") }
    }

    public fun setLlm(prdId: String, backend: String, effort: String, model: String) {
        val body = buildJsonObject {
            if (backend.isNotBlank()) put("backend", JsonPrimitive(backend))
            if (effort.isNotBlank()) put("effort", JsonPrimitive(effort))
            if (model.isNotBlank()) put("model", JsonPrimitive(model))
        }
        prdOp("Set LLM") { it.prdAction(prdId, "set_llm", body) }
    }

    public fun runPrd(prdId: String) {
        prdOp("Run") { it.prdAction(prdId, "run") }
    }

    public fun cancelPrd(prdId: String) {
        prdOp("Cancel") { it.deletePrd(prdId, hard = false) }
    }

    public fun requestRevision(prdId: String, note: String) {
        val body = buildJsonObject { put("note", JsonPrimitive(note)) }
        prdOp("Request revision") { it.prdAction(prdId, "request_revision", body) }
    }

    public fun editPrd(prdId: String, title: String?, spec: String?, permissionMode: String? = null) {
        prdOp("Edit PRD") {
            it.patchPrd(
                prdId = prdId,
                title = title?.takeIf { t -> t.isNotBlank() },
                spec = spec?.takeIf { t -> t.isNotBlank() },
                permissionMode = permissionMode?.ifBlank { null },
            )
        }
    }

    public fun hardDeletePrd(prdId: String) {
        prdOp("Delete") { it.deletePrd(prdId, hard = true) }
    }

    public fun editStory(
        prdId: String,
        storyId: String,
        newTitle: String?,
        newDescription: String?,
    ) {
        viewModelScope.launch {
            val (_, transport) = resolver.resolve() ?: return@launch
            transport.editStory(
                prdId = prdId,
                storyId = storyId,
                newTitle = newTitle?.takeIf { it.isNotBlank() },
                newDescription = newDescription?.takeIf { it.isNotBlank() },
            ).fold(
                onSuccess = { refresh() },
                onFailure = { err ->
                    _state.value =
                        _state.value.copy(
                            banner = "Edit story failed — ${err.message ?: err::class.simpleName}",
                        )
                },
            )
        }
    }

    public fun editFiles(
        prdId: String,
        storyId: String,
        files: List<String>,
    ) {
        viewModelScope.launch {
            val (_, transport) = resolver.resolve() ?: return@launch
            transport.editFiles(prdId = prdId, storyId = storyId, files = files).fold(
                onSuccess = { refresh() },
                onFailure = { err ->
                    _state.value =
                        _state.value.copy(
                            banner = "Edit files failed — ${err.message ?: err::class.simpleName}",
                        )
                },
            )
        }
    }

    public fun loadScanResult(prdId: String) {
        viewModelScope.launch {
            val (_, transport) = resolver.resolve() ?: return@launch
            _state.value = _state.value.copy(scanLoading = true, scanResult = null)
            transport.getScanResult(prdId).fold(
                onSuccess = { _state.value = _state.value.copy(scanLoading = false, scanResult = it) },
                onFailure = { _state.value = _state.value.copy(scanLoading = false) },
            )
        }
    }

    public fun triggerScan(prdId: String) {
        viewModelScope.launch {
            val (_, transport) = resolver.resolve() ?: return@launch
            _state.value = _state.value.copy(scanLoading = true)
            transport.triggerScan(prdId).fold(
                onSuccess = { _state.value = _state.value.copy(scanLoading = false, scanResult = it) },
                onFailure = { err ->
                    _state.value = _state.value.copy(scanLoading = false, banner = "Scan failed — ${err.message ?: err::class.simpleName}")
                },
            )
        }
    }

    public fun createFixPrd(prdId: String, onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            val (_, transport) = resolver.resolve() ?: return@launch
            transport.createFixPrd(prdId).fold(
                onSuccess = { prd -> refresh(); onSuccess(prd.id) },
                onFailure = { err -> _state.value = _state.value.copy(banner = "Fix PRD failed — ${err.message ?: err::class.simpleName}") },
            )
        }
    }

    public fun proposeRules(prdId: String) {
        viewModelScope.launch {
            val (_, transport) = resolver.resolve() ?: return@launch
            transport.proposeRules(prdId).fold(
                onSuccess = { _state.value = _state.value.copy(proposedRules = it) },
                onFailure = { err -> _state.value = _state.value.copy(banner = "Propose rules failed — ${err.message ?: err::class.simpleName}") },
            )
        }
    }

    public fun clearProposedRules() {
        _state.value = _state.value.copy(proposedRules = null)
    }

    public fun clearScan() {
        _state.value = _state.value.copy(scanResult = null, scanLoading = false, proposedRules = null)
    }

    public fun setPrdType(prdId: String, type: String) {
        val body = buildJsonObject { put("type", JsonPrimitive(type)) }
        prdOp("Set type") { it.prdAction(prdId, "set_type", body) }
    }

    public fun setPrdGuidedMode(prdId: String, guidedMode: Boolean) {
        val body = buildJsonObject { put("guided_mode", JsonPrimitive(guidedMode)) }
        prdOp("Set guided mode") { it.prdAction(prdId, "set_guided_mode", body) }
    }

    public fun setPrdSkills(prdId: String, skills: List<String>) {
        val body = buildJsonObject { put("skills", buildJsonArray { skills.forEach { add(JsonPrimitive(it)) } }) }
        prdOp("Set skills") { it.prdAction(prdId, "set_skills", body) }
    }

    public fun loadAutomataTypes() {
        viewModelScope.launch {
            val (_, transport) = resolver.resolve() ?: return@launch
            transport.listAutomataTypes().onSuccess { types ->
                _state.value = _state.value.copy(automataTypes = types)
            }
        }
    }

    /** Toggle selection state for an automaton row (v0.76.0). */
    public fun toggleSelection(id: String) {
        val current = _state.value.selectedIds
        _state.value = _state.value.copy(
            selectedIds = if (id in current) current - id else current + id,
        )
    }

    /** Clear all multi-select selections (v0.76.0). */
    public fun clearSelection() {
        _state.value = _state.value.copy(selectedIds = emptySet())
    }

    public fun createAutomataType(req: AutomataTypeRequestDto) {
        viewModelScope.launch {
            val (_, transport) = resolver.resolve() ?: return@launch
            transport.registerAutomataType(req).fold(
                onSuccess = { loadAutomataTypes() },
                onFailure = { err -> _state.value = _state.value.copy(banner = "Create type failed — ${err.message ?: err::class.simpleName}") },
            )
        }
    }

    public fun deleteAutomataType(id: String) {
        viewModelScope.launch {
            val (_, transport) = resolver.resolve() ?: return@launch
            transport.deleteAutomataType(id).fold(
                onSuccess = { loadAutomataTypes() },
                onFailure = { err -> _state.value = _state.value.copy(banner = "Delete type failed — ${err.message ?: err::class.simpleName}") },
            )
        }
    }

    // Cached active profile id for synchronous watch-toggle calls. Lazy to avoid
    // touching ServiceLocator at VM construction time (tests stub the resolver but
    // don't init ServiceLocator).
    private val _activeProfileId: StateFlow<String?> by lazy {
        ServiceLocator.activeProfileFlow()
            .flatMapLatest { profile -> flowOf(profile?.id) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    }

    /**
     * Sprint 23 (#116) — watched-automata IDs for the active profile,
     * reactive. Empty set = no automata watched.
     */
    public val watchedAutomataIds: StateFlow<Set<String>> by lazy {
        _activeProfileId
            .flatMapLatest { profileId ->
                if (profileId == null) {
                    flowOf(emptySet())
                } else {
                    ServiceLocator.watchedAutomataStore.watchedFlow(profileId)
                }
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    }

    /** Toggle the watched state for an automaton on the active profile. */
    public fun toggleWatchAutomata(prdId: String) {
        val profileId = _activeProfileId.value ?: return
        val current = ServiceLocator.watchedAutomataStore.isWatched(profileId, prdId)
        ServiceLocator.watchedAutomataStore.setWatched(profileId, prdId, !current)
    }
}
