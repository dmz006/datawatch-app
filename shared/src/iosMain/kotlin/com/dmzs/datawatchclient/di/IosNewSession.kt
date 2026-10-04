package com.dmzs.datawatchclient.di

import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.domain.Session
import com.dmzs.datawatchclient.domain.SessionState
import com.dmzs.datawatchclient.transport.dto.StartAgentRequestDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive

/** One enabled LLM registry entry, flattened for the Swift picker. */
public data class IosLlmChoice(
    val name: String,
    val kind: String,
    val computeNodes: List<String>,
    val defaultModel: String,
) {
    val isClaude: Boolean get() = kind.lowercase().contains("claude")
}

/**
 * Everything the New Session form needs, loaded in one parallel round trip.
 * Lists are empty when the server lacks the endpoint (older daemons) — the form
 * hides the matching control.
 */
public data class IosNewSessionOptions(
    val llms: List<IosLlmChoice>,
    val permissionModes: List<String>,
    val claudeModels: List<String>,
    val claudeEfforts: List<String>,
    /** Project profile name → backend label, sorted by name. */
    val projectProfiles: List<String>,
    val clusterProfiles: List<String>,
    /** Most recent finished sessions (completed / killed / error), newest first, max 30. */
    val recentDone: List<Session>,
)

/**
 * New Session support for Swift (parity row B1; mirrors Android NewSessionScreen):
 * option loading, per-backend model lists, and submit through either
 * `POST /api/sessions/start` (project directory) or `POST /api/agents` (project profile).
 */
public object IosNewSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    public fun loadOptions(
        profile: ServerProfile,
        onResult: (IosNewSessionOptions) -> Unit,
    ) {
        scope.launch {
            val t = IosServiceLocator.transportFor(profile)
            val options =
                coroutineScope {
                    val llms = async { t.listLlms().getOrNull().orEmpty() }
                    val modes = async { t.listClaudePermissionModes().getOrNull().orEmpty() }
                    val models = async { t.listClaudeModels().getOrNull().orEmpty() }
                    val efforts = async { t.listClaudeEfforts().getOrNull().orEmpty() }
                    val projects = async { t.listProfiles().getOrNull().orEmpty() }
                    val clusters = async { t.listKindProfiles("cluster").getOrNull().orEmpty() }
                    val sessions = async { t.listSessions().getOrNull().orEmpty() }
                    IosNewSessionOptions(
                        llms =
                            llms.await().filter { it.enabled }.map {
                                IosLlmChoice(
                                    name = it.name,
                                    kind = it.kind,
                                    computeNodes =
                                        (it.computeNodes + listOf(it.computeNode))
                                            .filter { n -> n.isNotBlank() }
                                            .distinct(),
                                    defaultModel = it.model,
                                )
                            },
                        permissionModes = modes.await(),
                        claudeModels = models.await(),
                        claudeEfforts = efforts.await(),
                        projectProfiles = projects.await().keys.sorted(),
                        clusterProfiles =
                            clusters.await().mapNotNull { (it["name"] as? JsonPrimitive)?.content },
                        recentDone =
                            sessions.await()
                                .filter {
                                    it.state == SessionState.Completed ||
                                        it.state == SessionState.Killed ||
                                        it.state == SessionState.Error
                                }
                                .sortedByDescending { it.lastActivityAt }
                                .take(30),
                    )
                }
            onResult(options)
        }
    }

    /** Model list for a non-Claude LLM kind (`GET /api/llm/{kind}/models`); empty on failure. */
    public fun loadModels(
        profile: ServerProfile,
        kind: String,
        onResult: (List<String>) -> Unit,
    ) {
        scope.launch {
            onResult(IosServiceLocator.transportFor(profile).listModels(kind).getOrNull().orEmpty())
        }
    }

    /**
     * Start a session. With [projectProfile] set this spawns an agent through
     * `POST /api/agents` (only task / name / cluster apply); otherwise it starts a
     * local session in [workingDir] with the LLM options. Blank strings mean "unset".
     * [onSuccess] receives the new session id.
     */
    public fun submit(
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
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        fun String.orNull(): String? = trim().ifBlank { null }
        scope.launch {
            val t = IosServiceLocator.transportFor(profile)
            val result =
                if (projectProfile.isNotBlank()) {
                    t.startAgent(
                        StartAgentRequestDto(
                            task = task.trim(),
                            projectProfile = projectProfile,
                            clusterProfile = clusterProfile.orNull(),
                            name = name.orNull(),
                        ),
                    )
                } else {
                    t.startSession(
                        task = task.trim(),
                        workingDir = workingDir.orNull(),
                        name = name.orNull(),
                        autoGitInit = autoGitInit,
                        autoGitCommit = autoGitCommit,
                        permissionMode = permissionMode.orNull(),
                        model = model.orNull(),
                        claudeEffort = effort.orNull(),
                        llm = llm.orNull(),
                        computeNode = computeNode.orNull(),
                        chrome = chrome.takeIf { it },
                    )
                }
            result.fold(
                onSuccess = { onSuccess(it) },
                onFailure = { onError(it.message ?: "Couldn't start the session.") },
            )
        }
    }
}
