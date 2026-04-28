package com.dmzs.datawatchclient.ui.autonomous

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.transport.dto.NewPrdRequestDto
import kotlinx.coroutines.flow.first

/**
 * New PRD modal — mirrors PWA v5.26.30 unified-Profile dropdown
 * (issue #11).
 *
 * Profile dropdown options:
 *   1. **— project directory (local checkout) —** (`__dir__` sentinel)
 *   2. Each configured project profile by name.
 *
 * When `__dir__` is selected: show the directory text field +
 * Backend / Effort / Model. When a profile is selected: hide the
 * dir + backend/effort/model row (the F10 image_pair carries the
 * worker LLM); show a Cluster dropdown.
 *
 * Cluster dropdown (PWA v5.26.34):
 *   1. **— Local service instance (daemon-side) —** (empty value)
 *   2. Each configured cluster profile by name.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewPrdDialog(
    onDismiss: () -> Unit,
    onCreate: (NewPrdRequestDto) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }

    var profileMenuOpen by remember { mutableStateOf(false) }
    /** "__dir__" sentinel = project-directory mode; else = profile name. */
    var profile by remember { mutableStateOf("__dir__") }

    var projectDir by remember { mutableStateOf("") }
    var backend by remember { mutableStateOf("") }
    var effort by remember { mutableStateOf("normal") }
    var model by remember { mutableStateOf("") }

    var clusterMenuOpen by remember { mutableStateOf(false) }
    var cluster by remember { mutableStateOf("") }

    var projectProfiles by remember { mutableStateOf<List<String>>(emptyList()) }
    var clusterProfiles by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        runCatching {
            val activeId = ServiceLocator.activeServerStore.get() ?: return@runCatching
            val sp =
                ServiceLocator.profileRepository.observeAll().first()
                    .firstOrNull { it.id == activeId && it.enabled } ?: return@runCatching
            ServiceLocator.transportFor(sp).listKindProfiles("project").onSuccess { list ->
                projectProfiles =
                    list.mapNotNull { obj ->
                        (obj["name"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                    }
            }
            ServiceLocator.transportFor(sp).listKindProfiles("cluster").onSuccess { list ->
                clusterProfiles =
                    list.mapNotNull { obj ->
                        (obj["name"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                    }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New PRD") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (required)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )

                // Profile dropdown.
                Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    OutlinedTextField(
                        value =
                            if (profile == "__dir__") "— project directory —"
                            else profile,
                        onValueChange = {},
                        label = { Text("Profile") },
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 4.dp),
                        trailingIcon = {
                            TextButton(onClick = { profileMenuOpen = !profileMenuOpen }) {
                                Text("▾")
                            }
                        },
                    )
                    DropdownMenu(
                        expanded = profileMenuOpen,
                        onDismissRequest = { profileMenuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("— project directory (local checkout) —") },
                            onClick = {
                                profile = "__dir__"
                                profileMenuOpen = false
                            },
                        )
                        projectProfiles.forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p) },
                                onClick = {
                                    profile = p
                                    profileMenuOpen = false
                                },
                            )
                        }
                    }
                }

                if (profile == "__dir__") {
                    OutlinedTextField(
                        value = projectDir,
                        onValueChange = { projectDir = it },
                        label = { Text("Project directory") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        OutlinedTextField(
                            value = backend,
                            onValueChange = { backend = it },
                            label = { Text("Backend") },
                            singleLine = true,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                        OutlinedTextField(
                            value = effort,
                            onValueChange = { effort = it },
                            label = { Text("Effort") },
                            singleLine = true,
                        )
                    }
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text("Model (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                } else {
                    // Cluster dropdown — only shown when a project profile
                    // is selected.
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        OutlinedTextField(
                            value = cluster.ifEmpty { "— Local service instance —" },
                            onValueChange = {},
                            label = { Text("Cluster") },
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                TextButton(onClick = { clusterMenuOpen = !clusterMenuOpen }) {
                                    Text("▾")
                                }
                            },
                        )
                        DropdownMenu(
                            expanded = clusterMenuOpen,
                            onDismissRequest = { clusterMenuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("— Local service instance (daemon-side) —") },
                                onClick = {
                                    cluster = ""
                                    clusterMenuOpen = false
                                },
                            )
                            clusterProfiles.forEach { c ->
                                DropdownMenuItem(
                                    text = { Text(c) },
                                    onClick = {
                                        cluster = c
                                        clusterMenuOpen = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val req =
                        if (profile == "__dir__") {
                            NewPrdRequestDto(
                                name = name.trim(),
                                title = title.trim().ifBlank { null },
                                projectDir = projectDir.trim().ifBlank { null },
                                backend = backend.trim().ifBlank { null },
                                effort = effort.trim().ifBlank { null },
                                model = model.trim().ifBlank { null },
                            )
                        } else {
                            NewPrdRequestDto(
                                name = name.trim(),
                                title = title.trim().ifBlank { null },
                                projectProfile = profile,
                                clusterProfile = cluster.ifBlank { null },
                            )
                        }
                    if (req.name.isNotBlank()) onCreate(req)
                },
                enabled = name.isNotBlank(),
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
