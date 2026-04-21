package com.dmzs.datawatchclient.ui.profiles

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.prefs.ActiveServerStore
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Shared list-plus-delete-plus-smoke card for F10 profile kinds
 * (`project` or `cluster`). Create / edit happens on the PWA —
 * the profile config shape is rich (nested image_pair, git,
 * memory, kubernetes context) and a dialog for it would be an
 * ADR-0019 violation. Mobile owns list → smoke → delete, which
 * is the common operational path.
 *
 * Matches PWA `loadProfiles(kind)` → `renderProfilesPanel`.
 */
@Composable
public fun KindProfilesCard(
    kind: String,
    title: String,
) {
    val scope = rememberCoroutineScope()
    var profiles by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var banner by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        val profilesList = ServiceLocator.profileRepository.observeAll().first()
        val activeId = ServiceLocator.activeServerStore.get()
        val profile =
            profilesList.firstOrNull {
                it.id == activeId && it.enabled && activeId != ActiveServerStore.SENTINEL_ALL_SERVERS
            } ?: profilesList.firstOrNull { it.enabled } ?: run {
                banner = "No enabled server."
                return
            }
        ServiceLocator.transportFor(profile).listKindProfiles(kind).fold(
            onSuccess = { profiles = it; banner = null },
            onFailure = {
                banner = "$title unavailable — ${it.message ?: it::class.simpleName}"
            },
        )
    }

    LaunchedEffect(kind) { refresh() }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).pwaCard(),
    ) {
        PwaSectionTitle(title)
        banner?.let {
            Text(
                it,
                modifier = Modifier.padding(horizontal = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color =
                    if (it.startsWith("Smoke OK") || it.startsWith("Deleted")) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
            )
        }
        if (profiles.isEmpty() && banner == null) {
            Text(
                "No $kind profiles. Create on the PWA → they'll appear here.",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        profiles.forEach { p ->
            val name = p.stringField("name") ?: "(unnamed)"
            val summary =
                when (kind) {
                    "project" -> {
                        val ip = p["image_pair"] as? JsonObject
                        val agent = ip?.stringField("agent") ?: "?"
                        val sidecar = ip?.stringField("sidecar") ?: "(solo)"
                        val git = (p["git"] as? JsonObject)?.stringField("url").orEmpty()
                        "$agent + $sidecar  —  $git"
                    }
                    "cluster" -> {
                        val k = p.stringField("kind") ?: "?"
                        val ctx = p.stringField("context") ?: "-"
                        val ns = p.stringField("namespace") ?: "default"
                        "kind=$k  ctx=$ctx  ns=$ns"
                    }
                    else -> ""
                }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        summary,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val profile =
                                ServiceLocator.profileRepository.observeAll().first()
                                    .firstOrNull { it.enabled } ?: return@launch
                            ServiceLocator.transportFor(profile)
                                .smokeKindProfile(kind, name).fold(
                                    onSuccess = { banner = "Smoke OK: $name" },
                                    onFailure = {
                                        banner = "Smoke failed — ${it.message ?: it::class.simpleName}"
                                    },
                                )
                        }
                    },
                    modifier = Modifier.width(80.dp),
                ) { Text("Smoke", style = MaterialTheme.typography.labelSmall) }
                Spacer(modifier = Modifier.width(6.dp))
                TextButton(
                    onClick = {
                        scope.launch {
                            val profile =
                                ServiceLocator.profileRepository.observeAll().first()
                                    .firstOrNull { it.enabled } ?: return@launch
                            ServiceLocator.transportFor(profile)
                                .deleteKindProfile(kind, name).fold(
                                    onSuccess = {
                                        banner = "Deleted $name"
                                        refresh()
                                    },
                                    onFailure = {
                                        banner = "Delete failed — ${it.message ?: it::class.simpleName}"
                                    },
                                )
                        }
                    },
                ) {
                    Text(
                        "Delete",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            HorizontalDivider()
        }
    }
}

private fun JsonObject.stringField(key: String): String? =
    (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content
