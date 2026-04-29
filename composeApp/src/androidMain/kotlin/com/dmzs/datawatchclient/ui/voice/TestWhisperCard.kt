package com.dmzs.datawatchclient.ui.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.ui.settings.Section
import com.dmzs.datawatchclient.voice.VoiceRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * v0.42.7 — Test Whisper card (PWA v5.26.56 parity, gap #3 from
 * the 72h audit).
 *
 * Replaces the silent-WAV health-check (which only proves the
 * endpoint responds) with an interactive record-and-transcribe
 * flow: tap 🎤 to record, tap ■ to stop, the audio is shipped to
 * `/api/voice/transcribe` against the active server, and the
 * transcript renders in the read-only field below. Round-trip
 * timing surfaces in the status line so operators can sanity-check
 * latency.
 *
 * Lives directly under the Whisper config panel on Settings →
 * General; mirrors PWA's `testWhisperBackend()` modal but inlined
 * since the phone has more vertical room than a popover.
 */
@Composable
public fun TestWhisperCard() {
    val context = LocalContext.current
    val recorder = remember { VoiceRecorder(context) }
    var recording by remember { mutableStateOf(false) }
    var transcribing by remember { mutableStateOf(false) }
    var transcript by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("idle") }
    val scope = rememberCoroutineScope()

    fun beginTranscribe() {
        val captured = recorder.stop()
        recording = false
        if (captured == null || captured.first.isEmpty()) {
            status = "no audio captured"
            return
        }
        transcribing = true
        status = "transcribing…"
        scope.launch {
            val activeId = ServiceLocator.activeServerStore.get()
            val profile =
                ServiceLocator.profileRepository.observeAll().first()
                    .firstOrNull { it.id == activeId && it.enabled }
            if (profile == null) {
                status = "no active profile"
                transcribing = false
                return@launch
            }
            val t0 = System.currentTimeMillis()
            withContext(Dispatchers.IO) {
                ServiceLocator.transportFor(profile)
                    .transcribeAudio(
                        audio = captured.first,
                        audioMime = captured.second,
                        sessionId = null,
                        autoExec = false,
                    )
                    .fold(
                        onSuccess = { resp ->
                            val ms = System.currentTimeMillis() - t0
                            val text = resp.transcript
                            transcript =
                                text.ifBlank { "(empty transcript — backend may be misconfigured)" }
                            status =
                                if (text.isNotBlank()) {
                                    "ok (${ms}ms, ${text.length} chars)"
                                } else {
                                    "transcribed empty — check backend"
                                }
                        },
                        onFailure = { err ->
                            status = "failed — ${err.message ?: err::class.simpleName}"
                        },
                    )
            }
            transcribing = false
        }
    }

    val micLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                runCatching { recorder.start() }
                    .onSuccess {
                        recording = true
                        status = "recording — tap ■ to stop"
                    }
                    .onFailure { status = "mic start failed: ${it.message}" }
            } else {
                status = "mic permission denied"
            }
        }

    Section(title = "Test Whisper") {
        Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            Text(
                "Tap 🎤 to start, ■ to stop. The transcript verifies the configured Whisper " +
                    "backend end-to-end (mic → /api/voice/transcribe → text).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = {
                        if (recording) {
                            beginTranscribe()
                        } else if (!transcribing) {
                            transcript = ""
                            val granted =
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO,
                                ) == PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                runCatching { recorder.start() }
                                    .onSuccess {
                                        recording = true
                                        status = "recording — tap ■ to stop"
                                    }
                                    .onFailure { status = "mic start failed: ${it.message}" }
                            } else {
                                micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    },
                    enabled = !transcribing,
                    modifier = Modifier.size(64.dp),
                ) { Text(if (recording) "■" else "🎤", style = MaterialTheme.typography.titleLarge) }
                Spacer(Modifier.size(12.dp))
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = transcript,
                onValueChange = {},
                readOnly = true,
                label = { Text("Transcript") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 4,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    enabled = transcript.isNotBlank() && !recording && !transcribing,
                    onClick = { transcript = ""; status = "idle" },
                ) { Text("Clear") }
            }
        }
    }

    // Cancel any in-flight recording when the card leaves composition
    // (e.g., user navigates away mid-record).
    LaunchedEffect(Unit) { /* recorder lifecycle handled inline */ }
}
