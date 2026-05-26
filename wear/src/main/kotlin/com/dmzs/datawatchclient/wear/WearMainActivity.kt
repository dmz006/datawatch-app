package com.dmzs.datawatchclient.wear

import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.dmzs.datawatchclient.wear.sync.WearSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.foundation.rememberSwipeToDismissBoxState
import androidx.wear.compose.material.SwipeToDismissBox
import androidx.wear.compose.material.Text
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
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colors = datawatchWearColors()) { WearRoot() }
        }
    }

    override fun onResume() {
        super.onResume()
        // S10-5: request a fresh dashboard push from the phone whenever
        // the user opens the Wear app. This replaces the 15 s polling
        // loop that was removed from WearSyncService.
        activityScope.launch {
            WearSyncManager.requestDashboard(this@WearMainActivity)
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
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val sessionScrollState = rememberScrollState()
    val automataScrollState = rememberScrollState()

    // Show splash on every launch — even warm restarts where the ViewModel
    // is already loaded. 800ms on warm restart (ViewModel pre-loaded),
    // or until ViewModel loading completes (whichever is longer — the
    // ViewModel already enforces MIN_SPLASH_MS = 1400ms on cold starts).
    var warmSplashDone by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(800L)
        warmSplashDone = true
    }
    if (!warmSplashDone || state.loading) {
        WearSplash()
        return
    }

    // Pager is now composed — safe to request rotary focus.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

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
    // v0.42.3 — Sessions page filter (Wait default, then Run, Total).
    var sessionFilter by remember { mutableStateOf(SessionFilter.Wait) }
    // v0.42.9 — full last_response buffer keyed by sessionId, populated
    // from the phone's /datawatch/sessionDetail MessageClient reply.
    // The popup prefers this over `session.lastResponse` (which is
    // capped at 4000 chars in the DataLayer broadcast budget).
    var fullDetailBodies by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
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
                    when (ev.path) {
                        WearSessionCountsViewModel.TRANSCRIPT_PATH -> {
                            val body =
                                runCatching { String(ev.data, Charsets.UTF_8) }
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
                        WearSessionCountsViewModel.SESSION_DETAIL_PATH -> {
                            // v0.42.9 — full last_response body for the
                            // session the user just tapped. Stored in
                            // the per-id map; the popup picks it up on
                            // recomposition.
                            val body =
                                runCatching { String(ev.data, Charsets.UTF_8) }
                                    .getOrNull().orEmpty()
                            val (sid, text) =
                                body.split("\n", limit = 2).let { p ->
                                    p.firstOrNull().orEmpty() to
                                        p.getOrNull(1).orEmpty()
                                }
                            if (sid.isNotEmpty()) {
                                Log.d(
                                    "WearMain",
                                    "sessionDetail recv sid=$sid bytes=${text.length}",
                                )
                                fullDetailBodies = fullDetailBodies + (sid to text)
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

    // BL303-W5 — TextToSpeech for spoken voice query replies.
    val tts = remember {
        var ref: android.speech.tts.TextToSpeech? = null
        ref = android.speech.tts.TextToSpeech(context) { status ->
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                ref?.language = java.util.Locale.US
            }
        }
        ref!!
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { tts.stop(); tts.shutdown() }
    }

    // Persistent listener for VOICE_REPLY_PATH — speaks the reply on arrival.
    androidx.compose.runtime.DisposableEffect(Unit) {
        val voiceReplyListener = com.google.android.gms.wearable.MessageClient
            .OnMessageReceivedListener { ev ->
                if (ev.path == WearSessionCountsViewModel.VOICE_REPLY_PATH) {
                    val text = runCatching { String(ev.data, Charsets.UTF_8) }
                        .getOrNull().orEmpty()
                    if (text.isNotBlank()) {
                        tts.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "dw-voice-reply")
                    }
                }
            }
        com.google.android.gms.wearable.Wearable.getMessageClient(context)
            .addListener(voiceReplyListener)
        onDispose {
            com.google.android.gms.wearable.Wearable.getMessageClient(context)
                .removeListener(voiceReplyListener)
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
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .onRotaryScrollEvent { event ->
                        val delta = if (event.verticalScrollPixels != 0f) event.verticalScrollPixels
                                    else event.horizontalScrollPixels
                        coroutineScope.launch {
                            when (pagerState.currentPage) {
                                2 -> sessionScrollState.scrollBy(delta)
                                3 -> automataScrollState.scrollBy(delta)
                                else -> if (delta > 0) pagerState.animateScrollToPage((pagerState.currentPage + 1).coerceAtMost(3))
                                        else if (delta < 0) pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0))
                            }
                        }
                        true
                    }
                    .focusRequester(focusRequester)
                    .focusable(),
            ) { page ->
                when (page) {
                    0 -> StatusPage(state)
                    1 -> MonitorPage(
                        state,
                        onSelectServer = { id -> vm.requestActiveServer(id) },
                        onVoiceQuery = { vm.sendVoiceQuery("status") },
                    )
                    2 ->
                        SessionsPage(
                            state = state,
                            filter = sessionFilter,
                            onFilterChange = { sessionFilter = it },
                            onSessionTap = { item ->
                                openSession = item
                                fullDetailBodies = fullDetailBodies - item.id
                                vm.refreshSession(item.id)
                            },
                            onStopSession = { sessionId -> vm.sendStopSession(sessionId) },
                            scrollState = sessionScrollState,
                        )
                    3 ->
                        AutomataPage(
                            state = state,
                            scrollState = automataScrollState,
                            onPrdAction = { id, action, reason -> vm.sendPrdAction(id, action, reason) },
                        )
                    else -> StatusPage(state)
                }
            }
            PagerDots(pagerState.currentPage, 4, Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp))
            // v0.42.3 — re-resolve the open session against the
            // latest published list so the popup shows the freshest
            // lastResponse body the moment the phone republishes (in
            // response to the refreshSession round-trip from tap).
            // Without this, `openSession` captured the SessionItem at
            // tap time and Compose never saw the post-refresh values.
            // Falls back to the captured item if the session was
            // dropped from the published window between tap and
            // republish.
            val popupSession =
                openSession?.let { o ->
                    state.sessions.firstOrNull { it.id == o.id } ?: o
                }
            popupSession?.let { item ->
                SessionDetailPopup(
                    session = item,
                    fullBody = fullDetailBodies[item.id],
                    voice =
                        VoiceUiState(
                            transcript = pendingTranscript,
                            recording = recording,
                            transcribing = transcribing,
                        ),
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
                    onDismiss = {
                        runCatching { recorder.cancel() }
                        pendingTranscript = ""
                        recording = false
                        transcribing = false
                        openSession = null
                    },
                    onQuickReply = { text ->
                        vm.sendReply(item.id, text)
                        openSession = null
                    },
                    onStop = {
                        vm.sendStopSession(item.id)
                        openSession = null
                    },
                )
                // Transcript review popup overlays the session popup once
                // transcription completes. User reads the text and chooses
                // Cancel (discard) or Send.
                if (pendingTranscript.isNotBlank() && !recording && !transcribing) {
                    TranscriptReviewPopup(
                        transcript = pendingTranscript,
                        onCancel = {
                            pendingTranscript = ""
                        },
                        onSend = {
                            vm.sendReply(item.id, pendingTranscript)
                            pendingTranscript = ""
                            openSession = null
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PagerDots(
    selected: Int,
    count: Int,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(count) { i ->
            val c = if (i == selected) MaterialTheme.colors.primary else MaterialTheme.colors.onSurfaceVariant
            val dot = if (i == selected) 6 else 4
            Box(
                modifier =
                    Modifier
                        .padding(1.dp)
                        .size(dot.dp)
                        .background(c, shape = androidx.compose.foundation.shape.CircleShape),
            )
        }
    }
}

@Composable
private fun PageScaffold(
    title: String,
    scrollState: ScrollState = rememberScrollState(),
    content: @Composable () -> Unit,
) {
    // Per user 2026-04-24 "the wear app should have borders around each
    // screen like cards" + "the watch is a samsung watch, the cards
    // should be round to match bezel" — each pager page renders inside
    // a CIRCULAR bordered card that follows the Samsung Galaxy Watch's
    // round bezel. Dark surface with teal primary-color border at
    // ~45% alpha. Inner padding is diamond-shaped (more horizontal,
    // less vertical) because a circular viewport cuts the corners
    // anyway — content stays within the safe area.
    //
    // v0.42.11 user direction 2026-04-29: "all pages on watch should
    // have circle clipping" — `Modifier.clip(cardShape)` on the inner
    // Box clips the verticalScroll Column's children (long lists,
    // multi-line text) to the round bezel so content doesn't bleed
    // into the rectangular face corners.
    //
    // v0.42.11 — pass an empty title to suppress the page header;
    // the About page does this since its body already opens with the
    // datawatch logo + name.
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        val cardShape = androidx.compose.foundation.shape.CircleShape
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clip(cardShape)
                    .background(MaterialTheme.colors.surface, shape = cardShape)
                    .border(
                        width = 1.5.dp,
                        color = MaterialTheme.colors.primary.copy(alpha = 0.45f),
                        shape = cardShape,
                    )
                    .padding(horizontal = 28.dp, vertical = 20.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (title.isNotBlank()) {
                    Text(
                        title,
                        style = MaterialTheme.typography.title3,
                        color = MaterialTheme.colors.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                content()
            }
        }
    }
}

@Composable
private fun WearSplash() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_dw_eye),
            contentDescription = stringResource(R.string.wear_splash_cd),
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
        )
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(bottom = 20.dp)
                    .size(18.dp),
                indicatorColor = MaterialTheme.colors.primary,
                trackColor = MaterialTheme.colors.onSurface.copy(alpha = 0.1f),
            )
        }
    }
}

/**
 * BL303-W2 — Primary status screen. Progress ring + current task +
 * sprint breadcrumb + health dots. First page in the pager.
 */
@Composable
private fun StatusPage(state: WearSessionCountsViewModel.UiState) {
    val runColor   = Color(0xFF10B981)
    val waitColor  = Color(0xFFF59E0B)
    val autoColor  = Color(0xFFA855F7)
    val blockColor = Color(0xFFEF4444)
    val dimColor   = MaterialTheme.colors.onSurfaceVariant

    val runningAutomata = state.prds.count { it.status.lowercase() == "running" }
    val reviewCount     = state.prds.count {
        it.status.lowercase() in listOf("needs_review", "revisions_asked")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background),
    ) {
        // Full-width block stripe at top when guardrail fires
        if (state.guardrailBlock) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(blockColor)
                    .align(Alignment.TopCenter),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            // Server name header
            if (state.serverName.isNotBlank()) {
                Text(
                    state.serverName,
                    style = MaterialTheme.typography.caption2,
                    color = dimColor,
                    textAlign = TextAlign.Center,
                )
            }

            // Running — always shown, 0 included
            GlanceStat(
                count = state.running,
                label = "RUNNING",
                activeColor = runColor,
                dimColor = dimColor,
                alwaysBright = true,
            )

            // Waiting — amber and bold when non-zero
            GlanceStat(
                count = state.waiting,
                label = "WAITING",
                activeColor = waitColor,
                dimColor = dimColor,
                alwaysBright = false,
            )

            // Automata running
            GlanceStat(
                count = runningAutomata,
                label = "AUTOMATA",
                activeColor = autoColor,
                dimColor = dimColor,
                alwaysBright = false,
            )

            // Review needed — only shown when > 0
            if (reviewCount > 0) {
                GlanceStat(
                    count = reviewCount,
                    label = "FOR REVIEW",
                    activeColor = blockColor,
                    dimColor = dimColor,
                    alwaysBright = true,
                )
            }

            // Guardrail block summary
            if (state.guardrailBlock && state.blockSummary.isNotBlank()) {
                Text(
                    "⚠ ${state.blockSummary.take(44)}",
                    style = MaterialTheme.typography.caption1,
                    color = blockColor,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            } else if (state.currentTask.isNotBlank()) {
                // Active task snippet at bottom
                Text(
                    state.currentTask.take(52),
                    style = MaterialTheme.typography.caption2,
                    color = dimColor,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun GlanceStat(
    count: Int,
    label: String,
    activeColor: Color,
    dimColor: Color,
    alwaysBright: Boolean,
) {
    val isActive = alwaysBright || count > 0
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            count.toString(),
            style = MaterialTheme.typography.title1,
            fontWeight = FontWeight.Bold,
            color = if (isActive) activeColor else dimColor,
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.caption1,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isActive) activeColor.copy(alpha = 0.85f) else dimColor,
        )
    }
}

@Composable
private fun AutomataPage(
    state: WearSessionCountsViewModel.UiState,
    scrollState: ScrollState,
    onPrdAction: (String, String, String) -> Unit,
) {
    val blockColor = Color(0xFFEF4444)
    val runColor = Color(0xFF10B981)
    val reviewColor = Color(0xFFF59E0B)
    val revisionColor = Color(0xFFA855F7)
    val dimColor = MaterialTheme.colors.onSurfaceVariant

    var selectedPrd by remember { mutableStateOf<WearSessionCountsViewModel.PrdItem?>(null) }

    // Sort: needs_review first (urgent), then running by gravity, then others
    val sorted = state.prds.sortedWith(
        compareByDescending<WearSessionCountsViewModel.PrdItem> {
            when (it.status.lowercase()) {
                "needs_review", "revisions_asked" -> 2
                "running" -> 1
                else -> 0
            }
        }.thenByDescending { it.gravityScore() }
    )

    val runningCount = state.prds.count { it.status.lowercase() == "running" }
    val reviewCount = state.prds.count { it.status.lowercase() in setOf("needs_review", "revisions_asked") }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
        PageScaffold(title = "", scrollState = scrollState) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("AUTOMATA", style = MaterialTheme.typography.title3,
                    color = MaterialTheme.colors.primary, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (runningCount > 0) Text("${runningCount}R", style = MaterialTheme.typography.caption2,
                        color = runColor, fontWeight = FontWeight.Bold)
                    if (reviewCount > 0) Text("${reviewCount}✓", style = MaterialTheme.typography.caption2,
                        color = reviewColor, fontWeight = FontWeight.Bold)
                }
            }

            if (sorted.isEmpty()) {
                Text("No active automata", style = MaterialTheme.typography.body2,
                    color = dimColor, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            } else {
                sorted.forEach { prd ->
                    val statusColor = when (prd.status.lowercase()) {
                        "running" -> if (prd.blockedCount > 0) blockColor else runColor
                        "needs_review" -> reviewColor
                        "revisions_asked" -> revisionColor
                        "approved" -> Color(0xFF3B82F6) // blue
                        else -> dimColor
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .background(statusColor.copy(alpha = 0.12f),
                                androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                            .clickable { selectedPrd = prd }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Mini progress arc
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(32.dp)) {
                            CircularProgressIndicator(
                                progress = prd.progress.coerceIn(0f, 1f),
                                modifier = Modifier.size(32.dp), strokeWidth = 3.dp,
                                indicatorColor = statusColor,
                                trackColor = statusColor.copy(alpha = 0.18f),
                            )
                            Text("${(prd.progress * 100).toInt()}",
                                style = MaterialTheme.typography.caption3, color = dimColor)
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(prd.title, style = MaterialTheme.typography.caption1,
                                color = MaterialTheme.colors.onSurface, maxLines = 1)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(prd.status.replace("_", " "), style = MaterialTheme.typography.caption3,
                                    color = statusColor)
                                if (prd.blockedCount > 0)
                                    Text("${prd.blockedCount}⚠", style = MaterialTheme.typography.caption3,
                                        color = blockColor)
                                if (prd.runningHours > 0f)
                                    Text("%.1fh".format(prd.runningHours),
                                        style = MaterialTheme.typography.caption3, color = dimColor)
                            }
                        }
                        // Quick action glyph for actionable states
                        when (prd.status.lowercase()) {
                            "needs_review", "revisions_asked" ->
                                Text("›", style = MaterialTheme.typography.title3, color = reviewColor)
                            "running" ->
                                Text("›", style = MaterialTheme.typography.title3, color = runColor)
                        }
                    }
                }
            }
        }

        // PRD detail overlay
        selectedPrd?.let { prd ->
            AutomataDetailOverlay(
                prd = prd,
                onDismiss = { selectedPrd = null },
                onAction = { action, reason ->
                    onPrdAction(prd.id, action, reason)
                    selectedPrd = null
                },
            )
        }
    }
}

@Composable
private fun AutomataDetailOverlay(
    prd: WearSessionCountsViewModel.PrdItem,
    onDismiss: () -> Unit,
    onAction: (action: String, reason: String) -> Unit,
) {
    val blockColor = Color(0xFFEF4444)
    val runColor = Color(0xFF10B981)
    val reviewColor = Color(0xFFF59E0B)
    val revisionColor = Color(0xFFA855F7)
    val dimColor = MaterialTheme.colors.onSurfaceVariant

    val statusColor = when (prd.status.lowercase()) {
        "running" -> if (prd.blockedCount > 0) blockColor else runColor
        "needs_review" -> reviewColor
        "revisions_asked" -> revisionColor
        "approved" -> Color(0xFF3B82F6)
        else -> dimColor
    }

    Box(
        modifier = Modifier.fillMaxSize()
            .background(Color(0xF0000000))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(6.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(MaterialTheme.colors.surface, androidx.compose.foundation.shape.CircleShape)
                .border(1.5.dp, statusColor.copy(alpha = 0.6f), androidx.compose.foundation.shape.CircleShape)
                .padding(horizontal = 18.dp, vertical = 16.dp)
                .clickable { /* consume click to prevent dismiss */ },
        ) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Dismiss X
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text("✕", style = MaterialTheme.typography.caption1, color = dimColor,
                        modifier = Modifier.clickable { onDismiss() }.padding(2.dp))
                }

                // Progress arc + title
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp).padding(bottom = 4.dp)) {
                    CircularProgressIndicator(progress = prd.progress.coerceIn(0f, 1f),
                        modifier = Modifier.size(48.dp), strokeWidth = 4.dp,
                        indicatorColor = statusColor, trackColor = statusColor.copy(alpha = 0.18f))
                    Text("${(prd.progress * 100).toInt()}%", style = MaterialTheme.typography.caption2, color = dimColor)
                }

                Text(prd.title, style = MaterialTheme.typography.caption1,
                    color = MaterialTheme.colors.onSurface, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center, maxLines = 2,
                    modifier = Modifier.padding(bottom = 2.dp))

                Text(prd.status.replace("_", " "), style = MaterialTheme.typography.caption2,
                    color = statusColor, modifier = Modifier.padding(bottom = 2.dp))

                if (prd.sprintName.isNotBlank()) {
                    Text(prd.sprintName, style = MaterialTheme.typography.caption3,
                        color = dimColor, modifier = Modifier.padding(bottom = 2.dp))
                }

                if (prd.blockedCount > 0) {
                    Text("${prd.blockedCount} blocked", style = MaterialTheme.typography.caption2,
                        color = blockColor, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 4.dp))
                }

                Spacer(Modifier.height(4.dp))

                // Action buttons based on status gate
                when (prd.status.lowercase()) {
                    "needs_review", "revisions_asked" -> {
                        // Approve
                        ActionChip("✓ Approve", Color(0xFF22C55E)) { onAction("approve", "") }
                        Spacer(Modifier.height(4.dp))
                        // Reject
                        ActionChip("✕ Reject", blockColor) { onAction("reject", "rejected on watch") }
                        Spacer(Modifier.height(4.dp))
                        // Request Revision
                        ActionChip("↩ Revise", revisionColor) { onAction("request_revision", "revision requested on watch") }
                    }
                    "running" -> {
                        ActionChip("✕ Cancel", blockColor) { onAction("cancel", "") }
                    }
                    "approved" -> {
                        ActionChip("▶ Instantiate", Color(0xFF3B82F6)) { onAction("instantiate", "") }
                        Spacer(Modifier.height(4.dp))
                        ActionChip("✕ Cancel", blockColor) { onAction("cancel", "") }
                    }
                    "decomposing", "planning" -> {
                        Text("Decomposing…", style = MaterialTheme.typography.caption3, color = dimColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionChip(label: String, color: Color, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.button,
        color = color,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .background(color.copy(alpha = 0.18f), androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun MonitorPage(
    state: WearSessionCountsViewModel.UiState,
    onSelectServer: (String) -> Unit = {},
    onVoiceQuery: () -> Unit = {},
) {
    if (state.loading) { WearSplash(); return }
    var detailServer by remember { mutableStateOf<WearSessionCountsViewModel.AllServerStat?>(null) }
    Box(Modifier.fillMaxSize()) {
        PageScaffold(stringResource(R.string.wear_page_monitor)) {
            if (state.pairedServer.isEmpty()) {
                Text(
                    stringResource(R.string.wear_monitor_open_phone),
                    modifier = Modifier.padding(top = 10.dp),
                    style = MaterialTheme.typography.body2,
                )
                return@PageScaffold
            }
            if (state.allServerStats.size > 1) {
                MonitorMultiServerSection(state, onSelectServer) { detailServer = it }
            } else {
                MonitorSingleServerSection(state) { detailServer = it }
            }
            // BL303-W5: voice status query quick button
            androidx.compose.foundation.layout.Spacer(Modifier.height(6.dp))
            Button(
                onClick = onVoiceQuery,
                modifier = Modifier.fillMaxWidth(0.7f),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.surface,
                    contentColor = MaterialTheme.colors.primary,
                ),
            ) {
                Text(
                    stringResource(R.string.wear_voice_ask_status),
                    style = MaterialTheme.typography.caption2,
                )
            }
        }
        detailServer?.let { s ->
            ServerDetailOverlay(
                stat = s,
                isActive = s.name == state.serverName,
                activeState = state,
                onDismiss = { detailServer = null },
            )
        }
    }
}

@Composable
private fun MonitorMultiServerSection(
    state: WearSessionCountsViewModel.UiState,
    onSelectServer: (String) -> Unit,
    onShowDetail: (WearSessionCountsViewModel.AllServerStat) -> Unit,
) {
    state.allServerStats.forEach { s ->
        val isActive = s.name == state.serverName
        val profileId = state.profiles.firstOrNull { it.second == s.name }?.first
        val prefix = if (isActive) "✓" else if (s.online) "●" else "○"
        val nameColor = when {
            isActive -> MaterialTheme.colors.primary
            s.online -> MaterialTheme.colors.onSurface
            else -> MaterialTheme.colors.error
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .background(
                    color = if (isActive) MaterialTheme.colors.surface else Color.Transparent,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                )
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "$prefix ${s.name}",
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = profileId != null && !isActive) { onSelectServer(profileId!!) },
                style = MaterialTheme.typography.caption2,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                color = nameColor,
            )
            if (s.online) {
                Row(
                    modifier = Modifier.clickable { onShowDetail(s) },
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    GaugeRing("CPU", s.cpuPct, "${s.cpuPct.toInt()}%", sizeDp = 38)
                    GaugeRing("MEM", s.memPct, "${s.memPct.toInt()}%", sizeDp = 38)
                }
            }
        }
    }
}

@Composable
private fun MonitorSingleServerSection(
    state: WearSessionCountsViewModel.UiState,
    onShowDetail: (WearSessionCountsViewModel.AllServerStat) -> Unit,
) {
    val activeStat = WearSessionCountsViewModel.AllServerStat(
        name = state.serverName,
        cpuPct = state.cpuPctFor(),
        memPct = state.memPct(),
        sessionsTotal = state.total,
        online = state.pairedServer.isNotEmpty(),
    )
    Text(
        "● ${state.serverName}",
        modifier = Modifier.padding(top = 2.dp),
        style = MaterialTheme.typography.caption1,
        color = MaterialTheme.colors.primary,
    )
    Box(modifier = Modifier.clickable { onShowDetail(activeStat) }) {
        MonitorGaugeGrid(state)
    }
    if (state.uptimeSeconds > 0) {
        Text(
            "up ${state.uptimeText()}",
            modifier = Modifier.padding(top = 4.dp),
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

/** Full-screen overlay showing gauge details for a tapped server. */
@Composable
private fun ServerDetailOverlay(
    stat: WearSessionCountsViewModel.AllServerStat,
    isActive: Boolean,
    activeState: WearSessionCountsViewModel.UiState,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "● ${stat.name}",
                style = MaterialTheme.typography.caption1,
                color = MaterialTheme.colors.primary,
            )
            if (isActive) {
                MonitorGaugeGrid(activeState)
                if (activeState.uptimeSeconds > 0) {
                    Text(
                        "up ${activeState.uptimeText()}",
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.caption2,
                        color = MaterialTheme.colors.onSurfaceVariant,
                    )
                }
                if (activeState.hasGpu() && activeState.gpuMemTotalMb > 0) {
                    Text(
                        "vram ${activeState.gpuMemUsedMb}/${activeState.gpuMemTotalMb}M",
                        style = MaterialTheme.typography.caption3,
                        color = MaterialTheme.colors.onSurfaceVariant,
                    )
                }
            } else {
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GaugeRing("CPU", stat.cpuPct, "${stat.cpuPct.toInt()}%")
                    GaugeRing("MEM", stat.memPct, "${stat.memPct.toInt()}%")
                }
                if (stat.sessionsTotal > 0) {
                    Text(
                        "${stat.sessionsTotal} sessions",
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.caption2,
                        color = MaterialTheme.colors.onSurfaceVariant,
                    )
                }
            }
            Text(
                "✕ tap to close",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.caption3,
                color = MaterialTheme.colors.onSurfaceVariant,
            )
        }
    }
}

/**
 * v0.42.9 — extracted gauge grid (CPU/MEM, DISK/GPU) so MonitorPage
 * stays under detekt's LongMethod cap.
 */
@Composable
private fun MonitorGaugeGrid(state: WearSessionCountsViewModel.UiState) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        GaugeRing(label = "CPU", pct = state.cpuPctFor(), center = cpuCenterText(state))
        GaugeRing(
            label = "MEM",
            pct = state.memPct(),
            center = if (state.memTotal > 0) "%.0f%%".format(state.memPct()) else "—",
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        GaugeRing(
            label = "DISK",
            pct = state.diskPct(),
            center = if (state.diskTotal > 0) "%.0f%%".format(state.diskPct()) else "—",
        )
        if (state.hasGpu()) {
            GaugeRing(
                label = "GPU",
                pct = state.gpuPct(),
                center = if (state.gpuPct() > 0) "%.0f%%".format(state.gpuPct()) else "—",
            )
        } else {
            Box(modifier = Modifier.size(GAUGE_SIZE_DP.dp))
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
    sizeDp: Int = GAUGE_SIZE_DP,
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
            modifier = Modifier.size(sizeDp.dp),
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
    filter: SessionFilter,
    onFilterChange: (SessionFilter) -> Unit,
    onSessionTap: (WearSessionCountsViewModel.SessionItem) -> Unit,
    onStopSession: (String) -> Unit = {},
    scrollState: ScrollState = rememberScrollState(),
) {
    PageScaffold(stringResource(R.string.wear_page_sessions), scrollState = scrollState) {
        if (state.pairedServer.isEmpty()) {
            Text(
                "—",
                modifier = Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.body2,
            )
            return@PageScaffold
        }
        SessionsFilterRow(state, filter, onFilterChange)
        val filtered =
            when (filter) {
                SessionFilter.Wait ->
                    state.sessions.filter { it.stateName.equals("Waiting", ignoreCase = true) }
                SessionFilter.Run ->
                    state.sessions.filter { it.stateName.equals("Running", ignoreCase = true) }
                SessionFilter.Total -> state.sessions
            }
        if (filtered.isEmpty()) {
            Text(
                if (state.sessions.isEmpty()) stringResource(R.string.wear_sessions_empty) else when (filter) {
                SessionFilter.Wait -> stringResource(R.string.wear_sessions_no_wait)
                SessionFilter.Run -> stringResource(R.string.wear_sessions_no_run)
                SessionFilter.Total -> stringResource(R.string.wear_sessions_no_total)
            },
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.onSurfaceVariant,
            )
            return@PageScaffold
        }
        // When showing all sessions, split into named groups so the list is scannable.
        val groups: List<Pair<String?, List<WearSessionCountsViewModel.SessionItem>>> =
            if (filter == SessionFilter.Total) {
                val running = filtered.filter { it.stateName.equals("running", ignoreCase = true) }
                val waiting = filtered.filter {
                    it.stateName.equals("waiting", ignoreCase = true) ||
                        it.stateName.equals("waiting_input", ignoreCase = true)
                }
                val other = filtered - running.toSet() - waiting.toSet()
                buildList {
                    if (running.isNotEmpty()) add("RUNNING (${running.size})" to running)
                    if (waiting.isNotEmpty()) add("WAITING (${waiting.size})" to waiting)
                    if (other.isNotEmpty()) add("OTHER (${other.size})" to other)
                }
            } else {
                listOf(null to filtered)
            }

        groups.forEach { (header, items) ->
            if (header != null) {
                Text(
                    header,
                    style = MaterialTheme.typography.caption3,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                )
            }
            items.forEach { item -> SessionRow(item, onSessionTap, onStopSession = { onStopSession(item.id) }) }
        }
    }
}

@Composable
private fun SessionRow(
    item: WearSessionCountsViewModel.SessionItem,
    onTap: (WearSessionCountsViewModel.SessionItem) -> Unit,
    onStopSession: () -> Unit,
) {
    val badgeColor = sessionBadgeColor(item.stateName)
    val agoText = wearSessionAgo(item.lastActivity)
    val abbrev = backendAbbrev(item.backend)
    val bColor = backendColor(item.backend)
    val isWaiting = item.stateName.equals("waiting", ignoreCase = true) ||
        item.stateName.equals("waiting_input", ignoreCase = true)
    val isRunning = item.stateName.equals("running", ignoreCase = true)

    when {
        isWaiting -> {
            val swipeState = rememberSwipeToDismissBoxState()
            SwipeToDismissBox(
                onDismissed = { onTap(item) },
                state = swipeState,
            ) { isBackground ->
                if (isBackground) {
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .background(Color(0xFF1A3A1A), androidx.compose.foundation.shape.RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Text("🎤 Reply", style = MaterialTheme.typography.caption1,
                            color = Color(0xFF00E5A0),
                            modifier = Modifier.padding(end = 12.dp))
                    }
                } else {
                    SessionRowContent(item, badgeColor, abbrev, bColor, agoText, null, onTap)
                }
            }
        }
        isRunning -> {
            val swipeState = rememberSwipeToDismissBoxState()
            SwipeToDismissBox(
                onDismissed = { onStopSession() },
                state = swipeState,
            ) { isBackground ->
                if (isBackground) {
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .background(Color(0xFF3A1A1A), androidx.compose.foundation.shape.RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Text("✕ Stop", style = MaterialTheme.typography.caption1,
                            color = Color(0xFFEF4444),
                            modifier = Modifier.padding(end = 12.dp))
                    }
                } else {
                    val durationText = wearRunningDuration(item.startedAt)
                    val durationColor = runningDurationColor(item.startedAt)
                    SessionRowContent(item, badgeColor, abbrev, bColor, agoText, durationText to durationColor, onTap)
                }
            }
        }
        else -> SessionRowContent(item, badgeColor, abbrev, bColor, agoText, null, onTap)
    }
}

@Composable
private fun SessionRowContent(
    item: WearSessionCountsViewModel.SessionItem,
    badgeColor: Color,
    abbrev: String,
    backendColor: Color,
    agoText: String,
    duration: Pair<String, Color>?,
    onTap: (WearSessionCountsViewModel.SessionItem) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .background(badgeColor.copy(alpha = 0.18f), androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .clickable { onTap(item) }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("●", color = badgeColor, style = MaterialTheme.typography.caption1)
        Column(modifier = Modifier.padding(start = 6.dp).weight(1f)) {
            Text(
                item.title.take(28),
                style = MaterialTheme.typography.caption1,
                color = MaterialTheme.colors.onSurface,
                maxLines = 1,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${item.shortId}  $agoText",
                    style = MaterialTheme.typography.caption2,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    maxLines = 1,
                )
                if (duration != null && duration.first.isNotEmpty()) {
                    Text(duration.first, style = MaterialTheme.typography.caption2,
                        color = duration.second, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (abbrev.isNotEmpty()) {
            Text(
                abbrev,
                style = MaterialTheme.typography.caption3,
                color = backendColor,
                modifier = Modifier
                    .background(backendColor.copy(alpha = 0.15f), androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

/**
 * v0.42.3 — counts row repurposed as the filter selector. Tap
 * **wait** (default) to show waiting_input only, **run** to show
 * running only, **total** to show all sessions in the published
 * window including completed / killed. Order is wait / run / total
 * because wait is the most actionable surface (sessions blocked on
 * a reply).
 */
@Composable
private fun SessionsFilterRow(
    state: WearSessionCountsViewModel.UiState,
    filter: SessionFilter,
    onFilterChange: (SessionFilter) -> Unit,
) {
    Row(
        modifier = Modifier.padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CountTile(
            value = state.waiting,
            label = stringResource(R.string.wear_filter_wait),
            color = MaterialTheme.colors.secondary,
            selected = filter == SessionFilter.Wait,
            onClick = { onFilterChange(SessionFilter.Wait) },
        )
        CountTile(
            value = state.running,
            label = stringResource(R.string.wear_filter_run),
            color = MaterialTheme.colors.primary,
            selected = filter == SessionFilter.Run,
            onClick = { onFilterChange(SessionFilter.Run) },
        )
        CountTile(
            value = state.total,
            label = stringResource(R.string.wear_filter_total),
            color = MaterialTheme.colors.onSurface,
            selected = filter == SessionFilter.Total,
            onClick = { onFilterChange(SessionFilter.Total) },
        )
    }
}

/** Color accent per session state — datawatch dark palette (Sprint 32). */
private fun sessionBadgeColor(stateName: String): Color =
    when (stateName.lowercase()) {
        "running" -> Color(0xFF1DE9B6)
        "waiting", "waiting_input", "waiting-input" -> Color(0xFFFFB300)
        "error", "failed", "killed" -> Color(0xFFEF4444)
        "ratelimited", "rate_limited" -> Color(0xFFEF4444)
        else -> Color(0xFF94A3B8)
    }

/** Format lastActivity epoch ms as "Xm ago" / "Xh ago" / "Xd ago"; "" when zero. */
private fun wearSessionAgo(epochMs: Long): String {
    if (epochMs <= 0L) return ""
    val delta = System.currentTimeMillis() - epochMs
    return when {
        delta < 60_000L -> "<1m ago"
        delta < 3_600_000L -> "${delta / 60_000L}m ago"
        delta < 86_400_000L -> "${delta / 3_600_000L}h ago"
        else -> "${delta / 86_400_000L}d ago"
    }
}

/** Elapsed time since session started; "" when zero. "1h23m", "47m", "<1m". */
private fun wearRunningDuration(startedAtMs: Long): String {
    if (startedAtMs <= 0L) return ""
    val delta = System.currentTimeMillis() - startedAtMs
    val hours = delta / 3_600_000L
    val minutes = (delta % 3_600_000L) / 60_000L
    return when {
        hours > 0 -> "${hours}h${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "<1m"
    }
}

/** Color for running duration badge — amber >1h, red >2h. */
private fun runningDurationColor(startedAtMs: Long): Color {
    if (startedAtMs <= 0L) return Color(0xFF94A3B8)
    val delta = System.currentTimeMillis() - startedAtMs
    return when {
        delta >= 7_200_000L -> Color(0xFFEF4444)
        delta >= 3_600_000L -> Color(0xFFF59E0B)
        else -> Color(0xFF94A3B8)
    }
}

/** Shortened backend label for session rows — keeps abbreviation to ≤6 chars. */
private fun backendAbbrev(backend: String): String = when {
    backend.isBlank() -> ""
    backend.startsWith("claude") -> "claude"
    backend.startsWith("gpt-4o") -> "gpt4o"
    backend.startsWith("gpt-4") -> "gpt4"
    backend.startsWith("gpt-3") -> "gpt3"
    backend.startsWith("gemini") -> "gemni"
    backend.startsWith("llama") -> "llama"
    backend.startsWith("deepseek") -> "deep"
    backend.startsWith("mistral") -> "mstrl"
    backend.startsWith("qwen") -> "qwen"
    else -> backend.take(6)
}

/** Color accent for a backend label — gives each major model family a distinct hue. */
private fun backendColor(backend: String): Color = when {
    backend.startsWith("claude") -> Color(0xFFD97706)
    backend.startsWith("gpt") -> Color(0xFF10B981)
    backend.startsWith("gemini") -> Color(0xFF3B82F6)
    backend.startsWith("llama") -> Color(0xFFA855F7)
    backend.startsWith("deepseek") -> Color(0xFF06B6D4)
    backend.startsWith("mistral") -> Color(0xFFEC4899)
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

/**
 * Voice-input UI state for [SessionDetailPopup]. Bundling the three
 * fields keeps the popup signature under detekt's 8-param cap and
 * keeps the call site readable.
 */
private data class VoiceUiState(
    val transcript: String,
    val recording: Boolean,
    val transcribing: Boolean,
)

/**
 * Full-screen overlay shown after transcription completes. Lets the user
 * read the transcribed text and choose to Send it or Cancel (discards the
 * transcript and returns to the session popup).
 */
@Composable
private fun TranscriptReviewPopup(
    transcript: String,
    onCancel: () -> Unit,
    onSend: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xEE000000)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(MaterialTheme.colors.surface, androidx.compose.foundation.shape.CircleShape)
                    .border(
                        2.dp,
                        MaterialTheme.colors.primary,
                        androidx.compose.foundation.shape.CircleShape,
                    )
                    .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.wear_transcript_send_title),
                    style = MaterialTheme.typography.title3,
                    color = MaterialTheme.colors.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    transcript,
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(vertical = 6.dp)
                            .verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurface,
                    textAlign = TextAlign.Center,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    Text(
                        stringResource(R.string.wear_action_cancel),
                        style = MaterialTheme.typography.button,
                        color = MaterialTheme.colors.onSurfaceVariant,
                        modifier =
                            Modifier
                                .background(
                                    Color(0x33FFFFFF),
                                    androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                )
                                .clickable(onClick = onCancel)
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                    Text(
                        stringResource(R.string.wear_action_send),
                        style = MaterialTheme.typography.button,
                        color = MaterialTheme.colors.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier =
                            Modifier
                                .background(
                                    MaterialTheme.colors.primary.copy(alpha = 0.22f),
                                    androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                )
                                .clickable(onClick = onSend)
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionDetailPopup(
    session: WearSessionCountsViewModel.SessionItem,
    fullBody: String?,
    voice: VoiceUiState,
    onRecord: () -> Unit,
    onDismiss: () -> Unit,
    onQuickReply: (String) -> Unit = {},
    onStop: () -> Unit = {},
) {
    val transcript = voice.transcript
    val recording = voice.recording
    val transcribing = voice.transcribing
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color(0xE6000000)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(6.dp)
                    // v0.42.10 — clip the popup contents to the
                    // circular surface so long lastResponse bodies
                    // (now scrollable up to ~95 KB on demand) don't
                    // visually leak past the green bezel ring on
                    // round-bezel faces. Without clip, the verticalScroll
                    // column draws to the rectangular Box bounds.
                    .clip(androidx.compose.foundation.shape.CircleShape)
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
            SessionPopupCentre(
                session = session,
                fullBody = fullBody,
                voice = voice,
                onDismiss = onDismiss,
                onQuickReply = onQuickReply,
                onStop = onStop,
            )
            BoxScopeMicButton(recording = recording, transcribing = transcribing, onRecord = onRecord)
        }
    }
}

/**
 * v0.42.9 — mic/stop affordance pinned to the right edge of the
 * round popup safe area. Extracted so [SessionDetailPopup] stays
 * under detekt's LongMethod cap.
 *
 * While transcribing, shows a spinner instead of the mic icon so the
 * user knows the audio is being processed on the phone.
 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.BoxScopeMicButton(
    recording: Boolean,
    transcribing: Boolean,
    onRecord: () -> Unit,
) {
    Box(modifier = Modifier.align(Alignment.CenterEnd)) {
        if (transcribing) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(44.dp)
                    .padding(4.dp),
                indicatorColor = MaterialTheme.colors.primary,
                trackColor = MaterialTheme.colors.onSurface.copy(alpha = 0.1f),
            )
        } else {
            val tint = if (recording) Color(0xFFEF4444) else MaterialTheme.colors.primary
            Text(
                if (recording) "■" else "🎤",
                style = MaterialTheme.typography.title2,
                color = tint,
                modifier =
                    Modifier
                        .background(tint.copy(alpha = 0.18f), androidx.compose.foundation.shape.CircleShape)
                        .clickable(onClick = onRecord)
                        .padding(10.dp),
            )
        }
    }
}

/**
 * v0.42.9 — extracted centre column of [SessionDetailPopup] so the
 * outer composable stays under detekt's LongMethod cap. Renders
 * dismiss button, title + state, the (possibly large) lastResponse
 * body, and the recording / transcribing / staged-transcript
 * status line.
 */
@Composable
private fun SessionPopupCentre(
    session: WearSessionCountsViewModel.SessionItem,
    fullBody: String?,
    voice: VoiceUiState,
    onDismiss: () -> Unit,
    onQuickReply: (String) -> Unit = {},
    onStop: () -> Unit = {},
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp)
                .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                "✕",
                style = MaterialTheme.typography.caption1,
                color = MaterialTheme.colors.onSurfaceVariant,
                modifier = Modifier.clickable(onClick = onDismiss).padding(4.dp),
            )
        }
        Text(
            session.title,
            style = MaterialTheme.typography.title3,
            color = MaterialTheme.colors.primary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
        )
        Text(session.stateName.lowercase(), style = MaterialTheme.typography.caption2,
            color = sessionBadgeColor(session.stateName))
        // B34: always show "Loading…" until fresh /datawatch/sessionDetail arrives;
        // never fall back to stale DataLayer preview.
        if (fullBody == null) {
            Text(stringResource(R.string.wear_session_loading),
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.onSurfaceVariant)
        } else if (fullBody.isNotBlank()) {
            Text(fullBody, modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.caption2, color = MaterialTheme.colors.onSurface)
        }
        SessionVoiceStatusRow(voice)
        if (session.stateName.equals("waiting", ignoreCase = true) &&
            !voice.recording && !voice.transcribing
        ) {
            SessionWaitingButtons(onQuickReply, onStop)
        }
    }
}

@Composable
private fun SessionVoiceStatusRow(voice: VoiceUiState) {
    when {
        voice.recording ->
            Text(stringResource(R.string.wear_session_listening),
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.caption1, color = Color(0xFFEF4444))
        voice.transcribing ->
            Text(stringResource(R.string.wear_session_processing),
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.caption1, color = MaterialTheme.colors.primary)
        voice.transcript.isNotBlank() ->
            Text(stringResource(R.string.wear_session_tap_send),
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.caption1, color = MaterialTheme.colors.primary,
                maxLines = 3)
    }
}

@Composable
private fun SessionWaitingButtons(onQuickReply: (String) -> Unit, onStop: () -> Unit) {
    Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.wear_action_continue),
            style = MaterialTheme.typography.button,
            color = MaterialTheme.colors.primary,
            modifier = Modifier
                .background(MaterialTheme.colors.primary.copy(alpha = 0.2f),
                    androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                .clickable { onQuickReply("y") }
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
        Text(
            stringResource(R.string.wear_action_stop),
            style = MaterialTheme.typography.button,
            color = MaterialTheme.colors.error,
            modifier = Modifier
                .background(MaterialTheme.colors.error.copy(alpha = 0.2f),
                    androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                .clickable { onStop() }
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun CountTile(
    value: Int,
    label: String,
    color: Color,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val mod = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            mod
                .background(
                    color =
                        if (selected) {
                            color.copy(alpha = 0.2f)
                        } else {
                            Color.Transparent
                        },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                )
                .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            value.toString(),
            style = MaterialTheme.typography.display3,
            color = color,
        )
        Text(
            label,
            style = MaterialTheme.typography.caption3,
            color =
                if (selected) {
                    color
                } else {
                    MaterialTheme.colors.onSurfaceVariant
                },
        )
    }
}

/**
 * Sessions list filter — controls which subset of the published
 * snapshot the per-session rows show. Default is [Wait] because
 * waiting_input sessions are the actionable ones (they're blocking
 * on a reply); Run lists active workers; Total shows everything in
 * the published window including completed / killed.
 */
public enum class SessionFilter { Wait, Run, Total }

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
        // Automata in needs_review/running/revisions_asked (v0.40.0).
        val prds: List<PrdItem> = emptyList(),
        // Enabled profiles the user can switch between.
        val profiles: List<Pair<String, String>> = emptyList(),
        // B28: compact summary for all enabled servers (multi-server mode).
        // Empty when ≤1 server is enabled — Monitor page shows gauge grid.
        val allServerStats: List<AllServerStat> = emptyList(),
        // BL303-W2: active session telemetry for the glance screen
        val currentTask: String = "",
        val taskProgress: Float = 0f,
        val sprintName: String = "",
        val automataName: String = "",
        val guardrailBlock: Boolean = false,
        val blockSummary: String = "",
        val telemetrySessionId: String = "",
    ) {
        public fun cpuText(): String =
            when {
                cpuCores > 0 -> "%.2f · %d cores".format(cpuLoad1, cpuCores)
                cpuLoad1 > 0 -> "%.1f%%".format(cpuLoad1)
                else -> "—"
            }

        public fun memText(): String = if (memTotal > 0) "${fmt(memUsed)} / ${fmt(memTotal)}" else "—"

        public fun diskText(): String = if (diskTotal > 0) "${fmt(diskUsed)} / ${fmt(diskTotal)}" else "—"

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
        val shortId: String = id.takeLast(8),
        val title: String,
        val backend: String,
        val stateName: String,
        val lastLine: String,
        val lastActivity: Long = 0L,
        val startedAt: Long = 0L,
    )

    public data class PrdItem(
        val id: String,
        val title: String,
        val status: String,
        // BL303-W4: automata carousel extras
        val blockedCount: Int = 0,
        val progress: Float = 0f,
        val sprintName: String = "",
        val runningHours: Float = 0f,
    ) {
        /** Gravitational sort score: blocked weight × 3 + running time hours */
        fun gravityScore(): Float = (blockedCount * 3) + runningHours
    }

    public data class AllServerStat(
        val name: String,
        val cpuPct: Float,
        val memPct: Float,
        val sessionsTotal: Int,
        val online: Boolean,
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
            val splashStart = System.currentTimeMillis()
            runCatching {
                val items = dataClient.dataItems.await()
                // Hold splash for at least MIN_SPLASH_MS before applying
                // cached data (which clears loading). Live DataLayer pushes
                // that arrive after launch bypass this gate intentionally.
                val elapsed = System.currentTimeMillis() - splashStart
                val remaining = MIN_SPLASH_MS - elapsed
                if (remaining > 0) kotlinx.coroutines.delay(remaining)
                try {
                    items.forEach { item ->
                        when (item.uri.path) {
                            COUNTS_PATH ->
                                applyCounts(DataMapItem.fromDataItem(item).dataMap)
                            PROFILES_PATH ->
                                applyProfiles(DataMapItem.fromDataItem(item).dataMap)
                            STATS_PATH ->
                                applyStats(DataMapItem.fromDataItem(item).dataMap)
                            ALL_STATS_PATH ->
                                applyAllStats(DataMapItem.fromDataItem(item).dataMap)
                            SESSIONS_PATH ->
                                applySessions(DataMapItem.fromDataItem(item).dataMap)
                            PRDS_PATH ->
                                applyPrds(DataMapItem.fromDataItem(item).dataMap)
                            TELEMETRY_PATH ->
                                applyTelemetry(DataMapItem.fromDataItem(item).dataMap)
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
                        ALL_STATS_PATH ->
                            applyAllStats(DataMapItem.fromDataItem(event.dataItem).dataMap)
                        SESSIONS_PATH ->
                            applySessions(DataMapItem.fromDataItem(event.dataItem).dataMap)
                        PRDS_PATH ->
                            applyPrds(DataMapItem.fromDataItem(event.dataItem).dataMap)
                        TELEMETRY_PATH ->
                            applyTelemetry(DataMapItem.fromDataItem(event.dataItem).dataMap)
                    }
                }
            }
        } finally {
            buffer.release()
        }
    }

    private fun applyPrds(map: DataMap) {
        val ids = map.getStringArray("ids") ?: emptyArray()
        val titles = map.getStringArray("titles") ?: emptyArray()
        val statuses = map.getStringArray("statuses") ?: emptyArray()
        // BL303-W4: automata carousel extras
        val blockedCounts = map.getIntegerArrayList("blockedCounts")?.toIntArray() ?: IntArray(0)
        val progresses = map.getFloatArray("progresses") ?: FloatArray(0)
        val sprintNames = map.getStringArray("sprintNames") ?: emptyArray()
        val runningHours = map.getFloatArray("runningHours") ?: FloatArray(0)
        val items =
            ids.indices.map { i ->
                PrdItem(
                    id = ids[i],
                    title = titles.getOrNull(i).orEmpty(),
                    status = statuses.getOrNull(i).orEmpty(),
                    blockedCount = blockedCounts.getOrElse(i) { 0 },
                    progress = progresses.getOrElse(i) { 0f },
                    sprintName = sprintNames.getOrNull(i).orEmpty(),
                    runningHours = runningHours.getOrElse(i) { 0f },
                )
            }
        _state.value = _state.value.copy(prds = items)
    }

    // BL303-W2
    private fun applyTelemetry(map: DataMap) {
        _state.value = _state.value.copy(
            currentTask = map.getString("currentTask", ""),
            taskProgress = map.getFloat("progress", 0f),
            sprintName = map.getString("sprintName", ""),
            automataName = map.getString("automataName", ""),
            guardrailBlock = map.getBoolean("guardrailBlock", false),
            blockSummary = map.getString("blockSummary", ""),
            telemetrySessionId = map.getString("sessionId", ""),
        )
    }

    private fun applySessions(map: DataMap) {
        val ids = map.getStringArray("ids") ?: emptyArray()
        val shortIds = map.getStringArray("shortIds") ?: emptyArray()
        val titles = map.getStringArray("titles") ?: emptyArray()
        val backends = map.getStringArray("backends") ?: emptyArray()
        val states = map.getStringArray("states") ?: emptyArray()
        val lastLines = map.getStringArray("lastLines") ?: emptyArray()
        val lastActivities = map.getLongArray("lastActivities") ?: LongArray(0)
        val startedAts = map.getLongArray("startedAts") ?: LongArray(0)
        val items =
            ids.indices.map { i ->
                SessionItem(
                    id = ids[i],
                    shortId = shortIds.getOrNull(i) ?: ids[i].takeLast(8),
                    title = titles.getOrNull(i).orEmpty(),
                    backend = backends.getOrNull(i).orEmpty(),
                    stateName = states.getOrNull(i).orEmpty(),
                    lastLine = lastLines.getOrNull(i).orEmpty(),
                    lastActivity = lastActivities.getOrElse(i) { 0L },
                    startedAt = startedAts.getOrElse(i) { 0L },
                )
            }
        Log.d("WearMain", "applySessions n=${items.size}")
        _state.value = _state.value.copy(sessions = items)
    }

    /**
     * Send a voice-reply to the phone for forwarding to the active
     * server's send_input hub. Payload format is "sessionId\ntext".
     */
    public fun sendReply(
        sessionId: String,
        text: String,
    ) {
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
     * v0.42.2 — ask the phone to refetch the latest `/api/sessions`
     * for the active profile and re-publish `/datawatch/sessions`.
     * Triggered when the user opens the session-detail popup so the
     * watch shows the same `lastResponse` body the PWA / Android app
     * render. Best-effort: failure leaves the cached snapshot in
     * place.
     */
    public fun refreshSession(sessionId: String) {
        if (sessionId.isBlank()) return
        Log.d("WearMain", "refreshSession ENTER sid=$sessionId")
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val body = sessionId.toByteArray(Charsets.UTF_8)
                val nodes: List<Node> = nodeClient.connectedNodes.await()
                Log.d("WearMain", "refreshSession nodes=${nodes.size} ${nodes.joinToString { it.displayName }}")
                if (nodes.isEmpty()) {
                    Log.w("WearMain", "refreshSession NO CONNECTED PHONE — message will not be delivered")
                }
                nodes.forEach { node ->
                    val rc = messageClient.sendMessage(node.id, REFRESH_SESSION_PATH, body).await()
                    Log.d("WearMain", "refreshSession sent to ${node.id} requestId=$rc")
                }
            }.onFailure { Log.w("WearMain", "refreshSession FAILED", it) }
        }
    }

    /**
     * Send an automata action to the phone for forwarding to
     * /api/autonomous/prds/{id}/{action}. Payload format is
     * "prdId\naction\nreason?" — `reason` only meaningful for
     * `reject`. Best-effort; failure is silent (the watch UI just
     * won't see the automaton disappear from needs_review on next refresh).
     */
    public fun sendPrdAction(
        prdId: String,
        action: String,
        reason: String = "",
    ) {
        if (prdId.isBlank() || action.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val body =
                    if (reason.isNotEmpty()) {
                        "$prdId\n$action\n$reason"
                    } else {
                        "$prdId\n$action"
                    }
                val payload = body.toByteArray(Charsets.UTF_8)
                val nodes: List<Node> = nodeClient.connectedNodes.await()
                nodes.forEach { node ->
                    messageClient.sendMessage(node.id, PRD_ACTION_PATH, payload).await()
                }
            }
        }
    }

    /**
     * BL303-W5 — send a voice status query to the phone. Payload is the query
     * text (UTF-8). The phone classifies it, builds a natural-language reply from
     * its cached session state, and sends it back on VOICE_REPLY_PATH.
     */
    public fun sendVoiceQuery(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val payload = query.toByteArray(Charsets.UTF_8)
                val nodes: List<Node> = nodeClient.connectedNodes.await()
                nodes.forEach { node ->
                    messageClient.sendMessage(node.id, VOICE_QUERY_PATH, payload).await()
                }
            }
        }
    }

    /**
     * BL303-W4 — send a memory sweep command to the phone. No payload needed;
     * the phone triggers the server's memory maintenance endpoint.
     */
    public fun sendMemorySweep() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val nodes: List<Node> = nodeClient.connectedNodes.await()
                nodes.forEach { node ->
                    messageClient.sendMessage(node.id, MEMORY_SWEEP_PATH, ByteArray(0)).await()
                }
            }
        }
    }

    /**
     * W-4 — ask the phone to kill a session. Payload is the session id (UTF-8).
     */
    public fun sendStopSession(sessionId: String) {
        if (sessionId.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val body = sessionId.toByteArray(Charsets.UTF_8)
                val nodes: List<Node> = nodeClient.connectedNodes.await()
                nodes.forEach { node ->
                    messageClient.sendMessage(node.id, STOP_SESSION_PATH, body).await()
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
    public fun sendAudio(
        sessionId: String,
        audio: ByteArray,
    ) {
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
        _state.value =
            _state.value.copy(
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
        _state.value =
            _state.value.copy(
                profiles = pairs,
                pairedServer = activeId,
            )
    }

    private fun applyStats(map: DataMap) {
        _state.value =
            _state.value.copy(
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

    private fun applyAllStats(map: DataMap) {
        val names = map.getStringArray("names") ?: emptyArray()
        val cpuPcts = map.getFloatArray("cpuPcts") ?: FloatArray(0)
        val memPcts = map.getFloatArray("memPcts") ?: FloatArray(0)
        val totals = map.getFloatArray("totals") ?: FloatArray(0)
        val statuses = map.getStringArray("statuses") ?: emptyArray()
        val items = names.indices.map { i ->
            AllServerStat(
                name = names[i],
                cpuPct = cpuPcts.getOrElse(i) { 0f },
                memPct = memPcts.getOrElse(i) { 0f },
                sessionsTotal = totals.getOrElse(i) { 0f }.toInt(),
                online = statuses.getOrNull(i) != "err",
            )
        }
        _state.value = _state.value.copy(allServerStats = items)
    }

    public companion object {
        public const val MIN_SPLASH_MS: Long = 1_400L
        public const val COUNTS_PATH: String = "/datawatch/counts"
        public const val PROFILES_PATH: String = "/datawatch/profiles"
        public const val STATS_PATH: String = "/datawatch/stats"
        public const val ALL_STATS_PATH: String = "/datawatch/allStats"
        public const val SESSIONS_PATH: String = "/datawatch/sessions"
        public const val PRDS_PATH: String = "/datawatch/prds"
        public const val SET_ACTIVE_PATH: String = "/datawatch/setActive"
        public const val REPLY_PATH: String = "/datawatch/reply"
        public const val PRD_ACTION_PATH: String = "/datawatch/prdAction"
        public const val AUDIO_PATH: String = "/datawatch/audio"
        public const val TRANSCRIPT_PATH: String = "/datawatch/transcript"
        public const val REFRESH_SESSION_PATH: String = "/datawatch/refreshSession"
        public const val SESSION_DETAIL_PATH: String = "/datawatch/sessionDetail"
        public const val STOP_SESSION_PATH: String = "/datawatch/stopSession"
        // BL303-W1/W2
        public const val TELEMETRY_PATH: String = "/datawatch/telemetry"
        // BL303-W4: memory sweep quick action
        public const val MEMORY_SWEEP_PATH: String = "/datawatch/memorySweep"
        // BL303-W5: voice status query + reply paths
        public const val VOICE_QUERY_PATH: String = "/datawatch/voiceQuery"
        public const val VOICE_REPLY_PATH: String = "/datawatch/voiceReply"
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
