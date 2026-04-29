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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Driver-safe Monitor view — surfaces the same vitals the PWA
 * Monitor tab does, constrained to driver-distraction-friendly
 * rows. Default entry screen per user request 2026-04-22 so the
 * first thing a driver sees is the fleet's health, not a session
 * list. Tapping into sessions lives in the ActionStrip.
 */
public class AutoMonitorScreen(carContext: CarContext) : Screen(carContext) {
    private var profile: ServerProfile? = null
    private var stats: StatsDto? = null
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
            val p =
                resolveActiveProfile() ?: run {
                    error = "No enabled server (configure on phone)"
                    profile = null
                    stats = null
                    return
                }
            profile = p
            AutoServiceLocator.transportFor(p).stats().fold(
                onSuccess = { dto ->
                    error = null
                    stats = dto
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
        val items = ItemList.Builder()
        profile?.let {
            items.addItem(
                Row.Builder()
                    .setTitle(colored("● ${it.displayName}", CarColor.GREEN))
                    .addText(it.baseUrl)
                    .build(),
            )
        }
        val s = stats
        if (s != null) {
            // CPU row — prefer load1/cores, fall back to flat pct.
            val load1 = s.cpuLoad1
            val cores = s.cpuCores
            val cpuText =
                when {
                    load1 != null && cores != null && cores > 0 ->
                        "%.2f load · %d cores".format(load1, cores)
                    s.cpuPct != null -> "%.1f%%".format(s.cpuPct)
                    else -> "—"
                }
            items.addItem(
                Row.Builder()
                    .setTitle("CPU")
                    .addText(cpuText)
                    .build(),
            )
            val memUsed = s.memUsed
            val memTotal = s.memTotal
            val memText =
                when {
                    memUsed != null && memTotal != null && memTotal > 0 ->
                        "${fmt(memUsed)} / ${fmt(memTotal)}"
                    s.memPct != null -> "%.1f%%".format(s.memPct)
                    else -> "—"
                }
            items.addItem(
                Row.Builder()
                    .setTitle("Memory")
                    .addText(memText)
                    .build(),
            )
            val diskUsed = s.diskUsed
            val diskTotal = s.diskTotal
            if (diskUsed != null && diskTotal != null && diskTotal > 0) {
                items.addItem(
                    Row.Builder()
                        .setTitle("Disk")
                        .addText("${fmt(diskUsed)} / ${fmt(diskTotal)}")
                        .build(),
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
                    .addText(
                        "${s.sessionsTotal} total · ${s.sessionsRunning} run · ${s.sessionsWaiting} wait",
                    )
                    .build(),
            )
            if (s.uptimeSeconds > 0) {
                items.addItem(
                    Row.Builder()
                        .setTitle("Uptime")
                        .addText(uptime(s.uptimeSeconds))
                        .build(),
                )
            }
        } else if (error == null) {
            items.addItem(
                Row.Builder()
                    .setTitle("Loading…")
                    .addText("Fetching server stats")
                    .build(),
            )
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
            .setTitle(error?.let { "$title · $it" } ?: title)
            .setHeaderAction(Action.APP_ICON)
            .setActionStrip(actionStrip)
            .setSingleList(items.build())
            .build()
    }
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
