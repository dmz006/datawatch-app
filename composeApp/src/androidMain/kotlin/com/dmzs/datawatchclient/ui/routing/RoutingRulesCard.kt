package com.dmzs.datawatchclient.ui.routing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.transport.TransportClient
import com.dmzs.datawatchclient.transport.dto.RoutingRuleDto
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * v0.80.0 Sprint 11 — Routing Rules card (GET/POST /api/routing-rules,
 * POST /api/routing-rules/test). Mirrors PWA routing rules panel.
 */
@Composable
public fun RoutingRulesCard() {
    val scope = rememberCoroutineScope()
    var rules by remember { mutableStateOf<List<RoutingRuleDto>>(emptyList()) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var pattern by remember { mutableStateOf("") }
    var backend by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var testTask by remember { mutableStateOf("") }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testMatched by remember { mutableStateOf(false) }
    var testRan by remember { mutableStateOf(false) }

    suspend fun transport(): TransportClient? {
        val id = ServiceLocator.activeServerStore.get()
        val p = ServiceLocator.profileRepository.observeAll().first()
            .firstOrNull { it.id == id && it.enabled }
        return if (p != null) ServiceLocator.transportFor(p) else null
    }

    fun load() {
        scope.launch {
            transport()?.getRoutingRules()
                ?.onSuccess { rules = it.rules; loadError = null }
                ?.onFailure { loadError = it.message }
        }
    }
    LaunchedEffect(Unit) { load() }

    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .pwaCard(),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            PwaSectionTitle(stringResource(R.string.routing_rules_title), docsAnchor = "routing-rules")

            // Add rule form
            Text(
                stringResource(R.string.routing_add_rule),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
            OutlinedTextField(
                value = pattern,
                onValueChange = { pattern = it },
                label = { Text(stringResource(R.string.routing_pattern_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = backend,
                onValueChange = { backend = it },
                label = { Text(stringResource(R.string.routing_backend_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = desc,
                onValueChange = { desc = it },
                label = { Text(stringResource(R.string.routing_desc_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = {
                    if (pattern.isBlank() || backend.isBlank()) return@Button
                    scope.launch {
                        val newRule = RoutingRuleDto(pattern = pattern, backend = backend, description = desc)
                        transport()?.setRoutingRules(rules + newRule)
                            ?.onSuccess { rules = it.rules; pattern = ""; backend = ""; desc = "" }
                    }
                },
            ) {
                Text(stringResource(R.string.routing_add_rule))
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // Test routing
            Text(
                stringResource(R.string.routing_test_section),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = testTask,
                    onValueChange = { testTask = it; testRan = false; testResult = null },
                    placeholder = { Text(stringResource(R.string.routing_test_task_hint)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    scope.launch {
                        transport()?.testRouting(testTask)
                            ?.onSuccess { result ->
                                testMatched = result.matched
                                testResult = if (result.matched) result.backend else null
                                testRan = true
                            }
                            ?.onFailure { testRan = true; testMatched = false; testResult = null }
                    }
                }) {
                    Text(stringResource(R.string.routing_test_btn))
                }
            }
            if (testRan) {
                if (testMatched && testResult != null) {
                    Text(
                        stringResource(R.string.routing_matched) + " " + testResult,
                        color = Color(0xFF10B981),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                } else {
                    Text(
                        stringResource(R.string.routing_no_match),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // Rules list
            if (rules.isEmpty()) {
                Text(
                    stringResource(R.string.routing_no_rules),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                rules.forEachIndexed { idx, rule ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    rule.pattern,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                )
                                Spacer(Modifier.width(6.dp))
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    Text(
                                        rule.backend,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            if (rule.description.isNotEmpty()) {
                                Text(
                                    rule.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        IconButton(onClick = {
                            scope.launch {
                                val newRules = rules.filterIndexed { i, _ -> i != idx }
                                transport()?.setRoutingRules(newRules)
                                    ?.onSuccess { rules = it.rules }
                            }
                        }) {
                            Text(
                                "×",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    if (idx < rules.lastIndex) HorizontalDivider()
                }
            }
        }
    }
}
