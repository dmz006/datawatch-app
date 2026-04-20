package com.dmzs.datawatchclient.ui.sessions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.prefs.ActiveServerStore
import kotlinx.coroutines.launch

/**
 * Start-session form — v0.11 parity with the PWA's "New session" tab.
 * Re-uses `TransportClient.startSession`. LLM backend picker is read-only in
 * this phase (the active backend governs; per-session override lands in v0.11
 * phase 2.7's active-backend picker).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun NewSessionScreen(
    onStarted: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val profiles by ServiceLocator.profileRepository.observeAll()
        .collectAsState(initial = emptyList())
    val activeId by ServiceLocator.activeServerStore.observe()
        .collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    var task by remember { mutableStateOf("") }
    var selectedProfileId by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var banner by remember { mutableStateOf<String?>(null) }

    // Default-select active profile (or the first enabled one) on first composition.
    LaunchedEffect(profiles, activeId) {
        if (selectedProfileId == null) {
            val enabled = profiles.filter { it.enabled }
            selectedProfileId = when {
                activeId != null && activeId != ActiveServerStore.SENTINEL_ALL_SERVERS ->
                    enabled.firstOrNull { it.id == activeId }?.id
                else -> null
            } ?: enabled.firstOrNull()?.id
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New session") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
        ) {
            banner?.let {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                ) {
                    Text(
                        it,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Text(
                "Task",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            OutlinedTextField(
                value = task,
                onValueChange = { task = it },
                placeholder = { Text("e.g. refactor payments module to use new auth") },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                maxLines = 8,
            )

            Text(
                "Server",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
            )
            ServerPickerDropdown(
                profiles = profiles.filter { it.enabled },
                selectedId = selectedProfileId,
                onSelect = { selectedProfileId = it },
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onCancel, enabled = !submitting) { Text("Cancel") }
                Box(modifier = Modifier.padding(start = 8.dp)) {
                    Button(
                        onClick = {
                            val profile =
                                profiles.firstOrNull { it.id == selectedProfileId }
                                    ?: return@Button
                            if (task.isBlank()) {
                                banner = "Task cannot be empty."
                                return@Button
                            }
                            submitting = true
                            banner = null
                            scope.launch {
                                ServiceLocator.transportFor(profile)
                                    .startSession(task = task.trim())
                                    .fold(
                                        onSuccess = { sessionId ->
                                            submitting = false
                                            onStarted(sessionId)
                                        },
                                        onFailure = { err ->
                                            submitting = false
                                            banner = "Start failed — " +
                                                (err.message ?: err::class.simpleName)
                                        },
                                    )
                            }
                        },
                        enabled = !submitting && task.isNotBlank() && selectedProfileId != null,
                    ) {
                        if (submitting) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        } else {
                            Text("Start")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerPickerDropdown(
    profiles: List<ServerProfile>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = profiles.firstOrNull { it.id == selectedId }
    if (profiles.isEmpty()) {
        Text(
            "No servers configured — add one in Settings first.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        return
    }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selected?.displayName ?: "Pick a server",
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
        )
        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            profiles.forEach { p ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(p.displayName)
                            Text(
                                p.baseUrl,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        onSelect(p.id)
                        expanded = false
                    },
                )
            }
        }
    }
}
