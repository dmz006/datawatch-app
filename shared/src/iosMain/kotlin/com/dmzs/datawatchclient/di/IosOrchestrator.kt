package com.dmzs.datawatchclient.di

import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.transport.dto.OrchestratorGraphListItemDto
import com.dmzs.datawatchclient.transport.dto.PipelineListItemDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Automata Orchestrator graphs + Pipeline manager (parity B19; PWA
 * loadOrchestratorPanel / loadPipelinesPanel, Android OrchestratorGraphsCard /
 * PipelineManagerCard). Callbacks return null on success or an error message.
 */
public object IosOrchestrator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    public fun graphs(
        profile: ServerProfile,
        onSuccess: (List<OrchestratorGraphListItemDto>) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            IosServiceLocator.transportFor(profile).getOrchestratorGraphsList().fold(
                onSuccess = { onSuccess(it.graphs) },
                onFailure = { onError(it.message ?: "Couldn't load graphs.") },
            )
        }
    }

    public fun createGraph(
        profile: ServerProfile,
        title: String,
        projectDir: String,
        onDone: (String?) -> Unit,
    ) {
        scope.launch {
            val r = IosServiceLocator.transportFor(profile).createOrchestratorGraph(title.trim(), projectDir.trim())
            onDone(r.exceptionOrNull()?.let { it.message ?: "Couldn't create the graph." })
        }
    }

    public fun runGraph(
        profile: ServerProfile,
        id: String,
        onDone: (String?) -> Unit,
    ) {
        scope.launch {
            val r = IosServiceLocator.transportFor(profile).runOrchestratorGraph(id)
            onDone(r.exceptionOrNull()?.let { it.message ?: "Couldn't start the run." })
        }
    }

    public fun deleteGraph(
        profile: ServerProfile,
        id: String,
        onDone: (String?) -> Unit,
    ) {
        scope.launch {
            val r = IosServiceLocator.transportFor(profile).deleteOrchestratorGraph(id)
            onDone(r.exceptionOrNull()?.let { it.message ?: "Couldn't cancel the graph." })
        }
    }

    public fun pipelines(
        profile: ServerProfile,
        onSuccess: (List<PipelineListItemDto>) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            IosServiceLocator.transportFor(profile).getPipelines().fold(
                onSuccess = { onSuccess(it) },
                onFailure = { onError(it.message ?: "Failed to load pipelines.") },
            )
        }
    }

    public fun cancelPipeline(
        profile: ServerProfile,
        id: String,
        onDone: (String?) -> Unit,
    ) {
        scope.launch {
            val r = IosServiceLocator.transportFor(profile).cancelPipeline(id)
            onDone(r.exceptionOrNull()?.let { it.message ?: "Couldn't cancel the pipeline." })
        }
    }
}
