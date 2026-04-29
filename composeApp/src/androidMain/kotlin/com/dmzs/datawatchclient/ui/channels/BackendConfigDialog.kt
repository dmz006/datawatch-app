package com.dmzs.datawatchclient.ui.channels

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.ui.configfields.ConfigFieldsPanel
import com.dmzs.datawatchclient.ui.configfields.LlmBackendSchemas

/**
 * Structured per-LLM-backend config editor. Dispatches to
 * [LlmBackendSchemas.sectionFor] to get the right field set for
 * the backend type (ollama, openai, anthropic, groq, openrouter,
 * gemini, xai, openwebui, opencode) — unknown backends fall back
 * to a generic model / base_url / api_key shape. All fields
 * prefill from the live `/api/config` and auto-save on change via
 * flat dot-path patches.
 *
 * ADR-0019: only structured edits. Raw YAML editing stays off
 * the phone; all writes go through `writeConfig` which the parent
 * daemon merges into its config document.
 */
@Composable
public fun BackendConfigDialog(
    backendName: String,
    onDismiss: () -> Unit,
) {
    val section = LlmBackendSchemas.sectionFor(backendName)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configure $backendName") },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
            ) {
                ConfigFieldsPanel(section)
                Text(
                    "Other fields on this backend are preserved. Changes " +
                        "take effect on the next new session.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}
