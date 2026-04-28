package com.dmzs.datawatchclient.wear

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

    // v0.35.5 — session tap popup with voice reply.
    // v0.35.8 — replaced RecognizerIntent with phone-relayed Whisper.
    // Watch records audio locally via WearVoiceRecorder, ships bytes
    // to the phone over MessageClient `/datawatch/audio`, phone
    // resolves the session's profile + posts to /api/voice/transcribe,
    // phone replies on `/datawatch/transcript` with the text. Watch
    // shows the transcript in the popup for the user to validate
    // before tapping Send (which uses the existing /datawatch/reply
    // path from v0.35.5).
    var openSession: WearSessionCountsViewModel.SessionItem? by remember {
        mutableStateOf(null)
    }
    var pendingTranscript by remember { mutableStateOf("") }
    var recording by remember { mutableStateOf(false) }
    var transcribing by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val recorder =
        remember { com.dmzs.datawatchclient.wear.voice.WearVoiceRecorder(context) }

    // Subscribe to the phone's transcript replies. The DisposableEffect
    // tied to `openSession` (re-armed every popup) lets us tear down
    // the listener cleanly when the popup closes.
    androidx.compose.runtime.DisposableEffect(openSession?.id) {
        val listener =
            com.google.android.gms.wearable.MessageClient
                .OnMessageReceivedListener { ev ->
                    if (ev.path == WearSessionCountsViewModel.TRANSCRIPT_PATH) {
                        val body = runCatching { String(ev.data, Charsets.UTF_8) }
                            .getOrNull().orEmpty()
                        val (sid, text) =
                            body.split("\n", limit = 2).let { p ->
                                p.firstOrNull().orEmpty() to
                                    p.getOrNull(1).orEmpty()
                            }
                        if (sid == openSession?.id) {
                            transcribing = false
                            pendingTranscript =
                                if (text.startsWith("error:")) {
                                    "[transcribe failed: ${text.removePrefix("error:")}]"
                                } else {
                                    text
                                }
                        }
                    }
                }
        com.google.android.gms.wearable.Wearable.getMessageClient(context)
            .addListener(listener)
        onDispose {
            com.google.android.gms.wearable.Wearable.getMessageClient(context)
                .removeListener(listener)
        }
    }

    val micPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            if (granted) {
                runCatching { recorder.start() }
                    .onSuccess { recording = true }
                    .onFailure { recording = false }
            }
        }

    Scaffold {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                when (page) {
                    0 -> MonitorPage(state)
                    1 ->
                        SessionsPage(
                            state = state,
                            onSessionTap = { item -> openSession = item },
                        )
                    2 -> ServersPage(state) { id -> vm.requestActiveServer(id) }
                    3 -> AboutPage()
                }
            }
            PagerDots(pagerState.currentPage, 4, Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp))
            openSession?.let { item ->
                SessionDetailPopup(
                    session = item,
                    transcript = pendingTranscript,
                    recording = recording,
                    transcribing = transcribing,
                    onRecord = {
                        if (!recording) {
                            pendingTranscript = ""
                            val granted =
                                androidx.core.content.ContextCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.RECORD_AUDIO,
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                runCatching { recorder.start() }
                                    .onSuccess { recording = true }
                            } else {
                                micPermissionLauncher.launch(
                                    android.Manifest.permission.RECORD_AUDIO,
                                )
                            }
                        } else {
                            val captured = recorder.stop()
                            recording = false
                            if (captured != null && captured.first.isNotEmpty()) {
                                transcribing = true
                                vm.sendAudio(item.id, captured.first)
                            }
                        }
                    },
                    onConfirm = {
                        vm.sendReply(item.id, pendingTranscript)
                        pendingTranscript = ""
                        recording = false
                        transcribing = false
                        openSession = null
                    },
                    onDismiss = {
                        runCatching { recorder.cancel() }
                        pendingTranscript = ""
                        recording = false
                        transcribing = false
                        openSession = null
                    },
                )
            }
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
    // Per user 2026-04-24 "the wear app should have borders around each
    // screen like cards" + "the watch is a samsung watch, the cards
    // should be round to match bezel" — each pager page renders inside
    // a CIRCULAR bordered card that follows the Samsung Galaxy Watch's
    // round bezel. Dark surface with teal primary-color border at
    // ~45% alpha. Inner padding is diamond-shaped (more horizontal,
    // less vertical) because a circular viewport cuts the corners
    // anyway — content stays within the safe area.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        val cardShape = androidx.compose.foundation.shape.CircleShape
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.surface, shape = cardShape)
                .border(
                    width = 1.5.dp,
                    color = MaterialTheme.colors.primary.copy(alpha = 0.45f),
                    shape = cardShape,
                )
                .padding(horizontal = 28.dp, vertical = 20.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
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
        // v0.35.4 — color-gauge redesign. Active-server name stays at
        // top; CPU / Memory / Disk / GPU render as 2-up gauge rings
        // with threshold-coloured arcs (green → amber → red). GPU
        // shows only when the phone has published a real snapshot.
        // Uptime hangs below as a single-line caption. The whole
        // column is vertically scrollable inside the round card
        // (PageScaffold already wraps in verticalScroll) so content
        // that overflows the bezel is reachable via bezel scroll.
        Text(
            "● ${state.serverName}",
            modifier = Modifier.padding(top = 2.dp),
            style = MaterialTheme.typography.caption1,
            color = MaterialTheme.colors.primary,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            GaugeRing(
                label = "CPU",
                pct = state.cpuPctFor(),
                center = cpuCenterText(state),
            )
            GaugeRing(
                label = "MEM",
                pct = state.memPct(),
                center =
                    if (state.memTotal > 0) "%.0f%%".format(state.memPct())
                    else "—",
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            GaugeRing(
                label = "DISK",
                pct = state.diskPct(),
                center =
                    if (state.diskTotal > 0) "%.0f%%".format(state.diskPct())
                    else "—",
            )
            if (state.hasGpu()) {
                GaugeRing(
                    label = "GPU",
                    pct = state.gpuPct(),
                    center =
                        if (state.gpuPct() > 0) "%.0f%%".format(state.gpuPct())
                        else "—",
                )
            } else {
                // Empty slot keeps the 2-up grid symmetric when the
                // phone hasn't published GPU stats yet.
                Box(modifier = Modifier.size(GAUGE_SIZE_DP.dp))
            }
        }
        if (state.uptimeSeconds > 0) {
            Text(
                "up ${state.uptimeText()}",
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.onSurfaceVariant,
            )
        }
        if (state.hasGpu() && state.gpuMemTotalMb > 0) {
            Text(
                "vram ${state.gpuMemUsedMb}/${state.gpuMemTotalMb}M",
                style = MaterialTheme.typography.caption3,
                color = MaterialTheme.colors.onSurfaceVariant,
            )
        }
    }
}

/**
 * Threshold-coloured ring gauge sized for a round-bezel watch.
 * Green for nominal load, amber 60–80 %, red 80+ %. Value renders
 * inside the ring so the gauge is self-labelling and the overall
 * page stays compact.
 */
@Composable
private fun GaugeRing(
    label: String,
    pct: Float,
    center: String,
) {
    val safePct = pct.coerceIn(0f, 100f)
    val ringColor =
        when {
            safePct >= GAUGE_RED_THRESHOLD -> Color(0xFFEF4444) // red-500
            safePct >= GAUGE_AMBER_THRESHOLD -> Color(0xFFF59E0B) // amber-500
            safePct > 0f -> Color(0xFF22C55E) // green-500
            else -> MaterialTheme.colors.onSurfaceVariant
        }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(GAUGE_SIZE_DP.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                progress = safePct / 100f,
                modifier = Modifier.fillMaxSize(),
                indicatorColor = ringColor,
                trackColor = MaterialTheme.colors.onSurfaceVariant.copy(alpha = 0.25f),
                strokeWidth = GAUGE_STROKE_DP.dp,
            )
            Text(
                center,
                style = MaterialTheme.typography.caption1,
                color = MaterialTheme.colors.onSurface,
                fontFamily = FontFamily.Monospace,
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.caption3,
            color = MaterialTheme.colors.onSurfaceVariant,
        )
    }
}

private fun cpuCenterText(state: WearSessionCountsViewModel.UiState): String =
    when {
        state.cpuCores > 0 -> "%.1f".format(state.cpuLoad1)
        state.cpuLoad1 > 0 -> "%.0f%%".format(state.cpuLoad1)
        else -> "—"
    }

private const val GAUGE_SIZE_DP: Int = 54
private const val GAUGE_STROKE_DP: Int = 4
private const val GAUGE_AMBER_THRESHOLD: Float = 60f
private const val GAUGE_RED_THRESHOLD: Float = 80f

@Composable
private fun SessionsPage(
    state: WearSessionCountsViewModel.UiState,
    onSessionTap: (WearSessionCountsViewModel.SessionItem) -> Unit,
) {
    PageScaffold("Sessions") {
        if (state.pairedServer.isEmpty()) {
            Text(
                "—",
                modifier = Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.body2,
            )
            return@PageScaffold
        }
        // Counts strip at top — pre-existing glance remains the
        // first-impression read ("is anything waiting on me?"). The
        // per-session rows below are the v0.35.5 tap-to-reply
        // affordance; each row opens the popup with voice input.
        Row(
            modifier = Modifier.padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CountTile(value = state.running, label = "run", color = MaterialTheme.colors.primary)
            CountTile(value = state.waiting, label = "wait", color = MaterialTheme.colors.secondary)
            CountTile(value = state.total, label = "total", color = MaterialTheme.colors.onSurface)
        }
        if (state.sessions.isEmpty()) {
            Text(
                state.serverName,
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.onSurfaceVariant,
            )
            return@PageScaffold
        }
        // Per-session rows. Bezel-scrollable column above already
        // exists via PageScaffold, so rows naturally paginate when
        // the count exceeds visible area on the round face.
        state.sessions.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .background(
                        color = sessionBadgeColor(item.stateName).copy(alpha = 0.25f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                    )
                    .clickable { onSessionTap(item) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "●",
                    color = sessionBadgeColor(item.stateName),
                    style = MaterialTheme.typography.caption1,
                )
                Text(
                    item.title,
                    modifier = Modifier.padding(start = 6.dp),
                    style = MaterialTheme.typography.caption1,
                    color = MaterialTheme.colors.onSurface,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Color accent per session state — running green, waiting amber, others grey. */
private fun sessionBadgeColor(stateName: String): Color =
    when (stateName.lowercase()) {
        "running" -> Color(0xFF22C55E)
        "waiting" -> Color(0xFFF59E0B)
        "ratelimited", "rate_limited" -> Color(0xFFEF4444)
        else -> Color(0xFF94A3B8)
    }

/**
 * Round-bezel tap popup.
 *
 * v0.35.8 layout: title + state + last-line context occupy the centre
 * column; the microphone button anchors to the **right edge** so the
 * thumb lands on it without crossing the screen, and the Send chip
 * appears on the left edge once a transcript is staged. ✕ dismiss
 * stays top-right inside the safe area.
 *
 * Voice flow (v0.35.8): mic toggles between record and stop. While
 * recording, the icon turns red and the label below changes to
 * "Listening…". On stop, audio bytes ship to the phone via
 * `/datawatch/audio`; while waiting for the transcript the centre
 * shows a small `…transcribing` line. Reply lands on
 * `/datawatch/transcript` and populates the transcript box for the
 * user to validate before tapping Send.
 */
@Composable
private fun SessionDetailPopup(
    session: WearSessionCountsViewModel.SessionItem,
    transcript: String,
    recording: Boolean,
    transcribing: Boolean,
    onRecord: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE6000000)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp)
                .background(
                    MaterialTheme.colors.surface,
                    androidx.compose.foundation.shape.CircleShape,
                )
                .border(
                    1.5.dp,
                    MaterialTheme.colors.primary.copy(alpha = 0.55f),
                    androidx.compose.foundation.shape.CircleShape,
                )
                .padding(horizontal = 16.dp, vertical = 18.dp),
        ) {
            // Centre content stack — title, state, last-line, transcript.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Text(
                        "✕",
                        style = MaterialTheme.typography.caption1,
                        color = MaterialTheme.colors.onSurfaceVariant,
                        modifier = Modifier
                            .clickable(onClick = onDismiss)
                            .padding(4.dp),
                    )
                }
                Text(
                    session.title,
                    style = MaterialTheme.typography.title3,
                    color = MaterialTheme.colors.primary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                )
                Text(
                    session.stateName.lowercase(),
                    style = MaterialTheme.typography.caption2,
                    color = sessionBadgeColor(session.stateName),
                )
                if (session.lastLine.isNotBlank()) {
                    Text(
                        session.lastLine,
                        modifier = Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.caption2,
                        color = MaterialTheme.colors.onSurface,
                        maxLines = 4,
                    )
                }
                when {
                    recording ->
                        Text(
                            "Listening…",
                            modifier = Modifier.padding(top = 6.dp),
                            style = MaterialTheme.typography.caption1,
                            color = Color(0xFFEF4444),
                        )
                    transcribing ->
                        Text(
                            "…transcribing",
                            modifier = Modifier.padding(top = 6.dp),
                            style = MaterialTheme.typography.caption1,
                            color = MaterialTheme.colors.onSurfaceVariant,
                        )
                    transcript.isNotBlank() ->
                        Text(
                            "“$transcript”",
                            modifier = Modifier.padding(top = 6.dp),
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.primary,
                            maxLines = 3,
                        )
                }
            }
            // Mic button anchored to the right edge of the safe area.
            // Stop icon when recording, mic glyph when idle. Tinting
            // tells the user at a glance which state they're in.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 0.dp),
            ) {
                Text(
                    if (recording) "■" else "🎤",
                    style = MaterialTheme.typography.title2,
                    color =
                        if (recording) Color(0xFFEF4444)
                        else MaterialTheme.colors.primary,
                    modifier = Modifier
                        .background(
                            (
                                if (recording) Color(0xFFEF4444)
                                else MaterialTheme.colors.primary
                            ).copy(alpha = 0.18f),
                            androidx.compose.foundation.shape.CircleShape,
                        )
                        .clickable(onClick = onRecord)
                        .padding(10.dp),
                )
            }
            // Send chip anchored to the left edge once a transcript
            // is staged. Hidden during record / transcribe / no-text.
            if (transcript.isNotBlank() && !recording && !transcribing) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 0.dp),
                ) {
                    Text(
                        "Send",
                        style = MaterialTheme.typography.button,
                        color = MaterialTheme.colors.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .background(
                                MaterialTheme.colors.primary.copy(alpha = 0.2f),
                                androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            )
                            .clickable(onClick = onConfirm)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
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
        // GPU — published by the phone's WearSyncService.publishStats
        // starting v0.35.4. Absent on older phone builds, in which
        // case the Monitor page hides the GPU gauge.
        val gpuUtilPct: Double = 0.0,
        val gpuTempC: Double = 0.0,
        val gpuMemUsedMb: Long = 0L,
        val gpuMemTotalMb: Long = 0L,
        val gpuName: String = "",
        // Per-session list (v0.35.5) — sourced from
        // /datawatch/sessions. Sorted by last-activity desc, capped
        // at SESSIONS_PUBLISH_LIMIT on the phone side.
        val sessions: List<SessionItem> = emptyList(),
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

        // Percentages used by the round-bezel gauge rings. 0..100 with
        // 0f sentinel when the field isn't available. Caller decides
        // whether to hide the gauge when the backing data is absent.
        public fun cpuPctFor(maxCores: Int = cpuCores): Float =
            when {
                maxCores > 0 && cpuLoad1 > 0 ->
                    ((cpuLoad1 / maxCores.toDouble()) * 100.0)
                        .coerceIn(0.0, 100.0)
                        .toFloat()
                cpuLoad1 in 0.0..100.0 -> cpuLoad1.toFloat()
                else -> 0f
            }
        public fun memPct(): Float =
            if (memTotal > 0) ((memUsed.toDouble() / memTotal.toDouble()) * 100.0).toFloat() else 0f
        public fun diskPct(): Float =
            if (diskTotal > 0) ((diskUsed.toDouble() / diskTotal.toDouble()) * 100.0).toFloat() else 0f
        public fun gpuPct(): Float = gpuUtilPct.coerceIn(0.0, 100.0).toFloat()
        public fun hasGpu(): Boolean = gpuName.isNotBlank() || gpuUtilPct > 0 || gpuMemTotalMb > 0
    }

    public data class SessionItem(
        val id: String,
        val title: String,
        val backend: String,
        val stateName: String,
        val lastLine: String,
    )

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
                            SESSIONS_PATH ->
                                applySessions(DataMapItem.fromDataItem(item).dataMap)
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
                        SESSIONS_PATH ->
                            applySessions(DataMapItem.fromDataItem(event.dataItem).dataMap)
                    }
                }
            }
        } finally {
            buffer.release()
        }
    }

    private fun applySessions(map: DataMap) {
        val ids = map.getStringArray("ids") ?: emptyArray()
        val titles = map.getStringArray("titles") ?: emptyArray()
        val backends = map.getStringArray("backends") ?: emptyArray()
        val states = map.getStringArray("states") ?: emptyArray()
        val lastLines = map.getStringArray("lastLines") ?: emptyArray()
        val items = ids.indices.map { i ->
            SessionItem(
                id = ids[i],
                title = titles.getOrNull(i).orEmpty(),
                backend = backends.getOrNull(i).orEmpty(),
                stateName = states.getOrNull(i).orEmpty(),
                lastLine = lastLines.getOrNull(i).orEmpty(),
            )
        }
        _state.value = _state.value.copy(sessions = items)
    }

    /**
     * Send a voice-reply to the phone for forwarding to the active
     * server's send_input hub. Payload format is "sessionId\ntext".
     */
    public fun sendReply(sessionId: String, text: String) {
        if (sessionId.isBlank() || text.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val body = "$sessionId\n$text".toByteArray(Charsets.UTF_8)
                val nodes: List<Node> = nodeClient.connectedNodes.await()
                nodes.forEach { node ->
                    messageClient.sendMessage(node.id, REPLY_PATH, body).await()
                }
            }
        }
    }

    /**
     * Ship raw audio bytes to the phone for Whisper transcription.
     * Payload layout: `sessionId` UTF-8 + `\n` (0x0A) + raw audio
     * bytes. The phone's `WearSyncService` parses by the first
     * newline so the audio body isn't UTF-8 decoded. Reply lands on
     * [TRANSCRIPT_PATH] handled by [WearMainActivity]'s listener.
     */
    public fun sendAudio(sessionId: String, audio: ByteArray) {
        if (sessionId.isBlank() || audio.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val sidBytes = sessionId.toByteArray(Charsets.UTF_8)
                val body = ByteArray(sidBytes.size + 1 + audio.size)
                System.arraycopy(sidBytes, 0, body, 0, sidBytes.size)
                body[sidBytes.size] = '\n'.code.toByte()
                System.arraycopy(audio, 0, body, sidBytes.size + 1, audio.size)
                val nodes: List<Node> = nodeClient.connectedNodes.await()
                nodes.forEach { node ->
                    messageClient.sendMessage(node.id, AUDIO_PATH, body).await()
                }
            }
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
            gpuUtilPct = map.getDouble("gpuUtilPct", 0.0),
            gpuTempC = map.getDouble("gpuTempC", 0.0),
            gpuMemUsedMb = map.getLong("gpuMemUsedMb", 0L),
            gpuMemTotalMb = map.getLong("gpuMemTotalMb", 0L),
            gpuName = map.getString("gpuName", ""),
        )
    }

    public companion object {
        public const val COUNTS_PATH: String = "/datawatch/counts"
        public const val PROFILES_PATH: String = "/datawatch/profiles"
        public const val STATS_PATH: String = "/datawatch/stats"
        public const val SESSIONS_PATH: String = "/datawatch/sessions"
        public const val SET_ACTIVE_PATH: String = "/datawatch/setActive"
        public const val REPLY_PATH: String = "/datawatch/reply"
        public const val AUDIO_PATH: String = "/datawatch/audio"
        public const val TRANSCRIPT_PATH: String = "/datawatch/transcript"
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
