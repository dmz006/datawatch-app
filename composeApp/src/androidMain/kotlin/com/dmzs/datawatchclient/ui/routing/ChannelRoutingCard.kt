package com.dmzs.datawatchclient.ui.routing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.dmzs.datawatchclient.transport.dto.ChannelRoutingRuleDto
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * T30 — Channel Routing CRUD card (GET/PUT /api/channel/routing).
 * Mirrors the PWA channel routing panel under Settings → Comms.
 */
@Composable
public fun ChannelRoutingCard() {
    val scope = rememberCoroutineScope()
    var rules by remember { mutableStateOf<List<ChannelRoutingRuleDto>>(emptyList()) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var channelPattern by remember { mutableStateOf("") }
    var peerName by remember { mutableStateOf("") }
    var automataType by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    suspend fun transport(): TransportClient? {
        val id = ServiceLocator.activeServerStore.get()
        val p = ServiceLocator.profileRepository.observeAll().first()
            .firstOrNull { it.id == id && it.enabled }
        return if (p != null) ServiceLocator.transportFor(p) else null
    }

    fun load() {
        scope.launch {
            transport()?.getChannelRouting()
                ?.onSuccess { rules = it.rules; loadError = null }
                ?.onFailure { loadError = it.message }
        }
    }
    LaunchedEffect(Unit) { load() }

    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .pwaCard(),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            PwaSectionTitle(stringResource(R.string.channel_routing_title))

            // Add rule form
            Text(
                stringResource(R.string.channel_routing_add_rule),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
            OutlinedTextField(
                value = channelPattern,
                onValueChange = { channelPattern = it },
                label = { Text(stringResource(R.string.channel_routing_pattern_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = peerName,
                onValueChange = { peerName = it },
                label = { Text(stringResource(R.string.channel_routing_peer_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = automataType,
                onValueChange = { automataType = it },
                label = { Text(stringResource(R.string.channel_routing_type_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = {
                    if (channelPattern.isBlank()) return@Button
                    scope.launch {
                        saving = true
                        val newRule = ChannelRoutingRuleDto(
                            channelPattern = channelPattern,
                            peerName = peerName,
                            automataType = automataType,
                        )
                        transport()?.putChannelRouting(rules + newRule)
                            ?.onSuccess {
                                rules = it.rules
                                channelPattern = ""
                                peerName = ""
                                automataType = ""
                                loadError = null
                            }
                            ?.onFailure { loadError = it.message }
                        saving = false
                    }
                },
                enabled = !saving,
            ) {
                Text(stringResource(R.string.channel_routing_add_rule))
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // Rules list
            loadError?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            if (rules.isEmpty()) {
                Text(
                    stringResource(R.string.channel_routing_no_rules),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                rules.forEachIndexed { idx, rule ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    rule.channelPattern,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                )
                                if (rule.peerName.isNotEmpty()) {
                                    Spacer(Modifier.width(6.dp))
                                    Surface(
                                        shape = MaterialTheme.shapes.small,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                    ) {
                                        Text(
                                            rule.peerName,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                            }
                            if (rule.automataType.isNotEmpty()) {
                                Text(
                                    rule.automataType,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        IconButton(onClick = {
                            scope.launch {
                                val newRules = rules.filterIndexed { i, _ -> i != idx }
                                transport()?.putChannelRouting(newRules)
                                    ?.onSuccess { rules = it.rules }
                                    ?.onFailure { loadError = it.message }
                            }
                        }) {
                            Text(
                                "×",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    if (idx < rules.lastIndex) HorizontalDivider()
                }
            }
        }
    }
}
