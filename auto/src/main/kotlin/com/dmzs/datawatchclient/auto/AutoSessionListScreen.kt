package com.dmzs.datawatchclient.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.dmzs.datawatchclient.domain.Session
import com.dmzs.datawatchclient.domain.SessionState
import com.dmzs.datawatchclient.transport.dto.SessionTelemetryDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * BL303-A1 — Live session list for Android Auto.
 *
 * Shows all sessions sorted by urgency: guardrail-blocked first,
 * then Error, Waiting, RateLimited, Running, then terminal states.
 * Progress % is fetched from telemetry for active sessions.
 * Tap a row → [AutoSessionDetailScreen].
 */
public class AutoSessionListScreen(carContext: CarContext) : Screen(carContext) {

    private data class SessionRow(
        val session: Session,
        val progress: Float?,
        val hasGuardrailBlock: Boolean,
    )

    private var rows: List<SessionRow> = emptyList()
    private var serverName: String = "datawatch"
    private var error: String? = null
    private var pollJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                pollJob?.cancel()
                pollJob = scope.launch { pollLoop() }
            }

            override fun onStop(owner: LifecycleOwner) {
                pollJob?.cancel()
                pollJob = null
            }
        })
    }

    private suspend fun pollLoop() {
        while (scope.isActive) {
            refresh()
            invalidate()
            delay(POLL_MS)
        }
    }

    private suspend fun refresh() {
        try {
            val profile = resolveActiveProfile() ?: run {
                error = "No enabled server (configure on phone)"
                rows = emptyList()
                return
            }
            serverName = profile.displayName
            val transport = AutoServiceLocator.transportFor(profile)
            transport.listSessions().fold(
                onSuccess = { sessions ->
                    error = null
                    val telemetryMap: Map<String, SessionTelemetryDto?> = coroutineScope {
                        sessions
                            .filter { it.state == SessionState.Running || it.state == SessionState.Waiting }
                            .map { s ->
                                async { s.id to transport.getSessionTelemetry(s.id).getOrNull() }
                            }
                            .awaitAll()
                            .toMap()
                    }
                    rows = sessions
                        .map { s ->
                            val telem = telemetryMap[s.id]
                            SessionRow(
                                session = s,
                                progress = telem?.progress?.takeIf { it > 0f },
                                hasGuardrailBlock = telem?.guardrailVerdicts
                                    ?.any { it.outcome == "block" } == true,
                            )
                        }
                        .sortedWith(compareBy { urgencyScore(it) })
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
        if (rows.isEmpty()) {
            builder.addItem(
                Row.Builder()
                    .setTitle(if (error != null) "Error" else "No sessions")
                    .addText(error ?: "No active sessions on $serverName.")
                    .build(),
            )
        } else {
            rows.take(MAX_ROWS).forEach { row ->
                val s = row.session
                val subtitle = buildSubtitle(row)
                builder.addItem(
                    Row.Builder()
                        .setTitle(colored(s.name ?: s.taskSummary ?: s.id, stateColor(row)))
                        .addText(subtitle)
                        .setOnClickListener {
                            screenManager.push(
                                AutoSessionDetailScreen(carContext, s.id, s.name ?: s.taskSummary ?: s.id),
                            )
                        }
                        .build(),
                )
            }
            if (rows.size > MAX_ROWS) {
                builder.addItem(
                    Row.Builder()
                        .setTitle("… and ${rows.size - MAX_ROWS} more")
                        .addText("Showing top $MAX_ROWS by urgency")
                        .build(),
                )
            }
        }
        return ListTemplate.Builder()
            .setTitle("$serverName Sessions")
            .setHeaderAction(Action.BACK)
            .setSingleList(builder.build())
            .build()
    }

    private companion object {
        const val POLL_MS: Long = 10_000L
        const val MAX_ROWS: Int = 5

        fun urgencyScore(row: SessionRow): Int =
            sessionUrgencyScore(row.session.state, row.hasGuardrailBlock)

        fun stateColor(row: SessionRow): CarColor = when {
            row.hasGuardrailBlock || row.session.state == SessionState.Error -> CarColor.RED
            row.session.state == SessionState.Waiting ||
                row.session.state == SessionState.RateLimited -> CarColor.YELLOW
            row.session.state == SessionState.Running -> CarColor.GREEN
            else -> CarColor.DEFAULT
        }

        fun buildSubtitle(row: SessionRow): String = buildString {
            when {
                row.hasGuardrailBlock -> append("⚠ guardrail blocked")
                row.session.state == SessionState.Error -> append("✗ error")
                row.session.state == SessionState.Waiting -> append("● waiting input")
                row.session.state == SessionState.RateLimited -> append("● rate limited")
                row.session.state == SessionState.Running -> append("● running")
                row.session.state == SessionState.Completed -> append("✓ completed")
                row.session.state == SessionState.Killed -> append("✗ killed")
                else -> append(row.session.state.name.lowercase())
            }
            row.progress?.let { p -> append(" · ${(p * 100).toInt()}%") }
        }
    }
}
