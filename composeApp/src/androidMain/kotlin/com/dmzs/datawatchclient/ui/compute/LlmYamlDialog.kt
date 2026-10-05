package com.dmzs.datawatchclient.ui.compute

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.ui.theme.LocalDatawatchColors
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private val prettyJson = Json { prettyPrint = true }

/**
 * PWA `_llmOpenYAMLForCurrent` → `openFormEditPopup` raw view: the LLM record
 * (GET /api/llms/{name}) as editable text. The PWA's "YAML" view is
 * pretty-printed JSON (JSON.stringify / JSON.parse), so no YAML library is
 * involved. Test = save then POST /api/llms/{name}/test; Save = PUT, after
 * which the caller reopens the form with the parsed values.
 */
@Composable
internal fun LlmYamlDialog(
    name: String,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val dw = LocalDatawatchColors.current
    var text by remember(name) { mutableStateOf("") }
    // Server copy; literal secrets are masked in [text] and restored from here on save.
    var original by remember(name) { mutableStateOf<JsonObject?>(null) }
    var loaded by remember(name) { mutableStateOf(false) }
    var status by remember { mutableStateOf<Pair<String, Boolean?>?>(null) }
    var busy by remember { mutableStateOf(false) }
    val testingLabel = stringResource(R.string.form_edit_testing)
    val savingLabel = stringResource(R.string.form_edit_saving)
    val testOk = stringResource(R.string.form_edit_test_ok)
    val testFail = stringResource(R.string.form_edit_test_fail)
    val saveFail = stringResource(R.string.form_edit_save_fail)

    LaunchedEffect(name) {
        val tr = resolveActiveTransport() ?: return@LaunchedEffect
        tr.fetchLlmJson(name).fold(
            onSuccess = {
                original = it
                text = prettyJson.encodeToString(JsonObject.serializer(), com.dmzs.datawatchclient.transport.SecretMask.mask(it))
                loaded = true
            },
            onFailure = { status = (it.message ?: "Load failed") to false },
        )
    }

    fun parsed(): JsonObject? {
        val obj = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull()
        if (obj == null) status = "JSON parse error: expected a JSON object" to false
        val base = original ?: return obj
        return obj?.let { com.dmzs.datawatchclient.transport.SecretMask.restore(it, base) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.llm_yaml_title, name)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.llm_edit_yaml_tip),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    enabled = loaded,
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 420.dp),
                )
                status?.let { (msg, ok) ->
                    Text(
                        msg,
                        style = MaterialTheme.typography.labelSmall,
                        color =
                            when (ok) {
                                true -> dw.success
                                false -> MaterialTheme.colorScheme.error
                                null -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(
                    enabled = loaded && !busy,
                    onClick = {
                        val obj = parsed() ?: return@TextButton
                        busy = true
                        status = testingLabel to null
                        scope.launch {
                            val tr = resolveActiveTransport()
                            val saved = tr?.saveLlmJson(name, obj)
                            val saveErr = saved?.exceptionOrNull()
                            status =
                                if (tr == null || saveErr != null) {
                                    "✕ $testFail ${(saveErr?.message ?: "").take(240)}" to false
                                } else {
                                    tr.testLlmJson(name, null).fold(
                                        onSuccess = { o ->
                                            val t = (o["text"] as? JsonPrimitive)?.content ?: o.toString()
                                            "✓ $testOk ${t.take(200)}" to true
                                        },
                                        onFailure = { "✕ $testFail ${(it.message ?: "").take(240)}" to false },
                                    )
                                }
                            busy = false
                        }
                    },
                ) { Text(stringResource(R.string.action_test)) }
                TextButton(
                    enabled = loaded && !busy,
                    onClick = {
                        val obj = parsed() ?: return@TextButton
                        busy = true
                        status = savingLabel to null
                        scope.launch {
                            val r = resolveActiveTransport()?.saveLlmJson(name, obj)
                            busy = false
                            if (r != null && r.isSuccess) {
                                onSaved()
                            } else {
                                status = "✕ $saveFail ${r?.exceptionOrNull()?.message ?: ""}" to false
                            }
                        }
                    },
                ) { Text(stringResource(R.string.action_save)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
