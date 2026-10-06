package com.dmzs.datawatchclient.di

import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.transport.TransportClient
import com.dmzs.datawatchclient.transport.dto.StatsDto
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.create
import platform.Foundation.writeToFile
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

// ─────────────────────────────────────────────────────────────────────────
// iOS Observer parity (B20–B25). Every model below is display-ready: the
// PWA's per-metric thresholds (D29a) and copy live here so Swift only maps
// a `tone` string to a DatawatchColors token. Tones: error · warning ·
// success · accent · accent2 · muted · text.
// ─────────────────────────────────────────────────────────────────────────

/** One labelled gauge bar (PWA `bar(label, val, max, color, extra)`). */
public data class IosObsBar(val label: String, val fraction: Double, val tone: String, val value: String)

/** Key/value line (stat-card rows). Empty [key] = free-text line. */
public data class IosObsKv(val key: String, val value: String, val tone: String)

/** A PWA `.stat-card` block. [clipboardCommand] non-empty = tap-to-copy row (RTK upgrade). */
public data class IosObsCard(val title: String, val rows: List<IosObsKv>, val clipboardCommand: String)

/** Peer-resource chip; [gpu] chips use the accent2 tint. */
public data class IosObsChip(val text: String, val gpu: Boolean)

/** BL379 per-system grid card (local host first, then one per observer peer). */
public data class IosSystemCard(
    val name: String,
    val isLocal: Boolean,
    val dotTone: String,
    val bars: List<IosObsBar>,
    val lines: List<IosObsKv>,
)

/** One observer peer (datawatch-stats) with everything the three peer surfaces need. */
public data class IosPeerRow(
    val name: String,
    /** "A" / "B" / "C" or "" — filter pills key. */
    val shapeKey: String,
    /** agent / standalone / cluster / "shape X". */
    val shapeLabel: String,
    /** Raw lower-cased shape for the Peer Resources tag ("?" when absent). */
    val shapeTag: String,
    val dotTone: String,
    /** "last push 5s ago" / "never pushed" (stats block). */
    val ageLabel: String,
    /** "5s ago" / "never" (bottom Federated Peers card). */
    val ageShort: String,
    val version: String,
    val attachedNodes: List<String>,
    val chips: List<IosObsChip>,
)

/** Fields of raw `/api/stats` the typed [StatsDto] doesn't carry. */
public data class IosStatsExtras(
    val hostname: String,
    /** -1 when the server didn't send `active_sessions`. */
    val activeSessions: Int,
    val rtkLatestVersion: String,
)

/** 8 s refresh payload: grid + peer rows share one set of peer snapshots. */
public data class IosSystemsSnapshot(
    val grid: List<IosSystemCard>,
    val peers: List<IosPeerRow>,
    /** "" ok · "off" (503 registry disabled) · "unavailable". */
    val peersError: String,
    val extras: IosStatsExtras,
)

/** PWA renderStatsData output for one StatsDto frame. */
public data class IosStatsPanel(
    val bars: List<IosObsBar>,
    /** Non-empty → red "GPU probe failed" card spanning the grid. */
    val gpuError: String,
    val cards: List<IosObsCard>,
    val sessionActive: Int,
    val sessionMax: Int,
    val sessionFraction: Double,
    val sessionCounts: String,
    /** "" none · otherwise banner/notice text. */
    val ebpfBanner: String,
    val ebpfDegraded: Boolean,
)

/** D78a server-info card (null when /api/info failed) + `session.max_sessions` (0 = unknown). */
public data class IosServerContext(val serverInfo: IosObsCard?, val maxSessions: Int)

public data class IosNetProc(val name: String, val rx: String, val tx: String)

/** `/api/stats?v=2` → eBPF status line + per-process network table. */
public data class IosEbpfSnapshot(
    val tone: String,
    val headline: String,
    val message: String,
    val procs: List<IosNetProc>,
)

public data class IosPluginRow(
    val name: String,
    val native: Boolean,
    val enabled: Boolean,
    val version: String,
    val detail: String,
)

public data class IosClusterRow(
    val name: String,
    val ready: Boolean,
    val pressure: String,
    val pods: String,
    val cpuPct: Int,
    val memPct: Int,
)

public data class IosObsLine(val text: String, val tone: String)

public data class IosChannelDiagnostics(val lines: List<IosObsLine>, val hints: List<String>)

public data class IosCommBackends(
    /** "" ok, else the PWA message ("No communication backends enabled." …). */
    val message: String,
    val enabled: List<String>,
    val matrixEnabled: Boolean,
)

public data class IosMatrixStatus(val text: String, val tone: String)

public data class IosWebSearchStats(
    val hasProviders: Boolean,
    val rows: List<IosObsKv>,
    val providers: List<IosObsKv>,
    val series: List<Int>,
)

public data class IosWebSearchHistoryRow(
    val time: String,
    val provider: String,
    val query: String,
    val status: String,
    val tone: String,
)

public data class IosMetaGroup(
    val title: String,
    val subtitle: String,
    val lines: List<String>,
    val unbound: Boolean,
)

public data class IosSnapshotEnvelope(val kind: String, val id: String, val stats: String)

public data class IosPeerSnapshot(val headLine: String, val envelopes: List<IosSnapshotEnvelope>)

public data class IosPeersCard(
    val statPills: List<IosObsKv>,
    val config: List<IosObsKv>,
    val peers: List<IosPeerRow>,
)

public data class IosMemoryStats(val enabled: Boolean, val tiles: List<IosObsKv>)

public data class IosMemoryRow(val id: Long, val header: String, val content: String)

public data class IosScheduleRow(
    val id: String,
    val label: String,
    val command: String,
    val cron: String,
    val whenText: String,
    val state: String,
    val stateTone: String,
    val pending: Boolean,
    val runAtIso: String,
)

public data class IosCooldown(val active: Boolean, val text: String, val tone: String)

public data class IosAnalyticsRow(
    val date: String,
    val total: Int,
    val ok: Int,
    val err: Int,
    val fraction: Double,
    val tone: String,
)

public data class IosAnalytics(val successRate: String, val rows: List<IosAnalyticsRow>)

public data class IosAuditRow(
    val ts: String,
    val action: String,
    val actor: String,
    val session: String,
    val details: String,
)

public data class IosKgTriple(val subject: String, val predicate: String, val obj: String, val validFrom: String)

public data class IosLogPage(val lines: List<IosObsLine>, val info: String)

public object IosObserver {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun t(profile: ServerProfile): TransportClient = IosServiceLocator.transportFor(profile)

    private fun msg(e: Throwable, fallback: String): String = e.message?.takeIf { it.isNotBlank() } ?: fallback

    // ── B20/B22: per-system grid + peer rows (8 s) ──────────────────────

    public fun loadSystems(
        profile: ServerProfile,
        onResult: (IosSystemsSnapshot) -> Unit,
    ) {
        scope.launch {
            val tr = t(profile)
            val snapshot =
                coroutineScope {
                    val localJob = async { tr.fetchStatsJson().getOrNull() }
                    val nodesJob = async { tr.listComputeNodes().getOrNull().orEmpty() }
                    val peersRes = tr.observerPeers()
                    val peers = peersRes.getOrNull()?.peers.orEmpty()
                    val snaps =
                        peers.map { p ->
                            async { p to tr.fetchObserverPeerSnapshot(p.name).getOrNull() }
                        }.awaitAll()
                    val local = localJob.await()
                    val nodes = nodesJob.await()
                    val peerToNodes = HashMap<String, MutableList<String>>()
                    for (n in nodes) {
                        val explicit = n.observerPeer.orEmpty()
                        val key = if (explicit.isNotBlank()) explicit else n.name
                        peerToNodes.getOrPut(key) { mutableListOf() }.add(n.name)
                    }
                    val now = Clock.System.now().toEpochMilliseconds()
                    val grid = mutableListOf<IosSystemCard>()
                    local?.let { localCard(it) }?.let { grid.add(it) }
                    val rows = mutableListOf<IosPeerRow>()
                    for ((p, snap) in snaps) {
                        val age = ageOf(p.lastPushAt, now)
                        grid.add(peerCard(p.name, dotTone(age), snap))
                        rows.add(
                            IosPeerRow(
                                name = p.name,
                                shapeKey = p.shape.uppercase().takeIf { it == "A" || it == "B" || it == "C" } ?: "",
                                shapeLabel = shapeLabel(p.shape),
                                shapeTag = p.shape.ifBlank { "?" }.lowercase(),
                                dotTone = dotTone(age),
                                ageLabel = if (age == null) "never pushed" else "last push " + ago(age),
                                ageShort = if (age == null) "never" else ago(age),
                                version = p.version.orEmpty(),
                                attachedNodes = peerToNodes[p.name].orEmpty(),
                                chips = resourceChips(snap),
                            ),
                        )
                    }
                    val err =
                        peersRes.exceptionOrNull()?.let { e ->
                            val status = (e as? com.dmzs.datawatchclient.transport.TransportError.ServerError)?.status
                            if (status == 503) "off" else "unavailable"
                        } ?: ""
                    IosSystemsSnapshot(
                        grid = grid,
                        peers = rows,
                        peersError = err,
                        extras =
                            IosStatsExtras(
                                hostname = local?.str("hostname").orEmpty(),
                                activeSessions = local?.dbl("active_sessions")?.toInt() ?: -1,
                                rtkLatestVersion = local?.str("rtk_latest_version").orEmpty(),
                            ),
                    )
                }
            onResult(snapshot)
        }
    }

    private fun localCard(d: JsonObject): IosSystemCard? {
        if (d.str("timestamp") == null) return null
        val cores = d.dbl("cpu_cores") ?: 0.0
        val l1 = d.dbl("cpu_load_avg_1") ?: 0.0
        val l5 = d.dbl("cpu_load_avg_5") ?: 0.0
        val l15 = d.dbl("cpu_load_avg_15") ?: 0.0
        val cpuPct = if (cores > 0) min(100, round(100 * l1 / cores).toInt()) else 0
        val memUsed = d.dbl("mem_used") ?: 0.0
        val memTotal = d.dbl("mem_total") ?: 0.0
        val memPct = if (memTotal > 0) round(memUsed / memTotal * 100).toInt() else 0
        val bars = mutableListOf<IosObsBar>()
        bars.add(
            IosObsBar("CPU", pctFrac(cpuPct.toDouble(), 100.0), gridCpuTone(cpuPct.toDouble()), "$cpuPct% · ${fixed(l1, 2)}/${fixed(l5, 2)}/${fixed(l15, 2)}"),
        )
        bars.add(
            IosObsBar("RAM", pctFrac(memUsed, memTotal), if (memPct > 85) "error" else "accent", "${gb(memUsed)} / ${gb(memTotal)}"),
        )
        if (!d.str("gpu_name").isNullOrBlank()) {
            val gu = d.dbl("gpu_util_pct") ?: 0.0
            bars.add(IosObsBar("GPU util", pctFrac(gu, 100.0), if (gu > 80) "error" else "accent2", "${num(gu)}%"))
            val temp = d.dbl("gpu_temp") ?: 0.0
            if (temp != 0.0) bars.add(IosObsBar("GPU temp", pctFrac(temp, 100.0), tempTone(temp), "${num(temp)}°C"))
            val used = (d.dbl("gpu_mem_used_mb") ?: 0.0) * 1048576
            val total = (d.dbl("gpu_mem_total_mb") ?: 0.0) * 1048576
            if (total > 0) bars.add(IosObsBar("GPU VRAM", pctFrac(used, total), "accent2", "${gb(used)} / ${gb(total)}"))
        }
        return IosSystemCard(d.str("hostname") ?: "local", true, "success", bars, emptyList())
    }

    private fun peerCard(
        name: String,
        dot: String,
        snap: JsonObject?,
    ): IosSystemCard {
        if (snap == null) return IosSystemCard(name, false, "muted", emptyList(), emptyList())
        val bars = mutableListOf<IosObsBar>()
        val lines = mutableListOf<IosObsKv>()
        snap.obj("cpu")?.let { cpu ->
            cpu.dbl("pct")?.let { c ->
                val load1 = cpu.dbl("load1")
                val loadStr =
                    if (load1 != null) {
                        "${fixed(c, 0)}% · ${fixed(load1, 2)}/${fixed(cpu.dbl("load5") ?: 0.0, 2)}/${fixed(cpu.dbl("load15") ?: 0.0, 2)}"
                    } else {
                        "${fixed(c, 1)}%"
                    }
                bars.add(IosObsBar("CPU", pctFrac(c, 100.0), gridCpuTone(c), loadStr))
            }
        }
        snap.obj("mem")?.let { mem ->
            val used = mem.dbl("used_bytes") ?: 0.0
            val total = mem.dbl("total_bytes") ?: 0.0
            if (used > 0 && total > 0) {
                val mp = round(used / total * 100).toInt()
                bars.add(IosObsBar("RAM", pctFrac(used, total), if (mp > 85) "error" else "accent", "${gb(used)} / ${gb(total)}"))
            }
        }
        val gpus = snap.arr("gpu")?.mapNotNull { it as? JsonObject }.orEmpty()
        for (g in gpus) {
            val label = if (gpus.size > 1) "GPU " + g.str("name").orEmpty() else "GPU"
            g.dbl("util_pct")?.let { u -> bars.add(IosObsBar("$label util", pctFrac(u, 100.0), if (u > 80) "error" else "accent2", "${fixed(u, 0)}%")) }
            g.dbl("temp_c")?.let { tc -> bars.add(IosObsBar("$label temp", pctFrac(tc, 100.0), tempTone(tc), "${fixed(tc, 0)}°C")) }
            g.dbl("power_w")?.let { pw -> lines.add(IosObsKv("$label power", "${fixed(pw, 1)} W", "text")) }
            val used = g.dbl("mem_used_bytes") ?: 0.0
            val total = g.dbl("mem_total_bytes") ?: 0.0
            if (used > 0 && total > 0) bars.add(IosObsBar("$label VRAM", pctFrac(used, total), "accent2", "${gb(used)} / ${gb(total)}"))
        }
        return IosSystemCard(name, false, dot, bars, lines)
    }

    private fun resourceChips(snap: JsonObject?): List<IosObsChip> {
        if (snap == null) return emptyList()
        val chips = mutableListOf<IosObsChip>()
        snap.obj("cpu")?.dbl("pct")?.takeIf { it > 0 }?.let { chips.add(IosObsChip("CPU ${fixed(it, 0)}%", false)) }
        snap.obj("mem")?.let { mem ->
            val used = mem.dbl("used_bytes") ?: 0.0
            val total = mem.dbl("total_bytes") ?: 0.0
            if (used > 0 && total > 0) {
                chips.add(IosObsChip("Mem ${gb(used)} / ${gb(total)} (${round(used / total * 100).toInt()}%)", false))
            }
        }
        for (g in snap.arr("gpu")?.mapNotNull { it as? JsonObject }.orEmpty()) {
            (g.dbl("util_pct") ?: 0.0).takeIf { it > 0 }?.let { chips.add(IosObsChip("GPU ${fixed(it, 0)}%", true)) }
            (g.dbl("temp_c") ?: 0.0).takeIf { it != 0.0 }?.let { chips.add(IosObsChip("${fixed(it, 0)}°C", true)) }
            (g.dbl("power_w") ?: 0.0).takeIf { it != 0.0 }?.let { chips.add(IosObsChip("${fixed(it, 1)} W", true)) }
            val used = g.dbl("mem_used_bytes") ?: 0.0
            val total = g.dbl("mem_total_bytes") ?: 0.0
            if (used > 0 && total > 0) chips.add(IosObsChip("VRAM ${gb(used)} (${round(used / total * 100).toInt()}%)", true))
        }
        return chips
    }

    // ── B20/B21: statistics panel (pure; fed by WS + one-shot REST) ─────

    public fun buildStatsPanel(
        s: StatsDto,
        extras: IosStatsExtras,
        maxSessions: Int,
    ): IosStatsPanel {
        val bars = mutableListOf<IosObsBar>()
        val load1 = s.cpuLoad1
        val cores = s.cpuCores
        if (load1 != null && cores != null && cores > 0) {
            val cpuPct = min(100, round(100 * load1 / cores).toInt())
            val tone = if (cpuPct > 80) "error" else if (cpuPct > 50) "warning" else "success"
            bars.add(IosObsBar("CPU Load", pctFrac(load1, cores.toDouble()), tone, "${fixed(load1, 2)} / $cores cores"))
        }
        val memUsed = (s.memUsed ?: 0L).toDouble()
        val memTotal = (s.memTotal ?: 0L).toDouble()
        if (memTotal > 0) {
            bars.add(IosObsBar("Memory", pctFrac(memUsed, memTotal), if (pctInt(memUsed, memTotal) > 85) "error" else "accent", "${kb(memUsed)} / ${kb(memTotal)}"))
        }
        val diskUsed = (s.diskUsed ?: 0L).toDouble()
        val diskTotal = (s.diskTotal ?: 0L).toDouble()
        if (diskTotal > 0) {
            bars.add(IosObsBar("Disk", pctFrac(diskUsed, diskTotal), if (pctInt(diskUsed, diskTotal) > 90) "error" else "accent2", "${kb(diskUsed)} / ${kb(diskTotal)}"))
        }
        if (s.swapTotal > 0) {
            bars.add(IosObsBar("Swap", pctFrac(s.swapUsed.toDouble(), s.swapTotal.toDouble()), "warning", "${kb(s.swapUsed.toDouble())} / ${kb(s.swapTotal.toDouble())}"))
        }
        var gpuError = ""
        val gpuName = s.gpuName
        if (!gpuName.isNullOrBlank()) {
            val u = s.gpuUtilPct ?: 0.0
            bars.add(IosObsBar("GPU $gpuName", pctFrac(u, 100.0), if (u > 80) "error" else "success", "${num(u)}% ${num(s.gpuTemp ?: 0.0)}°C"))
            val vt = s.gpuMemTotalMb ?: 0L
            if (vt > 0) {
                val vu = s.gpuMemUsedMb ?: 0L
                bars.add(IosObsBar("GPU VRAM", pctFrac(vu.toDouble(), vt.toDouble()), "accent2", "$vu / $vt MB"))
            }
        } else if (!s.gpuError.isNullOrBlank()) {
            gpuError = s.gpuError.orEmpty()
        }

        val cards = mutableListOf<IosObsCard>()
        cards.add(
            IosObsCard(
                if (s.ebpfActive) "Network (datawatch)" else "Network (system)",
                listOf(
                    IosObsKv("↓ Download", kb(s.netRxBytes.toDouble()), "text"),
                    IosObsKv("↑ Upload", kb(s.netTxBytes.toDouble()), "text"),
                ),
                "",
            ),
        )
        val up = s.uptimeSeconds
        val upStr = if (up > 3600) "${up / 3600}h ${(up % 3600) / 60}m" else "${up / 60}m ${up % 60}s"
        cards.add(
            IosObsCard(
                "Daemon",
                listOf(
                    IosObsKv("Memory", "${kb(s.daemonRssBytes.toDouble())} RSS", "text"),
                    IosObsKv("Goroutines", "${s.goroutines}", "text"),
                    IosObsKv("File descriptors", "${s.openFds}", "text"),
                    IosObsKv("Uptime", upStr, "text"),
                ),
                "",
            ),
        )
        cards.add(infrastructureCard(s))
        if (s.rtkInstalled) cards.add(rtkCard(s, extras))
        cards.add(memoryCard(s))
        s.ollamaStats?.let { os ->
            if (os.available) {
                val running = os.runningModels
                val rows =
                    mutableListOf(
                        IosObsKv("Host", os.host ?: "—", "text"),
                        IosObsKv("Status", "online", "success"),
                        IosObsKv("Models", "${os.modelCount}", "text"),
                        IosObsKv("Disk Used", kb(os.totalSizeBytes.toDouble()), "text"),
                        IosObsKv("Running", "${running.size}", "text"),
                        IosObsKv("VRAM Used", kb(running.sumOf { it.sizeVram }.toDouble()), "text"),
                    )
                running.forEach { m -> rows.add(IosObsKv("  " + m.name, kb(m.sizeVram.toDouble()), "accent2")) }
                cards.add(IosObsCard("Ollama Server", rows, ""))
            } else {
                cards.add(IosObsCard("Ollama Server", listOf(IosObsKv("", "offline", "error")), ""))
            }
        }
        // D78a — Android-only cards, built on iOS too.
        if (s.envelopes.isNotEmpty()) {
            val rows =
                s.envelopes.sortedByDescending { it.cpuPct }.take(8).map { e ->
                    IosObsKv("${e.kind}: ${e.label.ifBlank { e.id }}", "${fixed(e.cpuPct, 1)}% · ${kb(e.rssBytes.toDouble())}", "text")
                }
            cards.add(IosObsCard("Process Envelopes", rows, ""))
        }
        if (s.backends.isNotEmpty()) {
            val rows =
                s.backends.map { b ->
                    if (b.reachable) {
                        IosObsKv(b.name, "ok · ${b.latencyMs} ms", "success")
                    } else {
                        IosObsKv(b.name, b.error?.takeIf { it.isNotBlank() } ?: "unreachable", "error")
                    }
                }
            cards.add(IosObsCard("Backend Health", rows, ""))
        }

        val active = if (extras.activeSessions >= 0) extras.activeSessions else s.sessionsRunning
        val maxS = if (maxSessions > 0) maxSessions else 10
        val frac = min(1.0, max(0.0, active.toDouble() / maxS.toDouble()))
        val counts = "running ${s.sessionsRunning} · waiting ${s.sessionsWaiting} · total ${s.sessionsTotal}"
        var banner = ""
        var degraded = false
        if (s.ebpfEnabled == true && !s.ebpfActive) {
            banner = s.ebpfMessage?.takeIf { it.isNotBlank() } ?: "eBPF enabled but not active"
            degraded = true
        } else if (s.ebpfEnabled == true && s.ebpfActive) {
            banner = "● eBPF active — per-session network tracking"
        }
        return IosStatsPanel(bars, gpuError, cards, active, maxS, frac, counts, banner, degraded)
    }

    private fun infrastructureCard(s: StatsDto): IosObsCard {
        val host = s.boundInterfaces.firstOrNull() ?: "0.0.0.0"
        val httpPort = s.webPort ?: 8080
        val hasTls = s.tlsEnabled && s.tlsPort > 0
        val rows = mutableListOf<IosObsKv>()
        rows.add(IosObsKv("HTTP", "http://$host:$httpPort" + if (hasTls) " (→ HTTPS)" else "", "text"))
        if (hasTls) rows.add(IosObsKv("HTTPS", "https://$host:${s.tlsPort} 🔒", "success"))
        if (!hasTls && s.tlsEnabled) rows.add(IosObsKv("TLS", "https://$host:$httpPort 🔒", "success"))
        s.mcpSsePort?.takeIf { it > 0 }?.let { rows.add(IosObsKv("MCP SSE", "${s.mcpSseHost ?: "0.0.0.0"}:$it", "text")) }
        val orphan = s.orphanedTmux.size
        rows.add(IosObsKv("Tmux", "${s.tmuxSessions} sessions" + if (orphan > 0) " ($orphan orphan)" else "", if (orphan > 0) "warning" else "text"))
        return IosObsCard("Infrastructure", rows, "")
    }

    private const val RTK_INSTALL_CMD: String =
        "curl -fsSL https://raw.githubusercontent.com/rtk-ai/rtk/refs/heads/master/install.sh | sh"

    private fun rtkCard(
        s: StatsDto,
        extras: IosStatsExtras,
    ): IosObsCard {
        val ver = s.rtkVersion ?: "?"
        val versionRow =
            if (s.rtkUpdateAvailable) {
                IosObsKv("Version", "$ver → ${extras.rtkLatestVersion.ifBlank { "?" }}", "error")
            } else {
                IosObsKv("Version", "$ver ✓", "success")
            }
        val rows =
            listOf(
                versionRow,
                IosObsKv("Hooks", if (s.rtkHooksActive) "active" else "inactive", if (s.rtkHooksActive) "success" else "warning"),
                IosObsKv("Tokens saved", grouped(s.rtkTotalSaved), "text"),
                IosObsKv("Avg savings", s.rtkAvgSavingsPct?.takeIf { it != 0.0 }?.let { fixed(it, 1) + "%" } ?: "—", "text"),
                IosObsKv("Commands", "${s.rtkTotalCommands}", "text"),
            )
        return IosObsCard("RTK Token Savings", rows, if (s.rtkUpdateAvailable) RTK_INSTALL_CMD else "")
    }

    private fun memoryCard(s: StatsDto): IosObsCard {
        val rows = mutableListOf<IosObsKv>()
        rows.add(IosObsKv("Status", if (s.memoryEnabled) "enabled" else "disabled", if (s.memoryEnabled) "success" else "muted"))
        if (s.memoryEnabled) {
            rows.add(IosObsKv("Backend", s.memoryBackend ?: "sqlite", "text"))
            rows.add(IosObsKv("Embedder", s.memoryEmbedder ?: "—", "text"))
            rows.add(
                if (s.memoryEncrypted) {
                    IosObsKv("Encryption", "encrypted (${s.memoryKeyFingerprint ?: "?"})", "success")
                } else {
                    IosObsKv("Encryption", "plaintext", "muted")
                },
            )
            rows.add(IosObsKv("Total", "${s.memoryTotalCount}", "text"))
            rows.add(IosObsKv("Manual", "${s.memoryManualCount}", "text"))
            rows.add(IosObsKv("Sessions", "${s.memorySessionCount}", "text"))
            rows.add(IosObsKv("Learnings", "${s.memoryLearningCount}", "text"))
            rows.add(IosObsKv("DB Size", kb(s.memoryDbSizeBytes.toDouble()), "text"))
        }
        return IosObsCard("Episodic Memory", rows, "")
    }

    /** D78a server-info card + `session.max_sessions` (ring denominator). */
    public fun loadServerContext(
        profile: ServerProfile,
        onResult: (IosServerContext) -> Unit,
    ) {
        scope.launch {
            val tr = t(profile)
            val info = tr.fetchInfo().getOrNull()
            val cfg = tr.fetchConfig().getOrNull()?.raw.orEmpty()
            val maxS =
                (cfg["session.max_sessions"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
                    ?: (cfg["session"] as? JsonObject)?.dbl("max_sessions")?.toInt()
                    ?: 0
            val card =
                info?.let {
                    IosObsCard(
                        "Server info",
                        listOfNotNull(
                            IosObsKv("Hostname", it.hostname, "text"),
                            IosObsKv("Version", it.version, "text"),
                            it.serverHost?.let { h -> IosObsKv("Host", h, "text") },
                            it.serverPort?.let { p -> IosObsKv("Port", "$p", "text") },
                        ),
                        "",
                    )
                }
            onResult(IosServerContext(card, maxS))
        }
    }

    // ── B21: eBPF + plugins ─────────────────────────────────────────────

    public fun loadEbpf(
        profile: ServerProfile,
        onResult: (IosEbpfSnapshot) -> Unit,
    ) {
        scope.launch {
            val res = t(profile).fetchStatsJson(v2 = true)
            val s = res.getOrNull()
            if (s == null) {
                onResult(IosEbpfSnapshot("muted", "/api/stats?v=2 unavailable", "", emptyList()))
                return@launch
            }
            val e = s.obj("host")?.obj("ebpf")
            val (tone, head) =
                when {
                    e == null -> "muted" to "observer disabled"
                    e.bool("kprobes_loaded") == true -> "success" to "live — per-process net wired"
                    e.bool("configured") == true && e.bool("capability") == true -> "accent2" to "configured + capability granted"
                    e.bool("configured") == true -> "warning" to "configured but capability missing"
                    else -> "muted" to "off"
                }
            val procs =
                s.obj("net")?.arr("per_process")?.mapNotNull { it as? JsonObject }.orEmpty()
                    .sortedByDescending { (it.dbl("rx_bps") ?: 0.0) + (it.dbl("tx_bps") ?: 0.0) }
                    .take(10)
                    .map { p ->
                        IosNetProc(
                            name = p.str("comm")?.takeIf { it.isNotBlank() } ?: ("PID " + (p.lng("pid") ?: 0)),
                            rx = fmtBytes((p.dbl("rx_bps") ?: 0.0)) + "/s",
                            tx = fmtBytes((p.dbl("tx_bps") ?: 0.0)) + "/s",
                        )
                    }
            onResult(IosEbpfSnapshot(tone, head, e?.str("message").orEmpty(), procs))
        }
    }

    public fun loadPlugins(
        profile: ServerProfile,
        onSuccess: (List<IosPluginRow>) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            t(profile).listPlugins().fold(
                onSuccess = { d ->
                    val native =
                        d.native.map { p ->
                            val detail =
                                listOfNotNull(p.description?.takeIf { it.isNotBlank() }, p.message?.takeIf { it.isNotBlank() })
                                    .joinToString(" · ")
                            IosPluginRow(p.name, true, p.enabled, p.version.orEmpty(), detail)
                        }
                    val sub =
                        d.plugins.map { p ->
                            val detail = listOfNotNull(if (p.enabled) "enabled" else "disabled", p.message?.takeIf { it.isNotBlank() }).joinToString(" · ")
                            IosPluginRow(p.name, false, p.enabled, p.version.orEmpty(), detail)
                        }
                    onSuccess(native + sub)
                },
                onFailure = { onError("plugin status unavailable") },
            )
        }
    }

    // ── B22: cluster, meta-peers, snapshot, remove ──────────────────────

    public fun loadCluster(
        profile: ServerProfile,
        onResult: (List<IosClusterRow>) -> Unit,
    ) {
        scope.launch {
            val nodes = t(profile).observerStats().getOrNull()?.cluster?.nodes.orEmpty()
            onResult(
                nodes.map { n ->
                    IosClusterRow(
                        name = n.name,
                        ready = n.ready,
                        pressure = if (n.pressures.isEmpty()) "" else "[" + n.pressures.joinToString(",") + "]",
                        pods = "${n.podCount} pods",
                        cpuPct = round(n.cpuPct).toInt(),
                        memPct = round(n.memPct).toInt(),
                    )
                },
            )
        }
    }

    public fun loadMetaPeers(
        profile: ServerProfile,
        onSuccess: (List<IosMetaGroup>) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            t(profile).getFederationMetaPeers().fold(
                onSuccess = { meta ->
                    val groups =
                        meta.byNode.keys.sorted().map { node ->
                            val b = meta.byNode.getValue(node)
                            val obs = if (b.observerCount == 1) "observer" else "observers"
                            val pri = if (b.primaryCount == 1) "primary" else "primaries"
                            IosMetaGroup(
                                title = "⇄ $node",
                                subtitle = "${b.observerCount} $obs · ${b.primaryCount} $pri",
                                lines =
                                    b.observers.map { o ->
                                        "↳ ${o.peer} (primary ${o.primary}" + (if (o.shape.isNotBlank()) "; shape ${o.shape}" else "") + ")"
                                    },
                                unbound = false,
                            )
                        }.toMutableList()
                    if (meta.unbound.isNotEmpty()) {
                        groups.add(
                            IosMetaGroup(
                                title = "Unbound peers (no ComputeNode)",
                                subtitle = "",
                                lines = meta.unbound.map { o -> "↳ ${o.peer} (primary ${o.primary})" },
                                unbound = true,
                            ),
                        )
                    }
                    onSuccess(groups)
                },
                onFailure = { onError(msg(it, "unavailable")) },
            )
        }
    }

    public fun peerSnapshot(
        profile: ServerProfile,
        name: String,
        onSuccess: (IosPeerSnapshot) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            t(profile).fetchObserverPeerSnapshot(name).fold(
                onSuccess = { snap ->
                    val host = snap.obj("host")
                    val head =
                        "$name · ${host?.str("name") ?: "?"} · ${host?.str("os") ?: "?"} ${host?.str("arch").orEmpty()} · uptime ${host?.lng("uptime_seconds") ?: 0}s"
                    val envs =
                        snap.arr("envelopes")?.mapNotNull { it as? JsonObject }.orEmpty()
                            .sortedByDescending { it.dbl("cpu_pct") ?: 0.0 }
                            .map { e ->
                                val rss = round((e.dbl("rss_bytes") ?: 0.0) / 1e6).toLong()
                                IosSnapshotEnvelope(
                                    kind = e.str("kind") ?: "?",
                                    id = e.str("id") ?: "?",
                                    stats =
                                        "cpu ${fixed(e.dbl("cpu_pct") ?: 0.0, 1)}% · rss $rss MB · " +
                                            "${e.lng("process_count") ?: 0} procs · ${e.lng("open_fds") ?: 0} fds",
                                )
                            }
                    onSuccess(IosPeerSnapshot(head, envs))
                },
                onFailure = { onError(msg(it, "Snapshot unavailable")) },
            )
        }
    }

    public fun removePeer(
        profile: ServerProfile,
        name: String,
        onDone: (String?) -> Unit,
    ) {
        scope.launch {
            val r = t(profile).removeObserverPeer(name)
            onDone(r.exceptionOrNull()?.let { "Remove failed: " + msg(it, "error") })
        }
    }

    /** B25 bottom Federated Peers card: stats pills + config table + peers (8 s). */
    public fun loadPeersCard(
        profile: ServerProfile,
        onResult: (IosPeersCard) -> Unit,
    ) {
        scope.launch {
            val tr = t(profile)
            val card =
                coroutineScope {
                    val statsJob = async { tr.fetchObserverStatsJson().getOrNull() }
                    val cfgJob = async { tr.fetchObserverConfig().getOrNull() }
                    val peers = tr.observerPeers().getOrNull()?.peers.orEmpty()
                    val stats = statsJob.await()
                    val cfg = cfgJob.await()
                    val pills =
                        stats?.entries?.filter { (_, v) -> v is JsonPrimitive }?.map { (k, v) ->
                            IosObsKv(k, (v as JsonPrimitive).contentOrNull ?: "null", "text")
                        }.orEmpty()
                    val cfgRows =
                        cfg?.entries?.map { (k, v) ->
                            when (v) {
                                is JsonObject -> IosObsKv(k, "{" + v.keys.joinToString(", ") + "}", "muted")
                                is JsonArray -> IosObsKv(k, "{" + v.indices.joinToString(", ") + "}", "muted")
                                is JsonPrimitive -> IosObsKv(k, v.contentOrNull ?: "null", "text")
                            }
                        }.orEmpty()
                    val now = Clock.System.now().toEpochMilliseconds()
                    val rows =
                        peers.map { p ->
                            val age = ageOf(p.lastPushAt, now)
                            IosPeerRow(
                                name = p.name,
                                shapeKey = p.shape.uppercase(),
                                shapeLabel = shapeLabel(p.shape).removePrefix("shape "),
                                shapeTag = p.shape.lowercase(),
                                dotTone = dotTone(age),
                                ageLabel = if (age == null) "never pushed" else "last push " + ago(age),
                                ageShort = if (age == null) "never" else ago(age),
                                version = p.version.orEmpty(),
                                attachedNodes = emptyList(),
                                chips = emptyList(),
                            )
                        }
                    IosPeersCard(pills, cfgRows, rows)
                }
            onResult(card)
        }
    }

    // ── B23: channel bridge, diagnostics, comm backends, web search ─────

    public fun loadChannelBridge(
        profile: ServerProfile,
        onResult: (List<IosObsLine>) -> Unit,
    ) {
        scope.launch {
            val info = t(profile).fetchChannelInfo().getOrNull() as? JsonObject
            if (info == null) {
                onResult(listOf(IosObsLine("unavailable", "muted")))
                return@launch
            }
            val lines: List<IosObsLine> =
                com.dmzs.datawatchclient.transport.ChannelBridgeFormat.lines(info)
                    .map { l -> IosObsLine(l.text, l.tone) }
            onResult(lines)
        }
    }

    public fun loadChannelDiagnostics(
        profile: ServerProfile,
        onResult: (IosChannelDiagnostics) -> Unit,
    ) {
        scope.launch {
            val d = t(profile).fetchChannelDiagnostics().getOrNull()
            if (d == null) {
                onResult(IosChannelDiagnostics(listOf(IosObsLine("unavailable", "muted")), emptyList()))
                return@launch
            }
            val sessions = d.arr("sessions")?.mapNotNull { it as? JsonObject }.orEmpty()
            val lines =
                if (sessions.isEmpty()) {
                    listOf(IosObsLine("(no active sessions)", "muted"))
                } else {
                    sessions.map { sd ->
                        val port = sd.lng("channel_port") ?: 0
                        val portStr = if (port > 0) "port $port" else "port –"
                        val name = sd.str("session_name")?.takeIf { it.isNotBlank() } ?: sd.str("session_id").orEmpty()
                        if (sd.bool("bridge_alive") == true) {
                            IosObsLine("✓ $name  $portStr", "text")
                        } else {
                            val err = sd.str("probe_error")?.takeIf { it.isNotBlank() }?.let { " — $it" }.orEmpty()
                            IosObsLine("✗ $name  $portStr$err", "warning")
                        }
                    }
                }
            val hints = d.arr("hints")?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
            onResult(IosChannelDiagnostics(lines, hints))
        }
    }

    private val COMM_SERVICES =
        listOf("telegram", "discord", "slack", "matrix", "ntfy", "email", "twilio", "github_webhook", "webhook", "dns_channel")

    public fun loadCommBackends(
        profile: ServerProfile,
        onResult: (IosCommBackends) -> Unit,
    ) {
        scope.launch {
            val res = t(profile).fetchConfig()
            val cfg = res.getOrNull()?.raw
            if (cfg == null) {
                onResult(IosCommBackends("Unavailable", emptyList(), false))
                return@launch
            }
            val enabled =
                COMM_SERVICES.filter { s ->
                    (cfg[s] as? JsonObject)?.bool("enabled") == true ||
                        (cfg["$s.enabled"] as? JsonPrimitive)?.booleanOrNull == true
                }
            onResult(
                IosCommBackends(
                    message = if (enabled.isEmpty()) "No communication backends enabled." else "",
                    enabled = enabled.map { it.replace('_', ' ') },
                    matrixEnabled = "matrix" in enabled,
                ),
            )
        }
    }

    public fun loadMatrixStatus(
        profile: ServerProfile,
        onResult: (IosMatrixStatus) -> Unit,
    ) {
        scope.launch {
            t(profile).fetchMatrixStatus().fold(
                onSuccess = { d -> onResult(if (d.connected) IosMatrixStatus("● connected", "success") else IosMatrixStatus("● disconnected", "error")) },
                onFailure = { onResult(IosMatrixStatus("unavailable", "muted")) },
            )
        }
    }

    public fun matrixTest(
        profile: ServerProfile,
        onDone: (String?) -> Unit,
    ) {
        scope.launch {
            val r = t(profile).sendMatrixTest()
            onDone(r.exceptionOrNull()?.let { msg(it, "Matrix test failed") })
        }
    }

    public fun loadWebSearchStats(
        profile: ServerProfile,
        onSuccess: (IosWebSearchStats) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            t(profile).fetchWebSearchStatsV2(14).fold(
                onSuccess = { st ->
                    val s = st.summary
                    onSuccess(
                        IosWebSearchStats(
                            hasProviders = st.providerNames.isNotEmpty(),
                            rows =
                                listOf(
                                    IosObsKv("Total", "${s.total}", "text"),
                                    IosObsKv("Today", "${s.today}", "text"),
                                    IosObsKv("This week", "${s.thisWeek}", "text"),
                                    IosObsKv("This month", "${s.thisMonth}", "text"),
                                    IosObsKv("Cache hits", "${s.cacheHits}", "text"),
                                ),
                            providers = s.providers.map { p -> IosObsKv(p.name, "${p.total} (${p.today} today)", "accent2") },
                            series = st.dailySeries.map { it.count },
                        ),
                    )
                },
                onFailure = { onError("Search usage stats unavailable.") },
            )
        }
    }

    public fun loadWebSearchHistory(
        profile: ServerProfile,
        onSuccess: (List<IosWebSearchHistoryRow>) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            t(profile).fetchWebSearchHistory(50, 0).fold(
                onSuccess = { d ->
                    onSuccess(
                        d.history.map { h ->
                            val (status, tone) =
                                when {
                                    !h.success -> "error" to "error"
                                    h.cacheHit -> "cache" to "accent2"
                                    else -> "live" to "success"
                                }
                            IosWebSearchHistoryRow(localTime(h.time), h.providerName, h.query, status, tone)
                        },
                    )
                },
                onFailure = { onError(msg(it, "History unavailable")) },
            )
        }
    }

    // ── B24: memory browser + maintenance (D89b: dry-run only) ──────────

    public fun memoryStats(
        profile: ServerProfile,
        onSuccess: (IosMemoryStats) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            t(profile).memoryStats().fold(
                onSuccess = { d ->
                    if (d.bool("enabled") != true) {
                        onSuccess(IosMemoryStats(false, emptyList()))
                    } else {
                        onSuccess(
                            IosMemoryStats(
                                true,
                                listOf(
                                    IosObsKv("Total Memories", "${d.lng("total_count") ?: 0}", "text"),
                                    IosObsKv("Manual", "${d.lng("manual_count") ?: 0}", "text"),
                                    IosObsKv("Session", "${d.lng("session_count") ?: 0}", "text"),
                                    IosObsKv("Learnings", "${d.lng("learning_count") ?: 0}", "text"),
                                    IosObsKv("Chunks", "${d.lng("chunk_count") ?: 0}", "text"),
                                    IosObsKv("DB Size", fmtBytes(d.dbl("db_size_bytes") ?: 0.0), "text"),
                                ),
                            ),
                        )
                    }
                },
                onFailure = { onError("Memory stats unavailable") },
            )
        }
    }

    /** [role] "" = all; [sinceDays] 0 = all time. PWA `listMemories` (n=50). */
    public fun memoryList(
        profile: ServerProfile,
        role: String,
        sinceDays: Int,
        onSuccess: (List<IosMemoryRow>) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            val since =
                if (sinceDays > 0) {
                    Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds() - sinceDays * 86_400_000L).toString()
                } else {
                    null
                }
            t(profile).memoryList(50, role.ifBlank { null }, since).fold(
                onSuccess = { list -> onSuccess(list.map { memoryRow(it, withDate = true) }) },
                onFailure = { onError("Failed to load memories") },
            )
        }
    }

    public fun memorySearch(
        profile: ServerProfile,
        query: String,
        onSuccess: (List<IosMemoryRow>) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            t(profile).memorySearch(query).fold(
                onSuccess = { list -> onSuccess(list.map { memoryRow(it, withDate = false) }) },
                onFailure = { onError("Search failed") },
            )
        }
    }

    private fun memoryRow(
        m: JsonObject,
        withDate: Boolean,
    ): IosMemoryRow {
        val id = m.lng("id") ?: 0
        val date =
            if (withDate) {
                m.str("created_at")?.let { runCatching { Instant.parse(it).toLocalDateTime(TimeZone.currentSystemDefault()).date.toString() }.getOrNull() }.orEmpty()
            } else {
                ""
            }
        val sim = m.dbl("similarity")?.takeIf { it != 0.0 }?.let { " [${round(it * 100).toInt()}%]" }.orEmpty()
        val content = m.str("content").orEmpty()
        val clipped = if (content.length > 200) content.take(200) + "…" else content
        val header = listOf("#$id", m.str("role").orEmpty(), date).filter { it.isNotBlank() }.joinToString(" ") + sim
        return IosMemoryRow(id, header, clipped)
    }

    public fun memoryDelete(
        profile: ServerProfile,
        id: Long,
        onDone: (String?) -> Unit,
    ) {
        scope.launch {
            val r = t(profile).memoryDelete(id)
            onDone(r.exceptionOrNull()?.let { "Delete failed: " + msg(it, "error") })
        }
    }

    /** D78a add-memory dialog: text + comma-separated tags, role manual. */
    public fun memoryRemember(
        profile: ServerProfile,
        text: String,
        tags: String,
        onDone: (String?) -> Unit,
    ) {
        scope.launch {
            val tagList = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            val r = t(profile).memoryRemember(text.trim(), "manual", tagList)
            onDone(r.exceptionOrNull()?.let { msg(it, "Save failed") })
        }
    }

    /** PWA Export (JSON backup) → temp file path for the iOS share sheet. */
    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    public fun memoryExport(
        profile: ServerProfile,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            t(profile).memoryExport().fold(
                onSuccess = { bytes ->
                    val path = NSTemporaryDirectory() + "datawatch-memories.json"
                    val data =
                        if (bytes.isEmpty()) {
                            NSData()
                        } else {
                            bytes.usePinned { pinned -> NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong()) }
                        }
                    if (data.writeToFile(path, atomically = true)) onSuccess(path) else onError("Export failed")
                },
                onFailure = { onError("Export failed: " + msg(it, "error")) },
            )
        }
    }

    public fun memorySweepDryRun(
        profile: ServerProfile,
        days: Int,
        onResult: (String) -> Unit,
    ) {
        scope.launch {
            t(profile).memorySweepStale(days, dryRun = true).fold(
                onSuccess = { n -> onResult("Dry-run: $n candidates, 0 deleted") },
                onFailure = { onResult("Failed: " + msg(it, "error")) },
            )
        }
    }

    public fun memorySpellcheck(
        profile: ServerProfile,
        text: String,
        onResult: (String) -> Unit,
    ) {
        scope.launch {
            t(profile).memorySpellcheck(text).fold(
                onSuccess = { list ->
                    onResult(
                        if (list.isEmpty()) {
                            "No suggestions"
                        } else {
                            list.joinToString("   ") { s -> "${s.word} → ${s.suggestions.joinToString(", ")}" }
                        },
                    )
                },
                onFailure = { onResult("Failed: " + msg(it, "error")) },
            )
        }
    }

    public fun memoryExtractFacts(
        profile: ServerProfile,
        text: String,
        onResult: (String) -> Unit,
    ) {
        scope.launch {
            t(profile).memoryExtractFacts(text).fold(
                onSuccess = { list ->
                    onResult(if (list.isEmpty()) "No triples" else list.joinToString("   ") { tr -> "(${tr.subject} ${tr.verb} ${tr.obj})" })
                },
                onFailure = { onResult("Failed: " + msg(it, "error")) },
            )
        }
    }

    public fun memorySchemaVersion(
        profile: ServerProfile,
        onResult: (String) -> Unit,
    ) {
        scope.launch {
            t(profile).memoryStats().fold(
                onSuccess = { d -> onResult("schema_version: " + (d.str("schema_version")?.takeIf { it.isNotBlank() && it != "0" } ?: "(not reported by this backend)")) },
                onFailure = { onResult("Failed: " + msg(it, "error")) },
            )
        }
    }

    // ── B25: schedules, cooldown, analytics, audit, KG, daemon log ──────

    public fun listSchedules(
        profile: ServerProfile,
        onSuccess: (List<IosScheduleRow>) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            t(profile).listSchedules().fold(
                onSuccess = { list ->
                    onSuccess(
                        list.sortedByDescending { it.createdAt }.map { sc ->
                            val state = sc.state ?: if (sc.enabled) "pending" else "disabled"
                            val tone =
                                when (state) {
                                    "pending" -> "warning"
                                    "done" -> "success"
                                    else -> "muted"
                                }
                            // PWA loadSchedulesList label: "NEW: <name>" for deferred
                            // sessions, else "<session_name|session_id> [<schedule_name>]: <command>".
                            val ref: String? = (sc.sessionName ?: sc.sessionId)?.takeIf { it.isNotBlank() }
                            val schedRef: String = sc.scheduleName?.let { " [$it]" } ?: ""
                            val label: String =
                                if (sc.type == "new_session" && sc.deferredSessionName != null) {
                                    "NEW: ${sc.deferredSessionName}"
                                } else if (ref != null) {
                                    "$ref$schedRef: ${sc.task}"
                                } else {
                                    sc.task
                                }
                            IosScheduleRow(
                                id = sc.id,
                                label = label,
                                command = sc.task,
                                cron = sc.cron.orEmpty(),
                                whenText = sc.runAt?.let { localTime(it.toString()) } ?: sc.cron.orEmpty(),
                                state = state,
                                stateTone = tone,
                                pending = state == "pending",
                                runAtIso = sc.runAt?.toString().orEmpty(),
                            )
                        },
                    )
                },
                onFailure = { onError("Failed to load schedules.") },
            )
        }
    }

    public fun deleteSchedules(
        profile: ServerProfile,
        ids: List<String>,
        onDone: (String?) -> Unit,
    ) {
        scope.launch {
            val tr = t(profile)
            val errors = ids.mapNotNull { id -> tr.deleteSchedule(id).exceptionOrNull() }
            onDone(errors.firstOrNull()?.let { "Delete failed: " + msg(it, "error") })
        }
    }

    public fun updateSchedule(
        profile: ServerProfile,
        id: String,
        command: String,
        runAt: String,
        onDone: (String?) -> Unit,
    ) {
        scope.launch {
            val r = t(profile).updateSchedule(id, command, runAt.trim().ifBlank { null })
            onDone(r.exceptionOrNull()?.let { "Update failed: " + msg(it, "error") })
        }
    }

    public fun cooldownStatus(
        profile: ServerProfile,
        onSuccess: (IosCooldown) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            t(profile).getCooldownStatus().fold(
                onSuccess = { d ->
                    if (d.active) {
                        val until = d.untilUnixMs ?: 0L
                        val now = Clock.System.now().toEpochMilliseconds()
                        val remaining = if (until > 0) max(0L, (until - now + 59_999) / 60_000) else 0L
                        val reason = d.reason?.takeIf { it.isNotBlank() }?.let { " — $it" }.orEmpty()
                        onSuccess(IosCooldown(true, "⚠ Active — ${remaining}m remaining$reason", "warning"))
                    } else {
                        onSuccess(IosCooldown(false, "✓ No active cooldown", "success"))
                    }
                },
                onFailure = { onError("Failed to load cooldown status.") },
            )
        }
    }

    public fun setCooldown(
        profile: ServerProfile,
        minutes: Int,
        reason: String,
        onDone: (String?) -> Unit,
    ) {
        scope.launch {
            val until = Clock.System.now().toEpochMilliseconds() + minutes * 60_000L
            val r = t(profile).setCooldown(until, reason.trim())
            onDone(r.exceptionOrNull()?.let { "Failed to set cooldown" })
        }
    }

    public fun clearCooldown(
        profile: ServerProfile,
        onDone: (String?) -> Unit,
    ) {
        scope.launch {
            val r = t(profile).clearCooldown()
            onDone(r.exceptionOrNull()?.let { "Failed to clear cooldown" })
        }
    }

    public fun analytics(
        profile: ServerProfile,
        rangeDays: Int,
        onSuccess: (IosAnalytics) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            t(profile).getAnalytics(rangeDays).fold(
                onSuccess = { d ->
                    val maxTotal = max(1, d.buckets.maxOfOrNull { it.sessionCount } ?: 1)
                    val rows =
                        d.buckets.map { b ->
                            val total = b.sessionCount
                            val errors = b.failed + b.killed
                            val errPct = if (total > 0) round(errors.toDouble() / total * 100).toInt() else 0
                            val tone = if (errPct > 20) "error" else if (errPct > 5) "warning" else "success"
                            IosAnalyticsRow(b.date, total, total - errors, errors, total.toDouble() / maxTotal, tone)
                        }
                    val rate = d.successRate?.let { "Success rate: ${fixed(it * 100, 1)}%" }.orEmpty()
                    onSuccess(IosAnalytics(rate, rows))
                },
                onFailure = { onError("Failed to load analytics.") },
            )
        }
    }

    public fun audit(
        profile: ServerProfile,
        actor: String,
        action: String,
        limit: Int,
        onSuccess: (List<IosAuditRow>) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            t(profile).getAuditLog(actor.trim().ifBlank { null }, action.trim().ifBlank { null }, limit).fold(
                onSuccess = { d ->
                    onSuccess(
                        d.entries.map { e ->
                            IosAuditRow(
                                ts = e.ts?.let { localTime(it) }.orEmpty(),
                                action = e.action,
                                actor = e.actor.orEmpty(),
                                session = e.sessionId?.takeLast(8).orEmpty(),
                                details = e.details?.takeIf { it.isNotEmpty() }?.toString().orEmpty(),
                            )
                        },
                    )
                },
                onFailure = { onError("Failed to load audit log.") },
            )
        }
    }

    public fun kgQuery(
        profile: ServerProfile,
        entity: String,
        onSuccess: (List<IosKgTriple>) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            t(profile).queryKg(entity.trim()).fold(
                onSuccess = { list ->
                    onSuccess(
                        list.map { tr ->
                            IosKgTriple(tr.str("subject").orEmpty(), tr.str("predicate").orEmpty(), tr.str("object").orEmpty(), tr.str("valid_from").orEmpty())
                        },
                    )
                },
                onFailure = { onError(msg(it, "Query failed")) },
            )
        }
    }

    public fun kgAdd(
        profile: ServerProfile,
        subject: String,
        predicate: String,
        obj: String,
        onDone: (String?) -> Unit,
    ) {
        scope.launch {
            val r = t(profile).addKgTriple(subject.trim(), predicate.trim(), obj.trim())
            onDone(r.exceptionOrNull()?.let { msg(it, "Add failed") })
        }
    }

    public fun daemonLog(
        profile: ServerProfile,
        offset: Int,
        onSuccess: (IosLogPage) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            t(profile).fetchLogs(50, offset).fold(
                onSuccess = { v ->
                    val lines =
                        v.lines.map { line ->
                            val tone =
                                when {
                                    line.contains("[warn]") || line.contains("WARNING") -> "warning"
                                    line.contains("ERROR") || line.contains("[error]") -> "error"
                                    line.contains("[ebpf]") -> "success"
                                    line.contains("[debug]") -> "accent2"
                                    else -> "muted"
                                }
                            IosObsLine(line, tone)
                        }
                    onSuccess(IosLogPage(lines, "Showing ${v.lines.size} of ${v.total} lines (offset $offset)"))
                },
                onFailure = { onError("Log unavailable") },
            )
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private fun JsonObject.obj(k: String): JsonObject? = this[k] as? JsonObject

    private fun JsonObject.arr(k: String): JsonArray? = this[k] as? JsonArray

    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.dbl(k: String): Double? = (this[k] as? JsonPrimitive)?.doubleOrNull

    private fun JsonObject.lng(k: String): Long? = (this[k] as? JsonPrimitive)?.let { it.longOrNull ?: it.doubleOrNull?.toLong() }

    private fun JsonObject.bool(k: String): Boolean? = (this[k] as? JsonPrimitive)?.booleanOrNull

    private fun ageOf(
        iso: String?,
        nowMs: Long,
    ): Long? {
        if (iso.isNullOrBlank()) return null
        val ts = runCatching { Instant.parse(iso).toEpochMilliseconds() }.getOrNull() ?: return null
        if (ts <= 0) return null
        return max(0L, nowMs - ts)
    }

    /** PWA peer dot: green < 15 s · amber < 60 s · red ≥ 60 s · grey never. */
    private fun dotTone(ageMs: Long?): String =
        when {
            ageMs == null -> "muted"
            ageMs < 15_000 -> "success"
            ageMs < 60_000 -> "warning"
            else -> "error"
        }

    private fun ago(ms: Long): String =
        when {
            ms < 1_000 -> "just now"
            ms < 60_000 -> "${ms / 1_000}s ago"
            ms < 3_600_000 -> "${ms / 60_000}m ago"
            else -> "${ms / 3_600_000}h ago"
        }

    private fun shapeLabel(shape: String): String =
        when (shape.uppercase()) {
            "A" -> "agent"
            "B" -> "standalone"
            "C" -> "cluster"
            else -> "shape " + shape.ifBlank { "?" }
        }

    /** Grid CPU: > 80 error · > 50 warning · success. */
    private fun gridCpuTone(pct: Double): String = if (pct > 80) "error" else if (pct > 50) "warning" else "success"

    /** GPU temp: ≥ 80 error · ≥ 60 warning · success. */
    private fun tempTone(c: Double): String = if (c >= 80) "error" else if (c >= 60) "warning" else "success"

    private fun pctInt(
        used: Double,
        total: Double,
    ): Int = if (total > 0) round(100 * used / total).toInt() else 0

    /** PWA bar width: round(100·val/max) clamped to 100, as a 0…1 fraction. */
    private fun pctFrac(
        v: Double,
        maxV: Double,
    ): Double = if (maxV > 0) min(100.0, max(0.0, round(100 * v / maxV))) / 100.0 else 0.0

    /** Grid fmtBytes: — / B / MB / GB / TB (decimal). */
    private fun gb(b: Double): String =
        when {
            b <= 0 -> "—"
            b >= 1e12 -> fixed(b / 1e12, 1) + " TB"
            b >= 1e9 -> fixed(b / 1e9, 1) + " GB"
            b >= 1e6 -> fixed(b / 1e6, 1) + " MB"
            else -> "${b.toLong()} B"
        }

    /** renderStatsData fmt: GB / MB / KB / B (decimal, strict >). */
    private fun kb(b: Double): String =
        when {
            b > 1e9 -> fixed(b / 1e9, 1) + " GB"
            b > 1e6 -> fixed(b / 1e6, 1) + " MB"
            b > 1e3 -> fixed(b / 1e3, 1) + " KB"
            else -> "${b.toLong()} B"
        }

    /** PWA formatBytes (binary): B / KB / MB. */
    private fun fmtBytes(b: Double): String =
        when {
            b < 1024 -> "${b.toLong()} B"
            b < 1024 * 1024 -> fixed(b / 1024, 1) + " KB"
            else -> fixed(b / (1024 * 1024), 1) + " MB"
        }

    /** JS number → string: integers without decimals, else one decimal. */
    private fun num(v: Double): String = if (v == round(v)) v.toLong().toString() else fixed(v, 1)

    private fun fixed(
        v: Double,
        digits: Int,
    ): String {
        if (v.isNaN() || v.isInfinite()) return "0"
        var factor = 1L
        repeat(digits) { factor *= 10 }
        val r = round(abs(v) * factor).toLong()
        val ip = r / factor
        val fp = r % factor
        val body = if (digits == 0) "$ip" else "$ip." + fp.toString().padStart(digits, '0')
        return if (v < 0 && r != 0L) "-$body" else body
    }

    private fun grouped(n: Long): String {
        val s = abs(n).toString()
        val out = StringBuilder()
        s.forEachIndexed { i, c ->
            if (i > 0 && (s.length - i) % 3 == 0) out.append(',')
            out.append(c)
        }
        return if (n < 0) "-$out" else out.toString()
    }

    private fun localTime(iso: String): String =
        runCatching {
            val dt = Instant.parse(iso).toLocalDateTime(TimeZone.currentSystemDefault())
            val hh = dt.hour.toString().padStart(2, '0')
            val mm = dt.minute.toString().padStart(2, '0')
            val ss = dt.second.toString().padStart(2, '0')
            "${dt.date} $hh:$mm:$ss"
        }.getOrDefault(iso)
}
