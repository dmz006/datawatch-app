package com.dmzs.datawatchclient.di

import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.transport.dto.NewPrdRequestDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive

/** Picker data for the Launch Automaton wizard. */
public data class IosPrdWizardOptions(
    /** Execution backends from `/api/backends`. */
    val backends: List<String>,
    /** Project profiles (`kind=project`). */
    val projectProfiles: List<String>,
    /** Backend → model ids (Ollama, OpenWebUI, OpenCode, and LLM-registry kinds). */
    val modelsByBackend: Map<String, List<String>>,
    /** claude-code models / efforts (`/api/llm/claude/...`). */
    val claudeModels: List<String>,
    val efforts: List<String>,
)

/**
 * Automata operations for Swift (parity B14 wizard, B16 detail actions). Option loading
 * mirrors Android NewPrdDialog; OpenCode models are a flat list (grouping is D2).
 */
public object IosAutomata {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    public fun loadWizardOptions(
        profile: ServerProfile,
        onResult: (IosPrdWizardOptions) -> Unit,
    ) {
        scope.launch {
            val t = IosServiceLocator.transportFor(profile)
            val options =
                coroutineScope {
                    val backends = async { t.listBackends().getOrNull()?.llm.orEmpty() }
                    val projects = async { t.listKindProfiles("project").getOrNull().orEmpty() }
                    val ollama = async { t.listOllamaModels().getOrNull().orEmpty() }
                    val owui = async { t.listOpenWebUiModels().getOrNull().orEmpty() }
                    val openCode = async { t.fetchOpenCodeModels().getOrNull() }
                    val llms = async { t.listLlms().getOrNull().orEmpty() }
                    val claudeModels = async { t.listClaudeModels().getOrNull().orEmpty() }
                    val efforts = async { t.listClaudeEfforts().getOrNull().orEmpty() }

                    val models = linkedMapOf<String, List<String>>()
                    ollama.await().takeIf { it.isNotEmpty() }?.let { models["ollama"] = it }
                    owui.await().takeIf { it.isNotEmpty() }?.let { models["openwebui"] = it }
                    openCode.await()?.models?.map { it.id }?.filter { it.isNotBlank() }
                        ?.takeIf { it.isNotEmpty() }?.let { models["opencode"] = it }
                    llms.await()
                        .filter { it.enabled && it.kind !in setOf("ollama", "openwebui") }
                        .groupBy { it.kind }
                        .forEach { (kind, entries) ->
                            if (kind !in models) {
                                val ids =
                                    entries.flatMap { e -> e.models.map { p -> p.model } + listOf(e.model) }
                                        .filter { it.isNotBlank() }
                                        .distinct()
                                if (ids.isNotEmpty()) models[kind] = ids
                            }
                        }
                    IosPrdWizardOptions(
                        backends = backends.await(),
                        projectProfiles =
                            projects.await().mapNotNull { (it["name"] as? JsonPrimitive)?.content },
                        modelsByBackend = models,
                        claudeModels = claudeModels.await(),
                        efforts = efforts.await(),
                    )
                }
            onResult(options)
        }
    }

    /** Models offered for [backend]: claude-code uses the Claude list, others the per-backend map. */
    public fun modelsFor(options: IosPrdWizardOptions, backend: String): List<String> =
        when {
            backend.isBlank() -> emptyList()
            backend.contains("claude", ignoreCase = true) -> options.claudeModels
            backend.startsWith("opencode", ignoreCase = true) -> options.modelsByBackend["opencode"].orEmpty()
            else -> options.modelsByBackend[backend].orEmpty()
        }

    /**
     * POST /api/autonomous/prds. With [projectProfile] set only title/spec apply
     * (Android/PWA profile mode); otherwise directory + LLM fields. Blank = unset.
     * [onSuccess] receives the new PRD id.
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
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        fun String.orNull(): String? = trim().ifBlank { null }
        val request =
            if (projectProfile.isNotBlank()) {
                NewPrdRequestDto(
                    name = "",
                    title = title.orNull(),
                    spec = spec.orNull(),
                    projectProfile = projectProfile,
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
                )
            }
        scope.launch {
            IosServiceLocator.transportFor(profile).createPrd(request).fold(
                onSuccess = { onSuccess(it) },
                onFailure = { onError(it.message ?: "Couldn't create the automaton.") },
            )
        }
    }

    /** PATCH title and/or spec; blank = unchanged. */
    public fun editPrd(
        profile: ServerProfile,
        prdId: String,
        title: String,
        spec: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            IosServiceLocator.transportFor(profile)
                .patchPrd(prdId, title = title.trim().ifBlank { null }, spec = spec.ifBlank { null })
                .fold(
                    onSuccess = { onSuccess() },
                    onFailure = { onError(it.message ?: "Couldn't save changes.") },
                )
        }
    }

    /**
     * Hard-delete with a memory strategy ("keep" | "purge" | "archive"); archive takes an
     * optional comma-separated role-prefix filter and "project-shared" | "global-shared".
     */
    public fun deletePrd(
        profile: ServerProfile,
        prdId: String,
        strategy: String,
        roleFilter: String,
        archiveScope: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            val archive = strategy == "archive"
            IosServiceLocator.transportFor(profile).deletePrd(
                prdId = prdId,
                hard = true,
                memoryStrategy = strategy.takeIf { it != "keep" },
                archiveRoleFilter =
                    if (archive) roleFilter.split(',').map { it.trim() }.filter { it.isNotEmpty() } else null,
                archiveToScope = archiveScope.takeIf { archive && it.isNotBlank() },
            ).fold(
                onSuccess = { onSuccess() },
                onFailure = { onError(it.message ?: "Delete failed.") },
            )
        }
    }
}

/**
 * Story / task operations inside a PRD (parity B18; PWA prdStory* / prd*Task).
 * Story actions: approve | reject | cancel. Task actions: retry (reset) | cancel |
 * requeue | remove. [reason] applies to story reject/cancel and task cancel.
 */
public object IosPrdItemOps {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    public fun storyAction(
        profile: ServerProfile,
        prdId: String,
        storyId: String,
        action: String,
        reason: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            val t = IosServiceLocator.transportFor(profile)
            val result: Result<Any> =
                when (action) {
                    "remove" -> t.removeStory(prdId, storyId, actor = "operator")
                    "approve" -> t.approveStory(prdId, storyId)
                    "reject" -> t.rejectStory(prdId, storyId, reason)
                    "cancel" -> t.cancelPrdStory(prdId, storyId, reason.ifBlank { null })
                    else -> Result.failure(IllegalArgumentException("Unknown story action: $action"))
                }
            result.fold(
                onSuccess = { onSuccess() },
                onFailure = { onError(it.message ?: "Story action failed.") },
            )
        }
    }

    public fun taskAction(
        profile: ServerProfile,
        prdId: String,
        storyId: String,
        taskId: String,
        action: String,
        reason: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            val t = IosServiceLocator.transportFor(profile)
            val result: Result<Any> =
                when (action) {
                    "retry" -> t.resetPrdTask(prdId, taskId)
                    "cancel" -> t.cancelPrdTask(prdId, taskId, reason.ifBlank { null })
                    "requeue" -> t.requeuePrdTask(prdId, taskId)
                    "remove" -> t.removeTask(prdId, storyId, taskId, actor = "operator")
                    else -> Result.failure(IllegalArgumentException("Unknown task action: $action"))
                }
            result.fold(
                onSuccess = { onSuccess() },
                onFailure = { onError(it.message ?: "Task action failed.") },
            )
        }
    }
}

/**
 * Automata templates (parity B15; PWA Templates tab, Android TemplatesTab):
 * list / create / update / delete / instantiate, and clone a PRD into a template.
 */
public object IosTemplates {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    public fun list(
        profile: ServerProfile,
        onSuccess: (List<com.dmzs.datawatchclient.transport.dto.TemplateDto>) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            IosServiceLocator.transportFor(profile).listTemplates().fold(
                onSuccess = { onSuccess(it.templates) },
                onFailure = { onError(it.message ?: "Couldn't load templates.") },
            )
        }
    }

    /** Create when [id] is blank, otherwise update. [tags] is comma-separated. */
    public fun save(
        profile: ServerProfile,
        id: String,
        title: String,
        type: String,
        description: String,
        tags: String,
        spec: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val tagList = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        scope.launch {
            val t = IosServiceLocator.transportFor(profile)
            val result =
                if (id.isBlank()) {
                    t.createTemplate(
                        com.dmzs.datawatchclient.transport.dto.CreateTemplateRequestDto(
                            title = title.trim(),
                            spec = spec,
                            type = type.ifBlank { null },
                            tags = tagList,
                            description = description.trim().ifBlank { null },
                        ),
                    )
                } else {
                    t.updateTemplate(
                        id,
                        com.dmzs.datawatchclient.transport.dto.UpdateTemplateRequestDto(
                            title = title.trim(),
                            spec = spec,
                            type = type.ifBlank { null },
                            tags = tagList,
                            description = description.trim(),
                        ),
                    )
                }
            result.fold(
                onSuccess = { onSuccess() },
                onFailure = { onError(it.message ?: "Couldn't save the template.") },
            )
        }
    }

    public fun delete(
        profile: ServerProfile,
        id: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            IosServiceLocator.transportFor(profile).deleteTemplate(id).fold(
                onSuccess = { onSuccess() },
                onFailure = { onError(it.message ?: "Delete failed.") },
            )
        }
    }

    /** Instantiate into a new PRD; [onSuccess] receives the new PRD id. */
    public fun instantiate(
        profile: ServerProfile,
        id: String,
        projectDir: String,
        vars: Map<String, String>,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            IosServiceLocator.transportFor(profile).instantiateTemplate(
                id,
                com.dmzs.datawatchclient.transport.dto.InstantiateTemplateRequestDto(
                    projectDir = projectDir.trim().ifBlank { null },
                    vars = vars,
                ),
            ).fold(
                onSuccess = { onSuccess(it.id) },
                onFailure = { onError(it.message ?: "Couldn't create an automaton from this template.") },
            )
        }
    }

    public fun clonePrd(
        profile: ServerProfile,
        prdId: String,
        description: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            IosServiceLocator.transportFor(profile).clonePrdToTemplate(
                prdId,
                com.dmzs.datawatchclient.transport.dto.ClonePrdToTemplateRequestDto(
                    description = description.trim().ifBlank { null },
                    actor = "operator",
                ),
            ).fold(
                onSuccess = { onSuccess() },
                onFailure = { onError(it.message ?: "Couldn't save as a template.") },
            )
        }
    }
}

/** PRD capacity (parity B17; GET /api/capacity?prd_id=…): pools + wait queue, or null. */
public object IosPrdCapacity {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    public fun load(
        profile: ServerProfile,
        prdId: String,
        onResult: (com.dmzs.datawatchclient.transport.dto.CapacityResponseDto?) -> Unit,
    ) {
        scope.launch { onResult(IosServiceLocator.transportFor(profile).getCapacity(prdId).getOrNull()) }
    }
}

/**
 * PRD settings panel (parity B16; PWA prdSettings*): type, guided mode,
 * continue-on-story-failure ("inherit" | "on" | "off"), priority, read/write dirs
 * (comma-separated). Applies only fields that differ from the PRD; returns the
 * first error, or null.
 */
public object IosPrdSettings {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    public fun apply(
        profile: ServerProfile,
        prd: com.dmzs.datawatchclient.transport.dto.PrdDto,
        type: String,
        guidedMode: Boolean,
        continueOnFailure: String,
        priority: Int,
        readDirs: String,
        writeDirs: String,
        onDone: (String?) -> Unit,
    ) {
        fun dirs(s: String) = s.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        scope.launch {
            val t = IosServiceLocator.transportFor(profile)
            val results = mutableListOf<Result<Unit>>()
            if (type.isNotBlank() && type != (prd.type ?: "")) results += t.setPrdType(prd.id, type)
            if (guidedMode != prd.guidedMode) results += t.setPrdGuidedMode(prd.id, guidedMode)
            val cont: Boolean? = when (continueOnFailure) { "on" -> true; "off" -> false; else -> null }
            if (cont != prd.continueOnStoryFailure) results += t.setPrdContinueOnStoryFailure(prd.id, cont)
            if (priority != prd.priority) results += t.setPrdPriority(prd.id, priority)
            val rd = dirs(readDirs)
            val wd = dirs(writeDirs)
            if (rd != prd.readDirs || wd != prd.writeDirs) results += t.setPrdDirs(prd.id, rd, wd)
            onDone(results.firstOrNull { it.isFailure }?.exceptionOrNull()?.let { it.message ?: "Couldn't save settings." })
        }
    }
}

/**
 * PRD security scan + rule proposals (parity B16; PWA prd_btn_run_scan /
 * prd_btn_run_rules, Android ScanResultCard). `load` returns null when the
 * PRD has never been scanned.
 */
public object IosPrdScan {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    public fun load(
        profile: ServerProfile,
        prdId: String,
        onResult: (com.dmzs.datawatchclient.transport.dto.ScanResultDto?) -> Unit,
    ) {
        scope.launch { onResult(IosServiceLocator.transportFor(profile).getScanResult(prdId).getOrNull()) }
    }

    public fun run(
        profile: ServerProfile,
        prdId: String,
        onSuccess: (com.dmzs.datawatchclient.transport.dto.ScanResultDto) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            IosServiceLocator.transportFor(profile).triggerScan(prdId).fold(
                onSuccess = { onSuccess(it) },
                onFailure = { onError("Scan failed — ${it.message ?: "unknown error"}") },
            )
        }
    }

    public fun createFixPrd(
        profile: ServerProfile,
        prdId: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            IosServiceLocator.transportFor(profile).createFixPrd(prdId).fold(
                onSuccess = { onSuccess(it.id) },
                onFailure = { onError("Fix PRD failed — ${it.message ?: "unknown error"}") },
            )
        }
    }

    /** Proposed rules as display text (text, else diff). */
    public fun proposeRules(
        profile: ServerProfile,
        prdId: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            IosServiceLocator.transportFor(profile).proposeRules(prdId).fold(
                onSuccess = { onSuccess(it.text.ifBlank { it.diff ?: "" }) },
                onFailure = { onError("Propose rules failed — ${it.message ?: "unknown error"}") },
            )
        }
    }
}

/**
 * Story/task structure edits (parity B18; PWA story/task edit groups, Android
 * PrdDetailDialog). `files` is newline- or comma-separated. Every call reports
 * null on success or an error message.
 */
public object IosPrdItemEdit {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun parseFiles(s: String): List<String> =
        s.split('\n', ',').map { it.trim() }.filter { it.isNotEmpty() }

    private fun run(
        profile: ServerProfile,
        fallback: String,
        onDone: (String?) -> Unit,
        block: suspend (com.dmzs.datawatchclient.transport.TransportClient) -> Result<*>,
    ) {
        scope.launch {
            val r = block(IosServiceLocator.transportFor(profile))
            onDone(r.exceptionOrNull()?.let { it.message ?: fallback })
        }
    }

    public fun editStory(profile: ServerProfile, prdId: String, storyId: String, title: String, description: String, onDone: (String?) -> Unit) {
        run(profile, "Couldn't save the story.", onDone) {
            it.editStory(prdId, storyId, newTitle = title.trim(), newDescription = description.trim(), actor = "operator")
        }
    }

    public fun editStoryFiles(profile: ServerProfile, prdId: String, storyId: String, files: String, onDone: (String?) -> Unit) {
        run(profile, "Couldn't save the files.", onDone) {
            it.editFiles(prdId, storyId = storyId, files = parseFiles(files), actor = "operator")
        }
    }

    public fun editTaskFiles(profile: ServerProfile, prdId: String, taskId: String, files: String, onDone: (String?) -> Unit) {
        run(profile, "Couldn't save the files.", onDone) {
            it.editFiles(prdId, taskId = taskId, files = parseFiles(files), actor = "operator")
        }
    }

    public fun editTaskSpec(profile: ServerProfile, prdId: String, taskId: String, spec: String, onDone: (String?) -> Unit) {
        run(profile, "Couldn't save the task.", onDone) { it.editPrdTask(prdId, taskId, spec.trim()) }
    }

    public fun addStory(profile: ServerProfile, prdId: String, title: String, description: String, onDone: (String?) -> Unit) {
        run(profile, "Couldn't add the story.", onDone) {
            it.addStory(prdId, title.trim(), description.trim(), actor = "operator")
        }
    }

    public fun addTask(profile: ServerProfile, prdId: String, storyId: String, title: String, spec: String, onDone: (String?) -> Unit) {
        run(profile, "Couldn't add the task.", onDone) {
            it.addTask(prdId, storyId, title.trim(), spec.trim(), actor = "operator")
        }
    }

    /** File viewer: relative paths resolve against the PRD project dir (Android openFileViewer). */
    public fun fileContent(
        profile: ServerProfile,
        path: String,
        projectDir: String?,
        onSuccess: (String, String) -> Unit,
        onError: (String) -> Unit,
    ) {
        val abs = if (path.startsWith("/")) path else "${projectDir.orEmpty().trimEnd('/')}/$path"
        scope.launch {
            IosServiceLocator.transportFor(profile).getFileContent(abs).fold(
                onSuccess = { onSuccess(abs, it) },
                onFailure = { onError("Unable to load file") },
            )
        }
    }
}
