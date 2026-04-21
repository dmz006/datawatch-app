package com.dmzs.datawatchclient.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.dmzs.datawatchclient.domain.Session
import com.dmzs.datawatchclient.domain.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Waiting-input session list. Tap a row → [SessionReplyScreen]. */
public class WaitingSessionsScreen(carContext: CarContext) : Screen(carContext) {
    private var sessions: List<Session> = emptyList()
    private var error: String? = null
    private var pollJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    init {
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    pollJob?.cancel()
                    pollJob = scope.launch { pollLoop() }
                }
                override fun onStop(owner: LifecycleOwner) {
                    pollJob?.cancel()
                    pollJob = null
                }
            },
        )
    }

    private suspend fun pollLoop() {
        while (scope.isActive) {
            refresh()
            invalidate()
            delay(15_000L)
        }
    }

    private suspend fun refresh() {
        try {
            val profiles = AutoServiceLocator.profileRepository.observeAll().first()
            val profile = profiles.firstOrNull { it.enabled } ?: run {
                error = "No enabled server"
                sessions = emptyList()
                return
            }
            AutoServiceLocator.transportFor(profile).listSessions().fold(
                onSuccess = { list ->
                    error = null
                    sessions =
                        list.filter { it.state == SessionState.Waiting }
                            .sortedByDescending { it.lastActivityAt }
                },
                onFailure = { err ->
                    error = "Unreachable: ${err.message ?: err::class.simpleName}"
                },
            )
        } catch (e: Throwable) {
            error = "Error: ${e.message ?: e::class.simpleName}"
        }
    }

    override fun onGetTemplate(): Template {
        val builder = ItemList.Builder()
        if (sessions.isEmpty()) {
            builder.addItem(
                Row.Builder()
                    .setTitle(if (error != null) "Error" else "All caught up")
                    .addText(error ?: "No sessions waiting on input.")
                    .build(),
            )
        } else {
            sessions.forEach { s ->
                val body =
                    s.promptContext
                        ?.lineSequence()
                        ?.map { it.trim() }
                        ?.firstOrNull { it.isNotEmpty() }
                        ?: s.lastPrompt?.take(80)
                        ?: s.taskSummary?.take(80)
                        ?: "(no context)"
                builder.addItem(
                    Row.Builder()
                        .setTitle(s.name ?: s.id)
                        .addText(body)
                        .setOnClickListener {
                            screenManager.push(
                                SessionReplyScreen(carContext, s.id),
                            )
                        }
                        .build(),
                )
            }
        }
        return ListTemplate.Builder()
            .setTitle("Waiting input")
            .setHeaderAction(Action.BACK)
            .setSingleList(builder.build())
            .build()
    }
}
