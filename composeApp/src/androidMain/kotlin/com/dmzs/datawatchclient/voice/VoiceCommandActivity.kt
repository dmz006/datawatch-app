package com.dmzs.datawatchclient.voice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.domain.Session
import com.dmzs.datawatchclient.domain.SessionState
import com.dmzs.datawatchclient.ui.theme.DatawatchTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * BL34 — Google Assistant App Actions confirmation screen.
 *
 * Launched by the SEND_MESSAGE BII ("Ok Google, send [command] to datawatch").
 * Never auto-sends — always shows a confirmation UI so the user can review
 * the command and target session before it is dispatched.
 */
public class VoiceCommandActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val command = intent.getStringExtra("command")
            ?: intent.getStringExtra("message.text")
            ?: ""
        val sessionNameHint = intent.getStringExtra("sessionName")
            ?: intent.getStringExtra("message.recipient.name")

        if (command.isBlank()) {
            finish()
            return
        }

        setContent {
            DatawatchTheme {
                VoiceCommandConfirmScreen(
                    command = command,
                    sessionNameHint = sessionNameHint,
                    onConfirm = { session ->
                        lifecycleScope.launch {
                            val profile = ServiceLocator.activeProfileFlow().first()
                            if (profile != null) {
                                ServiceLocator.transportFor(profile).replyToSession(session.id, command)
                            }
                        }
                        finish()
                    },
                    onCancel = { finish() },
                )
            }
        }
    }
}

@Composable
private fun VoiceCommandConfirmScreen(
    command: String,
    sessionNameHint: String?,
    onConfirm: (Session) -> Unit,
    onCancel: () -> Unit,
) {
    var targetSession by remember { mutableStateOf<Session?>(null) }
    var noSession by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val profile = ServiceLocator.activeProfileFlow().first()
        if (profile == null) {
            noSession = true
            return@LaunchedEffect
        }
        val sessions = ServiceLocator.sessionRepository.observeForProfile(profile.id).first()
        val active = sessions
            .filter { it.state == SessionState.Running || it.state == SessionState.Waiting }
            .sortedByDescending { it.lastActivityAt.toEpochMilliseconds() }
        val resolved = if (!sessionNameHint.isNullOrBlank()) {
            active.firstOrNull { s ->
                s.name?.contains(sessionNameHint, ignoreCase = true) == true ||
                    s.id.startsWith(sessionNameHint, ignoreCase = true)
            } ?: active.firstOrNull()
        } else {
            active.firstOrNull()
        }
        if (resolved != null) targetSession = resolved else noSession = true
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.voice_cmd_confirm_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "\"$command\"",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            val session = targetSession
            when {
                noSession -> Text(
                    stringResource(R.string.voice_cmd_no_session),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                session != null -> Text(
                    stringResource(R.string.voice_cmd_session_label, session.name ?: session.id.takeLast(8)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                else -> Text(
                    "…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(32.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.voice_cmd_confirm_cancel))
                }
                Button(
                    onClick = { targetSession?.let { onConfirm(it) } },
                    modifier = Modifier.weight(1f),
                    enabled = targetSession != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text(stringResource(R.string.voice_cmd_confirm_send))
                }
            }
        }
    }
}
