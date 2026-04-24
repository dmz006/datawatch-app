package com.dmzs.datawatchclient.ui.configfields

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.prefs.ActiveServerStore
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Generic renderer for a [ConfigSection]. Reads `/api/config`,
 * pulls current values for the section's fields (supports dotted
 * keys like `session.log_level`), renders each field with its
 * native widget, and writes the merged document back via
 * `PUT /api/config` on Save.
 *
 * One card per section — multiple sections compose vertically.
 * Empty-preserving password inputs: blank password fields keep the
 * existing stored value rather than nuke it on Save.
 */
@Composable
public fun ConfigFieldsPanel(section: ConfigSection) {
    val scope = rememberCoroutineScope()
    var rawConfig by remember { mutableStateOf<JsonObject?>(null) }
    var banner by remember { mutableStateOf<String?>(null) }
    // Per-field string state — covers all field types uniformly.
    val values = remember { mutableStateMapOf<String, String>() }
    // Snapshot of what's on the server, used to decide "dirty" diff
    // for autosave so we don't PUT values that haven't changed.
    val loaded = remember { mutableStateMapOf<String, String>() }
    var savingCount by remember { mutableStateOf(0) }
    // For interface/llm selects that need async data.
    var interfaces by remember { mutableStateOf<List<String>>(emptyList()) }
    var backends by remember { mutableStateOf<List<String>>(emptyList()) }

    suspend fun resolveProfile(): ServerProfile? {
        val profiles = ServiceLocator.profileRepository.observeAll().first()
        val activeId = ServiceLocator.activeServerStore.get()
        return profiles.firstOrNull {
            it.id == activeId && it.enabled && activeId != ActiveServerStore.SENTINEL_ALL_SERVERS
        } ?: profiles.firstOrNull { it.enabled }
    }

    // Observe active-server changes so cards reload with the newly
    // selected server's config without losing the user's tab /
    // scroll position. Before 2026-04-22 this only re-ran on
    // section-id change, forcing users to leave Settings and come
    // back to see new-server values.
    val activeId by ServiceLocator.activeServerStore.observe()
        .collectAsState(initial = ServiceLocator.activeServerStore.get())

    LaunchedEffect(section.id, activeId) {
        // Clear local state so we don't briefly show the old
        // server's values while the fetch is in flight.
        rawConfig = null
        values.clear()
        loaded.clear()
        val profile = resolveProfile() ?: run {
            banner = "No enabled server."
            return@LaunchedEffect
        }
        val transport = ServiceLocator.transportFor(profile)
        transport.fetchConfig().fold(
            onSuccess = { cfg ->
                val raw = JsonObject(cfg.raw.toMap())
                rawConfig = raw
                section.fields.forEach { f ->
                    val v = readDottedAsString(raw, f.key)
                    values[f.key] = v
                    loaded[f.key] = v
                }
            },
            onFailure = { banner = "Config load failed — ${it.message ?: it::class.simpleName}" },
        )
        // Pre-populate async select options if any field needs them.
        if (section.fields.any { it is ConfigField.InterfaceSelect }) {
            transport.listInterfaces().onSuccess { list ->
                interfaces =
                    list.mapNotNull {
                        (it["name"] as? JsonPrimitive)?.takeIf { p -> p.isString }?.content
                    } + listOf("0.0.0.0", "127.0.0.1")
            }
        }
        if (section.fields.any { it is ConfigField.LlmSelect }) {
            transport.listBackends().onSuccess { v -> backends = v.llm }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).pwaCard(),
    ) {
        PwaSectionTitle(section.title)
        banner?.let {
            // Only error banners fire after the autosave switch (S4). Drop
            // the old "Saved." success path — the inline "Saving…" label
            // is the sole positive indicator, matching PWA which doesn't
            // toast on successful save either.
            Text(
                it,
                modifier = Modifier.padding(horizontal = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (rawConfig == null && banner == null) {
            Text(
                "Loading…",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        section.fields.forEach { field ->
            FieldRow(
                field = field,
                value = values[field.key].orEmpty(),
                onChange = { v -> values[field.key] = v },
                interfaces = interfaces,
                backends = backends,
            )
        }
        // Tiny "Saving…" / "Saved" indicator where the Save button used
        // to live. PWA auto-saves on every field change; we match by
        // diffing values against the loaded snapshot, debouncing 500 ms,
        // and sending a **flat dot-path patch** (server's
        // applyConfigPatch cases on dotted top-level keys; nested trees
        // are silently dropped — that's the v0.33.5-and-earlier bug).
        if (savingCount > 0) {
            Text(
                "Saving…",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    // Compute the dirty set and debounce-save when anything changes.
    val dirtyKey by remember(section.id) {
        derivedStateOf {
            section.fields
                .filter { f -> (values[f.key] ?: "") != (loaded[f.key] ?: "") }
                .joinToString(",") { f -> "${f.key}=${values[f.key].orEmpty()}" }
        }
    }
    LaunchedEffect(dirtyKey) {
        if (dirtyKey.isEmpty()) return@LaunchedEffect
        kotlinx.coroutines.delay(SAVE_DEBOUNCE_MS)
        val patch = buildDotPatch(section.fields, values, loaded) ?: return@LaunchedEffect
        val profile = resolveProfile() ?: return@LaunchedEffect
        savingCount += 1
        ServiceLocator.transportFor(profile).writeConfig(patch).fold(
            onSuccess = {
                // Promote the changed values into the loaded snapshot so
                // they're no longer "dirty" and we don't re-PUT them.
                section.fields.forEach { f ->
                    values[f.key]?.let { v -> loaded[f.key] = v }
                }
                banner = null
            },
            onFailure = { banner = "Save failed — ${it.message ?: it::class.simpleName}" },
        )
        savingCount = (savingCount - 1).coerceAtLeast(0)
    }
}

private const val SAVE_DEBOUNCE_MS: Long = 500

/**
 * Tight phone-sized widget rhythm — every settings row uses the
 * same `ROW_PADDING`, `INPUT_WIDTH`, and `INPUT_TEXT_STYLE` so
 * labels and inputs align across the whole Settings surface.
 * Matches PWA's 13px / 11px `.settings-row` density rather than
 * Material3's default 16sp body text which looked oversized on
 * real devices (S1/S2/S3).
 */
private val ROW_PADDING_H = 12.dp
private val ROW_PADDING_V = 4.dp
private val INPUT_WIDTH = 160.dp

@Composable
private fun rowLabelStyle() =
    MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp)

@Composable
private fun inputTextStyle() =
    MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp)

@Composable
private fun FieldRow(
    field: ConfigField,
    value: String,
    onChange: (String) -> Unit,
    interfaces: List<String>,
    backends: List<String>,
) {
    when (field) {
        is ConfigField.Toggle -> {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = ROW_PADDING_H, vertical = ROW_PADDING_V),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(field.label, modifier = Modifier.weight(1f), style = rowLabelStyle())
                Switch(
                    checked = value.toBooleanStrictOrNull() ?: false,
                    onCheckedChange = { on -> onChange(on.toString()) },
                )
            }
        }
        is ConfigField.NumberField -> InputRow(field.label) {
            CompactInput(
                value = value,
                onChange = { s -> onChange(s.filter { it.isDigit() || it == '-' }) },
                placeholder = field.placeholder,
                password = false,
                keyboardType = KeyboardType.Number,
            )
        }
        is ConfigField.TextField -> InputRow(field.label) {
            CompactInput(
                value = value,
                onChange = onChange,
                placeholder = field.placeholder,
                password = field.password,
                keyboardType = KeyboardType.Text,
            )
        }
        is ConfigField.Select -> SelectRow(field.label, field.options, value, onChange)
        is ConfigField.InterfaceSelect -> SelectRow(field.label, interfaces, value, onChange)
        is ConfigField.LlmSelect -> SelectRow(field.label, backends, value, onChange)
    }
}

@Composable
private fun InputRow(label: String, trailing: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = ROW_PADDING_H, vertical = ROW_PADDING_V),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = rowLabelStyle())
        trailing()
    }
}

@Composable
private fun SelectRow(
    label: String,
    options: List<String>,
    value: String,
    onChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = ROW_PADDING_H, vertical = ROW_PADDING_V),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = rowLabelStyle())
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.width(INPUT_WIDTH),
            ) {
                Text(value.ifBlank { "(default)" }, style = inputTextStyle())
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                if (options.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("(loading…)") },
                        onClick = { expanded = false },
                    )
                }
                options.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(opt) },
                        onClick = {
                            onChange(opt)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

/** Read a dotted key like `session.log_level` from the config root, stringified. */
internal fun readDottedAsString(root: JsonObject, dottedKey: String): String {
    val parts = dottedKey.split('.')
    var cur: JsonElement = root
    for (p in parts) {
        val obj = cur as? JsonObject ?: return ""
        cur = obj[p] ?: return ""
    }
    return when (cur) {
        is JsonPrimitive ->
            if (cur.isString) cur.content
            else cur.content.removeSurrounding("\"")
        else -> cur.toString()
    }
}

/**
 * Build the flat dot-path patch the server's `applyConfigPatch`
 * actually accepts. Returns `null` if nothing has actually changed
 * relative to [loaded]. Blank password fields are skipped
 * (ADR-0019 empty-preserving rule — matches PWA).
 *
 * Matches parent `dmz006/datawatch internal/server/api.go`
 * `applyConfigPatch`: a `map[string]interface{}` keyed by dotted
 * path like `"ntfy.enabled": true`. Our previous `mergeValues`
 * produced a nested tree, which the server silently ignores —
 * that's why saves never persisted (#S7).
 */
internal fun buildDotPatch(
    fields: List<ConfigField>,
    values: Map<String, String>,
    loaded: Map<String, String>,
): JsonObject? {
    val changed: List<Pair<String, JsonElement>> =
        fields.mapNotNull { field ->
            val key = field.key
            val raw = values[key] ?: return@mapNotNull null
            val prev = loaded[key] ?: ""
            if (raw == prev) return@mapNotNull null
            val trimmed = raw.trim()
            if (field is ConfigField.TextField && field.password && trimmed.isEmpty()) {
                return@mapNotNull null
            }
            val el: JsonElement =
                when (field) {
                    is ConfigField.Toggle -> JsonPrimitive(trimmed.toBooleanStrictOrNull() ?: false)
                    is ConfigField.NumberField -> JsonPrimitive(trimmed.toIntOrNull() ?: 0)
                    is ConfigField.TextField -> JsonPrimitive(trimmed)
                    is ConfigField.Select,
                    is ConfigField.InterfaceSelect,
                    is ConfigField.LlmSelect,
                    -> JsonPrimitive(trimmed)
                }
            key to el
        }
    if (changed.isEmpty()) return null
    return buildJsonObject { changed.forEach { (k, v) -> put(k, v) } }
}

/**
 * Compact text input designed to match PWA `.form-input` density:
 * 36 dp total height, 13 sp text, 6 dp vertical content padding.
 * Standard M3 OutlinedTextField enforces a 56 dp minimum height
 * and 16 dp internal padding which made the box dwarf the text
 * (user-flagged 2026-04-23 "buttons + input windows way larger
 * than their text").
 *
 * Built on BasicTextField inside a bordered Box. Loses the floating
 * label animation — acceptable because the row already has a leading
 * label column, and the floating label was never rendered anyway
 * (we pass `placeholder`, not `label`).
 */
@Composable
private fun CompactInput(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String?,
    password: Boolean,
    keyboardType: KeyboardType,
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val placeholderColor = MaterialTheme.colorScheme.onSurfaceVariant
    val borderColor = MaterialTheme.colorScheme.outline
    val bgColor = MaterialTheme.colorScheme.surface
    val textStyle =
        LocalTextStyle.current.copy(
            color = textColor,
            fontSize = 13.sp,
        )
    Box(
        modifier =
            Modifier
                .width(INPUT_WIDTH)
                .height(36.dp)
                .background(bgColor, RoundedCornerShape(6.dp))
                .border(1.dp, borderColor, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = textStyle,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            visualTransformation =
                if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (value.isEmpty() && !placeholder.isNullOrEmpty()) {
                    Text(
                        placeholder,
                        style = textStyle.copy(color = placeholderColor),
                    )
                }
                inner()
            },
        )
    }
}

