package com.dmzs.datawatchclient.ui.detection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.prefs.ActiveServerStore
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/**
 * Detection filters card — distinct from Output Filters
 * (/api/filters CRUD). These live at `config.detection.*_patterns`
 * as four string arrays + two timing ints. Mirrors PWA
 * `loadDetectionFilters` (app.js line 6003+). Global regex
 * patterns applied to all backends without structured channels.
 *
 * Four sections:
 *   - prompt_patterns: waiting-for-input markers
 *   - completion_patterns: session-done markers
 *   - rate_limit_patterns: rate-limit-hit markers
 *   - input_needed_patterns: explicit input-needed protocol markers
 *
 * Plus two timing knobs (prompt_debounce sec, notify_cooldown sec).
 */
@Composable
public fun DetectionFiltersCard() {
    val scope = rememberCoroutineScope()
    var raw by remember { mutableStateOf<JsonObject?>(null) }
    var promptPatterns by remember { mutableStateOf<List<String>>(emptyList()) }
    var completionPatterns by remember { mutableStateOf<List<String>>(emptyList()) }
    var rateLimitPatterns by remember { mutableStateOf<List<String>>(emptyList()) }
    var inputNeededPatterns by remember { mutableStateOf<List<String>>(emptyList()) }
    var debounce by remember { mutableStateOf("3") }
    var cooldown by remember { mutableStateOf("15") }
    var banner by remember { mutableStateOf<String?>(null) }

    suspend fun resolveProfile() =
        ServiceLocator.profileRepository.observeAll().first().let { profiles ->
            val activeId = ServiceLocator.activeServerStore.get()
            profiles.firstOrNull {
                it.id == activeId && it.enabled && activeId != ActiveServerStore.SENTINEL_ALL_SERVERS
            } ?: profiles.firstOrNull { it.enabled }
        }

    LaunchedEffect(Unit) {
        val profile =
            resolveProfile() ?: run {
                banner = "No enabled server."
                return@LaunchedEffect
            }
        ServiceLocator.transportFor(profile).fetchConfig().fold(
            onSuccess = { cfg ->
                raw = JsonObject(cfg.raw.toMap())
                val d = raw!!["detection"] as? JsonObject
                promptPatterns = d.stringArray("prompt_patterns")
                completionPatterns = d.stringArray("completion_patterns")
                rateLimitPatterns = d.stringArray("rate_limit_patterns")
                inputNeededPatterns = d.stringArray("input_needed_patterns")
                // Match PWA's `cfg.value || default` semantics
                // (app.js:6022-6023) — server sends 0 when the YAML
                // omits the key, and PWA treats 0 as "use default 3 / 15"
                // because 0 is falsy in JS. Kotlin `.content ?: "x"`
                // only kicks in for nulls, so check for "0"/"" too.
                debounce =
                    (d?.get("prompt_debounce") as? JsonPrimitive)?.content
                        ?.takeUnless { it == "0" || it.isBlank() }
                        ?: "3"
                cooldown =
                    (d?.get("notify_cooldown") as? JsonPrimitive)?.content
                        ?.takeUnless { it == "0" || it.isBlank() }
                        ?: "15"
            },
            onFailure = {
                banner = "Config load failed — ${it.message ?: it::class.simpleName}"
            },
        )
    }

    fun buildDetection(existing: JsonObject?): JsonObject =
        buildJsonObject {
            existing?.forEach { (k, v) ->
                if (
                    k != "prompt_patterns" && k != "completion_patterns" &&
                    k != "rate_limit_patterns" && k != "input_needed_patterns" &&
                    k != "prompt_debounce" && k != "notify_cooldown"
                ) {
                    put(k, v)
                }
            }
            put("prompt_patterns", buildJsonArray { promptPatterns.forEach { add(JsonPrimitive(it)) } })
            put("completion_patterns", buildJsonArray { completionPatterns.forEach { add(JsonPrimitive(it)) } })
            put("rate_limit_patterns", buildJsonArray { rateLimitPatterns.forEach { add(JsonPrimitive(it)) } })
            put("input_needed_patterns", buildJsonArray { inputNeededPatterns.forEach { add(JsonPrimitive(it)) } })
            debounce.toIntOrNull()?.let { put("prompt_debounce", JsonPrimitive(it)) }
            cooldown.toIntOrNull()?.let { put("notify_cooldown", JsonPrimitive(it)) }
        }

    fun save(onSuccess: () -> Unit = {}) {
        scope.launch {
            val profile = resolveProfile() ?: return@launch
            // Flat dot-path patch per server's applyConfigPatch
            // contract. Server handler `handleDetectionPatterns` /
            // `applyConfigPatch` cases on dotted top-level keys
            // (`detection.prompt_patterns`, etc.) — nested envelopes
            // silently drop. See dmz006/datawatch-app#1 (S7).
            val patch =
                buildJsonObject {
                    put(
                        "detection.prompt_patterns",
                        buildJsonArray { promptPatterns.forEach { add(JsonPrimitive(it)) } },
                    )
                    put(
                        "detection.completion_patterns",
                        buildJsonArray { completionPatterns.forEach { add(JsonPrimitive(it)) } },
                    )
                    put(
                        "detection.rate_limit_patterns",
                        buildJsonArray { rateLimitPatterns.forEach { add(JsonPrimitive(it)) } },
                    )
                    put(
                        "detection.input_needed_patterns",
                        buildJsonArray { inputNeededPatterns.forEach { add(JsonPrimitive(it)) } },
                    )
                    debounce.toIntOrNull()?.let { put("detection.prompt_debounce", JsonPrimitive(it)) }
                    cooldown.toIntOrNull()?.let { put("detection.notify_cooldown", JsonPrimitive(it)) }
                }
            ServiceLocator.transportFor(profile).writeConfig(patch).fold(
                onSuccess = {
                    banner = "Saved."
                    onSuccess()
                },
                onFailure = {
                    banner = "Save failed — ${it.message ?: it::class.simpleName}"
                },
            )
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).pwaCard(),
    ) {
        PwaSectionTitle("Detection filters")
        banner?.let {
            Text(
                it,
                modifier = Modifier.padding(horizontal = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color =
                    if (it.startsWith("Saved")) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
            )
        }
        Text(
            "Global regex patterns applied to all backends without structured channels.",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        TimingRow("Prompt debounce (sec)", debounce) { debounce = it }
        TimingRow("Notify cooldown (sec)", cooldown) { cooldown = it }

        PatternSection(
            title = "Prompt patterns",
            patterns = promptPatterns,
            defaults = BUILTIN_PROMPT_PATTERNS,
            onAdd = { p ->
                promptPatterns = promptPatterns + p
                save()
            },
            onRemove = { p ->
                promptPatterns = promptPatterns - p
                save()
            },
        )
        PatternSection(
            title = "Completion patterns",
            patterns = completionPatterns,
            defaults = BUILTIN_COMPLETION_PATTERNS,
            onAdd = { p ->
                completionPatterns = completionPatterns + p
                save()
            },
            onRemove = { p ->
                completionPatterns = completionPatterns - p
                save()
            },
        )
        PatternSection(
            title = "Rate-limit patterns",
            patterns = rateLimitPatterns,
            defaults = BUILTIN_RATE_LIMIT_PATTERNS,
            onAdd = { p ->
                rateLimitPatterns = rateLimitPatterns + p
                save()
            },
            onRemove = { p ->
                rateLimitPatterns = rateLimitPatterns - p
                save()
            },
        )
        PatternSection(
            title = "Input-needed patterns",
            patterns = inputNeededPatterns,
            defaults = BUILTIN_INPUT_NEEDED_PATTERNS,
            onAdd = { p ->
                inputNeededPatterns = inputNeededPatterns + p
                save()
            },
            onRemove = { p ->
                inputNeededPatterns = inputNeededPatterns - p
                save()
            },
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = { save() }) { Text("Save timing") }
        }
    }
}

@Composable
private fun TimingRow(
    label: String,
    value: String,
    onChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = value,
            onValueChange = { onChange(it.filter { c -> c.isDigit() }) },
            singleLine = true,
            modifier = Modifier.width(100.dp),
        )
    }
}

@Composable
private fun PatternSection(
    title: String,
    patterns: List<String>,
    defaults: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    var newPattern by remember { mutableStateOf("") }
    // v0.33.15 (B23): when the server returns an empty list, render
    // PWA's built-in defaults grayed-out + non-removable so the user
    // sees what patterns are active implicitly. First add call swaps
    // to the user's own list and the defaults vanish (matches PWA
    // `isUsingDefaults` semantics at app.js:6042).
    val usingDefaults = patterns.isEmpty()
    val displayed = if (usingDefaults) defaults else patterns
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (usingDefaults) {
            Text(
                "Using built-in defaults — add a pattern to override.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        displayed.forEach { p ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    p,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color =
                        if (usingDefaults) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                )
                if (!usingDefaults) {
                    IconButton(onClick = { onRemove(p) }, modifier = Modifier.width(32.dp)) {
                        Icon(
                            Icons.Filled.Delete,
                            "Remove",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            HorizontalDivider()
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newPattern,
                onValueChange = { newPattern = it },
                placeholder = { Text("New pattern") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(
                onClick = {
                    val t = newPattern.trim()
                    if (t.isNotBlank()) {
                        onAdd(t)
                        newPattern = ""
                    }
                },
                enabled = newPattern.isNotBlank(),
            ) { Text("Add") }
        }
    }
}

// Built-in default patterns mirror PWA `builtinDefaults` at app.js:6015.
// Server sends null when the YAML is empty; displaying defaults greyed
// out shows users what's implicitly active. Adding any pattern swaps
// to an explicit user-owned list.
private val BUILTIN_PROMPT_PATTERNS: List<String> =
    listOf(
        "? ", "> ", "$ ", "# ", "[y/N]", "[Y/n]", "Do you want to", "Allow ",
        "Trust ", "(y/n)", "Would you like", "Proceed?", "Enter to confirm",
        "❯", "Ask anything",
    )
private val BUILTIN_COMPLETION_PATTERNS: List<String> =
    listOf("DATAWATCH_COMPLETE:")
private val BUILTIN_RATE_LIMIT_PATTERNS: List<String> =
    listOf(
        "DATAWATCH_RATE_LIMITED:",
        "You've hit your limit",
        "rate limit exceeded",
        "quota exceeded",
    )
private val BUILTIN_INPUT_NEEDED_PATTERNS: List<String> =
    listOf("DATAWATCH_NEEDS_INPUT:")

private fun JsonObject?.stringArray(key: String): List<String> =
    (this?.get(key) as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
        ?: emptyList()
