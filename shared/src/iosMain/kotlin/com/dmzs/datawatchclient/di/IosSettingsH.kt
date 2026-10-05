package com.dmzs.datawatchclient.di

import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.transport.DocsTrustEntry
import com.dmzs.datawatchclient.transport.TransportClient
import com.dmzs.datawatchclient.transport.dto.EvalRunHistoryDto
import com.dmzs.datawatchclient.transport.dto.FileServiceMetaDto
import com.dmzs.datawatchclient.transport.dto.LlmSessionsDto
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.Foundation.NSData

/** Docs Search trust state: sources awaiting trust + already-trusted sources. */
public data class IosDocsTrust(
    val pending: List<DocsTrustEntry>,
    val trusted: List<DocsTrustEntry>,
)

/** File Service storage overview (PWA loadFileServicePanel). */
public data class IosFileService(
    val root: String,
    val peers: List<String>,
    val discussions: List<String>,
)

/** One recent eval run (GET /api/evals compat shape). */
public data class IosEvalRun(
    val id: String,
    val name: String,
    val passed: Boolean,
    val scorePercent: Int,
    val createdAt: String,
)

/** One session routed through an LLM (PWA "In use…" / Android LlmDetailDialog). */
public data class IosLlmSession(
    val id: String,
    val task: String,
    val state: String,
    val createdAt: String,
)

/** One page of [IosLlmSession]s plus the server's total count. */
public data class IosLlmInUse(
    val sessions: List<IosLlmSession>,
    val total: Int,
)

/**
 * iOS-H parity helpers (Settings › General Docs Search trust queue + File
 * Service, Settings › Automata Evals run history). Mutations report null on
 * success or an error message; callbacks fire off the main thread.
 */
public object IosSettingsH {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun t(profile: ServerProfile): TransportClient = IosServiceLocator.transportFor(profile)

    private fun msg(
        e: Throwable?,
        fallback: String,
    ): String? = e?.let { it.message ?: fallback }

    // ---- Docs Search trust queue ----

    public fun docsTrust(
        profile: ServerProfile,
        onSuccess: (IosDocsTrust) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            val tr: TransportClient = t(profile)
            val pending: Result<List<DocsTrustEntry>> = tr.docsTrustPendingEntries()
            val trusted: Result<List<DocsTrustEntry>> = tr.docsTrustedEntries()
            val err: Throwable? = pending.exceptionOrNull() ?: trusted.exceptionOrNull()
            if (pending.isFailure && trusted.isFailure) {
                onError(err?.message ?: "Failed to load trust sources.")
                return@launch
            }
            onSuccess(
                IosDocsTrust(
                    pending = pending.getOrElse { emptyList() },
                    trusted = trusted.getOrElse { emptyList() },
                ),
            )
        }
    }

    /** Trust ([accept] true) or dismiss the given pending sources. */
    public fun docsTrustDecide(
        profile: ServerProfile,
        sources: List<String>,
        accept: Boolean,
        onDone: (String?) -> Unit,
    ) {
        scope.launch {
            val r: Result<Unit> = t(profile).docsTrustDecide(sources, accept)
            onDone(msg(r.exceptionOrNull(), "Update failed."))
        }
    }

    public fun docsTrustRemove(
        profile: ServerProfile,
        source: String,
        onDone: (String?) -> Unit,
    ) {
        scope.launch {
            val r: Result<Unit> = t(profile).docsTrustRemove(source)
            onDone(msg(r.exceptionOrNull(), "Remove failed."))
        }
    }

    // ---- File Service ----

    public fun fileService(
        profile: ServerProfile,
        onSuccess: (IosFileService) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            val r: Result<FileServiceMetaDto> = t(profile).getFileServiceMeta()
            r.fold(
                onSuccess = { m -> onSuccess(IosFileService(root = m.root, peers = m.peers, discussions = m.discussions)) },
                onFailure = { onError(it.message ?: "Failed to load file service.") },
            )
        }
    }

    public fun setFileServiceRoot(
        profile: ServerProfile,
        path: String,
        onDone: (String?) -> Unit,
    ) {
        scope.launch {
            val r: Result<Unit> = t(profile).setFileServiceRoot(path.trim())
            onDone(msg(r.exceptionOrNull(), "Save failed."))
        }
    }

    /** Upload [data] to the file service at [destPath] (blank → [fileName]). */
    @OptIn(ExperimentalForeignApi::class)
    public fun uploadFile(
        profile: ServerProfile,
        data: NSData,
        fileName: String,
        destPath: String,
        onDone: (String?) -> Unit,
    ) {
        val length: Int = data.length.toInt()
        val bytes: ByteArray = data.bytes?.reinterpret<ByteVar>()?.readBytes(length) ?: ByteArray(0)
        val name: String = fileName.ifBlank { "file" }
        val dest: String = destPath.trim().ifBlank { name }
        scope.launch {
            val r: Result<Unit> = t(profile).uploadFile(bytes, name, dest)
            onDone(msg(r.exceptionOrNull(), "Upload failed."))
        }
    }

    // ---- Evals run history ----

    public fun evalRuns(
        profile: ServerProfile,
        onSuccess: (List<IosEvalRun>) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            val r: Result<List<EvalRunHistoryDto>> = t(profile).listEvalRuns()
            r.fold(
                onSuccess = { runs ->
                    onSuccess(
                        runs.take(5).map { run ->
                            IosEvalRun(
                                id = run.id,
                                name = run.name.ifEmpty { run.id },
                                passed = run.status == "pass",
                                scorePercent = (run.score * 100.0).toInt(),
                                createdAt = run.createdAt,
                            )
                        },
                    )
                },
                onFailure = { onError(it.message ?: "Failed to load runs.") },
            )
        }
    }

    // ---- LLM "In use…" ----

    /** GET /api/llms/{name}/sessions page [page] (1-based) of [size]. */
    public fun llmInUse(
        profile: ServerProfile,
        name: String,
        page: Int,
        size: Int,
        onSuccess: (IosLlmInUse) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            val r: Result<LlmSessionsDto> = t(profile).getLlmSessions(name, page, size)
            r.fold(
                onSuccess = { d ->
                    onSuccess(
                        IosLlmInUse(
                            sessions =
                                d.sessions.map { s ->
                                    IosLlmSession(id = s.id, task = s.task, state = s.state, createdAt = s.createdAt)
                                },
                            total = d.total,
                        ),
                    )
                },
                onFailure = { onError(it.message ?: "Failed to load sessions.") },
            )
        }
    }
}
