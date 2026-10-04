package com.dmzs.datawatchclient.di

import com.dmzs.datawatchclient.domain.SavedCommand
import com.dmzs.datawatchclient.domain.ServerProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** One `/api/filters` row as Swift-friendly fields. */
public data class IosFilterRow(
    val id: String,
    val pattern: String,
    val action: String,
    val value: String,
    val enabled: Boolean,
)

/**
 * Saved commands + output/detection filters editors (parity B30 / B5;
 * PWA loadSavedCommands / loadFilters, Android SavedCommandsCard /
 * FiltersCard). Mutations report null on success or an error message.
 */
public object IosRulesEditors {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun done(r: Result<*>, fallback: String): String? = r.exceptionOrNull()?.let { it.message ?: fallback }

    public fun commands(profile: ServerProfile, onSuccess: (List<SavedCommand>) -> Unit, onError: (String) -> Unit) {
        scope.launch {
            IosServiceLocator.transportFor(profile).listCommands().fold(
                onSuccess = { onSuccess(it) },
                onFailure = { onError("Failed to load commands.") },
            )
        }
    }

    public fun saveCommand(profile: ServerProfile, name: String, command: String, onDone: (String?) -> Unit) {
        scope.launch { onDone(done(IosServiceLocator.transportFor(profile).saveCommand(name.trim(), command.trim()), "Save failed")) }
    }

    public fun updateCommand(profile: ServerProfile, oldName: String, name: String, command: String, onDone: (String?) -> Unit) {
        scope.launch {
            onDone(done(IosServiceLocator.transportFor(profile).updateCommand(oldName, name.trim(), command.trim()), "Update failed"))
        }
    }

    public fun deleteCommand(profile: ServerProfile, name: String, onDone: (String?) -> Unit) {
        scope.launch { onDone(done(IosServiceLocator.transportFor(profile).deleteCommand(name), "Delete failed")) }
    }

    public fun filters(profile: ServerProfile, onSuccess: (List<IosFilterRow>) -> Unit, onError: (String) -> Unit) {
        scope.launch {
            IosServiceLocator.transportFor(profile).listFilters().fold(
                onSuccess = { list ->
                    onSuccess(
                        list.map { o ->
                            fun s(k: String) = o[k]?.jsonPrimitive?.contentOrNull.orEmpty()
                            IosFilterRow(
                                id = s("id"),
                                pattern = s("pattern"),
                                action = s("action"),
                                value = s("value"),
                                enabled = o["enabled"]?.jsonPrimitive?.booleanOrNull ?: true,
                            )
                        },
                    )
                },
                onFailure = { onError("Failed to load filters.") },
            )
        }
    }

    public fun createFilter(profile: ServerProfile, pattern: String, action: String, value: String, onDone: (String?) -> Unit) {
        scope.launch {
            val r = IosServiceLocator.transportFor(profile).createFilter(pattern.trim(), action, value.trim().ifBlank { null })
            onDone(done(r, "Create failed"))
        }
    }

    public fun updateFilter(profile: ServerProfile, id: String, pattern: String, action: String, value: String, onDone: (String?) -> Unit) {
        scope.launch {
            val r = IosServiceLocator.transportFor(profile).updateFilter(id, pattern = pattern.trim(), action = action, value = value.trim())
            onDone(done(r, "Update failed"))
        }
    }

    public fun setFilterEnabled(profile: ServerProfile, id: String, enabled: Boolean, onDone: (String?) -> Unit) {
        scope.launch { onDone(done(IosServiceLocator.transportFor(profile).updateFilter(id, enabled = enabled), "Update failed")) }
    }

    public fun deleteFilter(profile: ServerProfile, id: String, onDone: (String?) -> Unit) {
        scope.launch { onDone(done(IosServiceLocator.transportFor(profile).deleteFilter(id), "Delete failed")) }
    }
}
