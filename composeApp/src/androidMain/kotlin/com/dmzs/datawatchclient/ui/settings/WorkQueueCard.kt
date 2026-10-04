package com.dmzs.datawatchclient.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.ui.common.ProfileResolver
import com.dmzs.datawatchclient.ui.shell.AlertDockChannel
import com.dmzs.datawatchclient.ui.shell.DockLevel
import com.dmzs.datawatchclient.ui.theme.LocalDatawatchColors
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import com.dmzs.datawatchclient.ui.theme.pwaInputTextStyle
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** PWA `pushQueueItem` payload parse: blank = `{}`; anything else must be a JSON object. */
internal fun parseQueuePayload(text: String): Result<JsonObject> =
    if (text.isBlank()) {
        Result.success(JsonObject(emptyMap()))
    } else {
        runCatching { Json.parseToJsonElement(text) as? JsonObject ?: error("payload must be a JSON object") }
    }

/**
 * BL357 — durable role-based Work Queue (PWA Settings → Compute): filter by
 * role / state, list items with delete, and "+ Push Work Item".
 */
@Composable
public fun WorkQueueCard() {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<JsonObject>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var roleFilter by remember { mutableStateOf("") }
    var stateFilter by remember { mutableStateOf("") }
    var pushOpen by remember { mutableStateOf(false) }
    var newRole by remember { mutableStateOf("") }
    var newPayload by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        val (_, t) = ProfileResolver.Default.resolve() ?: return
        t.listQueueJson(roleFilter.trim().ifBlank { null }, stateFilter.ifBlank { null }).fold(
            onSuccess = { arr ->
                items = arr.mapNotNull { it as? JsonObject }
                error = null
            },
            onFailure = { error = it.message ?: "Failed to load queue items." },
        )
    }

    LaunchedEffect(stateFilter) { load() }

    fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.content.orEmpty()

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .pwaCard()
                .padding(12.dp),
    ) {
        PwaSectionTitle(stringResource(R.string.work_queue_title))
        Text(
            stringResource(R.string.work_queue_help),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = roleFilter,
                onValueChange = { roleFilter = it },
                placeholder = { Text(stringResource(R.string.work_queue_role_hint)) },
                singleLine = true,
                textStyle = pwaInputTextStyle(),
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = { scope.launch { load() } }) { Text(stringResource(R.string.action_refresh)) }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(vertical = 4.dp)) {
            items(listOf("", "pending", "claimed", "complete", "failed")) { st ->
                FilterChip(
                    selected = stateFilter == st,
                    onClick = { stateFilter = st },
                    label = { Text(st.ifBlank { "all" }, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
        val list = items
        val dw = LocalDatawatchColors.current
        when {
            error != null -> Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            list == null -> Text(stringResource(R.string.loading_ellipsis), style = MaterialTheme.typography.bodySmall)
            list.isEmpty() ->
                Text(
                    stringResource(R.string.work_queue_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            else ->
                list.forEach { it ->
                    val state = it.s("state")
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(it.s("id"), style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                            Text(
                                listOf(it.s("role"), state, it.s("claimed_by")).filter { v -> v.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color =
                                    when (state) {
                                        "pending" -> dw.success
                                        "claimed" -> dw.warning
                                        "complete" -> MaterialTheme.colorScheme.onSurfaceVariant
                                        else -> MaterialTheme.colorScheme.error
                                    },
                            )
                        }
                        IconButton(onClick = { deleteTarget = it.s("id") }, modifier = Modifier.size(32.dp)) {
                            Text("✕", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
        }
        TextButton(onClick = { pushOpen = !pushOpen }) { Text("+ " + stringResource(R.string.work_queue_push)) }
        if (pushOpen) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = newRole,
                    onValueChange = { newRole = it },
                    placeholder = { Text(stringResource(R.string.work_queue_new_role_hint)) },
                    singleLine = true,
                    textStyle = pwaInputTextStyle(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = newPayload,
                    onValueChange = { newPayload = it },
                    placeholder = { Text("{\"task\":\"do thing\"}") },
                    textStyle = pwaInputTextStyle().copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    enabled = newRole.isNotBlank(),
                    onClick = {
                        val payload =
                            parseQueuePayload(newPayload).getOrElse { e ->
                                AlertDockChannel.post("Invalid payload JSON: ${e.message}", DockLevel.Error)
                                return@OutlinedButton
                            }
                        scope.launch {
                            val (_, t) = ProfileResolver.Default.resolve() ?: return@launch
                            t.pushQueueItem(newRole.trim(), payload).fold(
                                onSuccess = {
                                    newRole = ""
                                    newPayload = ""
                                    load()
                                },
                                onFailure = { AlertDockChannel.post("Failed to push queue item.", DockLevel.Error) },
                            )
                        }
                    },
                ) { Text(stringResource(R.string.work_queue_push)) }
            }
        }
    }

    deleteTarget?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.work_queue_delete_title, id)) },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    scope.launch {
                        val (_, t) = ProfileResolver.Default.resolve() ?: return@launch
                        t.deleteQueueItem(id).onFailure {
                            AlertDockChannel.post("Failed to delete queue item $id", DockLevel.Error)
                        }
                        load()
                    }
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
