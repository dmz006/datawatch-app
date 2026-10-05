package com.dmzs.datawatchclient.ui.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.profiles.ProfileEditor
import com.dmzs.datawatchclient.profiles.ProfileField
import com.dmzs.datawatchclient.profiles.ProfileForm
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/**
 * PWA `renderProfileEditor` for project / cluster profiles: one editor with the
 * PWA's form fields and a Home-Assistant-style "YAML view" ↔ "Form view" toggle.
 * Switching serializes the current form state to YAML ([ProfileEditor.toYaml]);
 * switching back (or saving) parses it, and a parse error is shown inline while
 * the text stays put. Keys the form doesn't know are preserved, and literal
 * secrets are masked in both views and restored from [stored] on save.
 *
 * [save] performs the POST (new) / PUT (edit) and returns the server's verdict;
 * on failure the dialog stays open with the error.
 */
@Composable
internal fun ProfileEditorDialog(
    kind: String,
    stored: JsonObject?,
    onDismiss: () -> Unit,
    save: suspend (name: String, body: JsonObject) -> Result<Unit>,
    onSaved: (name: String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val originalName: String? = stored?.let { ProfileForm.valuesFrom(kind, it)["name"] }
    var doc by remember { mutableStateOf(ProfileEditor.workingDoc(kind, stored)) }
    var values by remember { mutableStateOf(ProfileForm.valuesFrom(kind, doc)) }
    var yamlMode by remember { mutableStateOf(false) }
    var yamlText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val context = LocalContext.current

    fun toggle() {
        error = null
        if (!yamlMode) {
            doc = ProfileForm.apply(kind, doc, values)
            yamlText = ProfileEditor.toYaml(kind, doc, emptyMap())
            yamlMode = true
        } else {
            try {
                doc = ProfileEditor.parseYaml(yamlText)
                values = ProfileForm.valuesFrom(kind, doc)
                yamlMode = false
            } catch (e: IllegalArgumentException) {
                error = e.message
            }
        }
    }

    fun submit() {
        error = null
        val body =
            try {
                ProfileEditor.buildBody(kind, doc, values, yamlMode, yamlText, originalName, stored)
            } catch (e: IllegalArgumentException) {
                error = e.message
                return
            }
        val name = originalName ?: ProfileForm.valuesFrom(kind, body)["name"].orEmpty()
        busy = true
        scope.launch {
            save(name, body).fold(
                onSuccess = {
                    busy = false
                    onSaved(name)
                },
                onFailure = {
                    busy = false
                    error = context.getString(R.string.pfe_save_failed, it.message ?: it::class.simpleName.orEmpty())
                },
            )
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 24.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        originalName
                            ?: stringResource(if (kind == "cluster") R.string.pfe_new_cluster else R.string.pfe_new_project),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = { toggle() }, enabled = !busy) {
                        Text(
                            stringResource(if (yamlMode) R.string.pfe_form_view else R.string.pfe_yaml_view),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                Column(
                    modifier =
                        Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(top = 8.dp),
                ) {
                    if (yamlMode) {
                        Text(
                            stringResource(R.string.pfe_yaml_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                        OutlinedTextField(
                            value = yamlText,
                            onValueChange = { yamlText = it },
                            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 320.dp),
                        )
                    } else {
                        ProfileFormFields(
                            kind = kind,
                            values = values,
                            nameLocked = originalName != null,
                            onChange = { k, v -> values = values + (k to v) },
                        )
                    }
                }
                error?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (busy) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp).size(20.dp), strokeWidth = 2.dp)
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    TextButton(onClick = { submit() }, enabled = !busy) { Text(stringResource(R.string.save)) }
                }
            }
        }
    }
}

@Composable
private fun ProfileFormFields(
    kind: String,
    values: Map<String, String>,
    nameLocked: Boolean,
    onChange: (String, String) -> Unit,
) {
    var lastSection = ""
    ProfileForm.fields(kind).forEach { f ->
        if (f.section != lastSection) {
            lastSection = f.section
            if (f.section == ProfileForm.SECTION_AGENT_SETTINGS) {
                Text(
                    stringResource(R.string.profile_agent_settings_section),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                )
            }
        }
        val value = values[f.key].orEmpty()
        val (labelRes, phRes) = fieldStrings(kind, f.key)
        val label = labelRes?.let { stringResource(it) } ?: f.label
        val placeholder = phRes?.let { stringResource(it) } ?: f.placeholder
        when (f.type) {
            ProfileForm.TYPE_BOOL ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = value == "true", onCheckedChange = { onChange(f.key, it.toString()) })
                }
            ProfileForm.TYPE_SELECT -> ProfileSelect(f, label, value) { onChange(f.key, it) }
            else ->
                OutlinedTextField(
                    value = value,
                    onValueChange = { onChange(f.key, it) },
                    label = { Text(label) },
                    placeholder = {
                        if (placeholder.isNotEmpty()) Text(placeholder, style = MaterialTheme.typography.labelSmall)
                    },
                    singleLine = f.key != "description",
                    enabled = !(nameLocked && f.key == "name"),
                    keyboardOptions =
                        KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            keyboardType = if (f.type == ProfileForm.TYPE_NUMBER) KeyboardType.Number else KeyboardType.Text,
                        ),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileSelect(
    field: ProfileField,
    label: String,
    value: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val none = stringResource(R.string.pfe_none)
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    ) {
        OutlinedTextField(
            value = value.ifEmpty { none },
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ProfileForm.optionsFor(field, value).forEach { o ->
                DropdownMenuItem(
                    text = { Text(o.ifEmpty { none }) },
                    onClick = {
                        onSelect(o)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** Localized label / placeholder for a [ProfileForm] field (null → the shared English copy). */
private fun fieldStrings(
    kind: String,
    key: String,
): Pair<Int?, Int?> =
    when (key) {
        "name" -> R.string.pfe_name to (if (kind == "cluster") R.string.pfe_name_cluster_ph else R.string.pfe_name_project_ph)
        "description" -> R.string.pfe_description to R.string.pfe_optional
        "git.url" -> R.string.pfe_git_url to R.string.pfe_git_url_ph
        "git.branch" -> R.string.pfe_git_branch to R.string.pfe_git_branch_ph
        "git.provider" -> R.string.pfe_git_provider to null
        "image_pair.agent" -> R.string.pfe_agent to null
        "image_pair.sidecar" -> R.string.pfe_sidecar to null
        "memory.mode" -> R.string.pfe_memory_mode to null
        "memory.namespace" -> R.string.pfe_memory_namespace to R.string.pfe_memory_namespace_ph
        "memory.shared_with" -> R.string.pfe_memory_shared_with to R.string.pfe_memory_shared_with_ph
        "allow_spawn_children" -> R.string.pfe_allow_spawn to null
        "spawn_budget_total" -> R.string.pfe_spawn_total to R.string.pfe_spawn_total_ph
        "spawn_budget_per_minute" -> R.string.pfe_spawn_per_min to R.string.pfe_spawn_per_min_ph
        "agent_settings.claude_auth_key_secret" -> R.string.profile_claude_key_secret_label to R.string.profile_claude_key_secret_ph
        "agent_settings.opencode_ollama_url" -> R.string.profile_ollama_url_label to R.string.profile_ollama_url_ph
        "agent_settings.opencode_model" -> R.string.profile_ollama_model_label to R.string.profile_ollama_model_ph
        "agent_settings.opencode_models" -> R.string.profile_ollama_models_label to R.string.profile_ollama_models_ph
        "skills" -> R.string.profile_skills_label to R.string.profile_skills_ph
        "kind" -> R.string.pfe_kind to null
        "context" -> R.string.pfe_context to R.string.pfe_context_ph
        "endpoint" -> R.string.pfe_endpoint to R.string.pfe_endpoint_ph
        "namespace" -> R.string.pfe_namespace to R.string.pfe_namespace_ph
        "image_registry" -> R.string.pfe_registry to R.string.pfe_registry_ph
        "image_pull_secret" -> R.string.pfe_pull_secret to R.string.pfe_pull_secret_ph
        "parent_callback_url" -> R.string.pfe_parent_cb to R.string.pfe_parent_cb_ph
        else -> null to null
    }
