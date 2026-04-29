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
import com.dmzs.datawatchclient.ui.configfields.ChannelBackendSchemas
import com.dmzs.datawatchclient.ui.configfields.ConfigFieldsPanel

/**
 * Per-channel-instance config editor (`channels.<id>.*`). Dispatches
 * to [ChannelBackendSchemas.instanceSectionFor] to get the right
 * field set for the channel type (signal, telegram, discord, slack,
 * matrix, ntfy, email, twilio, webhook, github_webhook). Fields
 * prefill from `/api/config` and auto-save on change.
 */
@Composable
public fun ChannelConfigDialog(
    channelId: String,
    channelType: String?,
    onDismiss: () -> Unit,
) {
    val type = channelType?.takeIf { it.isNotBlank() } ?: "webhook"
    val section = ChannelBackendSchemas.instanceSectionFor(channelId, type)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$channelId · $type") },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
            ) {
                ConfigFieldsPanel(section)
                Text(
                    "Fields save as you type. Empty password fields keep " +
                        "the current stored secret — overwrite only when " +
                        "rotating.",
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
