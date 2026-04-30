package com.dmzs.datawatchclient.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.dmzs.datawatchclient.Version
import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.transport.dto.StatsDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
public class AutoMonitorScreen(carContext: CarContext) : Screen(carContext) {
    /** Snapshot per server: (profile, stats?, error?) */
    private var serverRows: List<ServerRow> = emptyList()
    private var pollJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

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
            },
        )
    }

    private companion object {
        const val POLL_MS: Long = 15_000L
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
            // Single-server: show full detail rows (original layout).
            val row = rows[0]
            items.addItem(
                Row.Builder()
                    .setTitle(colored("● ${row.profile.displayName}", CarColor.GREEN))
                    .addText(row.profile.baseUrl)
                    .build(),
            )
            val s = row.stats
            if (s != null) {
                addDetailRows(items, s)
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
            rows.forEach { row ->
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
                        .build(),
                )
            }
        }
        val actionStrip =
            ActionStrip.Builder()
                .addAction(
                    Action.Builder()
                        .setTitle("Sessions")
                        .setOnClickListener {
                            screenManager.push(AutoSummaryScreen(carContext))
                        }
                        .build(),
                )
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
                        .setTitle("About")
                        .setOnClickListener {
                            screenManager.push(AutoAboutScreen(carContext))
                        }
                        .build(),
                )
                .build()
        val title = "datawatch ${Version.VERSION}"
        return ListTemplate.Builder()
            .setTitle(title)
            .setHeaderAction(Action.APP_ICON)
            .setActionStrip(actionStrip)
            .setSingleList(items.build())
            .build()
    }
}

/** Adds the full detail rows for a single server (single-server mode). */
private fun addDetailRows(items: ItemList.Builder, s: StatsDto) {
    val load1 = s.cpuLoad1
    val cores = s.cpuCores
    val cpuText =
        when {
            load1 != null && cores != null && cores > 0 ->
                "%.2f load · %d cores".format(load1, cores)
            s.cpuPct != null -> "%.1f%%".format(s.cpuPct)
            else -> "—"
        }
    items.addItem(Row.Builder().setTitle("CPU").addText(cpuText).build())
    val memUsed = s.memUsed
    val memTotal = s.memTotal
    val memText =
        when {
            memUsed != null && memTotal != null && memTotal > 0 ->
                "${fmt(memUsed)} / ${fmt(memTotal)}"
            s.memPct != null -> "%.1f%%".format(s.memPct)
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
            load1 != null && cores != null && cores > 0 -> (load1 / cores * 100).toInt()
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
