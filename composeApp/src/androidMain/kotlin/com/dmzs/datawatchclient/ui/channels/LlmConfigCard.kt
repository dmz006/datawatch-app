package com.dmzs.datawatchclient.ui.channels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import com.dmzs.datawatchclient.ui.configfields.LlmBackendSchemas
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Settings → LLM → "LLM Configuration" card. Lists every backend
 * the active server reports via `/api/backends`, merges in
 * `KnownBackends` so a backend that's configured in
 * `backends.*` but not yet active still appears for edit, and
 * renders per-row actions that reflect configured state:
 *
 *  - **Not configured** (no fields populated in `backends.<name>`)
 *    → a single "Configure" button.
 *  - **Configured** → enable/disable Switch + pencil edit icon.
 *
 * Tapping Configure or the pencil opens [BackendConfigDialog],
 * which uses the type-specific schema from
 * [LlmBackendSchemas.sectionFor] with prefill + auto-save. The
 * per-backend "Make default" action was removed on 2026-04-22 —
 * that duplicates `session.llm_backend` on the General tab.
 */
@Composable
public fun LlmConfigCard() {
    val scope = rememberCoroutineScope()
    var backends by remember { mutableStateOf<List<String>>(emptyList()) }
    var defaultBackend by remember { mutableStateOf<String?>(null) }
    var banner by remember { mutableStateOf<String?>(null) }
    var configuring by remember { mutableStateOf<String?>(null) }
    // Per-backend "configured?" + "enabled?" derived from /api/config.
    val configured = remember { mutableStateMapOf<String, Boolean>() }
    val enabled = remember { mutableStateMapOf<String, Boolean>() }
    var refreshTick by remember { mutableStateOf(0) }
    // Re-run fetch whenever the user flips active server anywhere in
    // the app (Sessions tab picker, widget tap-to-cycle, Wear/Auto
    // picker). Before this, Settings only re-loaded when re-entered.
    val activeId by ServiceLocator.activeServerStore.observe()
        .collectAsState(initial = ServiceLocator.activeServerStore.get())

    LaunchedEffect(activeId, refreshTick) {
        val profile =
            resolveActiveProfile() ?: run {
                banner = "No enabled server."
                return@LaunchedEffect
            }
        val transport = ServiceLocator.transportFor(profile)
        // Names the server reports as available.
        transport.listBackends().fold(
            onSuccess = { v ->
                // Merge server-known + app-known so a backend whose
                // config exists but isn't registered still appears
                // (e.g. user configured claude-code via config.yaml
                // without restart). Dedup by name, preserving server
                // order.
                val merged = (v.llm + LlmBackendSchemas.KnownBackends).distinct()
                backends = merged
                defaultBackend = v.active
                banner = null
            },
            onFailure = {
                // Show all known backends even when listBackends fails,
                // so the user can still configure from scratch.
                backends = LlmBackendSchemas.KnownBackends
                banner = "Couldn't load backends — ${it.message ?: it::class.simpleName}"
            },
        )
        // Config drives "configured?" + per-backend enabled state.
        transport.fetchConfig().onSuccess { cfg ->
            val root = JsonObject(cfg.raw.toMap())
            backends.forEach { name ->
                configured[name] = isBackendConfigured(root, name)
                enabled[name] = readBackendEnabled(root, name)
            }
        }
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
            BackendRow(
                name = name,
                isDefault = name == defaultBackend,
                isConfigured = configured[name] == true,
                isEnabled = enabled[name] == true,
                onConfigure = { configuring = name },
                onToggleEnabled = { newValue ->
                    scope.launch {
                        val profile = resolveActiveProfile() ?: return@launch
                        // Use the backend-specific enabled path
                        // (e.g. ollama.enabled, session.claude_enabled).
                        // Previously used `backends.<name>.enabled`
                        // which the server doesn't recognise — every
                        // toggle was a silent no-op. See 2026-04-24
                        // parity audit G45 + LlmBackendSchemas.enabledKey.
                        val key = com.dmzs.datawatchclient.ui.configfields.LlmBackendSchemas.enabledKey(name)
                        val patch =
                            buildJsonObject {
                                put(key, JsonPrimitive(newValue))
                            }
                        ServiceLocator.transportFor(profile).writeConfig(patch).fold(
                            onSuccess = {
                                enabled[name] = newValue
                                refreshTick++
                            },
                            onFailure = {
                                banner = "Toggle failed — ${it.message ?: it::class.simpleName}"
                            },
                        )
                    }
                },
            )
        }
    }

    configuring?.let { name ->
        BackendConfigDialog(
            backendName = name,
            onDismiss = {
                configuring = null
                refreshTick++
            },
        )
    }
}

@Composable
private fun BackendRow(
    name: String,
    isDefault: Boolean,
    isConfigured: Boolean,
    isEnabled: Boolean,
    onConfigure: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
) {
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
            if (isDefault) {
                Text(
                    "default",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (isConfigured) {
            // Compact per-row on/off + pencil. Matches PWA's
            // "configured backend" cluster: toggle + edit icon.
            Switch(
                checked = isEnabled,
                onCheckedChange = onToggleEnabled,
            )
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = onConfigure) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = "Edit $name",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        } else {
            OutlinedButton(onClick = onConfigure) {
                Text("Configure", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/**
 * Is this backend "configured"? Tests whether any field under the
 * backend's canonical section (`ollama.*`, `session.*` for
 * claude-code, `shell_backend.*` for shell, etc.) has a non-blank /
 * truthy value, excluding the enable toggle itself. Updated 2026-04-24
 * per parity audit G45 — the prior implementation scanned
 * `backends.<name>.*` which is not where the server stores these.
 */
private fun isBackendConfigured(
    config: JsonObject,
    name: String,
): Boolean {
    val section = sectionNameFor(name)
    val enabledLeaf = enabledLeafFor(name)
    // /api/config returns a nested tree; iterate the section object
    // looking for any non-enabled key with a real value.
    val sec = config[section] as? JsonObject ?: return false
    return sec.entries.any { (k, v) -> k != enabledLeaf && hasValue(v) }
}

private fun readBackendEnabled(
    config: JsonObject,
    name: String,
): Boolean {
    val section = sectionNameFor(name)
    val leaf = enabledLeafFor(name)
    val sec = config[section] as? JsonObject ?: return false
    return sec[leaf]?.let { asBool(it) } ?: false
}

/** Mirrors [com.dmzs.datawatchclient.ui.configfields.LlmBackendSchemas] — keep in sync. */
private fun sectionNameFor(name: String): String =
    when (name.lowercase()) {
        "claude-code", "claude_code", "claudecode" -> "session"
        "opencode-acp", "opencode_acp" -> "opencode_acp"
        "opencode-prompt", "opencode_prompt" -> "opencode_prompt"
        "shell" -> "shell_backend"
        else -> name.lowercase()
    }

private fun enabledLeafFor(name: String): String =
    when (name.lowercase()) {
        "claude-code", "claude_code", "claudecode" -> "claude_enabled"
        else -> "enabled"
    }

private fun hasValue(e: kotlinx.serialization.json.JsonElement): Boolean =
    when (e) {
        is JsonPrimitive ->
            when {
                e.isString -> e.content.isNotBlank()
                else ->
                    e.content.isNotBlank() && e.content != "null" && e.content != "false" &&
                        e.content != "0"
            }
        else -> true
    }

private fun asBool(e: kotlinx.serialization.json.JsonElement): Boolean =
    (e as? JsonPrimitive)?.content?.toBooleanStrictOrNull() == true

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
