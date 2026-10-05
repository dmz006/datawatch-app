package com.dmzs.datawatchclient.di

import com.dmzs.datawatchclient.domain.ServerProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Installed-models sub-list of the Compute Node edit form (PWA
 * `refreshOllamaModelsList` / `ollamaRemoveModel`, Android ComputeNodeDialog
 * "Models" section): GET /api/compute/nodes/{name}/models?kind=ollama and
 * remove one model. Callbacks run on a background thread.
 */
public object IosComputeModels {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    public fun list(
        profile: ServerProfile,
        nodeName: String,
        onSuccess: (List<String>) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            IosServiceLocator.transportFor(profile).getInstalledOllamaModels(nodeName).fold(
                onSuccess = { onSuccess(it.models) },
                onFailure = { onError(it.message ?: "Couldn't load models.") },
            )
        }
    }

    public fun remove(
        profile: ServerProfile,
        nodeName: String,
        model: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            IosServiceLocator.transportFor(profile).deleteOllamaModel(nodeName, model).fold(
                onSuccess = { onSuccess() },
                onFailure = { onError(it.message ?: "Remove failed.") },
            )
        }
    }
}
