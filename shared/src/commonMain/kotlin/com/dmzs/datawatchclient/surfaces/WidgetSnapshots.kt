package com.dmzs.datawatchclient.surfaces

import com.dmzs.datawatchclient.domain.Session
import com.dmzs.datawatchclient.domain.SessionState
import com.dmzs.datawatchclient.transport.dto.StatsDto
import kotlin.math.abs
import kotlin.math.floor

/** Android `SessionsWidget` counts: running / waiting / total on the picked server. */
public data class WidgetSessionCounts(
    val running: Int,
    val waiting: Int,
    val total: Int,
) {
    public companion object {
        public fun of(sessions: List<Session>): WidgetSessionCounts =
            WidgetSessionCounts(
                running = sessions.count { it.state == SessionState.Running },
                waiting = sessions.count { it.state == SessionState.Waiting },
                total = sessions.size,
            )
    }
}

/**
 * Android `MonitorWidget` snapshot, computed the same way from `/api/stats`.
 * Percentages are 0–100, or -1 when the server did not report the value
 * (Android draws an empty bar). Texts are ready to show; the "Net" / "Net (eBPF)"
 * label is left to the UI via [ebpfActive] so it can be localised.
 */
public data class WidgetMonitorSnapshot(
    val cpuPct: Int = -1,
    val cpuText: String = DASH,
    val memPct: Int = -1,
    val memText: String = DASH,
    val diskPct: Int = -1,
    val diskText: String = DASH,
    val hasSwap: Boolean = false,
    val swapPct: Int = -1,
    val swapText: String = DASH,
    val hasGpu: Boolean = false,
    val gpuPct: Int = -1,
    val gpuText: String = "",
    val ebpfActive: Boolean = false,
    val netRxText: String = "",
    val netTxText: String = "",
    val daemonText: String = "",
    val uptimeText: String = "",
    val sessionsText: String = DASH,
) {
    public companion object {
        public const val DASH: String = "—"

        @Suppress("CyclomaticComplexMethod", "LongMethod")
        public fun of(s: StatsDto): WidgetMonitorSnapshot {
            val load1 = s.cpuLoad1
            val cores = s.cpuCores
            val cpuPct =
                when {
                    load1 != null && cores != null && cores > 0 -> clampPct(load1 / cores * 100.0)
                    s.cpuPct != null -> clampPct(s.cpuPct)
                    else -> null
                }
            val cpuText =
                when {
                    load1 != null && cores != null -> fixed(load1, 2)
                    s.cpuPct != null -> fixed(s.cpuPct, 1) + "%"
                    else -> DASH
                }
            val memUsed = s.memUsed
            val memTotal = s.memTotal
            val memPct =
                when {
                    memUsed != null && memTotal != null && memTotal > 0 ->
                        clampPct(memUsed.toDouble() / memTotal.toDouble() * 100.0)
                    s.memPct != null -> clampPct(s.memPct)
                    else -> null
                }
            val memText =
                when {
                    memUsed != null && memTotal != null -> "${bytes(memUsed)} / ${bytes(memTotal)}"
                    s.memPct != null -> fixed(s.memPct, 1) + "%"
                    else -> DASH
                }
            val diskUsed = s.diskUsed
            val diskTotal = s.diskTotal
            val diskPct =
                when {
                    diskUsed != null && diskTotal != null && diskTotal > 0 ->
                        clampPct(diskUsed.toDouble() / diskTotal.toDouble() * 100.0)
                    s.diskPct != null -> clampPct(s.diskPct)
                    else -> null
                }
            val diskText =
                when {
                    diskUsed != null && diskTotal != null -> "${bytes(diskUsed)} / ${bytes(diskTotal)}"
                    s.diskPct != null -> fixed(s.diskPct, 0) + "%"
                    else -> DASH
                }
            // GPU / VRAM — only when the host has one. Bar = VRAM when known, else util.
            val vramTotal = s.gpuMemTotalMb
            val gpuUtil = s.gpuUtilPct ?: s.gpuPct
            val hasGpu = (vramTotal != null && vramTotal > 0) || s.gpuName != null
            val gpuPct =
                if (vramTotal != null && vramTotal > 0) {
                    clampPct((s.gpuMemUsedMb ?: 0L).toDouble() / vramTotal.toDouble() * 100.0)
                } else {
                    gpuUtil?.let { clampPct(it) }
                }
            val gpuText =
                if (hasGpu) {
                    val parts = mutableListOf<String>()
                    gpuUtil?.let { parts += fixed(it, 0) + "%" }
                    s.gpuTemp?.let { parts += "${it.toInt()}°C" }
                    if (vramTotal != null && vramTotal > 0) parts += "${s.gpuMemUsedMb ?: 0L}/${vramTotal}M"
                    parts.joinToString(" · ").ifBlank { DASH }
                } else {
                    ""
                }
            val hasSwap = s.swapTotal > 0
            val daemonParts = mutableListOf<String>()
            if (s.daemonRssBytes > 0) daemonParts += "${bytes(s.daemonRssBytes)} RSS"
            if (s.goroutines > 0) daemonParts += "${s.goroutines}g"
            if (s.openFds > 0) daemonParts += "${s.openFds}fd"
            return WidgetMonitorSnapshot(
                cpuPct = cpuPct ?: -1,
                cpuText = cpuText,
                memPct = memPct ?: -1,
                memText = memText,
                diskPct = diskPct ?: -1,
                diskText = diskText,
                hasSwap = hasSwap,
                swapPct = if (hasSwap) clampPct(s.swapUsed.toDouble() / s.swapTotal.toDouble() * 100.0) else -1,
                swapText = if (hasSwap) "${bytes(s.swapUsed)} / ${bytes(s.swapTotal)}" else DASH,
                hasGpu = hasGpu,
                gpuPct = gpuPct ?: -1,
                gpuText = gpuText,
                ebpfActive = s.ebpfActive,
                netRxText = "↓ ${bytes(s.netRxBytes)}",
                netTxText = "↑ ${bytes(s.netTxBytes)}",
                daemonText = daemonParts.joinToString(" · "),
                uptimeText = if (s.uptimeSeconds > 0) uptimeShort(s.uptimeSeconds) else "",
                sessionsText = "${s.sessionsTotal} · ${s.sessionsRunning}r · ${s.sessionsWaiting}w",
            )
        }

        /** Android `Double.toInt()` after clamping to 0–100 (truncates). */
        private fun clampPct(v: Double): Int = v.coerceIn(0.0, 100.0).toInt()

        /** Android MonitorWidget `fmt`: decimal units, GB with one decimal. */
        internal fun bytes(b: Long): String =
            when {
                b >= 1_000_000_000 -> fixed(b / 1_000_000_000.0, 1) + " GB"
                b >= 1_000_000 -> fixed(b / 1_000_000.0, 0) + " MB"
                b >= 1_000 -> fixed(b / 1_000.0, 0) + " KB"
                else -> "$b B"
            }

        /** Android MonitorWidget `formatUptimeShort`. */
        internal fun uptimeShort(seconds: Long): String {
            val d = seconds / 86_400
            val h = (seconds % 86_400) / 3600
            val m = (seconds % 3600) / 60
            return when {
                d > 0 -> "${d}d${h}h"
                h > 0 -> "${h}h${m}m"
                else -> "${m}m"
            }
        }

        /** `String.format("%.Nf")` (half-up) without JVM formatting, so it runs on iOS. */
        internal fun fixed(
            v: Double,
            digits: Int,
        ): String {
            if (v.isNaN() || v.isInfinite()) return "0"
            var factor = 1L
            repeat(digits) { factor *= 10 }
            val r = floor(abs(v) * factor + 0.5).toLong()
            val ip = r / factor
            val fp = r % factor
            val body = if (digits == 0) "$ip" else "$ip." + fp.toString().padStart(digits, '0')
            return if (v < 0 && r != 0L) "-$body" else body
        }
    }
}

/**
 * Android `VoiceCommandActivity` target pick for "send … to datawatch": among
 * running or waiting sessions, newest activity first, the first whose name
 * contains [hint] or whose id starts with it (case-insensitive); with no hint
 * or no match, the most recently active one. Null when nothing is running.
 */
public object VoiceSendTarget {
    public fun resolve(
        sessions: List<Session>,
        hint: String?,
    ): Session? {
        val active =
            sessions
                .filter { it.state == SessionState.Running || it.state == SessionState.Waiting }
                .sortedByDescending { it.lastActivityAt }
        if (hint.isNullOrBlank()) return active.firstOrNull()
        return active.firstOrNull { s ->
            s.name?.contains(hint, ignoreCase = true) == true || s.id.startsWith(hint, ignoreCase = true)
        } ?: active.firstOrNull()
    }
}
