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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.ui.compute.resolveActiveTransport
import com.dmzs.datawatchclient.ui.theme.PwaCard
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Project / cluster profile card (PWA `loadProfiles(kind)` → `renderProfilesPanel`):
 * list with Smoke / Edit / Delete, "+ Add", and the full form editor with a
 * "YAML view" toggle ([ProfileEditorDialog]). Create = POST, edit = PUT.
 */
@Composable
public fun KindProfilesCard(
    kind: String,
    title: String,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var profiles by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var banner by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var editing by remember { mutableStateOf<JsonObject?>(null) }
    var creating by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }

    fun err(t: Throwable): String = t.message ?: t::class.simpleName.orEmpty()

    suspend fun refresh() {
        val tr =
            resolveActiveTransport() ?: run {
                banner = context.getString(R.string.pfe_no_server) to false
                return
            }
        tr.listKindProfiles(kind).fold(
            onSuccess = {
                profiles = it
                if (banner?.second == false) banner = null
            },
            onFailure = { banner = context.getString(R.string.pfe_load_failed, title, err(it)) to false },
        )
    }

    LaunchedEffect(kind) { refresh() }

    PwaCard(
        id = "gc_${kind}profiles",
        title = title,
        docsAnchor = "$kind-profiles",
        headerActions = {
            TextButton(onClick = { creating = true }) {
                Text(stringResource(R.string.pfe_add), style = MaterialTheme.typography.labelSmall)
            }
        },
    ) {
        banner?.let { (msg, ok) ->
            Text(
                msg,
                modifier = Modifier.padding(horizontal = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
        if (profiles.isEmpty() && banner?.second != false) {
            Text(
                stringResource(R.string.pfe_empty),
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        profiles.forEach { p ->
            val name = p.stringField("name") ?: "(unnamed)"
            ProfileRow(
                name = name,
                summary = summaryOf(kind, p),
                onSmoke = {
                    scope.launch {
                        val tr = resolveActiveTransport() ?: return@launch
                        tr.smokeKindProfile(kind, name).fold(
                            onSuccess = { banner = "Smoke OK: $name" to true },
                            onFailure = { banner = "Smoke failed — ${err(it)}" to false },
                        )
                    }
                },
                onEdit = { editing = p },
                onDelete = { pendingDelete = name },
            )
            HorizontalDivider()
        }
    }

    pendingDelete?.let { name ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            text = {
                Text(
                    stringResource(
                        if (kind == "cluster") R.string.pfe_delete_cluster_confirm else R.string.pfe_delete_project_confirm,
                        name,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        scope.launch {
                            val tr = resolveActiveTransport() ?: return@launch
                            tr.deleteKindProfile(kind, name).fold(
                                onSuccess = {
                                    banner = context.getString(R.string.pfe_deleted, name) to true
                                    refresh()
                                },
                                onFailure = { banner = context.getString(R.string.pfe_delete_failed, err(it)) to false },
                            )
                        }
                    },
                ) { Text(stringResource(R.string.pfe_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (creating || editing != null) {
        val stored = editing
        ProfileEditorDialog(
            kind = kind,
            stored = stored,
            onDismiss = {
                creating = false
                editing = null
            },
            save = { name, body ->
                val tr = resolveActiveTransport()
                when {
                    tr == null -> Result.failure(IllegalStateException(context.getString(R.string.pfe_no_server)))
                    stored == null -> tr.createKindProfile(kind, body)
                    else -> tr.putKindProfile(kind, name, body)
                }
            },
            onSaved = { name ->
                creating = false
                editing = null
                banner = context.getString(R.string.pfe_saved, name) to true
                scope.launch { refresh() }
            },
        )
    }
}

@Composable
private fun ProfileRow(
    name: String,
    summary: String,
    onSmoke: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        OutlinedButton(onClick = onSmoke, modifier = Modifier.width(80.dp)) {
            Text(stringResource(R.string.pfe_smoke), style = MaterialTheme.typography.labelSmall)
        }
        Spacer(modifier = Modifier.width(6.dp))
        TextButton(onClick = onEdit) {
            Text(stringResource(R.string.pfe_edit), style = MaterialTheme.typography.labelSmall)
        }
        TextButton(onClick = onDelete) {
            Text(
                stringResource(R.string.pfe_delete),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/** PWA `renderProfileRow` summary line. */
private fun summaryOf(
    kind: String,
    p: JsonObject,
): String =
    if (kind == "project") {
        val ip = p["image_pair"] as? JsonObject
        val agent = ip?.stringField("agent") ?: "?"
        val sidecar = ip?.stringField("sidecar")?.ifEmpty { null } ?: "(solo)"
        val repo = (p["git"] as? JsonObject)?.stringField("url").orEmpty()
        "$agent + $sidecar  —  $repo"
    } else {
        val k = p.stringField("kind") ?: "?"
        val ctx = p.stringField("context")?.ifEmpty { null } ?: "-"
        val ns = p.stringField("namespace")?.ifEmpty { null } ?: "default"
        "kind=$k  ctx=$ctx  ns=$ns"
    }

private fun JsonObject.stringField(key: String): String? = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content
