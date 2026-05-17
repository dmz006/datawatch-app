package com.dmzs.datawatchclient.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.transport.TransportError
import com.dmzs.datawatchclient.transport.dto.GuardrailLibraryItemDto
import com.dmzs.datawatchclient.transport.dto.GuardrailProfileDto
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
internal fun GuardrailLibraryCard() {
    var library by remember { mutableStateOf<List<GuardrailLibraryItemDto>>(emptyList()) }
    var profiles by remember { mutableStateOf<List<GuardrailProfileDto>>(emptyList()) }
    var visible by remember { mutableStateOf(false) }
    var libraryExpanded by remember { mutableStateOf(false) }
    var expandedProfileId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        runCatching {
            val activeId = ServiceLocator.activeServerStore.get()
            val sp = ServiceLocator.profileRepository.observeAll()
                .first { list -> list.any { it.enabled } }
                .let { list ->
                    if (activeId == null) list.filter { it.enabled }.firstOrNull()
                    else list.firstOrNull { it.id == activeId && it.enabled }
                } ?: return@runCatching
            val transport = ServiceLocator.transportFor(sp)

            val libraryResult = transport.listGuardrailLibrary()
            libraryResult.onSuccess { lib ->
                library = lib
                visible = true
            }.onFailure { err ->
                if (err is TransportError.NotFound) {
                    visible = false
                    return@runCatching
                }
                // Non-404 error: still show card but with empty library
                visible = true
            }

            if (visible) {
                transport.listGuardrailProfiles()
                    .onSuccess { list -> profiles = list }
                // Profiles failure doesn't hide the card; show empty list
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
        // ── Section 1: Guardrail Library (collapsible) ──────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PwaSectionTitle(
                stringResource(R.string.guardrail_library_title),
                modifier = Modifier.weight(1f),
                docsAnchor = "guardrail-library",
            )
            IconButton(
                onClick = { libraryExpanded = !libraryExpanded },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    if (libraryExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (libraryExpanded)
                        stringResource(R.string.guardrail_library_title) else stringResource(R.string.guardrail_library_browse),
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        if (libraryExpanded) {
            if (library.isEmpty()) {
                Text(
                    stringResource(R.string.guardrail_no_library),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                )
            } else {
                library.forEachIndexed { idx, item ->
                    if (idx > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                    GuardrailLibraryItemRow(item)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))

        // ── Section 2: Guardrail Profiles (always visible) ─────────────────
        PwaSectionTitle(stringResource(R.string.guardrail_profiles_title), docsAnchor = "guardrail-profiles")

        if (profiles.isEmpty()) {
            Text(
                stringResource(R.string.guardrail_no_profiles),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            profiles.forEachIndexed { idx, profile ->
                if (idx > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                GuardrailProfileRow(
                    profile = profile,
                    library = library,
                    isExpanded = expandedProfileId == profile.id,
                    onToggleExpand = {
                        expandedProfileId = if (expandedProfileId == profile.id) null else profile.id
                    },
                    onSave = { updated ->
                        scope.launch {
                            runCatching {
                                val activeId = ServiceLocator.activeServerStore.get()
                                val sp = ServiceLocator.profileRepository.observeAll()
                                    .first { list -> list.any { it.enabled } }
                                    .let { list ->
                                        if (activeId == null) list.filter { it.enabled }.firstOrNull()
                                        else list.firstOrNull { it.id == activeId && it.enabled }
                                    } ?: return@runCatching
                                ServiceLocator.transportFor(sp)
                                    .updateGuardrailProfile(updated.id, updated)
                                    .onSuccess { saved ->
                                        profiles = profiles.map { if (it.id == saved.id) saved else it }
                                        expandedProfileId = null
                                    }
                            }
                        }
                    },
                    onDelete = { id ->
                        scope.launch {
                            runCatching {
                                val activeId = ServiceLocator.activeServerStore.get()
                                val sp = ServiceLocator.profileRepository.observeAll()
                                    .first { list -> list.any { it.enabled } }
                                    .let { list ->
                                        if (activeId == null) list.filter { it.enabled }.firstOrNull()
                                        else list.firstOrNull { it.id == activeId && it.enabled }
                                    } ?: return@runCatching
                                ServiceLocator.transportFor(sp)
                                    .deleteGuardrailProfile(id)
                                    .onSuccess {
                                        profiles = profiles.filter { it.id != id }
                                        if (expandedProfileId == id) expandedProfileId = null
                                    }
                            }
                        }
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Add Profile button ──────────────────────────────────────────────
        FilledTonalButton(
            onClick = {
                scope.launch {
                    runCatching {
                        val activeId = ServiceLocator.activeServerStore.get()
                        val sp = ServiceLocator.profileRepository.observeAll()
                            .first { list -> list.any { it.enabled } }
                            .let { list ->
                                if (activeId == null) list.filter { it.enabled }.firstOrNull()
                                else list.firstOrNull { it.id == activeId && it.enabled }
                            } ?: return@runCatching
                        val newProfile = GuardrailProfileDto(
                            id = "",
                            name = "",
                            guardrails = emptyList(),
                            blockOn = emptyList(),
                            warnOn = emptyList(),
                        )
                        ServiceLocator.transportFor(sp)
                            .createGuardrailProfile(newProfile)
                            .onSuccess { created ->
                                profiles = profiles + created
                                expandedProfileId = created.id
                            }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.guardrail_profile_add))
        }
    }
}

@Composable
private fun GuardrailLibraryItemRow(item: GuardrailLibraryItemDto) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.bodySmall)
            if (item.description.isNotEmpty()) {
                Text(
                    item.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        GuardrailKindBadge(item.kind)
    }
}

@Composable
private fun GuardrailKindBadge(kind: String) {
    val (bg, fg) = when (kind.lowercase()) {
        "block" -> Color(0xFFEF4444) to Color(0xFFEF4444)
        "warn" -> Color(0xFFF59E0B) to Color(0xFFF59E0B)
        else -> Color(0xFF10B981) to Color(0xFF10B981)
    }
    Box(
        modifier = Modifier
            .background(bg.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            kind.ifEmpty { "check" },
            style = MaterialTheme.typography.labelSmall,
            color = fg,
        )
    }
}

@Composable
private fun GuardrailProfileRow(
    profile: GuardrailProfileDto,
    library: List<GuardrailLibraryItemDto>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onSave: (GuardrailProfileDto) -> Unit,
    onDelete: (String) -> Unit,
) {
    var editName by remember(profile.id) { mutableStateOf(profile.name) }
    var editGuardrails by remember(profile.id) { mutableStateOf(profile.guardrails.toSet()) }
    var editBlockOn by remember(profile.id) { mutableStateOf(profile.blockOn.toSet()) }
    var editWarnOn by remember(profile.id) { mutableStateOf(profile.warnOn.toSet()) }
    // Free-text fallback when library is empty
    var editGuardrailsText by remember(profile.id) { mutableStateOf(profile.guardrails.joinToString(", ")) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // ── Profile header row ──────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpand)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    profile.name.ifEmpty { stringResource(R.string.guardrail_profile_none_selected) },
                    style = MaterialTheme.typography.bodySmall,
                )
                val checkCount = profile.guardrails.size
                if (checkCount > 0) {
                    Text(
                        "$checkCount checks",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ── Inline editor ───────────────────────────────────────────────────
        if (isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Name field
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text(stringResource(R.string.guardrail_profile_name_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                )

                // Guardrails picker
                Text(
                    stringResource(R.string.guardrail_profile_guardrails_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (library.isEmpty()) {
                    // Fallback: free-text comma-separated
                    OutlinedTextField(
                        value = editGuardrailsText,
                        onValueChange = { editGuardrailsText = it },
                        label = { Text(stringResource(R.string.guardrail_profile_guardrails_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    library.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = item.name in editGuardrails,
                                onCheckedChange = { checked ->
                                    editGuardrails = if (checked) editGuardrails + item.name else editGuardrails - item.name
                                    // Remove from blockOn/warnOn if deselected
                                    if (!checked) {
                                        editBlockOn = editBlockOn - item.name
                                        editWarnOn = editWarnOn - item.name
                                    }
                                },
                            )
                            Text(item.name, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                // Block on (only from selected guardrails or all if library empty)
                val availableForBlockWarn = if (library.isEmpty()) {
                    editGuardrailsText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                } else {
                    editGuardrails.toList()
                }

                if (availableForBlockWarn.isNotEmpty()) {
                    Text(
                        stringResource(R.string.guardrail_block_on_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    availableForBlockWarn.forEach { name ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = name in editBlockOn,
                                onCheckedChange = { checked ->
                                    editBlockOn = if (checked) editBlockOn + name else editBlockOn - name
                                },
                            )
                            Text(name, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Text(
                        stringResource(R.string.guardrail_warn_on_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    availableForBlockWarn.forEach { name ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = name in editWarnOn,
                                onCheckedChange = { checked ->
                                    editWarnOn = if (checked) editWarnOn + name else editWarnOn - name
                                },
                            )
                            Text(name, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                // Save / Delete buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalButton(
                        onClick = {
                            val resolvedGuardrails = if (library.isEmpty()) {
                                editGuardrailsText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            } else {
                                editGuardrails.toList()
                            }
                            onSave(
                                profile.copy(
                                    name = editName,
                                    guardrails = resolvedGuardrails,
                                    blockOn = editBlockOn.toList(),
                                    warnOn = editWarnOn.toList(),
                                )
                            )
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.guardrail_profile_save))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    OutlinedButton(
                        onClick = { onDelete(profile.id) },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.guardrail_profile_delete),
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.guardrail_profile_delete))
                    }
                }
            }
        }
    }
}
