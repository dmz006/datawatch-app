package com.dmzs.datawatchclient.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.transport.TransportClient
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * T30 — Discussion Scopes card (GET /api/memory/discussion, POST /api/memory/discussion/{id}).
 * Lists discussion IDs and allows writing a quick message to a discussion WAL.
 */
@Composable
public fun DiscussionScopesCard() {
    val scope = rememberCoroutineScope()
    var discussions by remember { mutableStateOf<List<String>>(emptyList()) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var selectedDiscussion by remember { mutableStateOf<String?>(null) }
    var dialogMessage by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var sendResult by remember { mutableStateOf<String?>(null) }
    val sendOkLabel = stringResource(R.string.discussion_send_ok)

    suspend fun transport(): TransportClient? {
        val id = ServiceLocator.activeServerStore.get()
        val enabled = ServiceLocator.profileRepository.observeAll().first()
            .filter { it.enabled }
        val p = enabled.firstOrNull { it.id == id } ?: enabled.firstOrNull()
        return if (p != null) ServiceLocator.transportFor(p) else null
    }

    LaunchedEffect(Unit) {
        transport()?.listDiscussions()
            ?.onSuccess { discussions = it.discussions; loadError = null }
            ?.onFailure { loadError = it.message }
    }

    // Write-message dialog
    selectedDiscussion?.let { discId ->
        AlertDialog(
            onDismissRequest = {
                selectedDiscussion = null
                dialogMessage = ""
                sendResult = null
            },
            title = { Text(discId, style = MaterialTheme.typography.titleSmall, fontFamily = FontFamily.Monospace) },
            text = {
                Column {
                    OutlinedTextField(
                        value = dialogMessage,
                        onValueChange = { dialogMessage = it; sendResult = null },
                        label = { Text(stringResource(R.string.discussion_message_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 5,
                    )
                    sendResult?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (it.startsWith("Error")) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (dialogMessage.isBlank()) return@Button
                        scope.launch {
                            sending = true
                            transport()?.writeDiscussionMessage(discId, dialogMessage)
                                ?.onSuccess {
                                    sendResult = sendOkLabel
                                    dialogMessage = ""
                                }
                                ?.onFailure { sendResult = "Error: ${it.message}" }
                            sending = false
                        }
                    },
                    enabled = !sending && dialogMessage.isNotBlank(),
                ) {
                    Text(stringResource(R.string.action_send))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    selectedDiscussion = null
                    dialogMessage = ""
                    sendResult = null
                }) {
                    Text(stringResource(R.string.action_close))
                }
            },
        )
    }

    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .pwaCard(),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            PwaSectionTitle(stringResource(R.string.discussion_scopes_title), docsAnchor = "discussion-scopes")

            Spacer(Modifier.height(8.dp))

            when {
                loadError != null -> Text(
                    loadError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                discussions.isEmpty() -> Text(
                    stringResource(R.string.discussion_no_scopes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> {
                    Text(
                        stringResource(R.string.discussion_tap_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    discussions.forEachIndexed { idx, discId ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.weight(1f),
                                onClick = { selectedDiscussion = discId },
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        discId,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        stringResource(R.string.discussion_write_btn),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                        if (idx < discussions.lastIndex) HorizontalDivider()
                    }
                }
            }
        }
    }
}
