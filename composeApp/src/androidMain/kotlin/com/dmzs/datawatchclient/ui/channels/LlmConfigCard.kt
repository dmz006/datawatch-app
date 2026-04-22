package com.dmzs.datawatchclient.ui.channels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.prefs.ActiveServerStore
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Settings → LLM → "LLM Configuration" card. Lists every backend
 * the active server reports via `/api/backends`, marks the default,
 * and exposes two actions per row: **Configure** (opens
 * [BackendConfigDialog] for model / base-url / api-key edits), and
 * **Make default** (writes `session.llm_backend` through the flat
 * dot-path config patch so the picked backend becomes the default
 * for every new session).
 *
 * Full-field editing for each backend's uncommon knobs (temperature,
 * max_tokens, system_prompt) stays a follow-on — today the dialog
 * covers the three fields the PWA surfaces in its primary LLM UI.
 */
@Composable
public fun LlmConfigCard() {
    val scope = rememberCoroutineScope()
    var backends by remember { mutableStateOf<List<String>>(emptyList()) }
    var active by remember { mutableStateOf<String?>(null) }
    var banner by remember { mutableStateOf<String?>(null) }
    var configuringBackend by remember { mutableStateOf<String?>(null) }
    // Bumped every time we want to re-fetch (after a save). Keyed
    // on this + a Unit trigger so LaunchedEffect re-runs cleanly.
    var refreshTick by remember { mutableStateOf(0) }

    LaunchedEffect(refreshTick) {
        val profile = resolveActiveProfile()
        if (profile == null) {
            banner = "No enabled server."
            return@LaunchedEffect
        }
        ServiceLocator.transportFor(profile).listBackends().fold(
            onSuccess = { v ->
                backends = v.llm
                active = v.active
                banner = null
            },
            onFailure = {
                banner = "Couldn't load backends — ${it.message ?: it::class.simpleName}"
            },
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).pwaCard(),
    ) {
        PwaSectionTitle("LLM Configuration")
        banner?.let {
            Text(
                it,
                modifier = Modifier.padding(horizontal = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (backends.isEmpty() && banner == null) {
            Text(
                "Loading…",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        backends.forEachIndexed { idx, name ->
            if (idx > 0) HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        name,
                        fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (name == active) {
                        Text(
                            "default",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (name != active) {
                    TextButton(onClick = {
                        scope.launch {
                            val profile = resolveActiveProfile() ?: return@launch
                            val patch =
                                buildJsonObject {
                                    put("session.llm_backend", JsonPrimitive(name))
                                }
                            ServiceLocator.transportFor(profile).writeConfig(patch).fold(
                                onSuccess = {
                                    active = name
                                    refreshTick++
                                },
                                onFailure = {
                                    banner = "Couldn't set default — ${it.message ?: it::class.simpleName}"
                                },
                            )
                        }
                    }) { Text("Make default") }
                }
                TextButton(onClick = { configuringBackend = name }) {
                    Text("Configure")
                }
            }
        }
    }

    configuringBackend?.let { name ->
        BackendConfigDialog(
            backendName = name,
            onDismiss = {
                configuringBackend = null
                refreshTick++
            },
        )
    }
}

/**
 * Resolve the profile every LLM action should target: the user's
 * active selection (ignoring the SENTINEL_ALL_SERVERS fan-out mode,
 * which doesn't have a single authoritative backend set), falling
 * back to the first enabled profile.
 */
private suspend fun resolveActiveProfile() =
    ServiceLocator.profileRepository.observeAll().first().let { profiles ->
        val activeId = ServiceLocator.activeServerStore.get()
        profiles.firstOrNull {
            it.id == activeId && it.enabled &&
                activeId != ActiveServerStore.SENTINEL_ALL_SERVERS
        } ?: profiles.firstOrNull { it.enabled }
    }
