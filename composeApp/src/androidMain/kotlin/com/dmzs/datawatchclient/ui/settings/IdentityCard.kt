package com.dmzs.datawatchclient.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.transport.dto.IdentityDto
import com.dmzs.datawatchclient.ui.common.MicAttachableTextField
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
internal fun IdentityCard() {
    var identity by remember { mutableStateOf(IdentityDto()) }
    var wizardOpen by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun loadIdentity() {
        val activeId = ServiceLocator.activeServerStore.get() ?: return
        val sp = ServiceLocator.profileRepository.observeAll().first()
            .firstOrNull { it.id == activeId && it.enabled } ?: return
        ServiceLocator.transportFor(sp).getIdentity().onSuccess { identity = it }
    }

    LaunchedEffect(Unit) { runCatching { loadIdentity() } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .pwaCard()
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PwaSectionTitle(
                stringResource(R.string.identity_title),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { wizardOpen = true }) {
                Icon(Icons.Filled.SmartToy, contentDescription = stringResource(R.string.identity_wizard_open))
            }
        }
        OutlinedTextField(
            value = identity.role,
            onValueChange = { identity = identity.copy(role = it) },
            label = { Text(stringResource(R.string.identity_role_label)) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            singleLine = true,
        )
        MicAttachableTextField(
            value = identity.currentFocus,
            onValueChange = { identity = identity.copy(currentFocus = it) },
            label = { Text(stringResource(R.string.identity_focus_label)) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            minLines = 2,
            whisperConfigured = false,
            onMicClick = null,
        )
        MicAttachableTextField(
            value = identity.contextNotes,
            onValueChange = { identity = identity.copy(contextNotes = it) },
            label = { Text(stringResource(R.string.identity_notes_label)) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            minLines = 3,
            whisperConfigured = false,
            onMicClick = null,
        )
        Row(modifier = Modifier.padding(top = 8.dp)) {
            Button(
                onClick = {
                    saving = true
                    scope.launch {
                        runCatching {
                            val activeId = ServiceLocator.activeServerStore.get() ?: return@runCatching
                            val sp = ServiceLocator.profileRepository.observeAll().first()
                                .firstOrNull { it.id == activeId && it.enabled } ?: return@runCatching
                            ServiceLocator.transportFor(sp).setIdentity(identity)
                        }
                        saving = false
                    }
                },
                enabled = !saving,
            ) { Text(stringResource(R.string.identity_save)) }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = {
                scope.launch { runCatching { loadIdentity() } }
            }) { Text(stringResource(R.string.identity_reset)) }
        }
    }

    if (wizardOpen) {
        IdentityWizardSheet(
            initial = identity,
            onDismiss = { wizardOpen = false },
            onFinish = { updated ->
                identity = updated
                wizardOpen = false
                scope.launch {
                    runCatching {
                        val activeId = ServiceLocator.activeServerStore.get() ?: return@runCatching
                        val sp = ServiceLocator.profileRepository.observeAll().first()
                            .firstOrNull { it.id == activeId && it.enabled } ?: return@runCatching
                        ServiceLocator.transportFor(sp).setIdentity(updated)
                    }
                }
            },
        )
    }
}
