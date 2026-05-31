@file:Suppress("MagicNumber")
package com.dmzs.datawatchclient.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.CarText
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.domain.SessionState
import com.dmzs.datawatchclient.transport.dto.StatsDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Mission control hub for Android Auto — root screen.
 *
 * Reads live session counts and server vitals every 15s from the active
 * [AutoServiceLocator] profile. Server header shows CPU/mem inline.
 * ActionStrip: Info → About, Monitor icon → Monitor screen.
 *
 * Phase 1 (2026-05-31): title → "datawatch" (no version); automata row shows
 * live counts from listPrds(); Last Output row shows most recent lastResponse.
 *
 * ADR-0031 Play-compliance: static ListTemplate only, no free-form text input.
 */
public class AutoSummaryScreen(carContext: CarContext) : Screen(carContext) {
    private var running: Int = 0
    private var waiting: Int = 0
    private var blocked: Int = 0
    private var total: Int = 0
    private var serverStats: StatsDto? = null
    private var activeProfile: ServerProfile? = null
    private var error: String? = null
    private var pollJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var lastSnapshotHash: Int = -1

    // Automata counts
    private var automataRunning: Int = 0
    private var automataBlocked: Int = 0
    private var automataTotal: Int = 0

    // Last output across sessions
    private var lastOutputSessionId: String? = null
    private var lastOutputSessionName: String? = null
    private var lastOutputText: String? = null

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

                override fun onDestroy(owner: LifecycleOwner) {
                    scope.cancel()
                }
            },
        )
    }

    private suspend fun pollLoop() {
        while (scope.isActive) {
            refresh()
            val newHash = listOf(
                running, waiting, blocked, total, error,
                automataRunning, automataBlocked, automataTotal,
                lastOutputSessionId, lastOutputText,
                serverStats?.sessionsTotal,
            ).hashCode()
            if (newHash != lastSnapshotHash) {
                lastSnapshotHash = newHash
                invalidate()
            }
            delay(POLL_MS)
        }
    }

    private companion object {
        const val POLL_MS: Long = 15_000L

        /** Renders a compact progress bar: "▓▓▓░░░ 45%" (6 wide). */
        fun bar(pct: Int, width: Int = 6): String {
            val clamped = pct.coerceIn(0, 100)
            val filled = (clamped * width / 100).coerceIn(0, width)
            return "▓".repeat(filled) + "░".repeat(width - filled) + " $clamped%"
        }
    }

    private suspend fun refresh() {
        try {
            val profile =
                resolveActiveProfile() ?: run {
                    error = "No enabled server (configure on phone)"
                    activeProfile = null
                    running = 0
                    waiting = 0
                    total = 0
                    return
                }
            activeProfile = profile
            val transport = AutoServiceLocator.transportFor(profile)
            transport.listSessions().fold(
                onSuccess = { list ->
                    error = null
                    running = list.count { it.state == SessionState.Running }
                    waiting = list.count { it.state == SessionState.Waiting }
                    total = list.size
                    blocked = list.count { it.state == SessionState.Error }
                    // Find most recent non-blank lastResponse across all sessions
                    val withResponse = list.firstOrNull { !it.lastResponse.isNullOrBlank() }
                    lastOutputSessionId = withResponse?.id
                    lastOutputSessionName = withResponse?.let { it.name ?: it.taskSummary ?: it.id }
                    lastOutputText = withResponse?.lastResponse
                },
                onFailure = { err ->
                    error = "Unreachable: ${err.message ?: err::class.simpleName}"
                },
            )
            serverStats = transport.stats().getOrNull()
            // Automata counts from listPrds()
            transport.listPrds().getOrNull()?.let { prdList ->
                automataTotal = prdList.prds.size
                automataRunning = prdList.prds.count { it.status == "running" }
                automataBlocked = prdList.prds.count { p -> p.stories.any { it.status == "awaiting_approval" } }
            }
        } catch (e: Throwable) {
            error = "Error: ${e.message ?: e::class.simpleName}"
        }
    }

    override fun onGetTemplate(): Template {
        fun iconOf(resId: Int) =
            CarIcon.Builder(IconCompat.createWithResource(carContext, resId)).build()

        val listBuilder = ItemList.Builder()
        val profile = activeProfile

        if (profile != null) {
            // Row 1: Server — shows CPU/mem inline; tap to switch
            val statsLine = serverStats?.let { s ->
                val cpuPct = s.cpuLoad1?.let { load ->
                    s.cpuCores?.let { c -> if (c > 0) (load / c * 100).toInt() else null }
                } ?: s.cpuPct?.toInt()
                val memPct = s.memUsed?.let { used ->
                    s.memTotal?.let { total -> if (total > 0) (used * 100 / total).toInt() else null }
                } ?: s.memPct?.toInt()
                listOfNotNull(
                    cpuPct?.let { "cpu ${bar(it)}" },
                    memPct?.let { "mem ${bar(it)}" },
                ).joinToString("  ").takeIf { it.isNotBlank() }
            }
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("⬡ ${profile.displayName}")
                    .addText(statsLine ?: "Tap to switch server")
                    .setOnClickListener { screenManager.push(AutoServerPickerScreen(carContext)) }
                    .build(),
            )
        }

        // Row 2: Session counts — cyber glyph state summary
        val statusTitle = buildString {
            append("◉ $running")
            if (waiting > 0) append("  ⊙ $waiting")
            if (blocked > 0) append("  ⊗ $blocked")
        }.ifBlank { "◉ 0" }
        listBuilder.addItem(
            Row.Builder()
                .setTitle(statusTitle)
                .addText("$total sessions total · tap to view")
                .setOnClickListener { screenManager.push(AutoSessionListScreen(carContext)) }
                .build(),
        )

        // Row 3: Automata — live counts from listPrds()
        val automataTitle = buildString {
            append("⟫ $automataRunning")
            if (automataBlocked > 0) append("  ⊗ $automataBlocked")
        }.ifBlank { "⟫ 0" }
        val automataSubtitle = "$automataTotal automata · tap to view"
        listBuilder.addItem(
            Row.Builder()
                .setTitle(automataTitle)
                .addText(automataSubtitle)
                .setOnClickListener { screenManager.push(AutoAutomataScreen(carContext)) }
                .build(),
        )

        // Row 4: Last Output (conditional — only when any session has a non-blank lastResponse)
        val outId = lastOutputSessionId
        val outName = lastOutputSessionName
        val outText = lastOutputText
        if (outId != null && outName != null && !outText.isNullOrBlank()) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("◀ $outName")
                    .addText(outText.take(72))
                    .setOnClickListener {
                        screenManager.push(
                            LastOutputDetailScreen(carContext, outId, outName, outText, null),
                        )
                    }
                    .build(),
            )
        }

        val title = "datawatch"
        // ActionStrip: Info → About, Monitor icon → Monitor (2 actions max)
        val actionStrip =
            ActionStrip.Builder()
                .addAction(
                    Action.Builder()
                        .setIcon(iconOf(R.drawable.ic_auto_info))
                        .setOnClickListener { screenManager.push(AutoAboutScreen(carContext)) }
                        .build(),
                )
                .addAction(
                    Action.Builder()
                        .setIcon(iconOf(R.drawable.ic_auto_monitor))
                        .setOnClickListener { screenManager.push(AutoMonitorScreen(carContext)) }
                        .build(),
                )
                .build()

        return ListTemplate.Builder()
            .setTitle(if (error != null) "$title · $error" else title)
            .setHeaderAction(Action.APP_ICON)
            .setActionStrip(actionStrip)
            .setSingleList(listBuilder.build())
            .build()
    }
}

/**
 * Resolve the profile the user most recently picked via the car (or
 * phone) picker. Falls back to the first enabled profile so a
 * fresh install still shows something useful.
 */
internal suspend fun resolveActiveProfile(): ServerProfile? {
    val profiles = AutoServiceLocator.profileRepository.observeAll().first()
    val activeId = AutoServiceLocator.activeServerStore.get()
    val picked = profiles.firstOrNull { it.id == activeId && it.enabled }
    return picked ?: profiles.firstOrNull { it.enabled }
}

/**
 * ForegroundCarColorSpan is not allowed in MESSAGING category templates —
 * crashes with IllegalArgumentException on render. Return plain CarText;
 * status is conveyed via emoji/symbols already present in the text.
 */
internal fun colored(
    text: String,
    @Suppress("UNUSED_PARAMETER") color: CarColor,
): CarText = CarText.create(text)

/** Reusable datawatch-brand icon for template headers. */
internal fun brandIcon(carContext: CarContext): CarIcon? =
    runCatching {
        val resId =
            carContext.resources.getIdentifier(
                "ic_launcher",
                "mipmap",
                carContext.packageName,
            )
        if (resId != 0) {
            CarIcon.Builder(IconCompat.createWithResource(carContext, resId)).build()
        } else {
            null
        }
    }.getOrNull()
