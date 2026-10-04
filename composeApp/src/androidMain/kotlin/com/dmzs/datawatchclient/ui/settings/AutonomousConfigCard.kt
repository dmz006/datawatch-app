package com.dmzs.datawatchclient.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.transport.dto.AutonomousConfigDto
import com.dmzs.datawatchclient.ui.theme.PwaCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
internal fun AutonomousConfigCard() {
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf<AutonomousConfigDto?>(null) }
    var newBackend by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        runCatching {
            val activeId = ServiceLocator.activeServerStore.get()
            val sp = ServiceLocator.profileRepository.observeAll()
                .first { list -> list.any { it.enabled } }
                .let { list ->
                    if (activeId == null) list.firstOrNull { it.enabled }
                    else list.firstOrNull { it.id == activeId && it.enabled } ?: list.firstOrNull { it.enabled }
                } ?: return@runCatching
            ServiceLocator.transportFor(sp).getAutonomousConfig().onSuccess { config = it }
        }
    }

    fun save(updated: AutonomousConfigDto) {
        config = updated
        scope.launch {
            runCatching {
                val activeId = ServiceLocator.activeServerStore.get()
                val sp = ServiceLocator.profileRepository.observeAll()
                    .first { list -> list.any { it.enabled } }
                    .let { list ->
                        if (activeId == null) list.firstOrNull { it.enabled }
                        else list.firstOrNull { it.id == activeId && it.enabled } ?: list.firstOrNull { it.enabled }
                    } ?: return@runCatching
                ServiceLocator.transportFor(sp).updateAutonomousConfig(updated)
            }
        }
    }

    PwaCard(
        id = "automata_autonomous",
        title = stringResource(R.string.autonomous_config_title),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        docsAnchor = "autonomous-config",
        innerPadding = PaddingValues(12.dp),
    ) {
        val cfg = config ?: return@PwaCard
        val backends = cfg.verificationBackends ?: emptyList()

        Text(
            stringResource(R.string.autonomous_config_verification_backends),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )

        backends.forEachIndexed { idx, backend ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${idx + 1}. $backend",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    save(cfg.copy(verificationBackends = backends.toMutableList().also { it.removeAt(idx) }))
                }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_remove))
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = newBackend,
                onValueChange = { newBackend = it },
                label = { Text(stringResource(R.string.autonomous_config_add_backend)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    val trimmed = newBackend.trim()
                    if (trimmed.isNotEmpty()) {
                        save(cfg.copy(verificationBackends = backends + trimmed))
                        newBackend = ""
                    }
                },
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_add))
            }
        }

        if (backends.size > 1) {
            Text(
                stringResource(R.string.autonomous_config_backends_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = { save(cfg.copy(verificationBackends = emptyList())) }) {
                Text(stringResource(R.string.action_clear_all))
            }
        }
    }
}
