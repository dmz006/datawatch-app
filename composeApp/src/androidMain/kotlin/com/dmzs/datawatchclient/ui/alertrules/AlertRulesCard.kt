package com.dmzs.datawatchclient.ui.alertrules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import com.dmzs.datawatchclient.transport.TransportClient
import com.dmzs.datawatchclient.transport.dto.AlertActionDto
import com.dmzs.datawatchclient.transport.dto.AlertConditionDto
import com.dmzs.datawatchclient.transport.dto.AlertRuleDto
import com.dmzs.datawatchclient.transport.dto.AlertRulesListDto
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Alert Rules card — mirrors PWA Settings > Compute > Alert Rules.
 * Allows creating metric threshold rules that trigger alerts or autoscaling.
 * API: GET/POST/DELETE /api/alert-rules, POST /api/alert-rules/{name}/enable|disable.
 */
@Composable
public fun AlertRulesCard() {
    val scope = rememberCoroutineScope()
    var rules by remember { mutableStateOf<List<AlertRuleDto>>(emptyList()) }
    var banner by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    suspend fun transport(): TransportClient? {
        val profiles = ServiceLocator.profileRepository.observeAll().first()
        val activeId = ServiceLocator.activeServerStore.get()
        val profile = if (activeId == ActiveServerStore.SENTINEL_ALL_SERVERS)
            profiles.firstOrNull { it.enabled }
        else
            profiles.firstOrNull { it.id == activeId && it.enabled }
                ?: profiles.firstOrNull { it.enabled }
        return profile?.let { ServiceLocator.transportFor(it) }
    }

    suspend fun reload() {
        val t = transport() ?: return
        t.listAlertRules().onSuccess { rules = it.rules }.onFailure { banner = it.message }
    }

    LaunchedEffect(Unit) { reload() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .pwaCard()
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PwaSectionTitle("Alert Rules", docsAnchor = "alert-rules")
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { showAddDialog = true }) {
                Text("+ Add Rule", style = MaterialTheme.typography.labelSmall)
            }
        }

        banner?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(4.dp))
        }

        if (rules.isEmpty()) {
            Text(
                "No alert rules configured",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        rules.forEachIndexed { idx, rule ->
            if (idx > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            AlertRuleRow(
                rule = rule,
                onToggle = { enabled ->
                    scope.launch {
                        val t = transport() ?: return@launch
                        if (enabled) t.enableAlertRule(rule.name) else t.disableAlertRule(rule.name)
                        reload()
                    }
                },
                onDelete = {
                    scope.launch {
                        val t = transport() ?: return@launch
                        t.deleteAlertRule(rule.name)
                        reload()
                    }
                },
            )
        }
    }

    if (showAddDialog) {
        AddAlertRuleDialog(
            onDismiss = { showAddDialog = false },
            onSave = { rule ->
                showAddDialog = false
                scope.launch {
                    val t = transport() ?: return@launch
                    t.createAlertRule(rule).onSuccess { reload() }.onFailure { banner = it.message }
                }
            },
        )
    }
}

@Composable
private fun AlertRuleRow(
    rule: AlertRuleDto,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(rule.name, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            Text(
                "${rule.condition.metric} ${rule.condition.operator} ${rule.condition.threshold} → ${rule.action.kind}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            rule.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(
            checked = rule.enabled,
            onCheckedChange = onToggle,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete rule", tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun AddAlertRuleDialog(
    onDismiss: () -> Unit,
    onSave: (AlertRuleDto) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var metric by remember { mutableStateOf("cpu_pct") }
    var operator by remember { mutableStateOf(">") }
    var threshold by remember { mutableStateOf("90") }
    var window by remember { mutableStateOf("60") }
    var action by remember { mutableStateOf("alert") }
    var cooldown by remember { mutableStateOf("300") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Alert Rule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Name (e.g. high-cpu)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description, onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = metric, onValueChange = { metric = it },
                        label = { Text("Metric") },
                        singleLine = true, modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = operator, onValueChange = { operator = it },
                        label = { Text("Op") },
                        singleLine = true, modifier = Modifier.width(56.dp),
                    )
                    OutlinedTextField(
                        value = threshold, onValueChange = { threshold = it },
                        label = { Text("Value") },
                        singleLine = true, modifier = Modifier.weight(0.8f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = window, onValueChange = { window = it },
                        label = { Text("Window (s)") },
                        singleLine = true, modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = cooldown, onValueChange = { cooldown = it },
                        label = { Text("Cooldown (s)") },
                        singleLine = true, modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = action, onValueChange = { action = it },
                    label = { Text("Action (alert/scale_up/scale_down)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isBlank()) return@TextButton
                    onSave(
                        AlertRuleDto(
                            name = name.trim(),
                            description = description.takeIf { it.isNotBlank() },
                            condition = AlertConditionDto(
                                metric = metric.trim(),
                                operator = operator.trim(),
                                threshold = threshold.toDoubleOrNull() ?: 90.0,
                            ),
                            windowSeconds = window.toIntOrNull() ?: 60,
                            action = AlertActionDto(kind = action.trim()),
                            cooldownSeconds = cooldown.toIntOrNull() ?: 300,
                            enabled = true,
                        ),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
