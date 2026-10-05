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

/**
 * Launch-Automaton wizard create call with the PWA `_wizardSubmit` body
 * (`type`, `guided_mode`) on top of IosExtras.createPrd's fields. Scan / rules /
 * story-approval toggles are UI-only in the PWA and Android, so not sent.
 */
public object IosPrdWizard {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** PWA `_inferAutomataType` / Android NewPrdDialog keyword scan. */
    public fun inferType(intent: String): String {
        val lower: String = intent.lowercase()
        fun has(vararg words: String): Boolean = words.any { lower.contains(it) }
        val t: String =
            when {
                intent.isBlank() -> "software"
                has("code", "test", "refactor", "build", "fix", "implement") -> "software"
                has("research", "analyze", "study") -> "research"
                has("deploy", "restart", "migrate", "monitor") -> "operational"
                else -> "personal"
            }
        return t
    }

    public fun createPrd(
        profile: ServerProfile,
        title: String,
        spec: String,
        type: String,
        guidedMode: Boolean,
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
        val guided: Boolean? = if (guidedMode) true else null
        val request: NewPrdRequestDto =
            if (projectProfile.isNotBlank()) {
                NewPrdRequestDto(
                    name = "",
                    title = title.orNull(),
                    spec = spec.orNull(),
                    projectProfile = projectProfile,
                    type = type.orNull(),
                    guidedMode = guided,
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
                    type = type.orNull(),
                    guidedMode = guided,
                    memorySeed = seed,
                    memoryHarvest = harvest,
                )
            }
        scope.launch {
            IosServiceLocator.transportFor(profile).createPrd(request).fold(
                onSuccess = { id: String -> onSuccess(id) },
                onFailure = { e -> onError(e.message ?: "Couldn't create the automaton.") },
            )
        }
    }
}
