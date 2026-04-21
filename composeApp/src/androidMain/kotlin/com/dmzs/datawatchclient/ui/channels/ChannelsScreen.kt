package com.dmzs.datawatchclient.ui.channels

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Channels tab — surfaces:
 * - LLM backends from /api/backends (active highlighted)
 * - A note pointing at server-side messaging-channel config (Signal/
 *   Telegram/etc. are configured in datawatch.yaml; v3.x doesn't expose
 *   them via REST yet — tracked in PWA but read-only in mobile)
 *
 * Sprint 4 promotes the LLM backend row to a tap-to-switch action once
 * the parent ships POST /api/backends/active.
 */

/**
 * Embedded LLM backend card — used from Settings/LLM sub-tab.
 * (The old bottom-nav "Channels" screen is gone per PWA parity; its
 * content lives here now.)
 */
@Composable
public fun LlmBackendCard(vm: ChannelsViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var configureBackend: String? by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<String?>(null)
    }

    state.banner?.let {
        Surface(color = MaterialTheme.colorScheme.errorContainer) {
            Text(
                it,
                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    BackendsCard(
        llm = state.llm,
        active = state.activeBackend,
        setActiveSupported = state.setActiveSupported,
        onSelect = vm::setActive,
        onConfigure = { configureBackend = it },
    )
    configureBackend?.let { name ->
        com.dmzs.datawatchclient.ui.channels.BackendConfigDialog(
            backendName = name,
            onDismiss = { configureBackend = null },
        )
    }
}

@Composable
private fun BackendsCard(
    llm: List<String>,
    active: String?,
    setActiveSupported: Boolean,
    onSelect: (String) -> Unit,
    onConfigure: (String) -> Unit,
) {
    Card(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "LLM backends",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            if (!setActiveSupported) {
                Text(
                    "Read-only — the server doesn't expose POST /api/backends/active.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (llm.isEmpty()) {
                Text(
                    "No backends reported.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                llm.forEach { name ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = setActiveSupported && name != active) {
                                    onSelect(name)
                                }
                                .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = name == active,
                            onClick =
                                if (setActiveSupported && name != active) {
                                    { onSelect(name) }
                                } else {
                                    null
                                },
                            enabled = setActiveSupported,
                        )
                        Text(
                            name,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f).padding(start = 8.dp),
                        )
                        if (name == active) {
                            AssistChip(
                                onClick = {},
                                label = { Text("active") },
                                leadingIcon = { Icon(Icons.Filled.Check, contentDescription = null) },
                            )
                        }
                        androidx.compose.material3.TextButton(
                            onClick = { onConfigure(name) },
                        ) { Text("Configure…", style = MaterialTheme.typography.labelSmall) }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
