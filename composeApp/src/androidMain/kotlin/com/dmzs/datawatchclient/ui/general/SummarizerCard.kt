package com.dmzs.datawatchclient.ui.general

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Settings → General / Session — session response summarizer controls.
 *
 * Toggle: session.summarizer.enabled via PUT /api/config {"key": ..., "value": ...}
 * LLM picker: session.summarizer.llm_ref — lists Ollama LLMs (kind == "ollama") only.
 *
 * Server v8.8.13+. Summarization compresses last_response to ~3 sentences before it
 * reaches push notifications, Android Auto, and mobile alert bodies.
 */
@Composable
public fun SummarizerCard() {
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(false) }
    var llmRef by remember { mutableStateOf("") }
    var ollamaLlms by remember { mutableStateOf<List<String>>(emptyList()) }
    var llmPickerExpanded by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val profiles = ServiceLocator.profileRepository.observeAll().first()
        val activeId = ServiceLocator.activeServerStore.get()
        val profile = profiles.firstOrNull { it.id == activeId && it.enabled }
            ?: profiles.firstOrNull { it.enabled }
            ?: return@LaunchedEffect
        val transport = ServiceLocator.transportFor(profile)
        transport.fetchConfig().onSuccess { cfg ->
            enabled = cfg.raw["session.summarizer.enabled"]?.jsonPrimitive?.content == "true"
            llmRef = cfg.raw["session.summarizer.llm_ref"]?.jsonPrimitive?.content ?: ""
        }
        transport.listLlms().onSuccess { list ->
            ollamaLlms = list.filter { it.kind == "ollama" }.map { it.name }
        }
        loading = false
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .pwaCard()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        PwaSectionTitle("Session Summarizer", docsAnchor = "session-summarizer")

        // Toggle row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Summarize last response",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Compress session output to ~3 sentences for notifications",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = { checked ->
                    enabled = checked
                    scope.launch {
                        val profiles = ServiceLocator.profileRepository.observeAll().first()
                        val activeId = ServiceLocator.activeServerStore.get()
                        val profile = profiles.firstOrNull { it.id == activeId && it.enabled }
                            ?: profiles.firstOrNull { it.enabled } ?: return@launch
                        ServiceLocator.transportFor(profile).writeConfig(
                            buildJsonObject { put("session.summarizer.enabled", JsonPrimitive(checked)) },
                        )
                    }
                },
            )
        }

        // LLM picker — only shown when enabled
        if (enabled) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Summarizer LLM",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    llmRef.ifBlank { "— select —" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable(enabled = ollamaLlms.isNotEmpty()) { llmPickerExpanded = true }
                        .padding(4.dp),
                )
                DropdownMenu(
                    expanded = llmPickerExpanded,
                    onDismissRequest = { llmPickerExpanded = false },
                ) {
                    ollamaLlms.forEach { name ->
                        DropdownMenuItem(
                            text = { Text(name, style = MaterialTheme.typography.bodySmall) },
                            onClick = {
                                llmPickerExpanded = false
                                llmRef = name
                                scope.launch {
                                    val profiles = ServiceLocator.profileRepository.observeAll().first()
                                    val activeId = ServiceLocator.activeServerStore.get()
                                    val profile = profiles.firstOrNull { it.id == activeId && it.enabled }
                                        ?: profiles.firstOrNull { it.enabled } ?: return@launch
                                    ServiceLocator.transportFor(profile).writeConfig(
                                        buildJsonObject { put("session.summarizer.llm_ref", JsonPrimitive(name)) },
                                    )
                                }
                            },
                        )
                    }
                    if (ollamaLlms.isEmpty()) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "No Ollama LLMs configured",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            onClick = { llmPickerExpanded = false },
                        )
                    }
                }
            }
            Text(
                "Only Ollama LLMs are listed. Configure under Compute → LLMs.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            // Test row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (testResult != null) {
                    val isOk = testResult!!.startsWith("✓")
                    Text(
                        testResult!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (isTesting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(
                        onClick = {
                            isTesting = true
                            testResult = null
                            scope.launch {
                                val profiles = ServiceLocator.profileRepository.observeAll().first()
                                val activeId = ServiceLocator.activeServerStore.get()
                                val profile = profiles.firstOrNull { it.id == activeId && it.enabled }
                                    ?: profiles.firstOrNull { it.enabled }
                                if (profile == null) {
                                    testResult = "No active server"
                                    isTesting = false
                                    return@launch
                                }
                                ServiceLocator.transportFor(profile).testSummarizer().fold(
                                    onSuccess = { res ->
                                        testResult = if (res.ok) "✓ ok · ${res.latencyMs}ms" else "✗ server returned ok=false"
                                    },
                                    onFailure = { err ->
                                        testResult = "✗ ${err.message ?: "failed"}"
                                    },
                                )
                                isTesting = false
                            }
                        },
                    ) {
                        Text("Test", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
