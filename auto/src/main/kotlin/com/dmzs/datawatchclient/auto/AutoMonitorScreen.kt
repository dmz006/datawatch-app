package com.dmzs.datawatchclient.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.dmzs.datawatchclient.Version
import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.transport.dto.StatsDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Driver-safe Monitor view — surfaces the same vitals the PWA
 * Monitor tab does, constrained to driver-distraction-friendly
 * rows. B28: when multiple servers are enabled, renders one summary
 * row per server (CPU · Mem · sessions). Single-server detail rows
 * expand when only one server is configured or only one has data.
 */
public class AutoMonitorScreen(
    carContext: CarContext,
    private val forcedProfile: com.dmzs.datawatchclient.domain.ServerProfile? = null,
) : Screen(carContext) {
    /** Snapshot per server: (profile, stats?, error?) */
    private var serverRows: List<ServerRow> = emptyList()
    private var pollJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    // Track last snapshot hash to skip redundant invalidate() calls (standard §15).
    private var lastRowsHash: Int = -1

    private data class ServerRow(
        val profile: ServerProfile,
        val stats: StatsDto? = null,
        val error: String? = null,
    )

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

    private companion object {
        const val POLL_MS: Long = 15_000L
        const val MAX_ROWS: Int = 5
    }

    private suspend fun pollLoop() {
        while (scope.isActive) {
            refresh()
            // §15: only call invalidate() when data actually changed.
            val newHash = serverRows.hashCode()
            if (newHash != lastRowsHash) {
                lastRowsHash = newHash
                invalidate()
            }
            delay(POLL_MS)
        }
    }

    private suspend fun refresh() {
        try {
            // Forced single-server mode: only fetch stats for that profile.
            if (forcedProfile != null) {
                val result = AutoServiceLocator.transportFor(forcedProfile).stats().fold(
                    onSuccess = { dto -> ServerRow(forcedProfile, dto) },
                    onFailure = { err ->
                        ServerRow(forcedProfile, error = err.message ?: err::class.simpleName ?: "error")
                    },
                )
                serverRows = listOf(result)
                return
            }
            val profiles = AutoServiceLocator.profileRepository.observeAll().first()
            val enabled = profiles.filter { it.enabled }
            if (enabled.isEmpty()) {
                serverRows = emptyList()
                return
            }
            // B28: fetch all enabled servers in parallel.
            val rows = coroutineScope {
                enabled.map { p ->
                    async {
                        AutoServiceLocator.transportFor(p).stats().fold(
                            onSuccess = { dto -> ServerRow(p, dto) },
                            onFailure = { err ->
                                ServerRow(p, error = err.message ?: err::class.simpleName ?: "error")
                            },
                        )
                    }
                }.awaitAll()
            }
            serverRows = rows
        } catch (e: Throwable) {
            serverRows = emptyList()
        }
    }

    override fun onGetTemplate(): Template {
        val items = ItemList.Builder()
        val rows = serverRows
        if (rows.isEmpty()) {
            items.addItem(
                Row.Builder()
                    .setTitle("No enabled servers")
                    .addText("Configure a server on the paired phone")
                    .build(),
            )
        } else if (rows.size == 1) {
            // Single-server: full detail rows, no profile header row (Car App Library
            // caps ItemList at 6; detail rows alone can reach 6: CPU+Mem+Disk+GPU+Sessions+Uptime).
            val row = rows[0]
            val s = row.stats
            if (s != null) {
                addDetailRows(items, s, onSessionsClick = { screenManager.push(AutoSessionListScreen(carContext)) })
            } else {
                items.addItem(
                    Row.Builder()
                        .setTitle(row.error?.let { "Error" } ?: "Loading…")
                        .addText(row.error ?: "Fetching server stats")
                        .build(),
                )
            }
        } else {
            // Multi-server (B28): one compact summary row per server.
            rows.take(MAX_ROWS).forEach { row ->
                val s = row.stats
                val summary =
                    when {
                        row.error != null -> "offline — ${row.error}"
                        s == null -> "loading…"
                        else -> buildServerSummary(s)
                    }
                val titleColor = if (row.error != null) CarColor.RED else CarColor.GREEN
                items.addItem(
                    Row.Builder()
                        .setTitle(colored("● ${row.profile.displayName}", titleColor))
                        .addText(summary)
                        .setOnClickListener {
                            screenManager.push(AutoMonitorScreen(carContext, forcedProfile = row.profile))
                        }
                        .build(),
                )
            }
            if (rows.size > MAX_ROWS) {
                items.addItem(
                    Row.Builder()
                        .setTitle("… and ${rows.size - MAX_ROWS} more servers")
                        .addText("Manage servers on the paired phone")
                        .build(),
                )
            }
        }
        // ActionStrip: Car App Library hard-limits ListTemplate to 2 actions.
        // "Sessions" is the primary CTA (titled); server picker is secondary (icon only).
        // Voice and About are reachable via voice commands and are omitted here.
        fun iconOf(resId: Int) =
            CarIcon.Builder(IconCompat.createWithResource(carContext, resId)).build()
        val actionStrip =
            ActionStrip.Builder()
                .addAction(
                    Action.Builder()
                        .setTitle("Sessions")
                        .setOnClickListener {
                            screenManager.push(AutoSessionListScreen(carContext))
                        }
                        .build(),
                )
                .addAction(
                    Action.Builder()
                        .setIcon(iconOf(R.drawable.ic_auto_server))
                        .setOnClickListener {
                            screenManager.push(AutoServerPickerScreen(carContext))
                        }
                        .build(),
                )
                .build()
        val title = if (rows.size == 1) rows[0].profile.displayName else "datawatch ${Version.VERSION}"
        return ListTemplate.Builder()
            .setTitle(title)
            .setHeaderAction(Action.BACK)
            .setActionStrip(actionStrip)
            .setSingleList(items.build())
            .build()
    }
}

private const val PROGRESS_BAR_WIDTH: Int = 10

/** Renders a compact progress bar: "▓▓▓░░░░░░░ 28%" (10 wide). */
private fun progressBar(pct: Int, width: Int = PROGRESS_BAR_WIDTH): String {
    val clamped = pct.coerceIn(0, PCT_MULTIPLIER)
    val filled = (clamped * width / PCT_MULTIPLIER).coerceIn(0, width)
    return "▓".repeat(filled) + "░".repeat(width - filled) + " $clamped%"
}

/** Adds the full detail rows for a single server (single-server mode). */
private fun addDetailRows(items: ItemList.Builder, s: StatsDto, onSessionsClick: (() -> Unit)? = null) {
    val load1 = s.cpuLoad1
    val cores = s.cpuCores
    val cpuPct =
        when {
            load1 != null && cores != null && cores > 0 -> (load1 / cores * PCT_MULTIPLIER).toInt()
            s.cpuPct != null -> s.cpuPct!!.toInt()
            else -> null
        }
    val cpuText =
        when {
            cpuPct != null && load1 != null && cores != null && cores > 0 ->
                // Avoid .format() on a string containing the progress bar's literal '%' —
                // java.util.Formatter would parse it as a duplicate flag specifier and crash.
                "${progressBar(cpuPct)}  load ${"%.2f".format(load1)} · $cores cores"
            cpuPct != null -> progressBar(cpuPct)
            else -> "—"
        }
    items.addItem(Row.Builder().setTitle("CPU").addText(cpuText).build())
    val memUsed = s.memUsed
    val memTotal = s.memTotal
    val memPct =
        when {
            memUsed != null && memTotal != null && memTotal > 0 ->
                (memUsed * PCT_MULTIPLIER / memTotal).toInt()
            s.memPct != null -> s.memPct!!.toInt()
            else -> null
        }
    val memText =
        when {
            memPct != null && memUsed != null && memTotal != null && memTotal > 0 ->
                "${progressBar(memPct)}  ${fmt(memUsed)} / ${fmt(memTotal)}"
            memPct != null -> progressBar(memPct)
            else -> "—"
        }
    items.addItem(Row.Builder().setTitle("Memory").addText(memText).build())
    val diskUsed = s.diskUsed
    val diskTotal = s.diskTotal
    if (diskUsed != null && diskTotal != null && diskTotal > 0) {
        items.addItem(
            Row.Builder().setTitle("Disk").addText("${fmt(diskUsed)} / ${fmt(diskTotal)}").build(),
        )
    }
    val vramTotal = s.gpuMemTotalMb
    if (vramTotal != null && vramTotal > 0) {
        val used = s.gpuMemUsedMb ?: 0L
        val name = s.gpuName ?: "GPU"
        items.addItem(
            Row.Builder()
                .setTitle(name)
                .addText(
                    "${fmt(used * VRAM_MEBIBYTES_TO_BYTES)} / ${fmt(vramTotal * VRAM_MEBIBYTES_TO_BYTES)} VRAM",
                )
                .build(),
        )
    }
    items.addItem(
        Row.Builder()
            .setTitle("Sessions")
            .addText("${s.sessionsTotal} total · ${s.sessionsRunning} run · ${s.sessionsWaiting} wait")
            .setOnClickListener(onSessionsClick ?: {})
            .build(),
    )
    if (s.uptimeSeconds > 0) {
        items.addItem(Row.Builder().setTitle("Uptime").addText(uptime(s.uptimeSeconds)).build())
    }
}

/** Compact one-liner summary for multi-server mode: "CPU 45% · Mem 8.2/16 GB · 3 sessions". */
private fun buildServerSummary(s: StatsDto): String {
    val parts = mutableListOf<String>()
    val load1 = s.cpuLoad1
    val cores = s.cpuCores
    val cpuPct =
        when {
            load1 != null && cores != null && cores > 0 -> (load1 / cores * PCT_MULTIPLIER).toInt()
            s.cpuPct != null -> s.cpuPct!!.toInt()
            else -> null
        }
    cpuPct?.let { parts += "CPU $it%" }
    val memUsed = s.memUsed
    val memTotal = s.memTotal
    if (memUsed != null && memTotal != null && memTotal > 0) {
        parts += "Mem ${fmt(memUsed)} / ${fmt(memTotal)}"
    } else {
        s.memPct?.let { parts += "Mem ${"%.0f".format(it)}%" }
    }
    if (s.sessionsTotal > 0) parts += "${s.sessionsTotal}s"
    return parts.joinToString(" · ").ifBlank { "no data" }
}

// Named constants satisfy detekt's MagicNumber rule while keeping
// the byte / time maths readable. Decimal (SI) units rather than
// binary (IEC) — matches `fmt`'s "GB / MB / KB" strings.
private const val PCT_MULTIPLIER: Int = 100
private const val BYTES_PER_KB: Long = 1_000L
private const val BYTES_PER_MB: Long = 1_000_000L
private const val BYTES_PER_GB: Long = 1_000_000_000L
private const val SECONDS_PER_MINUTE: Long = 60L
private const val SECONDS_PER_HOUR: Long = 3_600L
private const val SECONDS_PER_DAY: Long = 86_400L

// VRAM figures arrive in mebibytes from the daemon; converting to
// bytes lets `fmt` do the "GB / MB" threshold choice. Kept inline
// with BYTES_PER_MB so the unit conversion is obvious to readers.
internal const val VRAM_MEBIBYTES_TO_BYTES: Long = BYTES_PER_MB

private fun fmt(bytes: Long): String =
    when {
        bytes >= BYTES_PER_GB -> "%.1f GB".format(bytes / BYTES_PER_GB.toDouble())
        bytes >= BYTES_PER_MB -> "%.1f MB".format(bytes / BYTES_PER_MB.toDouble())
        bytes >= BYTES_PER_KB -> "%.1f KB".format(bytes / BYTES_PER_KB.toDouble())
        else -> "$bytes B"
    }

private fun uptime(seconds: Long): String {
    val d = seconds / SECONDS_PER_DAY
    val h = (seconds % SECONDS_PER_DAY) / SECONDS_PER_HOUR
    val m = (seconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    return buildString {
        if (d > 0) append("${d}d ")
        if (h > 0 || d > 0) append("${h}h ")
        append("${m}m")
    }
}
