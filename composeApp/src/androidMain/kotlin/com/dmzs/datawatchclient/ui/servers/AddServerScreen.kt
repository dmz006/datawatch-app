package com.dmzs.datawatchclient.ui.servers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun AddServerScreen(
    onAdded: () -> Unit,
    onCancel: () -> Unit,
    vm: AddServerViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()

    LaunchedEffect(state.added) {
        if (state.added) onAdded()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add server") },
                navigationIcon = { TextButton(onClick = onCancel) { Text("Cancel") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(24.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.displayName,
                onValueChange = vm::onDisplayName,
                label = { Text("Display name") },
                placeholder = { Text("e.g. primary, laptop, workstation") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.baseUrl,
                onValueChange = vm::onBaseUrl,
                label = { Text("Base URL") },
                placeholder = { Text("https://host:8080") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.token,
                onValueChange = vm::onToken,
                label = { Text("Bearer token") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                enabled = !state.noToken,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = state.noToken, onCheckedChange = vm::onNoToken)
                Text(
                    "No bearer token  (insecure — only for test servers)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.noToken) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = state.selfSigned, onCheckedChange = vm::onSelfSigned)
                Text(
                    "  Server uses a self-signed certificate",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (state.error != null) {
                Text(
                    state.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(
                modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                if (state.probing) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 12.dp),
                        strokeWidth = 2.dp,
                    )
                }
                Button(enabled = state.canSubmit && !state.probing, onClick = vm::submit) {
                    Text(if (state.probing) "Checking…" else "Add server")
                }
            }
        }
    }
}
