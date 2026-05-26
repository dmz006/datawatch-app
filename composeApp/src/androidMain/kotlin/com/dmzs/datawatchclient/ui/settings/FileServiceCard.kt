package com.dmzs.datawatchclient.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.transport.TransportClient
import com.dmzs.datawatchclient.transport.dto.FileServiceMetaDto
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
public fun FileServiceCard() {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var meta by remember { mutableStateOf<FileServiceMetaDto?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var rootInput by remember { mutableStateOf("") }
    var rootSaving by remember { mutableStateOf(false) }
    var uploadPath by remember { mutableStateOf("") }
    var uploadBusy by remember { mutableStateOf(false) }
    var uploadStatus by remember { mutableStateOf<String?>(null) }
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("") }

    suspend fun activeTransport(): TransportClient? {
        val id = ServiceLocator.activeServerStore.get()
        val enabled = ServiceLocator.profileRepository.observeAll().first().filter { it.enabled }
        val p = enabled.firstOrNull { it.id == id } ?: enabled.firstOrNull()
        return p?.let { ServiceLocator.transportFor(it) }
    }

    suspend fun reload() {
        activeTransport()?.getFileServiceMeta()
            ?.onSuccess { m -> meta = m; loadError = null; rootInput = m.root }
            ?.onFailure { loadError = it.message }
    }

    LaunchedEffect(Unit) { reload() }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedFileUri = uri
            selectedFileName = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
            if (uploadPath.isBlank()) uploadPath = selectedFileName
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .pwaCard(),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PwaSectionTitle(stringResource(R.string.file_service_title), docsAnchor = "file-service", modifier = Modifier.weight(1f))
                IconButton(onClick = { scope.launch { reload() } }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                }
            }

            when {
                loadError != null -> Text(
                    loadError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
                meta == null -> Text(
                    stringResource(R.string.common_loading),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                else -> {
                    val m = meta!!

                    // Editable root path
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = rootInput,
                            onValueChange = { rootInput = it },
                            label = { Text(stringResource(R.string.file_service_root)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            onClick = {
                                scope.launch {
                                    rootSaving = true
                                    activeTransport()?.setFileServiceRoot(rootInput.trim())
                                        ?.onSuccess { reload() }
                                        ?.onFailure { loadError = it.message }
                                    rootSaving = false
                                }
                            },
                            enabled = !rootSaving && rootInput.trim() != m.root,
                        ) { Text("Save") }
                    }

                    // Storage overview
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stringResource(R.string.file_service_discussions), style = MaterialTheme.typography.bodySmall)
                        Text("${m.discussions.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stringResource(R.string.file_service_peers), style = MaterialTheme.typography.bodySmall)
                        Text("${m.peers.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    // File upload section
                    Text(
                        "Upload File",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { filePicker.launch("*/*") },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(if (selectedFileName.isBlank()) "Choose File" else selectedFileName, style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedTextField(
                            value = uploadPath,
                            onValueChange = { uploadPath = it },
                            label = { Text("Path") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Button(
                        onClick = {
                            val uri = selectedFileUri ?: return@Button
                            scope.launch {
                                uploadBusy = true
                                uploadStatus = null
                                runCatching {
                                    val bytes = context.contentResolver.openInputStream(uri)?.readBytes() ?: return@runCatching
                                    val name = selectedFileName.ifBlank { "file" }
                                    val dest = uploadPath.trim().ifBlank { name }
                                    activeTransport()?.uploadFile(bytes, name, dest)
                                        ?.onSuccess {
                                            uploadStatus = "Uploaded to $dest"
                                            selectedFileUri = null
                                            selectedFileName = ""
                                            uploadPath = ""
                                            reload()
                                        }
                                        ?.onFailure { uploadStatus = "Error: ${it.message}" }
                                }.onFailure { uploadStatus = "Error: ${it.message}" }
                                uploadBusy = false
                            }
                        },
                        enabled = !uploadBusy && selectedFileUri != null,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    ) { Text(if (uploadBusy) "Uploading…" else "Upload") }

                    uploadStatus?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (it.startsWith("Error")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
