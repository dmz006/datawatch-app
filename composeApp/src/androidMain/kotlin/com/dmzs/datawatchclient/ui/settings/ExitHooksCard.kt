package com.dmzs.datawatchclient.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** One row of `GET /api/exit-hooks` (server `ExitHookEntry`). */
internal data class ExitHookRow(
    val id: String,
    val name: String,
    val action: String,
    val notifySession: String,
    val cooldownSeconds: Int,
    val enabled: Boolean,
    val lastFiredAt: String,
) {
    companion object {
        private fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.content.orEmpty()

        fun from(o: JsonObject): ExitHookRow =
            ExitHookRow(
                id = o.s("id"),
                name = o.s("name"),
                action = o.s("action"),
                notifySession = o.s("notify_session"),
                cooldownSeconds = o.s("cooldown_seconds").toIntOrNull() ?: 0,
                enabled = o.s("enabled") == "true",
                lastFiredAt = o.s("last_fired_at").takeUnless { it.isBlank() || it.startsWith("0001-01-01") }.orEmpty(),
            )
    }
}

/** PWA `createExitHook` body: notify fields only for action=notify; cooldown defaults to 300. */
internal fun exitHookCreateBody(
    name: String,
    action: String,
    notifySession: String,
    notifyMessage: String,
    cooldown: String,
): JsonObject =
    buildJsonObject {
        put("name", name.trim())
        put("action", action)
        put("cooldown_seconds", cooldown.trim().toIntOrNull() ?: 300)
        if (action == "notify" && notifySession.isNotBlank()) {
            put("notify_session", notifySession.trim())
            if (notifyMessage.isNotBlank()) put("notify_message", notifyMessage)
        }
    }

/**
 * BL356 — Exit Hooks (PWA Settings → Compute). Restart or notify when a named
 * session goes zombie / failed / killed. List with enable toggle + delete, and
 * an "+ Add Exit Hook" form.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun ExitHooksCard() {
    val scope = rememberCoroutineScope()
    var hooks by remember { mutableStateOf<List<ExitHookRow>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var formOpen by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var action by remember { mutableStateOf("restart") }
    var actionMenu by remember { mutableStateOf(false) }
    var notifySession by remember { mutableStateOf("") }
    var notifyMessage by remember { mutableStateOf("") }
    var cooldown by remember { mutableStateOf("300") }

    suspend fun load() {
        val (_, t) = ProfileResolver.Default.resolve() ?: return
        t.listExitHooksJson().fold(
            onSuccess = { arr ->
                hooks = arr.mapNotNull { (it as? JsonObject)?.let(ExitHookRow::from) }
                error = null
            },
            onFailure = { error = it.message ?: "Failed to load exit hooks." },
        )
    }

    fun op(block: suspend (com.dmzs.datawatchclient.transport.TransportClient) -> Result<Unit>) {
        scope.launch {
            val (_, t) = ProfileResolver.Default.resolve() ?: return@launch
            block(t).onFailure { e -> AlertDockChannel.post("Exit hook: ${e.message ?: "failed"}", DockLevel.Error) }
            load()
        }
    }

    LaunchedEffect(Unit) { load() }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .pwaCard()
                .padding(12.dp),
    ) {
        PwaSectionTitle(stringResource(R.string.exit_hooks_title))
        Text(
            stringResource(R.string.exit_hooks_help),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        )
        val list = hooks
        when {
            error != null -> Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            list == null -> Text(stringResource(R.string.loading_ellipsis), style = MaterialTheme.typography.bodySmall)
            list.isEmpty() ->
                Text(
                    stringResource(R.string.exit_hooks_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            else ->
                list.forEach { h ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val dw = LocalDatawatchColors.current
                        Text(
                            if (h.enabled) "on" else "off",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (h.enabled) dw.success else MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(end = 6.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(h.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                h.action + (if (h.action == "notify" && h.notifySession.isNotBlank()) " → ${h.notifySession}" else "") +
                                    "  cooldown:${h.cooldownSeconds}s · last: ${h.lastFiredAt.ifBlank { "never" }}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(
                            onClick = { op { it.updateExitHook(h.id, buildJsonObject { put("enabled", !h.enabled) }) } },
                            modifier = Modifier.size(32.dp),
                        ) { Text(if (h.enabled) "⏸" else "▶") }
                        IconButton(onClick = { op { it.deleteExitHook(h.id) } }, modifier = Modifier.size(32.dp)) {
                            Text("✕", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
        }
        TextButton(onClick = { formOpen = !formOpen }) { Text("+ " + stringResource(R.string.exit_hooks_add)) }
        if (formOpen) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text(stringResource(R.string.exit_hooks_name_hint)) },
                    singleLine = true,
                    textStyle = pwaInputTextStyle(),
                    modifier = Modifier.fillMaxWidth(),
                )
                ExposedDropdownMenuBox(expanded = actionMenu, onExpandedChange = { actionMenu = !actionMenu }) {
                    OutlinedTextField(
                        value = action,
                        onValueChange = {},
                        readOnly = true,
                        textStyle = pwaInputTextStyle(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(actionMenu) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    androidx.compose.material3.DropdownMenu(expanded = actionMenu, onDismissRequest = { actionMenu = false }) {
                        listOf(
                            "restart" to R.string.exit_hooks_action_restart,
                            "notify" to R.string.exit_hooks_action_notify,
                        ).forEach { (v, label) ->
                            DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = {
                                action = v
                                actionMenu = false
                            })
                        }
                    }
                }
                if (action == "notify") {
                    OutlinedTextField(
                        value = notifySession,
                        onValueChange = { notifySession = it },
                        placeholder = { Text(stringResource(R.string.exit_hooks_notify_session_hint)) },
                        singleLine = true,
                        textStyle = pwaInputTextStyle(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = notifyMessage,
                        onValueChange = { notifyMessage = it },
                        placeholder = { Text(stringResource(R.string.exit_hooks_notify_message_hint)) },
                        singleLine = true,
                        textStyle = pwaInputTextStyle(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = cooldown,
                    onValueChange = { cooldown = it.filter(Char::isDigit) },
                    placeholder = { Text(stringResource(R.string.exit_hooks_cooldown_hint)) },
                    singleLine = true,
                    textStyle = pwaInputTextStyle(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        val body = exitHookCreateBody(name, action, notifySession, notifyMessage, cooldown)
                        op { t ->
                            t.createExitHook(body).onSuccess {
                                name = ""
                                notifySession = ""
                                notifyMessage = ""
                                cooldown = "300"
                                AlertDockChannel.post("Exit hook added", DockLevel.Success)
                            }
                        }
                    },
                ) { Text(stringResource(R.string.exit_hooks_add)) }
            }
        }
    }
}
