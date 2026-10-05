package com.dmzs.datawatchclient.di

import com.dmzs.datawatchclient.domain.ServerProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * New Session backend setup hint (parity 08 › Backend setup hint; PWA `#backendWarn`,
 * Android `backendNeedsSetup` + `BackendSetupHint`). Loads the backends the server
 * reports as installed / enabled (GET /api/backends `llm`); an empty list means the
 * server didn't say, so no warning is shown. Callbacks run on a background thread.
 */
public object IosBackendHint {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    public fun loadInstalled(
        profile: ServerProfile,
        onResult: (List<String>) -> Unit,
    ) {
        scope.launch {
            val list: List<String> =
                IosServiceLocator.transportFor(profile).listBackends().getOrNull()?.llm ?: emptyList<String>()
            onResult(list)
        }
    }

    /** Same rule as Android `backendNeedsSetup`. */
    public fun needsSetup(
        kind: String,
        installed: List<String>,
    ): Boolean =
        kind.isNotBlank() && installed.isNotEmpty() && installed.none { it.equals(kind, ignoreCase = true) }
}
