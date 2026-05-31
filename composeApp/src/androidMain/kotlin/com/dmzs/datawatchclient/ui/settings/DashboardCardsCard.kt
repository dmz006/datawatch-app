package com.dmzs.datawatchclient.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.transport.TransportError
import com.dmzs.datawatchclient.transport.dto.DashboardCardDto
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val VALID_CARD_IDS = listOf(
    "tree", "orbital", "events", "sparklines", "gantt", "heatmap", "guardrails", "ekg", "smoke",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DashboardCardsCard() {
    var cards by remember { mutableStateOf<List<DashboardCardDto>>(emptyList()) }
    var visible by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Fields for the "Add card" section
    var addId by remember { mutableStateOf("") }
    var addCs by remember { mutableStateOf(12f) }
    var addRs by remember { mutableStateOf("") }
    var addIdExpanded by remember { mutableStateOf(false) }

    // Which card row is currently expanded for editing (by index)
    var expandedIndex by remember { mutableStateOf<Int?>(null) }

    fun showError(msg: String?) {
        errorMsg = msg
        if (msg != null) {
            scope.launch {
                delay(3_000)
                errorMsg = null
            }
        }
    }

    suspend fun resolveTransport(): com.dmzs.datawatchclient.transport.TransportClient? {
        val activeId = ServiceLocator.activeServerStore.get()
        return ServiceLocator.profileRepository.observeAll()
            .first { list -> list.any { it.enabled } }
            .let { list ->
                if (activeId == null) list.filter { it.enabled }.firstOrNull()
                else list.firstOrNull { it.id == activeId && it.enabled }
            }
            ?.let { ServiceLocator.transportFor(it) }
    }

    suspend fun reload() {
        val transport = resolveTransport() ?: return
        transport.listDashboardCards()
            .onSuccess { cards = it }
            .onFailure { showError(it.message) }
    }

    LaunchedEffect(Unit) {
        runCatching {
            val activeId = ServiceLocator.activeServerStore.get()
            val sp = ServiceLocator.profileRepository.observeAll()
                .first { list -> list.any { it.enabled } }
                .let { list ->
                    if (activeId == null) list.filter { it.enabled }.firstOrNull()
                    else list.firstOrNull { it.id == activeId && it.enabled }
                } ?: return@runCatching
            val result = ServiceLocator.transportFor(sp).listDashboardCards()
            result.onSuccess { list ->
                cards = list
                visible = true
            }.onFailure { err ->
                visible = err !is TransportError.NotFound
            }
        }
    }

    if (!visible) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .pwaCard()
            .padding(12.dp),
    ) {
        PwaSectionTitle(stringResource(R.string.dash_cards_title), docsAnchor = "dashboard")

        // Error banner
        errorMsg?.let { msg ->
            Text(
                msg,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        if (cards.isEmpty()) {
            Text(
                stringResource(R.string.dash_cards_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
        } else {
            cards.forEachIndexed { idx, card ->
                if (idx > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                DashboardCardRow(
                    card = card,
                    expanded = expandedIndex == idx,
                    onToggleExpand = {
                        expandedIndex = if (expandedIndex == idx) null else idx
                    },
                    onRemove = {
                        scope.launch {
                            runCatching {
                                val transport = resolveTransport() ?: return@runCatching
                                transport.deleteDashboardCard(card.id)
                                    .onSuccess {
                                        if (expandedIndex == idx) expandedIndex = null
                                        reload()
                                    }
                                    .onFailure { showError(it.message) }
                            }
                        }
                    },
                    onSave = { updatedCard ->
                        scope.launch {
                            runCatching {
                                val transport = resolveTransport() ?: return@runCatching
                                transport.updateDashboardCard(card.id, updatedCard)
                                    .onSuccess {
                                        expandedIndex = null
                                        reload()
                                    }
                                    .onFailure { showError(it.message) }
                            }
                        }
                    },
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        // Add card section
        Text(
            stringResource(R.string.dash_card_add),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        // Card ID dropdown for add section
        ExposedDropdownMenuBox(
            expanded = addIdExpanded,
            onExpandedChange = { addIdExpanded = it },
        ) {
            OutlinedTextField(
                value = addId,
                onValueChange = {},
                label = { Text(stringResource(R.string.dash_card_id)) },
                placeholder = { Text(stringResource(R.string.dash_card_id_hint)) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = addIdExpanded) },
            )
            DropdownMenu(expanded = addIdExpanded, onDismissRequest = { addIdExpanded = false }) {
                VALID_CARD_IDS.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(opt) },
                        onClick = { addId = opt; addIdExpanded = false },
                    )
                }
            }
        }

        // Column span slider for add section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.dash_card_cs),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${addCs.toInt()}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Slider(
            value = addCs,
            onValueChange = { addCs = it },
            valueRange = 1f..12f,
            steps = 10,
            modifier = Modifier.fillMaxWidth(),
        )

        // Row span (optional) for add section
        OutlinedTextField(
            value = addRs,
            onValueChange = { v -> if (v.isEmpty() || v.all { it.isDigit() }) addRs = v },
            label = { Text(stringResource(R.string.dash_card_rs)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            singleLine = true,
        )

        FilledTonalButton(
            onClick = {
                val id = addId.trim()
                if (id.isBlank()) return@FilledTonalButton
                val rs = addRs.trim().toIntOrNull()
                scope.launch {
                    runCatching {
                        val transport = resolveTransport() ?: return@runCatching
                        transport.addDashboardCard(DashboardCardDto(id = id, cs = addCs.toInt(), rs = rs))
                            .onSuccess {
                                addId = ""
                                addCs = 12f
                                addRs = ""
                                reload()
                            }
                            .onFailure { showError(it.message) }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            enabled = addId.isNotBlank(),
        ) {
            Text(stringResource(R.string.dash_card_add))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardCardRow(
    card: DashboardCardDto,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onRemove: () -> Unit,
    onSave: (DashboardCardDto) -> Unit,
) {
    // Local edit state — initialized from the card when expanded
    var editId by remember(card.id) { mutableStateOf(card.id) }
    var editCs by remember(card.cs) { mutableStateOf(card.cs.toFloat()) }
    var editRs by remember(card.rs) { mutableStateOf(card.rs?.toString() ?: "") }
    var idDropdownExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Summary row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleExpand() }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                card.id,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                "cs=${card.cs}" + (card.rs?.let { " rs=$it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.dash_card_remove),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }

        // Inline editor (shown when expanded)
        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Card ID picker
                ExposedDropdownMenuBox(
                    expanded = idDropdownExpanded,
                    onExpandedChange = { idDropdownExpanded = it },
                ) {
                    OutlinedTextField(
                        value = editId,
                        onValueChange = {},
                        label = { Text(stringResource(R.string.dash_card_id)) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = idDropdownExpanded) },
                    )
                    DropdownMenu(
                        expanded = idDropdownExpanded,
                        onDismissRequest = { idDropdownExpanded = false },
                    ) {
                        VALID_CARD_IDS.forEach { opt ->
                            DropdownMenuItem(
                                text = { Text(opt) },
                                onClick = { editId = opt; idDropdownExpanded = false },
                            )
                        }
                    }
                }

                // Column span slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(R.string.dash_card_cs),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${editCs.toInt()}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Slider(
                    value = editCs,
                    onValueChange = { editCs = it },
                    valueRange = 1f..12f,
                    steps = 10,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Row span (optional)
                OutlinedTextField(
                    value = editRs,
                    onValueChange = { v -> if (v.isEmpty() || v.all { it.isDigit() }) editRs = v },
                    label = { Text(stringResource(R.string.dash_card_rs)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                OutlinedButton(
                    onClick = {
                        val rs = editRs.trim().toIntOrNull()
                        onSave(DashboardCardDto(id = editId, cs = editCs.toInt(), rs = rs))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.dash_card_save))
                }
            }
        }
    }
}
