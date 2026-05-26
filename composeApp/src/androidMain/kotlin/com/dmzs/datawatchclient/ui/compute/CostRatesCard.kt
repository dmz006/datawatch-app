package com.dmzs.datawatchclient.ui.compute

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.transport.dto.CostRateDto
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * v0.80.0 Sprint 11 — Cost Rates card (GET/POST /api/cost/rates).
 * Mirrors PWA Cost Rates panel: table of backend → in_per_k / out_per_k
 * with inline editing and Save button.
 */
@Composable
public fun CostRatesCard() {
    val scope = rememberCoroutineScope()
    var rates by remember { mutableStateOf<Map<String, CostRateDto>>(emptyMap()) }
    var editedIn by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var editedOut by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var error by remember { mutableStateOf<String?>(null) }
    var saveStatus by remember { mutableStateOf("") }

    fun load() {
        scope.launch {
            val activeId = ServiceLocator.activeServerStore.get()
            val profile = ServiceLocator.profileRepository.observeAll().first()
                .firstOrNull { it.id == activeId && it.enabled } ?: return@launch
            ServiceLocator.transportFor(profile).getCostRates().fold(
                onSuccess = { dto ->
                    rates = dto.rates
                    editedIn = dto.rates.mapValues { (_, v) -> v.inPerK?.toString() ?: "" }
                    editedOut = dto.rates.mapValues { (_, v) -> v.outPerK?.toString() ?: "" }
                    error = null
                },
                onFailure = { error = it.message },
            )
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
            PwaSectionTitle(stringResource(R.string.cost_rates_title), docsAnchor = "cost-rates")
            if (error != null || rates.isEmpty()) {
                Text(
                    stringResource(R.string.cost_rates_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                return@Column
            }
            // Header row
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(
                    stringResource(R.string.cost_rates_backend),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.cost_rates_in_per_k),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(80.dp),
                )
                Text(
                    stringResource(R.string.cost_rates_out_per_k),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(80.dp),
                )
            }
            rates.keys.sorted().forEach { name ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    OutlinedTextField(
                        value = editedIn[name] ?: "",
                        onValueChange = { editedIn = editedIn + (name to it) },
                        modifier = Modifier.width(80.dp).height(48.dp),
                        textStyle = MaterialTheme.typography.bodySmall,
                        placeholder = {
                            Text(
                                stringResource(R.string.cost_rates_placeholder),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        singleLine = true,
                    )
                    Spacer(Modifier.width(4.dp))
                    OutlinedTextField(
                        value = editedOut[name] ?: "",
                        onValueChange = { editedOut = editedOut + (name to it) },
                        modifier = Modifier.width(80.dp).height(48.dp),
                        textStyle = MaterialTheme.typography.bodySmall,
                        placeholder = {
                            Text(
                                stringResource(R.string.cost_rates_placeholder),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        singleLine = true,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = {
                    scope.launch {
                        val activeId = ServiceLocator.activeServerStore.get()
                        val profile = ServiceLocator.profileRepository.observeAll().first()
                            .firstOrNull { it.id == activeId && it.enabled } ?: return@launch
                        val newRates = rates.keys.associateWith { n ->
                            CostRateDto(
                                inPerK = editedIn[n]?.toDoubleOrNull(),
                                outPerK = editedOut[n]?.toDoubleOrNull(),
                            )
                        }
                        ServiceLocator.transportFor(profile).saveCostRates(newRates).fold(
                            onSuccess = { saveStatus = "Saved"; load() },
                            onFailure = { saveStatus = it.message ?: "Error" },
                        )
                    }
                }) {
                    Text(stringResource(R.string.cost_rates_save))
                }
                if (saveStatus.isNotEmpty()) {
                    Text(
                        saveStatus,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
