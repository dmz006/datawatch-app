package com.dmzs.datawatchclient.ui.autonomous

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmzs.datawatchclient.transport.dto.NewPrdRequestDto
import com.dmzs.datawatchclient.transport.dto.PrdDto
import com.dmzs.datawatchclient.ui.common.ProfileResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

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
    )

    private val _state = MutableStateFlow(UiState())
    public val state: StateFlow<UiState> = _state

    public fun refresh() {
        viewModelScope.launch {
            val (_, transport) = resolver.resolve() ?: run {
                _state.value = UiState(loading = false, banner = "No enabled server.")
                return@launch
            }
            transport.listPrds().fold(
                onSuccess = { dto ->
                    _state.value = UiState(loading = false, prds = dto.prds)
                },
                onFailure = { err ->
                    _state.value = UiState(
                        loading = false,
                        prds = emptyList(),
                        banner = "Load failed — ${err.message ?: err::class.simpleName}",
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
                    _state.value = _state.value.copy(
                        banner = "Create failed — ${err.message ?: err::class.simpleName}",
                    )
                },
            )
        }
    }

    public fun approve(prdId: String) {
        viewModelScope.launch {
            val (_, transport) = resolver.resolve() ?: return@launch
            transport.prdAction(prdId, "approve").fold(
                onSuccess = { refresh() },
                onFailure = { err ->
                    _state.value = _state.value.copy(
                        banner = "Approve failed — ${err.message ?: err::class.simpleName}",
                    )
                },
            )
        }
    }

    public fun reject(prdId: String, reason: String) {
        viewModelScope.launch {
            val (_, transport) = resolver.resolve() ?: return@launch
            val body =
                kotlinx.serialization.json.buildJsonObject {
                    put("reason", kotlinx.serialization.json.JsonPrimitive(reason))
                }
            transport.prdAction(prdId, "reject", body).fold(
                onSuccess = { refresh() },
                onFailure = { err ->
                    _state.value = _state.value.copy(
                        banner = "Reject failed — ${err.message ?: err::class.simpleName}",
                    )
                },
            )
        }
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
                    _state.value = _state.value.copy(
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
                    _state.value = _state.value.copy(
                        banner = "Edit files failed — ${err.message ?: err::class.simpleName}",
                    )
                },
            )
        }
    }
}
