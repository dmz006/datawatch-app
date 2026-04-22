package com.dmzs.datawatchclient.ui.channels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.ui.configfields.ChannelBackendSchemas
import com.dmzs.datawatchclient.ui.configfields.ConfigFieldsPanel
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard

/**
 * Settings → Comms → Messaging Backends card. Lists every known
 * channel backend type with a **Configure** action opening a
 * schema-driven editor rooted at `messaging.<type>.*`.
 *
 * Why this exists: the 2026-04-22 user report — "signal is
 * configured on one server but it is not in the list". The
 * `/api/channels` endpoint only returns channel *instances*; a
 * global per-type backend config (like `messaging.signal.*`)
 * doesn't create an instance row and would never show up in the
 * regular ChannelsCard. This card guarantees every known type is
 * always reachable for edit, independent of whether an instance
 * has been created.
 */
@Composable
public fun MessagingBackendsCard() {
    var configuring by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).pwaCard(),
    ) {
        PwaSectionTitle("Messaging Backends")
        Text(
            "Global per-type config (messaging.<type>.*). Edit a " +
                "backend here even if no channel instance exists yet.",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ChannelBackendSchemas.KnownTypes.forEachIndexed { idx, type ->
            if (idx > 0) HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    type.replaceFirstChar { it.titlecase() },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                TextButton(onClick = { configuring = type }) { Text("Configure") }
            }
        }
    }

    configuring?.let { type ->
        val section = ChannelBackendSchemas.globalSectionFor(type)
        AlertDialog(
            onDismissRequest = { configuring = null },
            title = { Text("${type.replaceFirstChar { it.titlecase() }} · global") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScrollFix(),
                ) {
                    ConfigFieldsPanel(section)
                    Text(
                        "Fields save as you type. Empty password fields " +
                            "keep the current stored secret.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { configuring = null }) { Text("Done") }
            },
        )
    }
}

// Tiny modifier alias so the dialog body scrolls without
// repeating the two imports in every file.
@Composable
private fun Modifier.verticalScrollFix(): Modifier =
    this.then(Modifier.verticalScroll(rememberScrollState()))
