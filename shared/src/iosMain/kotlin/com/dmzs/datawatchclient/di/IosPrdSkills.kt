package com.dmzs.datawatchclient.di

import com.dmzs.datawatchclient.domain.ServerProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Automaton skills (PWA prdSettings skills picker; Android `PrdSkillsRow`):
 * `POST /api/autonomous/prds/{id}/set_skills`. [skills] is comma-separated.
 * Calls [onDone] with null on success, or the error message.
 */
public object IosPrdSkills {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    public fun set(
        profile: ServerProfile,
        prdId: String,
        skills: String,
        onDone: (String?) -> Unit,
    ) {
        val list: List<String> = skills.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        scope.launch {
            val result: Result<Unit> = IosServiceLocator.transportFor(profile).setPrdSkills(prdId, list)
            val err: String? = result.exceptionOrNull()?.let { it.message ?: "Couldn't save skills." }
            onDone(err)
        }
    }
}
