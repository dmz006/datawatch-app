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
import com.dmzs.datawatchclient.Version
import com.dmzs.datawatchclient.domain.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Driver-safe session summary for Android Auto. Reads live
 * session counts from the first enabled [AutoServiceLocator]
 * profile every 15 s. Tapping "Waiting input" opens
 * [WaitingSessionsScreen] for per-session reply.
 *
 * ADR-0031 Play-compliance: static ListTemplate only.
 */
public class AutoSummaryScreen(carContext: CarContext) : Screen(carContext) {
    private var running: Int = 0
    private var waiting: Int = 0
    private var total: Int = 0
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
            delay(POLL_MS)
        }
    }

    private companion object {
        const val POLL_MS: Long = 15_000L
    }

    private suspend fun refresh() {
        try {
            val profiles = AutoServiceLocator.profileRepository.observeAll().first()
            val profile = profiles.firstOrNull { it.enabled } ?: run {
                error = "No enabled server (configure on phone)"
                running = 0; waiting = 0; total = 0
                return
            }
            AutoServiceLocator.transportFor(profile).listSessions().fold(
                onSuccess = { list ->
                    error = null
                    running = list.count { it.state == SessionState.Running }
                    waiting = list.count { it.state == SessionState.Waiting }
                    total = list.size
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
        val items =
            ItemList.Builder()
                .addItem(
                    Row.Builder()
                        .setTitle("Running")
                        .addText("$running sessions")
                        .build(),
                )
                .addItem(
                    Row.Builder()
                        .setTitle("Waiting input")
                        .addText("$waiting sessions")
                        .setOnClickListener {
                            screenManager.push(WaitingSessionsScreen(carContext))
                        }
                        .build(),
                )
                .addItem(
                    Row.Builder()
                        .setTitle("Total")
                        .addText("$total sessions")
                        .build(),
                )
                .build()
        val title = "datawatch ${Version.VERSION}"
        return ListTemplate.Builder()
            .setTitle(error?.let { "$title · $it" } ?: title)
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(items)
            .build()
    }
}
