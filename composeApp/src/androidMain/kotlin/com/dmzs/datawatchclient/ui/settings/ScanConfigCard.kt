package com.dmzs.datawatchclient.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.transport.dto.ScanConfigDto
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val SEVERITY_OPTIONS = listOf("info", "warning", "error")

@Composable
internal fun ScanConfigCard() {
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf<ScanConfigDto?>(null) }

    LaunchedEffect(Unit) {
        runCatching {
            val activeId = ServiceLocator.activeServerStore.get()
            val sp = ServiceLocator.profileRepository.observeAll()
                .first { list -> list.any { it.enabled } }
                .let { list ->
                    if (activeId == null) list.firstOrNull { it.enabled }
                    else list.firstOrNull { it.id == activeId && it.enabled }
                        ?: list.firstOrNull { it.enabled }
                } ?: return@runCatching
            ServiceLocator.transportFor(sp).getScanConfig().onSuccess { config = it }
        }
    }

    fun save(updated: ScanConfigDto) {
        config = updated
        scope.launch {
            runCatching {
                val activeId = ServiceLocator.activeServerStore.get()
                val sp = ServiceLocator.profileRepository.observeAll()
                    .first { list -> list.any { it.enabled } }
                    .let { list ->
                        if (activeId == null) list.firstOrNull { it.enabled }
                        else list.firstOrNull { it.id == activeId && it.enabled }
                            ?: list.firstOrNull { it.enabled }
                    } ?: return@runCatching
                ServiceLocator.transportFor(sp).updateScanConfig(updated)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .pwaCard()
            .padding(12.dp),
    ) {
        PwaSectionTitle(stringResource(R.string.scan_config_title))
        val cfg = config ?: return@Column
        ScanToggleRow(stringResource(R.string.scan_config_enabled), cfg.enabled) { save(cfg.copy(enabled = it)) }
        ScanToggleRow(stringResource(R.string.scan_config_sast), cfg.sast) { save(cfg.copy(sast = it)) }
        ScanToggleRow(stringResource(R.string.scan_config_secrets), cfg.secrets) { save(cfg.copy(secrets = it)) }
        ScanToggleRow(stringResource(R.string.scan_config_deps), cfg.deps) { save(cfg.copy(deps = it)) }
        ScanToggleRow(stringResource(R.string.scan_grader), cfg.grader) { save(cfg.copy(grader = it)) }
        ScanToggleRow(stringResource(R.string.scan_fix_loop), cfg.fixLoop) { save(cfg.copy(fixLoop = it)) }
        ScanSeverityRow(cfg.failOnSeverity) { save(cfg.copy(failOnSeverity = it)) }
        ScanRetryRow(cfg.maxRetries) { save(cfg.copy(maxRetries = it)) }
        // Run Scan + Run Rules buttons (v0.76.0)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        runCatching {
                            val activeId = ServiceLocator.activeServerStore.get()
                            val sp = ServiceLocator.profileRepository.observeAll()
                                .first { list -> list.any { it.enabled } }
                                .let { list ->
                                    if (activeId == null) list.firstOrNull { it.enabled }
                                    else list.firstOrNull { it.id == activeId && it.enabled }
                                        ?: list.firstOrNull { it.enabled }
                                } ?: return@runCatching
                            // Stub: trigger scan on first PRD available via scan config context
                            ServiceLocator.transportFor(sp).updateScanConfig(cfg)
                        }
                    }
                },
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.action_run_scan)) }
            OutlinedButton(
                onClick = {
                    scope.launch {
                        runCatching {
                            val activeId = ServiceLocator.activeServerStore.get()
                            val sp = ServiceLocator.profileRepository.observeAll()
                                .first { list -> list.any { it.enabled } }
                                .let { list ->
                                    if (activeId == null) list.firstOrNull { it.enabled }
                                    else list.firstOrNull { it.id == activeId && it.enabled }
                                        ?: list.firstOrNull { it.enabled }
                                } ?: return@runCatching
                            // Stub: trigger rules via scan config update (will hook up to dedicated endpoint in later sprint)
                            ServiceLocator.transportFor(sp).updateScanConfig(cfg)
                        }
                    }
                },
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.action_run_rules)) }
        }
    }
}

@Composable
private fun ScanToggleRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

@Composable
private fun ScanSeverityRow(value: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(stringResource(R.string.scan_fail_on), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { expanded = true }) { Text(value) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SEVERITY_OPTIONS.forEach { opt ->
                DropdownMenuItem(text = { Text(opt) }, onClick = { onSelect(opt); expanded = false })
            }
        }
    }
}

@Composable
private fun ScanRetryRow(value: Int, onUpdate: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(stringResource(R.string.scan_max_retries), style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { if (value > 0) onUpdate(value - 1) }) { Text("−") }
            Text("$value", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 4.dp))
            TextButton(onClick = { onUpdate(value + 1) }) { Text("+") }
        }
    }
}
