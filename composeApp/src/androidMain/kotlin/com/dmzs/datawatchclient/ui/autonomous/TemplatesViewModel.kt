package com.dmzs.datawatchclient.ui.autonomous

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmzs.datawatchclient.transport.dto.ClonePrdToTemplateRequestDto
import com.dmzs.datawatchclient.transport.dto.CreateTemplateRequestDto
import com.dmzs.datawatchclient.transport.dto.InstantiateTemplateRequestDto
import com.dmzs.datawatchclient.transport.dto.TemplateDto
import com.dmzs.datawatchclient.transport.dto.UpdateTemplateRequestDto
import com.dmzs.datawatchclient.ui.common.ProfileResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

public class TemplatesViewModel(
    private val resolver: ProfileResolver = ProfileResolver.Default,
) : ViewModel() {
    public data class UiState(
        val loading: Boolean = true,
        val templates: List<TemplateDto> = emptyList(),
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
            transport.listTemplates().fold(
                onSuccess = { dto ->
                    _state.value = UiState(loading = false, templates = dto.templates)
                },
                onFailure = { err ->
                    _state.value = UiState(loading = false, banner = err.message ?: "Failed to load templates.")
                },
            )
        }
    }

    public fun createTemplate(req: CreateTemplateRequestDto) {
        viewModelScope.launch {
            val (_, transport) = resolver.resolve() ?: return@launch
            transport.createTemplate(req).fold(
                onSuccess = { refresh() },
                onFailure = { err ->
                    _state.value = _state.value.copy(banner = err.message ?: "Create failed.")
                },
            )
        }
    }

    public fun updateTemplate(id: String, req: UpdateTemplateRequestDto) {
        viewModelScope.launch {
            val (_, transport) = resolver.resolve() ?: return@launch
            transport.updateTemplate(id, req).fold(
                onSuccess = { refresh() },
                onFailure = { err ->
                    _state.value = _state.value.copy(banner = err.message ?: "Update failed.")
                },
            )
        }
    }

    public fun deleteTemplate(id: String) {
        viewModelScope.launch {
            val (_, transport) = resolver.resolve() ?: return@launch
            transport.deleteTemplate(id).fold(
                onSuccess = { refresh() },
                onFailure = { err ->
                    _state.value = _state.value.copy(banner = err.message ?: "Delete failed.")
                },
            )
        }
    }

    public fun instantiateTemplate(
        id: String,
        req: InstantiateTemplateRequestDto,
        onSuccess: (String) -> Unit,
    ) {
        viewModelScope.launch {
            val (_, transport) = resolver.resolve() ?: return@launch
            transport.instantiateTemplate(id, req).fold(
                onSuccess = { prd -> onSuccess(prd.id) },
                onFailure = { err ->
                    _state.value = _state.value.copy(banner = err.message ?: "Instantiate failed.")
                },
            )
        }
    }

    public fun clonePrdToTemplate(prdId: String, description: String?, actor: String?) {
        viewModelScope.launch {
            val (_, transport) = resolver.resolve() ?: return@launch
            transport.clonePrdToTemplate(prdId, ClonePrdToTemplateRequestDto(description, actor)).fold(
                onSuccess = { refresh() },
                onFailure = { err ->
                    _state.value = _state.value.copy(banner = err.message ?: "Clone failed.")
                },
            )
        }
    }
}
