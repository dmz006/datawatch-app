package com.dmzs.datawatchclient.wear

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import com.dmzs.datawatchclient.Version
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Wear OS companion. Horizontal pager with four pages — Monitor first
 * per user request 2026-04-22, then Sessions, Server picker, About.
 * All data comes from the phone's [WearSyncService] via the Wearable
 * Data Layer; the watch never holds a bearer token. Server switches
 * tap-through to the picker and round-trip via MessageClient so the
 * phone's ActiveServerStore is the single source of truth.
 */
public class WearMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colors = datawatchWearColors()) { WearRoot() }
        }
    }
}

// Datawatch brand colours for Wear — dark surface with teal accent
// and amber "waiting" so the watch matches the phone's Monitor card
// treatment rather than stock Wear defaults.
private fun datawatchWearColors(): Colors =
    Colors(
        primary = Color(0xFF00E5A0),
        secondary = Color(0xFFFFB020),
        background = Color(0xFF0B0F14),
        surface = Color(0xFF0F1419),
        error = Color(0xFFFF5555),
        onPrimary = Color(0xFF00140B),
        onSecondary = Color(0xFF2A1B00),
        onBackground = Color(0xFFE7EDF3),
        onSurface = Color(0xFFE7EDF3),
        onSurfaceVariant = Color(0xFF9AA7B3),
        onError = Color(0xFF1A0000),
    )

@Composable
private fun WearRoot(
    vm: WearSessionCountsViewModel =
        viewModel(
            factory =
                ViewModelProvider.AndroidViewModelFactory.getInstance(
                    LocalApp.current,
                ),
        ),
) {
    val state by vm.state.collectAsState()
    val pagerState = rememberPagerState(initialPage = 0) { 4 }
    Scaffold {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                when (page) {
                    0 -> MonitorPage(state)
                    1 -> SessionsPage(state)
                    2 -> ServersPage(state) { id -> vm.requestActiveServer(id) }
                    3 -> AboutPage()
                }
            }
            PagerDots(pagerState.currentPage, 4, Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp))
        }
    }
}

@Composable
private fun PagerDots(selected: Int, count: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(count) { i ->
            val c = if (i == selected) MaterialTheme.colors.primary else MaterialTheme.colors.onSurfaceVariant
            val dot = if (i == selected) 6 else 4
            Box(
                modifier = Modifier
                    .padding(1.dp)
                    .size(dot.dp)
                    .background(c, shape = androidx.compose.foundation.shape.CircleShape),
            )
        }
    }
}

@Composable
private fun PageScaffold(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.title3,
            color = MaterialTheme.colors.primary,
            fontWeight = FontWeight.SemiBold,
        )
        content()
    }
}

@Composable
private fun MonitorPage(state: WearSessionCountsViewModel.UiState) {
    PageScaffold("Monitor") {
        if (state.loading) {
            CircularProgressIndicator(modifier = Modifier.padding(top = 12.dp))
            return@PageScaffold
        }
        if (state.pairedServer.isEmpty()) {
            Text(
                "Open datawatch on your phone",
                modifier = Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.body2,
            )
            return@PageScaffold
        }
        Text(
            "● ${state.serverName}",
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.caption1,
            color = MaterialTheme.colors.primary,
        )
        StatLine("CPU", state.cpuText())
        StatLine("Memory", state.memText())
        StatLine("Disk", state.diskText())
        StatLine("Uptime", state.uptimeText())
    }
}

@Composable
private fun SessionsPage(state: WearSessionCountsViewModel.UiState) {
    PageScaffold("Sessions") {
        if (state.pairedServer.isEmpty()) {
            Text(
                "—",
                modifier = Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.body2,
            )
            return@PageScaffold
        }
        Row(
            modifier = Modifier.padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CountTile(value = state.running, label = "run", color = MaterialTheme.colors.primary)
            CountTile(value = state.waiting, label = "wait", color = MaterialTheme.colors.secondary)
            CountTile(value = state.total, label = "total", color = MaterialTheme.colors.onSurface)
        }
        Text(
            state.serverName,
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.caption2,
            color = MaterialTheme.colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun ServersPage(
    state: WearSessionCountsViewModel.UiState,
    onPick: (String) -> Unit,
) {
    PageScaffold("Server") {
        if (state.profiles.isEmpty()) {
            Text(
                "No enabled servers",
                modifier = Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.body2,
            )
            return@PageScaffold
        }
        state.profiles.forEach { (id, name) ->
            val isActive = id == state.pairedServer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .background(
                        color = if (isActive) MaterialTheme.colors.surface else Color.Transparent,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    )
                    .clickable { onPick(id) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (isActive) "●" else "○",
                    style = MaterialTheme.typography.body2,
                    color = if (isActive) MaterialTheme.colors.primary else MaterialTheme.colors.onSurfaceVariant,
                )
                Text(
                    name,
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurface,
                )
            }
        }
    }
}

@Composable
private fun AboutPage() {
    PageScaffold("About") {
        Text(
            "datawatch",
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.title2,
            color = MaterialTheme.colors.primary,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "v${Version.VERSION} (${Version.VERSION_CODE})",
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onSurface,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            "Wear OS companion",
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.caption2,
            color = MaterialTheme.colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.caption2, color = MaterialTheme.colors.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.caption1,
            color = MaterialTheme.colors.onSurface,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun CountTile(
    value: Int,
    label: String,
    color: Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value.toString(),
            style = MaterialTheme.typography.display3,
            color = color,
        )
        Text(
            label,
            style = MaterialTheme.typography.caption3,
            color = MaterialTheme.colors.onSurfaceVariant,
        )
    }
}

public class WearSessionCountsViewModel(app: Application) : AndroidViewModel(app) {
    public data class UiState(
        val loading: Boolean = true,
        val pairedServer: String = "",
        val serverName: String = "",
        val running: Int = 0,
        val waiting: Int = 0,
        val total: Int = 0,
        // Stats snapshot (phone publishes on /datawatch/stats).
        val cpuLoad1: Double = 0.0,
        val cpuCores: Int = 0,
        val memUsed: Long = 0,
        val memTotal: Long = 0,
        val diskUsed: Long = 0,
        val diskTotal: Long = 0,
        val uptimeSeconds: Long = 0,
        // Enabled profiles the user can switch between.
        val profiles: List<Pair<String, String>> = emptyList(),
    ) {
        public fun cpuText(): String =
            when {
                cpuCores > 0 -> "%.2f · %d cores".format(cpuLoad1, cpuCores)
                cpuLoad1 > 0 -> "%.1f%%".format(cpuLoad1)
                else -> "—"
            }
        public fun memText(): String =
            if (memTotal > 0) "${fmt(memUsed)} / ${fmt(memTotal)}" else "—"
        public fun diskText(): String =
            if (diskTotal > 0) "${fmt(diskUsed)} / ${fmt(diskTotal)}" else "—"
        public fun uptimeText(): String = if (uptimeSeconds > 0) fmtUptime(uptimeSeconds) else "—"
    }

    private val _state = MutableStateFlow(UiState())
    public val state: StateFlow<UiState> = _state

    private val dataClient: DataClient = Wearable.getDataClient(app)
    private val messageClient = Wearable.getMessageClient(app)
    private val nodeClient = Wearable.getNodeClient(app)

    private val listener =
        DataClient.OnDataChangedListener { buffer: DataEventBuffer -> consume(buffer) }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val items = dataClient.dataItems.await()
                try {
                    items.forEach { item ->
                        when (item.uri.path) {
                            COUNTS_PATH ->
                                applyCounts(DataMapItem.fromDataItem(item).dataMap)
                            PROFILES_PATH ->
                                applyProfiles(DataMapItem.fromDataItem(item).dataMap)
                            STATS_PATH ->
                                applyStats(DataMapItem.fromDataItem(item).dataMap)
                        }
                    }
                } finally {
                    items.release()
                }
            }
            if (_state.value.loading) {
                _state.value = _state.value.copy(loading = false)
            }
        }
        dataClient.addListener(listener)
    }

    override fun onCleared() {
        super.onCleared()
        dataClient.removeListener(listener)
    }

    public fun requestActiveServer(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val nodes: List<Node> = nodeClient.connectedNodes.await()
                nodes.forEach { node ->
                    messageClient.sendMessage(
                        node.id,
                        SET_ACTIVE_PATH,
                        id.toByteArray(Charsets.UTF_8),
                    ).await()
                }
                // Optimistic local update so the picker reflects the
                // tap before the phone re-publishes.
                _state.value = _state.value.copy(pairedServer = id)
            }
        }
    }

    private fun consume(buffer: DataEventBuffer) {
        try {
            for (event in buffer) {
                if (event.type == DataEvent.TYPE_CHANGED) {
                    when (event.dataItem.uri.path) {
                        COUNTS_PATH ->
                            applyCounts(DataMapItem.fromDataItem(event.dataItem).dataMap)
                        PROFILES_PATH ->
                            applyProfiles(DataMapItem.fromDataItem(event.dataItem).dataMap)
                        STATS_PATH ->
                            applyStats(DataMapItem.fromDataItem(event.dataItem).dataMap)
                    }
                }
            }
        } finally {
            buffer.release()
        }
    }

    private fun applyCounts(map: DataMap) {
        _state.value = _state.value.copy(
            loading = false,
            pairedServer = map.getString("serverId", ""),
            serverName = map.getString("serverName", ""),
            running = map.getInt("running", 0),
            waiting = map.getInt("waiting", 0),
            total = map.getInt("total", 0),
        )
    }

    private fun applyProfiles(map: DataMap) {
        val ids = map.getStringArray("ids") ?: emptyArray()
        val names = map.getStringArray("names") ?: emptyArray()
        val pairs = ids.zip(names).map { (id, name) -> id to name }
        val activeId = map.getString("activeId", _state.value.pairedServer)
        _state.value = _state.value.copy(
            profiles = pairs,
            pairedServer = activeId,
        )
    }

    private fun applyStats(map: DataMap) {
        _state.value = _state.value.copy(
            cpuLoad1 = map.getDouble("cpuLoad1", 0.0),
            cpuCores = map.getInt("cpuCores", 0),
            memUsed = map.getLong("memUsed", 0),
            memTotal = map.getLong("memTotal", 0),
            diskUsed = map.getLong("diskUsed", 0),
            diskTotal = map.getLong("diskTotal", 0),
            uptimeSeconds = map.getLong("uptimeSeconds", 0),
        )
    }

    public companion object {
        public const val COUNTS_PATH: String = "/datawatch/counts"
        public const val PROFILES_PATH: String = "/datawatch/profiles"
        public const val STATS_PATH: String = "/datawatch/stats"
        public const val SET_ACTIVE_PATH: String = "/datawatch/setActive"
    }
}

private fun fmt(bytes: Long): String =
    when {
        bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> "%.0f MB".format(bytes / 1_000_000.0)
        bytes >= 1_000 -> "%.0f KB".format(bytes / 1_000.0)
        else -> "$bytes B"
    }

private fun fmtUptime(seconds: Long): String {
    val d = seconds / 86_400
    val h = (seconds % 86_400) / 3600
    val m = (seconds % 3600) / 60
    return buildString {
        if (d > 0) append("${d}d ")
        if (h > 0 || d > 0) append("${h}h ")
        append("${m}m")
    }
}

private object LocalApp {
    val current: Application
        @androidx.compose.runtime.Composable
        @androidx.compose.runtime.ReadOnlyComposable
        get() = androidx.compose.ui.platform.LocalContext.current.applicationContext as Application
}

private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T =
    kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resumeWith(Result.success(it)) }
        addOnFailureListener { cont.resumeWith(Result.failure(it)) }
        addOnCanceledListener { cont.cancel() }
    }
