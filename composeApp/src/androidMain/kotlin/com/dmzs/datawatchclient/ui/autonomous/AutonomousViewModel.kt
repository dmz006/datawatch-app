package com.dmzs.datawatchclient.ui.autonomous

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmzs.datawatchclient.transport.dto.NewPrdRequestDto
import com.dmzs.datawatchclient.transport.dto.PrdDto
import com.dmzs.datawatchclient.ui.common.ProfileResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Backs [AutonomousScreen]. Reads /api/autonomous/prds against the
 * active server profile.
 */
public class AutonomousViewModel(
    private val resolver: ProfileResolver = ProfileResolver.Default,
) : ViewModel() {
    public data class UiState(
        val loading: Boolean = true,
        val prds: List<PrdDto> = emptyList(),
        val banner: String? = null,
        /** Backend names from /api/backends — used for LLM dropdowns. */
        val backends: List<String> = emptyList(),
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
                    _state.value = UiState(loading = false, prds = dto.prds, backends = backends)
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

    public fun editPrd(prdId: String, title: String?, spec: String?) {
        prdOp("Edit PRD") {
            it.patchPrd(
                prdId = prdId,
                title = title?.takeIf { t -> t.isNotBlank() },
                spec = spec?.takeIf { t -> t.isNotBlank() },
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
}
