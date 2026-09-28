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
import com.dmzs.datawatchclient.domain.Session
import com.dmzs.datawatchclient.domain.SessionState
import com.dmzs.datawatchclient.transport.dto.ComputeNodeDetailDto
import com.dmzs.datawatchclient.transport.dto.ComputeNodeDto
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
 *
 * Compute node rows are expanded (one Row per metric group) so nothing
 * truncates: CPU+RAM get their own text lines, each GPU gets its own
 * Row with util/temp/power on line 1 and VRAM on line 2, disk from
 * server stats gets its own Row.
 */
public class AutoMonitorScreen(
    carContext: CarContext,
    private val forcedProfile: com.dmzs.datawatchclient.domain.ServerProfile? = null,
) : Screen(carContext) {
    /** Snapshot per server: (profile, stats?, error?) */
    private var serverRows: List<ServerRow> = emptyList()
    private var isLoading: Boolean = true
    private var pollJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Track last snapshot hash to skip redundant invalidate() calls (standard §15).
    private var lastRowsHash: Int = -1

    private data class ServerRow(
        val profile: ServerProfile,
        val stats: StatsDto? = null,
        val error: String? = null,
        // Live session counts (total, running, waiting) — supplemented from listSessions()
        // because the server's /api/stats often returns sessions_* as 0.
        val sessionCounts: Triple<Int, Int, Int>? = null,
        // All enabled compute nodes (dto → detail) — v8.25.3+ removed GPU from /api/stats.
        // Full ComputeNodeDto kept so hardwareSpec/kind/tags are available for display.
        val computeNodes: List<Pair<ComputeNodeDto, ComputeNodeDetailDto>> = emptyList(),
    )

    init {
        // Eager fetch so the first onGetTemplate() render shows real data, not "No enabled servers".
        scope.launch {
            refresh()
            invalidate()
        }
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

        /** Deduplicate compute nodes to one entry per unique physical host (by address hostname). */
        fun deduplicateComputeNodes(
            nodes: List<Pair<ComputeNodeDto, ComputeNodeDetailDto>>,
        ): List<Pair<ComputeNodeDto, ComputeNodeDetailDto>> =
            nodes
                .groupBy { (dto, _) ->
                    try { java.net.URL(dto.address).host } catch (_: Exception) { dto.address }
                }
                .values
                .map { group ->
                    // Prefer a node that has a bound observer peer (richer live data).
                    group.maxByOrNull { (dto, _) -> if (dto.observerPeer != null) 1 else 0 } ?: group.first()
                }
                .sortedBy { (dto, _) -> dto.name }

        /** One-liner summary shown on the per-host row in the monitor list. */
        fun buildNodeSummary(nodeDto: ComputeNodeDto, detail: ComputeNodeDetailDto): String {
            val parts = mutableListOf<String>()
            val cpuPct = detail.cpu?.pct?.toInt() ?: detail.cpuPct?.toInt()
            cpuPct?.let { parts += "CPU $it%" }
            val memPct = detail.mem?.pct?.toInt() ?: detail.memPct?.toInt()
            memPct?.let { parts += "Mem $it%" }
            val gpuPct = detail.gpu.firstOrNull()?.utilPct?.toInt()
            gpuPct?.let { parts += "GPU $it%" }
            if (gpuPct == null) {
                val diskPct = detail.disk.maxByOrNull { it.totalBytes }?.pct?.toInt()
                    ?: detail.diskPct?.toInt()
                diskPct?.let { parts += "Disk $it%" }
            }
            return parts.joinToString(" · ").ifBlank { nodeDto.name }
        }
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
                val transport = AutoServiceLocator.transportFor(forcedProfile)
                val statsResult = transport.stats()
                val liveSessions = transport.listSessions().getOrNull()
                val counts = buildSessionCounts(statsResult.getOrNull(), liveSessions)
                val computeNodes = runCatching {
                    transport.listComputeNodes().getOrNull()
                        ?.filter { it.enabled }
                        ?.mapNotNull { node ->
                            transport.getComputeNodeDetail(node.name).getOrNull()?.let { node to it }
                        } ?: emptyList()
                }.getOrElse { emptyList() }
                val result =
                    statsResult.fold(
                        onSuccess = { dto -> ServerRow(forcedProfile, dto, sessionCounts = counts, computeNodes = computeNodes) },
                        onFailure = { err ->
                            ServerRow(
                                forcedProfile,
                                error = err.message ?: err::class.simpleName ?: "error",
                                sessionCounts = counts,
                                computeNodes = computeNodes,
                            )
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
            // B28: fetch stats + session counts for all enabled servers in parallel.
            val rows =
                coroutineScope {
                    enabled.map { p ->
                        async {
                            val transport = AutoServiceLocator.transportFor(p)
                            val statsResult = transport.stats()
                            val liveSessions = transport.listSessions().getOrNull()
                            val counts = buildSessionCounts(statsResult.getOrNull(), liveSessions)
                            val computeNodes = runCatching {
                                transport.listComputeNodes().getOrNull()
                                    ?.filter { it.enabled }
                                    ?.mapNotNull { node ->
                                        transport.getComputeNodeDetail(node.name).getOrNull()?.let { node to it }
                                    } ?: emptyList()
                            }.getOrElse { emptyList() }
                            statsResult.fold(
                                onSuccess = { dto -> ServerRow(p, dto, sessionCounts = counts, computeNodes = computeNodes) },
                                onFailure = { err ->
                                    ServerRow(
                                        p,
                                        error = err.message ?: err::class.simpleName ?: "error",
                                        sessionCounts = counts,
                                        computeNodes = computeNodes,
                                    )
                                },
                            )
                        }
                    }.awaitAll()
                }
            serverRows = rows
        } catch (e: Throwable) {
            serverRows = emptyList()
        } finally {
            isLoading = false
        }
    }

    private fun buildSessionCounts(
        stats: StatsDto?,
        sessions: List<Session>?,
    ): Triple<Int, Int, Int>? =
        when {
            stats != null && stats.sessionsTotal > 0 ->
                Triple(stats.sessionsTotal, stats.sessionsRunning, stats.sessionsWaiting)
            sessions != null ->
                Triple(
                    sessions.size,
                    sessions.count { it.state == SessionState.Running },
                    sessions.count { it.state == SessionState.Waiting || it.state == SessionState.RateLimited },
                )
            else -> null
        }

    override fun onGetTemplate(): Template = try {
        val items = ItemList.Builder()
        val rows = serverRows
        if (isLoading) {
            items.addItem(
                Row.Builder()
                    .setTitle("Loading…")
                    .addText("Fetching server data")
                    .build(),
            )
        } else if (rows.isEmpty()) {
            items.addItem(
                Row.Builder()
                    .setTitle("No enabled servers")
                    .addText("Configure a server on the paired phone")
                    .build(),
            )
        } else if (rows.size == 1) {
            // Single-server: if compute nodes are present, show one clickable row per unique
            // physical host so each machine gets its own full-detail card.  When there are no
            // compute nodes fall back to the existing flat addDetailRows() layout.
            val row = rows[0]
            val s = row.stats
            val onSessions: () -> Unit =
                if (forcedProfile != null) {
                    {
                        screenManager.pop()
                        screenManager.push(AutoSessionListScreen(carContext))
                    }
                } else {
                    { screenManager.push(AutoSessionListScreen(carContext)) }
                }
            val uniqueNodes = deduplicateComputeNodes(row.computeNodes)
            if (uniqueNodes.isNotEmpty()) {
                // One row per unique physical host — tap opens AutoComputeNodeDetailScreen.
                uniqueNodes.take(MAX_ROWS - 1).forEach { (nodeDto, detail) ->
                    items.addItem(
                        Row.Builder()
                            .setTitle(nodeDto.name)
                            .addText(buildNodeSummary(nodeDto, detail))
                            .setOnClickListener {
                                screenManager.push(AutoComputeNodeDetailScreen(carContext, nodeDto, detail))
                            }
                            .build(),
                    )
                }
                // Sessions row always last.
                val (sesTotal, sesRunning, sesWaiting) =
                    row.sessionCounts ?: Triple(s?.sessionsTotal ?: 0, s?.sessionsRunning ?: 0, s?.sessionsWaiting ?: 0)
                items.addItem(
                    Row.Builder()
                        .setTitle("Sessions")
                        .addText("$sesTotal total · $sesRunning run · $sesWaiting wait")
                        .setOnClickListener(onSessions)
                        .build(),
                )
            } else if (s != null) {
                // No compute nodes — flat detail rows (existing behaviour).
                addDetailRows(items, s, onSessionsClick = onSessions, sessionCounts = row.sessionCounts, computeNodes = emptyList())
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
                        else -> buildServerSummary(s, row.sessionCounts, row.computeNodes)
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

        // Car App Library driving validator requires icon-only ActionStrip on ListTemplate —
        // titled strip actions cause "can't do that while driving" on some head units.
        // Sessions navigation is also reachable via the "Sessions" row in addDetailRows().
        fun iconOf(resId: Int) = CarIcon.Builder(IconCompat.createWithResource(carContext, resId)).build()
        val actionStrip =
            ActionStrip.Builder()
                .addAction(
                    Action.Builder()
                        .setIcon(iconOf(R.drawable.ic_auto_sessions))
                        .setOnClickListener {
                            // Monitor2 (forcedProfile): pop self first to stay within 5-screen limit.
                            if (forcedProfile != null) screenManager.pop()
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
        ListTemplate.Builder()
            .setTitle(title)
            .setHeaderAction(Action.BACK)
            .setActionStrip(actionStrip)
            .setSingleList(items.build())
            .build()
    } catch (e: Throwable) {
        val errItems = ItemList.Builder()
            .addItem(Row.Builder().setTitle("Error").addText(e.message ?: "Unknown").build())
            .build()
        ListTemplate.Builder()
            .setTitle("Monitor")
            .setHeaderAction(Action.BACK)
            .setSingleList(errItems)
            .build()
    }
}

private const val PROGRESS_BAR_WIDTH: Int = 10

/** Renders a compact progress bar: "▓▓▓░░░░░░░ 28%" (10 wide). */
private fun progressBar(
    pct: Int,
    width: Int = PROGRESS_BAR_WIDTH,
): String {
    val clamped = pct.coerceIn(0, PCT_MULTIPLIER)
    val filled = (clamped * width / PCT_MULTIPLIER).coerceIn(0, width)
    return "▓".repeat(filled) + "░".repeat(width - filled) + " $clamped%"
}

/** Adds the full detail rows for a single server (single-server mode).
 *
 * Layout — always in this order so all stats for one physical host stay together:
 *   1. CPU          (server /api/stats)
 *   2. Memory       (server /api/stats)
 *   3. Disk + Swap  (server /api/stats)
 *   4. GPU row(s)   (compute-node detail, GPU/power/VRAM only — no duplicate CPU/RAM)
 *   5. Ollama       (compute-node detail, if running)
 *   6. Sessions     (clickable)
 */
private fun addDetailRows(
    items: ItemList.Builder,
    s: StatsDto,
    onSessionsClick: (() -> Unit)? = null,
    sessionCounts: Triple<Int, Int, Int>? = null,
    computeNodes: List<Pair<ComputeNodeDto, ComputeNodeDetailDto>> = emptyList(),
) {
    // ── 1. CPU ──────────────────────────────────────────────────────────────
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
                "${progressBar(cpuPct)}  load ${"%.2f".format(load1)} · $cores cores"
            cpuPct != null -> progressBar(cpuPct)
            else -> "—"
        }
    items.addItem(Row.Builder().setTitle("CPU").addText(cpuText).build())

    // ── 2. Memory ───────────────────────────────────────────────────────────
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

    // ── 3. Disk + Swap ──────────────────────────────────────────────────────
    val diskUsed = s.diskUsed
    val diskTotal = s.diskTotal
    if (diskUsed != null && diskTotal != null && diskTotal > 0) {
        val diskPct = (diskUsed * PCT_MULTIPLIER / diskTotal).toInt()
        val diskBuilder = Row.Builder()
            .setTitle("Disk")
            .addText("${progressBar(diskPct)}  ${fmt(diskUsed)} / ${fmt(diskTotal)}")
        if (s.swapTotal > 0) {
            val swapPct = (s.swapUsed * PCT_MULTIPLIER / s.swapTotal).toInt()
            diskBuilder.addText("Swap ${progressBar(swapPct)}  ${fmt(s.swapUsed)} / ${fmt(s.swapTotal)}")
        }
        items.addItem(diskBuilder.build())
    }

    if (computeNodes.isNotEmpty()) {
        // ── 4 & 5. GPU + Ollama from compute nodes (no CPU/RAM — already shown above) ──
        // Each node contributes GPU rows + an optional Ollama row.  CPU/RAM from the node
        // detail are intentionally omitted: the server /api/stats already covers the host,
        // and duplicating them would imply they are different machines.
        val gpuRows = mutableListOf<Row>()
        computeNodes.forEach { (nodeDto, detail) ->
            gpuRows += buildComputeNodeGpuRows(nodeDto, detail)
        }
        // Budget: MAX_DETAIL_ROWS - rows already added (CPU+Mem+Disk = 3 max) - 1 for Sessions
        val used = 3 + (if (diskUsed != null && diskTotal != null && diskTotal > 0) 0 else -1)
        val budget = MAX_DETAIL_ROWS - used - 1
        gpuRows.take(budget.coerceAtLeast(1)).forEach { items.addItem(it) }
    } else {
        // No compute nodes: show GPU from /api/stats if present, then uptime.
        val gpuUtilInt = s.gpuUtilPct?.toInt() ?: s.gpuPct?.toInt()
        val vramTotal = s.gpuMemTotalMb
        val hasGpu = s.gpuName != null || gpuUtilInt != null || (vramTotal != null && vramTotal > 0)
        if (hasGpu) {
            val name = s.gpuName ?: "GPU"
            val tempSuffix = s.gpuTemp?.let { "  ${it.toInt()}°C" } ?: ""
            val rowBuilder = Row.Builder().setTitle(name)
            if (gpuUtilInt != null) {
                rowBuilder.addText("${progressBar(gpuUtilInt)}$tempSuffix")
                if (vramTotal != null && vramTotal > 0) {
                    val used = s.gpuMemUsedMb ?: 0L
                    val vramPct = (used * PCT_MULTIPLIER / vramTotal).toInt()
                    rowBuilder.addText(
                        "VRAM ${progressBar(vramPct)}  ${fmt(used * VRAM_MEBIBYTES_TO_BYTES)} / ${fmt(vramTotal * VRAM_MEBIBYTES_TO_BYTES)}",
                    )
                }
            } else if (vramTotal != null && vramTotal > 0) {
                val used = s.gpuMemUsedMb ?: 0L
                val vramPct = (used * PCT_MULTIPLIER / vramTotal).toInt()
                rowBuilder.addText(
                    "${progressBar(vramPct)}  ${fmt(used * VRAM_MEBIBYTES_TO_BYTES)} / ${fmt(vramTotal * VRAM_MEBIBYTES_TO_BYTES)}$tempSuffix",
                )
            } else {
                rowBuilder.addText(tempSuffix.ifBlank { "—" })
            }
            items.addItem(rowBuilder.build())
        }
        if (s.uptimeSeconds > 0) {
            items.addItem(Row.Builder().setTitle("Uptime").addText(uptime(s.uptimeSeconds)).build())
        }
    }

    // ── 6. Sessions ─────────────────────────────────────────────────────────
    val (sesTotal, sesRunning, sesWaiting) = sessionCounts ?: Triple(s.sessionsTotal, s.sessionsRunning, s.sessionsWaiting)
    items.addItem(
        Row.Builder()
            .setTitle("Sessions")
            .addText("$sesTotal total · $sesRunning run · $sesWaiting wait")
            .setOnClickListener(onSessionsClick ?: {})
            .build(),
    )
}

/**
 * Expands one compute node into multiple Rows — one per metric group.
 * Each metric gets its own full-width text line to avoid truncation.
 *
 * Row 1 — node name (title) + CPU line + RAM line
 * Row 2+ — one Row per GPU: title = GPU name/vendor, line1 = util+temp+power, line2 = VRAM
 * Optional Row — Ollama process stats (RSS + running models)
 */
private fun buildComputeNodeRows(nodeDto: ComputeNodeDto, detail: ComputeNodeDetailDto): List<Row> {
    val rows = mutableListOf<Row>()
    val nodeName = nodeDto.name.take(MAX_NODE_TITLE)
    val spec = nodeDto.hardwareSpec

    // ── Row 1: CPU + RAM ────────────────────────────────────────────────────
    val cpuRamBuilder = Row.Builder().setTitle(nodeName)

    val cpu = detail.cpu
    val cpuPct = cpu?.pct?.toInt() ?: detail.cpuPct?.toInt()
    if (cpuPct != null) {
        val cpuLine = buildString {
            append("CPU ${progressBar(cpuPct)}")
            if (cpu != null && cpu.load1 > 0 && cpu.cores > 0) {
                append("  load ${"%.1f".format(cpu.load1)} · ${cpu.cores} cores")
                if (cpu.load5 > 0) append("  (5m ${"%.1f".format(cpu.load5)})")
            } else if (spec?.cpuCores != null && spec.cpuCores > 0) {
                append("  ${spec.cpuCores} cores")
            }
        }
        cpuRamBuilder.addText(cpuLine)
    }

    val mem = detail.mem
    val memPct = mem?.pct?.toInt() ?: detail.memPct?.toInt()
    if (memPct != null) {
        val memLine = buildString {
            append("RAM ${progressBar(memPct)}")
            if (mem != null && mem.totalBytes > 0) {
                append("  ${fmt(mem.usedBytes)} / ${fmt(mem.totalBytes)}")
            } else if (spec?.memoryGb != null && spec.memoryGb > 0) {
                append("  / ${spec.memoryGb} GB")
            }
        }
        cpuRamBuilder.addText(memLine)
    }

    if (cpuPct != null || memPct != null) {
        rows += cpuRamBuilder.build()
    }

    // ── Row(s) 2+: one Row per GPU ─────────────────────────────────────────
    detail.gpu.forEachIndexed { idx, g ->
        val gpuBuilder = Row.Builder()

        // Title: GPU name or vendor, include index if multiple GPUs
        val rawName = g.name.takeIf { it.isNotBlank() && !it.equals("GPU", ignoreCase = true) }
        val rawVendor = g.vendor.takeIf { it.isNotBlank() }
        val gpuTitle = buildString {
            val label = rawName ?: rawVendor ?: "GPU"
            append(label.take(24))
            if (detail.gpu.size > 1) append(" [${idx + 1}]")
        }
        gpuBuilder.setTitle(gpuTitle)

        // Line 1: util % + temperature + power
        val line1 = buildString {
            append(progressBar(g.utilPct.toInt()))
            if (g.tempC > 0) append("  ${g.tempC.toInt()}°C")
            if (g.powerW > 0) append("  ${g.powerW.toInt()}W")
        }
        gpuBuilder.addText(line1)

        // Line 2: VRAM used / total
        if (g.memTotalBytes > 0) {
            val vramPct = (g.memUsedBytes * PCT_MULTIPLIER / g.memTotalBytes).toInt()
            gpuBuilder.addText("VRAM ${progressBar(vramPct)}  ${fmt(g.memUsedBytes)} / ${fmt(g.memTotalBytes)}")
        }

        rows += gpuBuilder.build()
    }

    // ── Optional: Ollama process row ────────────────────────────────────────
    val ollama = detail.ollamaStats
    if (ollama != null && ollama.rssBytes > 0) {
        rows += Row.Builder()
            .setTitle("Ollama ($nodeName)")
            .addText("RSS ${fmt(ollama.rssBytes)}")
            .build()
    }

    return rows
}

/**
 * GPU + Ollama rows only — used when the server host stats (CPU/Mem/Disk) are already
 * shown above, so we only need the compute-specific metrics that don't duplicate them.
 */
private fun buildComputeNodeGpuRows(nodeDto: ComputeNodeDto, detail: ComputeNodeDetailDto): List<Row> {
    val rows = mutableListOf<Row>()
    val nodeName = nodeDto.name.take(MAX_NODE_TITLE)

    // One Row per GPU
    detail.gpu.forEachIndexed { idx, g ->
        val gpuBuilder = Row.Builder()
        val rawName = g.name.takeIf { it.isNotBlank() && !it.equals("GPU", ignoreCase = true) }
        val rawVendor = g.vendor.takeIf { it.isNotBlank() }
        val gpuTitle = buildString {
            val label = rawName ?: rawVendor ?: "GPU"
            append(label.take(24))
            if (detail.gpu.size > 1) append(" [${idx + 1}]")
        }
        gpuBuilder.setTitle(gpuTitle)
        val line1 = buildString {
            append(progressBar(g.utilPct.toInt()))
            if (g.tempC > 0) append("  ${g.tempC.toInt()}°C")
            if (g.powerW > 0) append("  ${g.powerW.toInt()}W")
        }
        gpuBuilder.addText(line1)
        if (g.memTotalBytes > 0) {
            val vramPct = (g.memUsedBytes * PCT_MULTIPLIER / g.memTotalBytes).toInt()
            gpuBuilder.addText("VRAM ${progressBar(vramPct)}  ${fmt(g.memUsedBytes)} / ${fmt(g.memTotalBytes)}")
        }
        rows += gpuBuilder.build()
    }

    // Ollama process row
    val ollama = detail.ollamaStats
    if (ollama != null && ollama.rssBytes > 0) {
        rows += Row.Builder()
            .setTitle("Ollama ($nodeName)")
            .addText("RSS ${fmt(ollama.rssBytes)}")
            .build()
    }

    return rows
}

/** Server-level disk row — API does not expose per-node disk; one row covers the whole server. */
private fun buildDiskRow(s: StatsDto): Row? {
    val diskUsed = s.diskUsed ?: return null
    val diskTotal = s.diskTotal ?: return null
    if (diskTotal <= 0) return null
    val diskPct = (diskUsed * PCT_MULTIPLIER / diskTotal).toInt()
    val builder = Row.Builder()
        .setTitle("Server Disk")
        .addText("${progressBar(diskPct)}  ${fmt(diskUsed)} / ${fmt(diskTotal)}")
    if (s.swapTotal > 0) {
        val swapPct = (s.swapUsed * PCT_MULTIPLIER / s.swapTotal).toInt()
        builder.addText("Swap ${progressBar(swapPct)}  ${fmt(s.swapUsed)} / ${fmt(s.swapTotal)}")
    }
    return builder.build()
}

private const val MAX_DETAIL_ROWS: Int = 6
private const val MAX_NODE_TITLE: Int = 20

/** Compact one-liner summary for multi-server mode: "CPU 45% · Mem 8.2/16 GB · 3 sessions". */
private fun buildServerSummary(
    s: StatsDto,
    sessionCounts: Triple<Int, Int, Int>? = null,
    computeNodes: List<Pair<ComputeNodeDto, ComputeNodeDetailDto>> = emptyList(),
): String {
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
    val gpuSummaryPct = s.gpuUtilPct?.toInt() ?: s.gpuPct?.toInt()
        ?: computeNodes.firstOrNull()?.second?.gpu?.firstOrNull()?.utilPct?.let { it.toInt().takeIf { v -> v > 0 } }
    gpuSummaryPct?.let { parts += "GPU $it%" }
    val nodeCount = computeNodes.size
    if (nodeCount > 1) parts += "$nodeCount nodes"
    val totalSessions = sessionCounts?.first ?: s.sessionsTotal
    if (totalSessions > 0) parts += "${totalSessions}s"
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
