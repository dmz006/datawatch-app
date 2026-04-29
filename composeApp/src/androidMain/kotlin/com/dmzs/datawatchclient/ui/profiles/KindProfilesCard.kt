package com.dmzs.datawatchclient.ui.profiles

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
    var editing by remember { mutableStateOf<JsonObject?>(null) }
    var creating by remember { mutableStateOf(false) }

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
            onSuccess = {
                profiles = it
                banner = null
            },
            onFailure = {
                banner = "$title unavailable — ${it.message ?: it::class.simpleName}"
            },
        )
    }

    LaunchedEffect(kind) { refresh() }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).pwaCard(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PwaSectionTitle(title)
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = { creating = true }) {
                Text("+ Add", style = MaterialTheme.typography.labelSmall)
            }
        }
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
                TextButton(onClick = { editing = p }) {
                    Text("Edit", style = MaterialTheme.typography.labelSmall)
                }
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

    if (creating || editing != null) {
        ProfileEditDialog(
            kind = kind,
            existing = editing,
            onDismiss = {
                creating = false
                editing = null
            },
            onSave = { name, body ->
                creating = false
                editing = null
                scope.launch {
                    val profile =
                        ServiceLocator.profileRepository.observeAll().first()
                            .firstOrNull { it.enabled } ?: return@launch
                    ServiceLocator.transportFor(profile)
                        .putKindProfile(kind, name, body).fold(
                            onSuccess = {
                                banner = "Saved $name"
                                refresh()
                            },
                            onFailure = {
                                banner = "Save failed — ${it.message ?: it::class.simpleName}"
                            },
                        )
                }
            },
        )
    }
}

private fun JsonObject.stringField(key: String): String? = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

/**
 * Minimal profile-edit dialog. Mobile exposes the most-common
 * top-level fields — name + description + any primitive
 * string/number fields the server already emits — and preserves
 * nested objects (image_pair, git, memory, kubernetes) verbatim.
 * Users needing deep edits still go through the PWA or raw YAML.
 */
@androidx.compose.runtime.Composable
internal fun ProfileEditDialog(
    kind: String,
    existing: JsonObject?,
    onDismiss: () -> Unit,
    onSave: (String, JsonObject) -> Unit,
) {
    var nameInput by remember { mutableStateOf(existing?.stringField("name").orEmpty()) }
    var description by remember { mutableStateOf(existing?.stringField("description").orEmpty()) }
    val isCreating = existing == null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isCreating) "New $kind profile" else "Edit ${existing?.stringField("name") ?: kind}")
        },
        text = {
            Column {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Name") },
                    singleLine = true,
                    enabled = isCreating,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Text(
                    "Nested fields (image_pair / git / memory / " +
                        "kubernetes context) aren't editable on mobile — " +
                        "they're preserved from the existing profile on " +
                        "Save. Edit those on the PWA.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val n = nameInput.trim()
                    if (n.isBlank()) return@TextButton
                    val body =
                        kotlinx.serialization.json.buildJsonObject {
                            existing?.forEach { (k, v) ->
                                when (k) {
                                    "name" -> put(k, JsonPrimitive(n))
                                    "description" -> put(k, JsonPrimitive(description.trim()))
                                    else -> put(k, v)
                                }
                            }
                            if (existing == null || !existing.containsKey("name")) {
                                put("name", JsonPrimitive(n))
                            }
                            if (existing == null || !existing.containsKey("description")) {
                                if (description.isNotBlank()) {
                                    put("description", JsonPrimitive(description.trim()))
                                }
                            }
                        }
                    onSave(n, body)
                },
                enabled = nameInput.isNotBlank(),
            ) { Text(if (isCreating) "Create" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
