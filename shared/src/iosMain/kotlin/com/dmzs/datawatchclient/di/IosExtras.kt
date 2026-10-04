package com.dmzs.datawatchclient.di

import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.transport.dto.MemoryHarvestDto
import com.dmzs.datawatchclient.transport.dto.MemorySeedDto
import com.dmzs.datawatchclient.transport.dto.NewPrdRequestDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** One scoped-memory recall hit (BL385), flattened for Swift. [score] is preformatted ("" = none). */
public data class IosMemoryHit(
    val text: String,
    val role: String,
    val scope: String,
    val score: String,
)

/** One alert-rule firing (Android AlertRulesCard "Recent Firings"), flattened for Swift. */
public data class IosAlertFiring(
    val ruleName: String,
    /** "<fired_at> · <value> ≥ <threshold>[ · <pod>]" — the Android row subtitle. */
    val detail: String,
)

/**
 * App-only extras ported to iOS (parity D59–D83, Android is the reference):
 * PRD permission mode edit (D75), memory promote-to on create (D73), memory
 * report + scoped recall (D77), alert-rule firings (D70), and New Session
 * "Resume previous" (D82). All endpoints already exist on [TransportClient].
 */
public object IosExtras {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── PRD permission mode (D75) ────────────────────────────────────────

    /** GET /api/llm/claude/permission_modes — empty on older daemons / failure. */
    public fun permissionModes(
        profile: ServerProfile,
        onResult: (List<String>) -> Unit,
    ) {
        scope.launch {
            val list: List<String> =
                IosServiceLocator.transportFor(profile).listClaudePermissionModes().getOrNull().orEmpty()
            onResult(list)
        }
    }

    /**
     * PATCH title / spec / permission_mode (Android editPrd). Blank title / spec =
     * unchanged; [permissionMode] blank = unchanged (Android sends only a changed,
     * non-blank mode).
     */
    public fun editPrd(
        profile: ServerProfile,
        prdId: String,
        title: String,
        spec: String,
        permissionMode: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            IosServiceLocator.transportFor(profile).patchPrd(
                prdId = prdId,
                title = title.trim().ifBlank { null },
                spec = spec.ifBlank { null },
                permissionMode = permissionMode.trim().ifBlank { null },
            ).fold(
                onSuccess = { onSuccess() },
                onFailure = { onError(it.message ?: "Couldn't save changes.") },
            )
        }
    }

    // ── PRD create with memory seed / harvest (D73) ─────────────────────

    /**
     * Same request shape as [IosAutomata.createPrd] plus Android's memory toggles:
     * `memory_seed {enabled}` and `memory_harvest {enabled, promote_to}`
     * ([promoteTo]: session-local | story-shared | prd-shared | project-shared).
     */
    public fun createPrd(
        profile: ServerProfile,
        title: String,
        spec: String,
        projectDir: String,
        projectProfile: String,
        backend: String,
        model: String,
        effort: String,
        planningBackend: String,
        planningModel: String,
        memorySeed: Boolean,
        memoryHarvest: Boolean,
        promoteTo: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        fun String.orNull(): String? = trim().ifBlank { null }
        val seed: MemorySeedDto? = if (memorySeed) MemorySeedDto(enabled = true) else null
        val harvest: MemoryHarvestDto? =
            if (memoryHarvest) {
                MemoryHarvestDto(enabled = true, promoteTo = promoteTo.ifBlank { "story-shared" })
            } else {
                null
            }
        val request: NewPrdRequestDto =
            if (projectProfile.isNotBlank()) {
                NewPrdRequestDto(
                    name = "",
                    title = title.orNull(),
                    spec = spec.orNull(),
                    projectProfile = projectProfile,
                    memorySeed = seed,
                    memoryHarvest = harvest,
                )
            } else {
                NewPrdRequestDto(
                    name = "",
                    title = title.orNull(),
                    spec = spec.orNull(),
                    projectDir = projectDir.orNull(),
                    backend = backend.orNull(),
                    model = model.orNull(),
                    effort = effort.orNull(),
                    decompositionProfile = planningBackend.orNull(),
                    decompositionModel = planningModel.orNull(),
                    memorySeed = seed,
                    memoryHarvest = harvest,
                )
            }
        scope.launch {
            IosServiceLocator.transportFor(profile).createPrd(request).fold(
                onSuccess = { onSuccess(it) },
                onFailure = { onError(it.message ?: "Couldn't create the automaton.") },
            )
        }
    }

    // ── PRD memory report + recall (D77, BL385/386) ─────────────────────

    /** GET /api/autonomous/prds/{id}/memory-report; null when unavailable. */
    public fun memoryReport(
        profile: ServerProfile,
        prdId: String,
        onResult: (String?) -> Unit,
    ) {
        scope.launch {
            val report: String? =
                IosServiceLocator.transportFor(profile).getPrdMemoryReport(prdId).getOrNull()
            onResult(report?.takeIf { it.isNotBlank() })
        }
    }

    /** GET /api/memory/scopes/recall scoped to the PRD (and its project dir). */
    public fun recall(
        profile: ServerProfile,
        prdId: String,
        projectDir: String,
        query: String,
        onSuccess: (List<IosMemoryHit>) -> Unit,
        onError: (String) -> Unit,
    ) {
        if (query.isBlank()) {
            onSuccess(emptyList())
            return
        }
        scope.launch {
            IosServiceLocator.transportFor(profile).scopesRecall(
                query = query.trim(),
                projectDir = projectDir.trim().ifBlank { null },
                prdId = prdId,
            ).fold(
                onSuccess = { list ->
                    onSuccess(
                        list.map { e ->
                            val score: Float? = e.score
                            IosMemoryHit(
                                text = e.text,
                                role = e.role.orEmpty(),
                                scope = e.scope.orEmpty(),
                                score = if (score == null) "" else ((score * 100f).toInt() / 100f).toString(),
                            )
                        },
                    )
                },
                onFailure = { onError(it.message ?: "Recall failed.") },
            )
        }
    }

    // ── Alert-rule firings (D70) ─────────────────────────────────────────

    /** GET /api/alert-rules/firings — newest first as the server returns them, max 20. */
    public fun alertRuleFirings(
        profile: ServerProfile,
        onResult: (List<IosAlertFiring>) -> Unit,
    ) {
        scope.launch {
            val firings =
                IosServiceLocator.transportFor(profile).listAlertRuleFirings().getOrNull()?.firings.orEmpty()
            onResult(
                firings.take(20).map { f ->
                    val pod: String = f.pod?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""
                    IosAlertFiring(
                        ruleName = f.ruleName,
                        detail = "${f.firedAt} · ${f.value} ≥ ${f.threshold}$pod",
                    )
                },
            )
        }
    }

    // ── New Session with resume (D82) ────────────────────────────────────

    /**
     * [IosNewSession.submit] plus Android's `resume_id` (PWA populateResumeDropdown):
     * blank [resumeId] starts fresh. Profile (agent) mode ignores resume, as on Android.
     */
    public fun startSession(
        profile: ServerProfile,
        task: String,
        name: String,
        workingDir: String,
        projectProfile: String,
        clusterProfile: String,
        llm: String,
        computeNode: String,
        permissionMode: String,
        model: String,
        effort: String,
        chrome: Boolean,
        autoGitInit: Boolean,
        autoGitCommit: Boolean,
        resumeId: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        if (projectProfile.isNotBlank() || resumeId.isBlank()) {
            IosNewSession.submit(
                profile = profile,
                task = task,
                name = name,
                workingDir = workingDir,
                projectProfile = projectProfile,
                clusterProfile = clusterProfile,
                llm = llm,
                computeNode = computeNode,
                permissionMode = permissionMode,
                model = model,
                effort = effort,
                chrome = chrome,
                autoGitInit = autoGitInit,
                autoGitCommit = autoGitCommit,
                onSuccess = onSuccess,
                onError = onError,
            )
            return
        }
        fun String.orNull(): String? = trim().ifBlank { null }
        scope.launch {
            IosServiceLocator.transportFor(profile).startSession(
                task = task.trim(),
                workingDir = workingDir.orNull(),
                name = name.orNull(),
                resumeId = resumeId.trim(),
                autoGitInit = autoGitInit,
                autoGitCommit = autoGitCommit,
                permissionMode = permissionMode.orNull(),
                model = model.orNull(),
                claudeEffort = effort.orNull(),
                llm = llm.orNull(),
                computeNode = computeNode.orNull(),
                chrome = chrome.takeIf { it },
            ).fold(
                onSuccess = { onSuccess(it) },
                onFailure = { onError(it.message ?: "Couldn't start the session.") },
            )
        }
    }
}
