@file:Suppress("MagicNumber")

package com.dmzs.datawatchclient.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import com.dmzs.datawatchclient.transport.dto.ComputeNodeDetailDto
import com.dmzs.datawatchclient.transport.dto.ComputeNodeDto
import com.dmzs.datawatchclient.transport.dto.ComputeNodeDiskStatDto

/**
 * Full-detail screen for one physical compute node.
 * Rows: CPU · Memory · Disk mounts (primary ones) · GPU(s) · Ollama · Uptime.
 * Static: data is passed in at construction; the parent AutoMonitorScreen polls and
 * can push a fresh instance if the data changes significantly.
 */
public class AutoComputeNodeDetailScreen(
    carContext: CarContext,
    private val nodeDto: ComputeNodeDto,
    private val detail: ComputeNodeDetailDto,
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val speakerIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_auto_speaker)).build()
        val closeIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_auto_close)).build()
        val actionStrip = ActionStrip.Builder()
            .addAction(Action.Builder().setIcon(speakerIcon).setOnClickListener { AutoTts.speak(carContext, nodeDto.name) }.build())
            .addAction(Action.Builder().setIcon(closeIcon).setOnClickListener { screenManager.pop() }.build())
            .build()
        return try {
            val items = ItemList.Builder()
            buildRows(items)
            ListTemplate.Builder()
                .setTitle(nodeDto.name)
                .setHeaderAction(Action.BACK)
                .setSingleList(items.build())
                .setActionStrip(actionStrip)
                .build()
        } catch (e: Throwable) {
            val errItems = ItemList.Builder()
                .addItem(Row.Builder().setTitle("Error").addText(e.message ?: "Unknown").build())
                .build()
            ListTemplate.Builder()
                .setTitle(nodeDto.name)
                .setHeaderAction(Action.BACK)
                .setSingleList(errItems)
                .setActionStrip(actionStrip)
                .build()
        }
    }

    private fun buildRows(items: ItemList.Builder) {
        // ── CPU ─────────────────────────────────────────────────────────────
        val cpu = detail.cpu
        val cpuPct = cpu?.pct?.toInt() ?: detail.cpuPct?.toInt()
        if (cpuPct != null) {
            val text = buildString {
                append(progressBar(cpuPct))
                if (cpu != null) {
                    if (cpu.load1 > 0 && cpu.cores > 0) {
                        append("  load ${"%.2f".format(cpu.load1)} · ${cpu.cores} cores")
                    } else if (cpu.cores > 0) {
                        append("  ${cpu.cores} cores")
                    }
                } else {
                    nodeDto.hardwareSpec?.cpuCores?.takeIf { it > 0 }?.let { append("  $it cores") }
                }
            }
            items.addItem(Row.Builder().setTitle("CPU").addText(text).build())
        }

        // ── Memory ──────────────────────────────────────────────────────────
        val mem = detail.mem
        val memPct = mem?.pct?.toInt() ?: detail.memPct?.toInt()
        if (memPct != null) {
            val text = buildString {
                append(progressBar(memPct))
                if (mem != null && mem.totalBytes > 0) {
                    append("  ${fmt(mem.usedBytes)} / ${fmt(mem.totalBytes)}")
                }
            }
            items.addItem(Row.Builder().setTitle("Memory").addText(text).build())
        }

        // ── Disk mounts (skip tiny boot partitions, max 3) ──────────────────
        val significantDisks = detail.disk
            .filter { it.totalBytes > DISK_MIN_BYTES }
            .sortedByDescending { it.totalBytes }
            .take(MAX_DISK_ROWS)
        significantDisks.forEach { d ->
            val pct = d.pct.toInt()
            val label = diskLabel(d)
            val builder = Row.Builder()
                .setTitle(label)
                .addText("${progressBar(pct)}  ${fmt(d.usedBytes)} / ${fmt(d.totalBytes)}")
            items.addItem(builder.build())
        }

        // ── GPU(s) ──────────────────────────────────────────────────────────
        detail.gpu.forEachIndexed { idx, g ->
            val rawName = g.name.takeIf { it.isNotBlank() && !it.equals("GPU", ignoreCase = true) }
            val rawVendor = g.vendor.takeIf { it.isNotBlank() }
            val title = buildString {
                val label = rawName ?: rawVendor ?: "GPU"
                append(label.take(MAX_GPU_TITLE))
                if (detail.gpu.size > 1) append(" [${idx + 1}]")
            }
            val builder = Row.Builder().setTitle(title)
            val line1 = buildString {
                append(progressBar(g.utilPct.toInt()))
                if (g.tempC > 0) append("  ${g.tempC.toInt()}°C")
                if (g.powerW > 0) append("  ${g.powerW.toInt()}W")
            }
            builder.addText(line1)
            if (g.memTotalBytes > 0) {
                val vramPct = (g.memUsedBytes * PCT_MULTIPLIER / g.memTotalBytes).toInt()
                builder.addText("VRAM ${progressBar(vramPct)}  ${fmt(g.memUsedBytes)} / ${fmt(g.memTotalBytes)}")
            } else {
                // Jetson/unified memory: GPU shares system RAM — show shared pool
                val sysMem = detail.mem
                if (sysMem != null && sysMem.totalBytes > 0) {
                    val uPct = sysMem.pct.toInt()
                    builder.addText("Unified ${progressBar(uPct)}  ${fmt(sysMem.usedBytes)} / ${fmt(sysMem.totalBytes)}")
                } else {
                    builder.addText("VRAM: unified (shared)")
                }
            }
            items.addItem(builder.build())
        }

        // Fall back to hardware spec GPU info if no live GPU data
        if (detail.gpu.isEmpty()) {
            val spec = nodeDto.hardwareSpec
            val cap = nodeDto.declaredCapacity
            if (spec != null && (spec.gpuModel != null || spec.gpuCount > 0)) {
                val label = buildString {
                    spec.gpuModel?.let { append(it.take(MAX_GPU_TITLE)) } ?: append("GPU")
                    if (spec.gpuCount > 1) append(" ×${spec.gpuCount}")
                }
                val vramNote = cap?.gpuMemGb?.takeIf { it > 0 }?.let { "${it} GB VRAM · no live stats" } ?: "No live stats"
                items.addItem(Row.Builder().setTitle(label).addText(vramNote).build())
            }
        }

        // ── Ollama ──────────────────────────────────────────────────────────
        val ollama = detail.ollamaStats
        if (ollama != null && ollama.rssBytes > 0) {
            items.addItem(
                Row.Builder()
                    .setTitle("Ollama")
                    .addText("RSS ${fmt(ollama.rssBytes)}")
                    .build(),
            )
        }

        // ── Uptime ──────────────────────────────────────────────────────────
        if (detail.uptimeSeconds > 0) {
            items.addItem(
                Row.Builder()
                    .setTitle("Uptime")
                    .addText(uptime(detail.uptimeSeconds))
                    .build(),
            )
        }
    }

    private companion object {
        const val PCT_MULTIPLIER: Int = 100
        const val PROGRESS_BAR_WIDTH: Int = 10
        const val MAX_DISK_ROWS: Int = 3
        const val MAX_GPU_TITLE: Int = 24
        const val DISK_MIN_BYTES: Long = 500_000_000L // 500 MB — skip tiny EFI/boot partitions

        private const val BYTES_PER_KB: Long = 1_000L
        private const val BYTES_PER_MB: Long = 1_000_000L
        private const val BYTES_PER_GB: Long = 1_000_000_000L
        private const val BYTES_PER_TB: Long = 1_000_000_000_000L

        private const val SECONDS_PER_MINUTE: Long = 60L
        private const val SECONDS_PER_HOUR: Long = 3_600L
        private const val SECONDS_PER_DAY: Long = 86_400L

        fun progressBar(pct: Int, width: Int = PROGRESS_BAR_WIDTH): String {
            val clamped = pct.coerceIn(0, PCT_MULTIPLIER)
            val filled = (clamped * width / PCT_MULTIPLIER).coerceIn(0, width)
            return "▓".repeat(filled) + "░".repeat(width - filled) + " $clamped%"
        }

        fun fmt(bytes: Long): String = when {
            bytes >= BYTES_PER_TB -> "%.1f TB".format(bytes / BYTES_PER_TB.toDouble())
            bytes >= BYTES_PER_GB -> "%.1f GB".format(bytes / BYTES_PER_GB.toDouble())
            bytes >= BYTES_PER_MB -> "%.1f MB".format(bytes / BYTES_PER_MB.toDouble())
            bytes >= BYTES_PER_KB -> "%.1f KB".format(bytes / BYTES_PER_KB.toDouble())
            else -> "$bytes B"
        }

        fun diskLabel(d: ComputeNodeDiskStatDto): String {
            val mount = d.mount
            return when {
                mount == "/" -> "Disk /"
                mount.startsWith("/mnt/") -> "Disk ${mount.removePrefix("/mnt/").take(16)}"
                mount.startsWith("/home/") -> "Disk home"
                else -> "Disk ${mount.take(18)}"
            }
        }

        fun uptime(seconds: Long): String {
            val d = seconds / SECONDS_PER_DAY
            val h = (seconds % SECONDS_PER_DAY) / SECONDS_PER_HOUR
            val m = (seconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
            return buildString {
                if (d > 0) append("${d}d ")
                if (h > 0 || d > 0) append("${h}h ")
                append("${m}m")
            }
        }
    }
}
