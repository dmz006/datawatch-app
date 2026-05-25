@file:Suppress("MagicNumber")
package com.dmzs.datawatchclient.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.CarText
import androidx.car.app.model.ForegroundCarColorSpan
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.dmzs.datawatchclient.Version
import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.domain.SessionState
import com.dmzs.datawatchclient.transport.AlertsView
import com.dmzs.datawatchclient.transport.dto.StatsDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Driver-safe session summary for Android Auto. Reads live session
 * counts from the active [AutoServiceLocator] profile every 15 s.
 * Tapping "Waiting input" opens [WaitingSessionsScreen]. The action
 * strip exposes server picker, monitor and about screens — drivers
 * get a consistent datawatch entry point without leaving the car UI.
 *
 * ADR-0031 Play-compliance: static ListTemplate only, no free-form
 * text input from the driver surface.
 */
public class AutoSummaryScreen(carContext: CarContext) : Screen(carContext) {
    private var running: Int = 0
    private var waiting: Int = 0
    private var blocked: Int = 0
    private var total: Int = 0
    private var unreadAlerts: Int = 0
    private var serverStats: StatsDto? = null
    private var lastCompletedTask: String? = null
    private var activeProfile: ServerProfile? = null
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
        const val MB: Long = 1_000_000L
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
                    // BL303-A6.2: blocked = sessions with Error state (guardrail blocks resolved in telemetry)
                    blocked = list.count { it.state == SessionState.Error }
                },
                onFailure = { err ->
                    error = "Unreachable: ${err.message ?: err::class.simpleName}"
                },
            )
            // BL303-A6.3: CPU/Mem stats (best-effort)
            serverStats = transport.stats().getOrNull()
            // BL303-A6.4: last completed task (best-effort — first completed session's telemetry)
            transport.listSessions().getOrNull()
                ?.firstOrNull { it.state == SessionState.Completed }
                ?.let { s -> transport.getSessionTelemetry(s.id).getOrNull()?.currentTask?.takeIf { it.isNotBlank() } }
                ?.also { lastCompletedTask = it }
            // BL303-A5.3: load unread alert count (best-effort — does not block main data)
            transport.listAlerts().getOrNull()?.let { unreadAlerts = it.unreadCount }
        } catch (e: Throwable) {
            error = "Error: ${e.message ?: e::class.simpleName}"
        }
    }

    override fun onGetTemplate(): Template {
        val builder = ItemList.Builder()
        // BL303-A6.1: Mission Control layout — server header + status strip rows
        val profile = activeProfile
        if (profile != null) {
            // Row 1: Server header — tap to switch server (A6.5)
            builder.addItem(
                Row.Builder()
                    .setTitle(colored("● ${profile.displayName}", CarColor.GREEN))
                    .addText("Tap to switch server")
                    .setOnClickListener { screenManager.push(AutoServerPickerScreen(carContext)) }
                    .build(),
            )
        }
        // Row 2: Running / Waiting / Blocked counts (A6.2)
        val statusColor = when {
            blocked > 0 -> CarColor.RED
            waiting > 0 -> CarColor.YELLOW
            else -> CarColor.GREEN
        }
        val statusText = buildString {
            append("$running run")
            if (waiting > 0) append(" · $waiting wait")
            if (blocked > 0) append(" · $blocked blocked")
        }
        builder.addItem(
            Row.Builder()
                .setTitle(colored(statusText, statusColor))
                .addText("$total sessions total")
                .setOnClickListener { screenManager.push(AutoSessionListScreen(carContext)) }
                .build(),
        )
        // Row 3: CPU / Mem (A6.3) — conditional on data availability (Drive: max 6 rows total)
        val sysLine: String? = serverStats?.let { s ->
            val cpuPct = s.cpuLoad1?.let { load -> s.cpuCores?.let { c -> if (c > 0) (load / c * 100).toInt() else null } }
                ?: s.cpuPct?.toInt()
            val memText = s.memUsed?.let { used -> s.memTotal?.let { total -> if (total > 0) "${used / MB}/${total / MB} MB" else null } }
                ?: s.memPct?.let { "%.0f%% mem".format(it) }
            listOfNotNull(cpuPct?.let { "CPU $it%" }, memText).joinToString(" · ").takeIf { it.isNotBlank() }
        }
        sysLine?.let { line ->
            builder.addItem(Row.Builder().setTitle("System").addText(line).build())
        }
        // Row 4: Last completed task (A6.4) — conditional (Drive: max 6 rows total)
        lastCompletedTask?.let { task ->
            builder.addItem(
                Row.Builder().setTitle("Last completed").addText("✓ ${task.take(60)}").build(),
            )
        }
        // Row 5: Waiting input — only when sessions are waiting (tap → reply queue)
        if (waiting > 0) {
            builder.addItem(
                Row.Builder()
                    .setTitle(colored("Waiting input", CarColor.YELLOW))
                    .addText("$waiting sessions — tap for reply queue")
                    .setOnClickListener { screenManager.push(WaitingSessionsScreen(carContext)) }
                    .build(),
            )
        }
        // Row 6 (Drive max reached): Automata OR Alert dismiss — alerts take priority
        // BL303-A5.3: alert dismiss; BL303-A7.1: Drive compliance — max 6 rows per page
        if (unreadAlerts > 0) {
            builder.addItem(
                Row.Builder()
                    .setTitle(colored("⚠ $unreadAlerts Alert${if (unreadAlerts > 1) "s" else ""}", CarColor.RED))
                    .addText("Tap to dismiss all")
                    .setOnClickListener { onDismissAlerts() }
                    .build(),
            )
        } else {
            builder.addItem(
                Row.Builder()
                    .setTitle(colored("Automata", CarColor.YELLOW))
                    .addText("Running plans overview")
                    .setOnClickListener { screenManager.push(AutoAutomataScreen(carContext)) }
                    .build(),
            )
        }
        val title = "datawatch ${Version.VERSION}"
        val actionStrip =
            ActionStrip.Builder()
                .addAction(
                    Action.Builder()
                        .setTitle("Server")
                        .setOnClickListener {
                            screenManager.push(AutoServerPickerScreen(carContext))
                        }
                        .build(),
                )
                .addAction(
                    Action.Builder()
                        .setTitle("Monitor")
                        .setOnClickListener {
                            screenManager.push(AutoMonitorScreen(carContext))
                        }
                        .build(),
                )
                .addAction(
                    Action.Builder()
                        .setTitle("About")
                        .setOnClickListener {
                            screenManager.push(AutoAboutScreen(carContext))
                        }
                        .build(),
                )
                .build()
        return ListTemplate.Builder()
            .setTitle(error?.let { "$title · $it" } ?: title)
            .setHeaderAction(Action.APP_ICON)
            .setActionStrip(actionStrip)
            .setSingleList(builder.build())
            .build()
    }

    private fun onDismissAlerts() {
        scope.launch {
            runCatching {
                val profile = resolveActiveProfile() ?: return@runCatching
                AutoServiceLocator.transportFor(profile).markAlertRead(all = true)
                unreadAlerts = 0
            }
            invalidate()
        }
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
 * Apply a datawatch accent colour to a car row's title. The Car App
 * Library allows a narrow subset of [CarColor] spans; GREEN / YELLOW
 * mirror the PWA's "running / waiting" palette exactly.
 */
internal fun colored(
    text: String,
    color: CarColor,
): CarText {
    val spannable = android.text.SpannableString(text)
    spannable.setSpan(
        ForegroundCarColorSpan.create(color),
        0,
        text.length,
        android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
    )
    return CarText.create(spannable)
}

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
