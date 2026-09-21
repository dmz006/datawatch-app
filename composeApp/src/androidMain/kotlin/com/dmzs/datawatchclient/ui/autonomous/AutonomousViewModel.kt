package com.dmzs.datawatchclient.ui.autonomous

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.domain.Session
import com.dmzs.datawatchclient.prefs.ActiveServerStore
import com.dmzs.datawatchclient.transport.TransportError
import com.dmzs.datawatchclient.transport.dto.AutomataTypeDto
import com.dmzs.datawatchclient.transport.dto.AutomataTypeRequestDto
import com.dmzs.datawatchclient.transport.dto.ClonePrdToTemplateRequestDto
import com.dmzs.datawatchclient.transport.dto.NewPrdRequestDto
import com.dmzs.datawatchclient.transport.dto.PrdDto
import com.dmzs.datawatchclient.transport.dto.RuleProposalDto
import com.dmzs.datawatchclient.transport.dto.ScanResultDto
import com.dmzs.datawatchclient.transport.ws.PrdHub
import com.dmzs.datawatchclient.ui.common.ProfileResolver
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
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
    public data class FileViewerState(
        val path: String,
        val content: String? = null,
        val loading: Boolean = true,
        val error: Boolean = false,
    )

    public data class UiState(
        val loading: Boolean = true,
        val prds: List<PrdDto> = emptyList(),
        val banner: String? = null,
        val fileViewer: FileViewerState? = null,
        /** Backend names from /api/backends — used for LLM dropdowns. */
        val backends: List<String> = emptyList(),
        /** Permission modes from /api/llm/claude/permission_modes (v5.27.5+; empty on older daemons). */
        val permissionModes: List<String> = emptyList(),
        /** Model names from /api/ollama/models — populated on load; empty when Ollama not configured. */
        val ollamaModels: List<String> = emptyList(),
        /** Model IDs from /api/openwebui/models — populated on load; empty when OpenWebUI not configured. */
        val openWebUiModels: List<String> = emptyList(),
        /** Latest scan result for the open PRD (v0.62.0). */
        val scanResult: ScanResultDto? = null,
        val scanLoading: Boolean = false,
        /** Proposed rules from proposeRules (v0.62.0). */
        val proposedRules: RuleProposalDto? = null,
        /** Type registry (v0.63.0). */
        val automataTypes: List<AutomataTypeDto> = emptyList(),
        /** Multi-select set for bulk actions (v0.76.0). */
        val selectedIds: Set<String> = emptySet(),
        /** PRD id pending cancel confirmation; null = no dialog. Sprint 24 (BL293). */
        val confirmCancelId: String? = null,
        /** Sprint 30 — batch cancel confirm dialog pending. */
        val showBatchCancelConfirm: Boolean = false,
        /** Sprint 30 — batch hard-delete confirm dialog pending. */
        val showBatchDeleteConfirm: Boolean = false,
        /** All enabled profiles for the server picker. */
        val allProfiles: List<ServerProfile> = emptyList(),
        /** True when the user has selected "All servers" mode. */
        val allServersMode: Boolean = false,
        /** The currently active single-server profile (null in all-servers mode). */
        val activeProfile: ServerProfile? = null,
        /** prdId → profile displayName; populated only in all-servers mode. */
        val prdProfileNames: Map<String, String> = emptyMap(),
        /** Orchestrator DAG graph for the currently-open PRD (#184). */
        val prdGraph: com.dmzs.datawatchclient.transport.dto.OrchestratorGraphDto? = null,
        val prdGraphLoading: Boolean = false,
        /** BL386 — memory_report for the open PRD; null until loaded or if not available. */
        val memoryReport: String? = null,
        val memoryReportLoading: Boolean = false,
        /** BL383/385 — scoped memory recall results for the open PRD (#183). */
        val memoryRecallResults: List<com.dmzs.datawatchclient.transport.dto.ScopedMemoryEntryDto> = emptyList(),
        val memoryRecallLoading: Boolean = false,
        /** PRD progress stats — all observer envelopes (keyed by session_id) for per-story CPU/RSS. Updated every 5s while PRD is running/decomposing. */
        val prdEnvelopes: List<com.dmzs.datawatchclient.transport.dto.StatEnvelopeDto> = emptyList(),
        /** Live compute node detail for the PRD's active backend (GPU, CPU, Mem). */
        val prdComputeNodeDetail: com.dmzs.datawatchclient.transport.dto.ComputeNodeDetailDto? = null,
        /** Name of the compute node currently providing stats, for display. */
        val prdComputeNodeRef: String? = null,
    )

    private val _state = MutableStateFlow(UiState())

    // Lazy to avoid touching ServiceLocator at VM construction time — tests stub the
    // resolver but don't init ServiceLocator (no Android appContext available).
    @Suppress("ktlint:standard:property-naming")
    private val _allProfiles: StateFlow<List<ServerProfile>> by lazy {
        ServiceLocator.profileRepository.observeAll()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    }

    @Suppress("ktlint:standard:property-naming")
    private val _activeId: StateFlow<String?> by lazy {
        ServiceLocator.activeServerStore.observe()
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    }

    @Suppress("ktlint:standard:property-naming")
    private val _allServersMode: StateFlow<Boolean> by lazy {
        _activeId.map { it == ActiveServerStore.SENTINEL_ALL_SERVERS }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    }

    @Suppress("ktlint:standard:property-naming")
    private val _computedActiveProfile: StateFlow<ServerProfile?> by lazy {
        combine(_allProfiles, _activeId) { profiles, storedId ->
            val enabled = profiles.filter { it.enabled }
            if (storedId == ActiveServerStore.SENTINEL_ALL_SERVERS) return@combine null
            storedId?.let { id -> enabled.firstOrNull { it.id == id } } ?: enabled.firstOrNull()
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    }

    // When ServiceLocator is not yet initialized (unit-test environment), expose
    // _state directly so tests can read loading/prds/banner/selectedIds without
    // triggering the database + SharedPrefs initialization.
    public val state: StateFlow<UiState> =
        run {
            if (!ServiceLocator.isInitialized) {
                _state.asStateFlow()
            } else {
                combine(
                    _state,
                    _allProfiles,
                    _allServersMode,
                    _computedActiveProfile,
                ) { s, profiles, allMode, activeProf ->
                    s.copy(
                        allProfiles = profiles.filter { it.enabled },
                        allServersMode = allMode,
                        activeProfile = activeProf,
                    )
                }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState())
            }
        }

    /** Reachability of the active single-server profile (null = probing). */
    public val reachable: StateFlow<Boolean?> by lazy {
        _computedActiveProfile
            .flatMapLatest { profile ->
                if (profile == null) {
                    flowOf<Boolean?>(null)
                } else {
                    ServiceLocator.transportFor(profile).isReachable.map { it as Boolean? }
                }
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    }

    /** Epoch-ms of the last successful probe; updated whenever [reachable] flips to true. */
    public val lastProbeEpochMs: StateFlow<Long?> by lazy {
        reachable
            .runningFold(null as Long?) { acc, r -> if (r == true) System.currentTimeMillis() else acc }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    }

    init {
        if (ServiceLocator.isInitialized) {
            viewModelScope.launch {
                _activeId.collect { _ ->
                    refresh()
                    loadAutomataTypes()
                }
            }
        }
    }

    /** Fetch PRD list and backends list together. */
    public fun refresh() {
        if (ServiceLocator.isInitialized && _activeId.value == ActiveServerStore.SENTINEL_ALL_SERVERS) {
            refreshAllServers()
            return
        }
        viewModelScope.launch {
            val (_, transport) =
                resolver.resolve() ?: run {
                    _state.value = UiState(loading = false, banner = "No enabled server.")
                    return@launch
                }
            // Fetch PRD list and auxiliary data in parallel so slow/missing
            // backends or permission-modes endpoints don't block the list.
            val prdsResult: Result<com.dmzs.datawatchclient.transport.dto.PrdListDto>
            val backendsResult: List<String>
            val permModesResult: List<String>
            val ollamaResult: List<String>
            val openWebUiResult: List<String>
            coroutineScope {
                val prds = async { transport.listPrds() }
                val backends = async { transport.listBackends().getOrNull()?.llm.orEmpty() }
                val permModes = async { transport.listClaudePermissionModes().getOrElse { emptyList() } }
                val ollama = async { transport.listOllamaModels().getOrElse { emptyList() } }
                val openWebUi = async { transport.listOpenWebUiModels().getOrElse { emptyList() } }
                prdsResult = prds.await()
                backendsResult = backends.await()
                permModesResult = permModes.await()
                ollamaResult = ollama.await()
                openWebUiResult = openWebUi.await()
            }
            prdsResult.fold(
                onSuccess = { dto ->
                    _state.value =
                        UiState(
                            loading = false,
                            prds = dto.prds,
                            backends = backendsResult,
                            permissionModes = permModesResult,
                            ollamaModels = ollamaResult,
                            openWebUiModels = openWebUiResult,
                        )
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

    private fun refreshAllServers() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val profiles = ServiceLocator.profileRepository.observeAll().first().filter { it.enabled }
            val merged = mutableListOf<PrdDto>()
            val nameMap = mutableMapOf<String, String>()
            val errors = mutableListOf<String>()
            coroutineScope {
                profiles.map { profile ->
                    async {
                        ServiceLocator.transportFor(profile).listPrds().fold(
                            onSuccess = { dto ->
                                synchronized(merged) {
                                    dto.prds.forEach { prd ->
                                        merged.add(prd)
                                        nameMap[prd.id] = profile.displayName
                                    }
                                }
                            },
                            onFailure = { err ->
                                // 404 = autonomous not enabled on this server — skip silently
                                if (err !is TransportError.NotFound) {
                                    synchronized(errors) {
                                        errors += "${profile.displayName}: ${err.message ?: err::class.simpleName}"
                                    }
                                }
                            },
                        )
                    }
                }.awaitAll()
            }
            _state.value =
                _state.value.copy(
                    loading = false,
                    prds = merged.toList(),
                    prdProfileNames = nameMap.toMap(),
                    banner =
                        if (errors.isEmpty()) null
                        else "Some servers unreachable: " + errors.take(3).joinToString("; "),
                )
        }
    }

    private fun prdOp(
        label: String,
        block: suspend (com.dmzs.datawatchclient.transport.TransportClient) -> Result<Unit>,
    ) {
        viewModelScope.launch {
            val (_, transport) = resolver.resolve() ?: return@launch
            block(transport).fold(
                onSuccess = { refresh() },
                onFailure = { err ->
                    _state.value =
                        _state.value.copy(
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

    /**
     * Fetches the full PRD detail (all stories) and patches it into the prds list.
     * The list endpoint can truncate stories[]; the detail endpoint is authoritative (#163).
     */
    public fun fetchFullPrd(prdId: String) {
        viewModelScope.launch {
            val (_, transport) = resolver.resolve() ?: return@launch
            transport.getPrd(prdId).onSuccess { full ->
                _state.value = _state.value.copy(
                    prds = _state.value.prds.map { if (it.id == prdId) full else it },
                )
            }
        }
    }

    public fun fetchPrdGraph(prdId: String) {
        _state.value = _state.value.copy(prdGraphLoading = true, prdGraph = null)
        viewModelScope.launch {
            val (_, transport) = resolver.resolve() ?: run {
                _state.value = _state.value.copy(prdGraphLoading = false)
                return@launch
            }
            transport.orchestratorGraph(prdId).fold(
                onSuccess = { graph ->
                    _state.value = _state.value.copy(prdGraph = graph, prdGraphLoading = false)
                },
                onFailure = {
                    _state.value = _state.value.copy(prdGraph = null, prdGraphLoading = false)
                },
            )
        }
    }

    public fun clearPrdGraph() {
        _state.value = _state.value.copy(prdGraph = null, prdGraphLoading = false)
    }

    /** BL386 — load the auto-generated memory report for a completed PRD. */
    public fun fetchMemoryReport(prdId: String) {
        _state.value = _state.value.copy(memoryReportLoading = true, memoryReport = null)
        viewModelScope.launch {
            val (_, transport) = resolver.resolve() ?: run {
                _state.value = _state.value.copy(memoryReportLoading = false)
                return@launch
            }
            transport.getPrdMemoryReport(prdId).fold(
                onSuccess = { report ->
                    _state.value = _state.value.copy(memoryReport = report, memoryReportLoading = false)
                },
                onFailure = {
                    _state.value = _state.value.copy(memoryReport = null, memoryReportLoading = false)
                },
            )
        }
    }

    public fun clearMemoryReport() {
        _state.value = _state.value.copy(memoryReport = null, memoryReportLoading = false)
    }

    /** BL385/#183 — search scoped memories for the open PRD. */
    public fun recallPrdMemory(prdId: String, query: String, projectDir: String?) {
        if (query.isBlank()) return
        _state.value = _state.value.copy(memoryRecallLoading = true, memoryRecallResults = emptyList())
        viewModelScope.launch {
            val (_, transport) = resolver.resolve() ?: run {
                _state.value = _state.value.copy(memoryRecallLoading = false)
                return@launch
            }
            transport.scopesRecall(query = query, projectDir = projectDir, prdId = prdId).fold(
                onSuccess = { results ->
                    _state.value = _state.value.copy(memoryRecallResults = results, memoryRecallLoading = false)
                },
                onFailure = {
                    _state.value = _state.value.copy(memoryRecallResults = emptyList(), memoryRecallLoading = false)
                },
            )
        }
    }

    public fun clearMemoryRecall() {
        _state.value = _state.value.copy(memoryRecallResults = emptyList(), memoryRecallLoading = false)
    }

    /** BL386 — hard-delete with memory strategy. */
    public fun hardDeletePrdWithMemory(
        prdId: String,
        memoryStrategy: String,
        archiveRoleFilter: List<String> = emptyList(),
        archiveToScope: String? = null,
    ) {
        prdOp("Delete") {
            it.deletePrd(
                prdId = prdId,
                hard = true,
                memoryStrategy = memoryStrategy.takeIf { s -> s != "keep" },
                archiveRoleFilter = archiveRoleFilter.takeIf { it.isNotEmpty() },
                archiveToScope = archiveToScope,
            )
        }
    }

    public fun approve(prdId: String, note: String? = null) {
        if (note.isNullOrBlank()) {
            prdOp("Approve") { it.prdAction(prdId, "approve") }
        } else {
            val body = buildJsonObject { put("note", JsonPrimitive(note)) }
            prdOp("Approve") { it.prdAction(prdId, "approve", body) }
        }
    }

    public fun reject(
        prdId: String,
        reason: String,
    ) {
        val body = buildJsonObject { put("reason", JsonPrimitive(reason)) }
        prdOp("Reject") { it.prdAction(prdId, "reject", body) }
    }

    public fun decompose(prdId: String) {
        prdOp("Decompose") { it.prdAction(prdId, "decompose") }
    }

    public fun setLlm(
        prdId: String,
        backend: String,
        effort: String,
        model: String,
        decompositionProfile: String = "",
        decompositionModel: String = "",
    ) {
        val body =
            buildJsonObject {
                if (backend.isNotBlank()) put("backend", JsonPrimitive(backend))
                if (effort.isNotBlank()) put("effort", JsonPrimitive(effort))
                if (model.isNotBlank()) put("model", JsonPrimitive(model))
                if (decompositionProfile.isNotBlank()) put("decomposition_profile", JsonPrimitive(decompositionProfile))
                if (decompositionModel.isNotBlank()) put("decomposition_model", JsonPrimitive(decompositionModel))
                put("actor", JsonPrimitive("operator"))
            }
        prdOp("Set LLM") { it.prdAction(prdId, "set_llm", body) }
    }

    public fun resetToDraft(prdId: String) {
        prdOp("Reset to draft") { it.prdAction(prdId, "reset_to_draft") }
    }

    public fun resetTask(prdId: String, taskId: String) {
        viewModelScope.launch {
            val (_, transport) = resolver.resolve() ?: return@launch
            transport.resetPrdTask(prdId, taskId).fold(
                onSuccess = { refresh() },
                onFailure = { err ->
                    _state.value =
                        _state.value.copy(
                            banner = "Reset task failed — ${err.message ?: err::class.simpleName}",
                        )
                },
            )
        }
    }

    public fun requeueTask(prdId: String, taskId: String) {
        viewModelScope.launch {
            val (_, transport) = resolver.resolve() ?: return@launch
            transport.requeuePrdTask(prdId, taskId).fold(
                onSuccess = { refresh() },
                onFailure = { err ->
                    _state.value =
                        _state.value.copy(
                            banner = "Requeue task failed — ${err.message ?: err::class.simpleName}",
                        )
                },
            )
        }
    }

    public fun cancelStory(prdId: String, storyId: String, reason: String? = null) {
        viewModelScope.launch {
            val (_, transport) = resolver.resolve() ?: return@launch
            transport.cancelPrdStory(prdId, storyId, reason).fold(
                onSuccess = { refresh() },
                onFailure = { err ->
                    _state.value =
                        _state.value.copy(
                            banner = "Cancel story failed — ${err.message ?: err::class.simpleName}",
                        )
                },
            )
        }
    }

    public fun cancelTask(prdId: String, taskId: String, reason: String? = null) {
        viewModelScope.launch {
            val (_, transport) = resolver.resolve() ?: return@launch
            transport.cancelPrdTask(prdId, taskId, reason).fold(
                onSuccess = { refresh() },
                onFailure = { err ->
                    _state.value =
                        _state.value.copy(
                            banner = "Cancel task failed — ${err.message ?: err::class.simpleName}",
                        )
                },
            )
        }
    }

    public fun editTask(prdId: String, taskId: String, newSpec: String) {
        viewModelScope.launch {
            val (_, transport) = resolver.resolve() ?: return@launch
            transport.editPrdTask(prdId, taskId, newSpec).fold(
                onSuccess = { refresh() },
                onFailure = { err ->
                    _state.value =
                        _state.value.copy(
                            banner = "Edit task failed — ${err.message ?: err::class.simpleName}",
                        )
                },
            )
        }
    }

    public fun runPrd(prdId: String) {
        prdOp("Run") { it.prdAction(prdId, "run") }
    }

    /** Show confirm-cancel dialog for the given PRD. Dismiss without action via [dismissCancelConfirm]. */
    public fun requestCancel(prdId: String) {
        _state.value = _state.value.copy(confirmCancelId = prdId)
    }

    /** Dismiss the confirm-cancel dialog without cancelling. */
    public fun dismissCancelConfirm() {
        _state.value = _state.value.copy(confirmCancelId = null)
    }

    /** Sprint 30 — show batch-cancel confirmation dialog. */
    public fun requestBatchCancelConfirm() {
        _state.value = _state.value.copy(showBatchCancelConfirm = true)
    }

    /** Sprint 30 — show batch hard-delete confirmation dialog. */
    public fun requestBatchDeleteConfirm() {
        _state.value = _state.value.copy(showBatchDeleteConfirm = true)
    }

    /** Sprint 30 — dismiss whichever batch confirm dialog is open without acting. */
    public fun dismissBatchConfirm() {
        _state.value = _state.value.copy(showBatchCancelConfirm = false, showBatchDeleteConfirm = false)
    }

    /** Execute the soft-cancel after the user confirmed via the dialog. */
    public fun cancelPrd(prdId: String) {
        _state.value = _state.value.copy(confirmCancelId = null)
        prdOp("Cancel") { it.deletePrd(prdId, hard = false) }
    }

    public fun requestRevision(
        prdId: String,
        note: String,
    ) {
        val body = buildJsonObject { put("note", JsonPrimitive(note)) }
        prdOp("Request revision") { it.prdAction(prdId, "request_revision", body) }
    }

    public fun editPrd(
        prdId: String,
        title: String?,
        spec: String?,
        permissionMode: String? = null,
    ) {
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
                    _state.value =
                        _state.value.copy(
                            scanLoading = false,
                            banner = "Scan failed — ${err.message ?: err::class.simpleName}",
                        )
                },
            )
        }
    }

    public fun createFixPrd(
        prdId: String,
        onSuccess: (String) -> Unit,
    ) {
        viewModelScope.launch {
            val (_, transport) = resolver.resolve() ?: return@launch
            transport.createFixPrd(prdId).fold(
                onSuccess = { prd ->
                    refresh()
                    onSuccess(prd.id)
                },
                onFailure = { err ->
                    _state.value =
                        _state.value.copy(
                            banner = "Fix PRD failed — ${err.message ?: err::class.simpleName}",
                        )
                },
            )
        }
    }

    public fun proposeRules(prdId: String) {
        viewModelScope.launch {
            val (_, transport) = resolver.resolve() ?: return@launch
            transport.proposeRules(prdId).fold(
                onSuccess = { _state.value = _state.value.copy(proposedRules = it) },
                onFailure = { err ->
                    _state.value =
                        _state.value.copy(
                            banner = "Propose rules failed — ${err.message ?: err::class.simpleName}",
                        )
                },
            )
        }
    }

    public fun clearProposedRules() {
        _state.value = _state.value.copy(proposedRules = null)
    }

    public fun clearScan() {
        _state.value = _state.value.copy(scanResult = null, scanLoading = false, proposedRules = null)
    }

    public fun setPrdType(
        prdId: String,
        type: String,
    ) {
        val body = buildJsonObject { put("type", JsonPrimitive(type)) }
        prdOp("Set type") { it.prdAction(prdId, "set_type", body) }
    }

    public fun setPrdGuidedMode(
        prdId: String,
        guidedMode: Boolean,
    ) {
        val body = buildJsonObject { put("guided_mode", JsonPrimitive(guidedMode)) }
        prdOp("Set guided mode") { it.prdAction(prdId, "set_guided_mode", body) }
    }

    public fun setPrdSkills(
        prdId: String,
        skills: List<String>,
    ) {
        val body = buildJsonObject { put("skills", buildJsonArray { skills.forEach { add(JsonPrimitive(it)) } }) }
        prdOp("Set skills") { it.prdAction(prdId, "set_skills", body) }
    }

    public fun clonePrdToTemplate(prdId: String) {
        prdOp("Clone to template") { t ->
            t.clonePrdToTemplate(prdId, ClonePrdToTemplateRequestDto()).map { }
        }
    }

    public fun loadAutomataTypes() {
        viewModelScope.launch {
            val (_, transport) = resolver.resolve() ?: return@launch
            transport.listAutomataTypes().onSuccess { types ->
                _state.value = _state.value.copy(automataTypes = types)
            }
        }
    }

    public fun selectProfile(profileId: String) {
        ServiceLocator.activeServerStore.set(profileId)
    }

    public fun selectAllServers() {
        ServiceLocator.activeServerStore.set(ActiveServerStore.SENTINEL_ALL_SERVERS)
    }

    /** Toggle selection state for an automaton row (v0.76.0). */
    public fun toggleSelection(id: String) {
        val current = _state.value.selectedIds
        _state.value =
            _state.value.copy(
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
                onFailure = { err ->
                    _state.value =
                        _state.value.copy(
                            banner = "Create type failed — ${err.message ?: err::class.simpleName}",
                        )
                },
            )
        }
    }

    public fun deleteAutomataType(id: String) {
        viewModelScope.launch {
            val (_, transport) = resolver.resolve() ?: return@launch
            transport.deleteAutomataType(id).fold(
                onSuccess = { loadAutomataTypes() },
                onFailure = { err ->
                    _state.value =
                        _state.value.copy(
                            banner = "Delete type failed — ${err.message ?: err::class.simpleName}",
                        )
                },
            )
        }
    }

    // ---- #181: inline file viewer ----

    /**
     * Fetch [path] content and show the file viewer sheet. If [path] is relative,
     * it is resolved against [projectDir]. Viewable extensions open inline;
     * callers should fall back to a share intent for binary files.
     */
    public fun openFileViewer(path: String, projectDir: String?) {
        val absPath = if (path.startsWith("/")) path
        else "${projectDir?.trimEnd('/').orEmpty()}/$path"
        _state.value = _state.value.copy(fileViewer = FileViewerState(path = absPath, loading = true))
        viewModelScope.launch {
            val (_, transport) = resolver.resolve() ?: run {
                _state.value = _state.value.copy(
                    fileViewer = _state.value.fileViewer?.copy(loading = false, error = true),
                )
                return@launch
            }
            transport.getFileContent(absPath).fold(
                onSuccess = { content ->
                    _state.value = _state.value.copy(
                        fileViewer = _state.value.fileViewer?.copy(content = content, loading = false),
                    )
                },
                onFailure = {
                    _state.value = _state.value.copy(
                        fileViewer = _state.value.fileViewer?.copy(loading = false, error = true),
                    )
                },
            )
        }
    }

    public fun closeFileViewer() {
        _state.value = _state.value.copy(fileViewer = null)
    }

    // ---- #178: PRD detail live updates via WebSocket ----

    private var prdDetailJob: Job? = null
    private var prdProgressJob: Job? = null

    /** Poll /api/observer/envelopes + compute node detail every 5s while PRD is running/decomposing. */
    public fun startPrdProgressPolling(prdId: String) {
        prdProgressJob?.cancel()
        prdProgressJob = viewModelScope.launch {
            while (true) {
                val (_, transport) = resolver.resolve() ?: break
                val prd = _state.value.prds.firstOrNull { it.id == prdId }
                if (prd == null || prd.status !in setOf("running", "decomposing", "planning")) {
                    _state.value = _state.value.copy(
                        prdEnvelopes = emptyList(),
                        prdComputeNodeDetail = null,
                        prdComputeNodeRef = null,
                    )
                    break
                }
                // Fetch all envelopes in parallel with session list for compute node lookup.
                val envelopesResult: List<com.dmzs.datawatchclient.transport.dto.StatEnvelopeDto>
                val sessionsResult: List<com.dmzs.datawatchclient.domain.Session>
                coroutineScope {
                    val envs = async { transport.getAllEnvelopes().getOrElse { emptyList() } }
                    val sessions = async { transport.listSessions().getOrElse { emptyList() } }
                    envelopesResult = envs.await()
                    sessionsResult = sessions.await()
                }
                _state.value = _state.value.copy(prdEnvelopes = envelopesResult)

                // Resolve compute node: check active task sessions for compute_node_ref.
                val taskSessionIds = prd.stories
                    .flatMap { it.tasks }
                    .mapNotNull { it.sessionId }
                    .toSet()
                var cnRef = sessionsResult.firstOrNull {
                    (it.fullId in taskSessionIds || it.id in taskSessionIds) && it.computeNodeRef != null
                }?.computeNodeRef
                // Fall back to partial/suffix match for short ids stored in task.sessionId.
                if (cnRef == null && taskSessionIds.isNotEmpty()) {
                    cnRef = sessionsResult.firstOrNull { s ->
                        taskSessionIds.any { tid -> s.fullId.endsWith(tid) || tid.endsWith(s.id) }
                    }?.computeNodeRef
                }
                if (cnRef != null) {
                    val detail = transport.getComputeNodeDetail(cnRef).getOrNull()
                    _state.value = _state.value.copy(
                        prdComputeNodeDetail = detail,
                        prdComputeNodeRef = cnRef,
                    )
                }
                delay(5_000L)
            }
        }
    }

    /** Cancel PRD progress polling. Call when PRD detail closes or status leaves running/decomposing. */
    public fun stopPrdProgressPolling() {
        prdProgressJob?.cancel()
        prdProgressJob = null
        _state.value = _state.value.copy(
            prdEnvelopes = emptyList(),
            prdComputeNodeDetail = null,
            prdComputeNodeRef = null,
        )
    }

    /**
     * Start receiving `prd_update` WS events for [prdId]. Opens a dedicated
     * WS connection (sentinel subscription) so PrdHub receives frames even
     * when no session stream is active. Cancels any previous live-update job.
     */
    public fun startPrdLiveUpdates(prdId: String) {
        prdDetailJob?.cancel()
        prdDetailJob = viewModelScope.launch {
            val (profile, _) = resolver.resolve() ?: return@launch
            val wsTransport = ServiceLocator.wsTransportFor(profile)
            // Keep a WS connection alive so prd_update frames reach PrdHub via WebSocketTransport.
            // Sentinel subscription id "__autonomous__" triggers no session-specific output;
            // the server still broadcasts global events (prd_update, stats) to all WS clients.
            launch { wsTransport.events("__autonomous__").collect { } }
            // Patch in-place — no full re-render, no flicker, expanded rows preserved.
            PrdHub.flow
                .filter { it.id == prdId }
                .collect { updatedPrd ->
                    _state.value = _state.value.copy(
                        prds = _state.value.prds.map { if (it.id == updatedPrd.id) updatedPrd else it },
                    )
                }
        }
    }

    /** Cancel live updates started by [startPrdLiveUpdates]. Call when PRD detail closes. */
    public fun stopPrdLiveUpdates() {
        prdDetailJob?.cancel()
        prdDetailJob = null
    }

    // Cached active profile id for synchronous watch-toggle calls. Lazy to avoid
    // touching ServiceLocator at VM construction time (tests stub the resolver but
    // don't init ServiceLocator).
    @Suppress("ktlint:standard:property-naming")
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

    /**
     * Sprint 24 (BL293) — pinned-automata IDs for the active profile.
     * Pinned rows float to the top of the list regardless of status rank.
     */
    public val pinnedAutomataIds: StateFlow<Set<String>> by lazy {
        _activeProfileId
            .flatMapLatest { profileId ->
                if (profileId == null) {
                    flowOf(emptySet())
                } else {
                    ServiceLocator.pinnedAutomataStore.pinnedFlow(profileId)
                }
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    }

    /** Toggle the pinned state for an automaton on the active profile. */
    public fun togglePin(prdId: String) {
        val profileId = _activeProfileId.value ?: return
        val current = ServiceLocator.pinnedAutomataStore.isPinned(profileId, prdId)
        ServiceLocator.pinnedAutomataStore.setPinned(profileId, prdId, !current)
    }
}
