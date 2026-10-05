package com.dmzs.datawatchclient.di

import com.dmzs.datawatchclient.domain.ServerProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Automaton task concurrency (PWA prdSettings "Max concurrent tasks (0 = global
 * default)"; Android `PrdConcurrencyRow`): `POST /api/autonomous/prds/{id}/set_concurrency`.
 * [maxConcurrentTasks] is clamped to the PWA input range 0–32. Calls [onDone]
 * with null on success, or the error message.
 */
public object IosPrdConcurrency {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    public fun set(
        profile: ServerProfile,
        prdId: String,
        maxConcurrentTasks: Int,
        onDone: (String?) -> Unit,
    ) {
        val n: Int = maxConcurrentTasks.coerceIn(0, 32)
        scope.launch {
            val result: Result<Unit> = IosServiceLocator.transportFor(profile).setPrdConcurrency(prdId, n)
            val err: String? = result.exceptionOrNull()?.let { it.message ?: "Couldn't save concurrency." }
            onDone(err)
        }
    }
}
