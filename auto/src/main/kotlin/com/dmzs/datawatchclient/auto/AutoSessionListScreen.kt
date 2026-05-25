@file:Suppress("MagicNumber")
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
 *
 * @param automataId When non-null, filters to sessions belonging to this automaton
 *   (matched against session name prefix or automata telemetry field). BL303-A3.4.
 */
public class AutoSessionListScreen(
    carContext: CarContext,
    private val automataId: String? = null,
) : Screen(carContext) {

    private data class SessionRow(
        val session: Session,
        val progress: Float?,
        val hasGuardrailBlock: Boolean,
        val automataName: String = "",
    )

    private var rows: List<SessionRow> = emptyList()
    private var serverName: String = "datawatch"
    private var error: String? = null
    private var pollJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    // BL303-A5.1: ambient mode — after STALE_THRESHOLD polls with no change, slow down to AMBIENT_POLL_MS
    private var staleCount: Int = 0
    private var lastRowsKey: Int = -1

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
            val poll = if (staleCount >= STALE_THRESHOLD) AMBIENT_POLL_MS else POLL_MS
            delay(poll)
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
                    val isAmbient = staleCount >= STALE_THRESHOLD
                    // BL303-A5.1: ambient mode — skip per-session telemetry fetches, use cached rows
                    val telemetryMap: Map<String, SessionTelemetryDto?> = if (isAmbient) {
                        emptyMap()
                    } else {
                        coroutineScope {
                            sessions
                                .filter { it.state == SessionState.Running || it.state == SessionState.Waiting }
                                .map { s ->
                                    async { s.id to transport.getSessionTelemetry(s.id).getOrNull() }
                                }
                                .awaitAll()
                                .toMap()
                        }
                    }
                    val newRows = sessions
                        .map { s ->
                            val telem = telemetryMap[s.id]
                            val existing = if (isAmbient) rows.firstOrNull { it.session.id == s.id } else null
                            SessionRow(
                                session = s,
                                progress = telem?.progress?.takeIf { it > 0f } ?: existing?.progress,
                                hasGuardrailBlock = telem?.guardrailVerdicts
                                    ?.any { it.outcome == "block" } ?: existing?.hasGuardrailBlock ?: false,
                                automataName = telem?.sprint?.automata.orEmpty().ifEmpty { existing?.automataName.orEmpty() },
                            )
                        }
                        // BL303-A3.4: filter by automaton id when coming from AutomataScreen
                        .let { allRows ->
                            if (automataId != null) {
                                allRows.filter { row ->
                                    row.automataName.equals(automataId, ignoreCase = true) ||
                                        row.session.name?.startsWith(automataId, ignoreCase = true) == true
                                }
                            } else allRows
                        }
                        .sortedWith(compareBy { urgencyScore(it) })
                    // Track stale state for ambient poll
                    val newKey = newRows.map { it.session.id to it.session.state }.hashCode()
                    if (newKey == lastRowsKey) staleCount++ else { staleCount = 0; lastRowsKey = newKey }
                    rows = newRows
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
        val title = if (automataId != null) "$automataId Sessions" else "$serverName Sessions"
        return ListTemplate.Builder()
            .setTitle(title)
            .setHeaderAction(Action.BACK)
            .setSingleList(builder.build())
            .build()
    }

    private companion object {
        const val POLL_MS: Long = 10_000L
        const val AMBIENT_POLL_MS: Long = 60_000L
        const val STALE_THRESHOLD: Int = 3
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
