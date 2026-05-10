package com.dmzs.datawatchclient.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.ui.common.MicAttachableTextField
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CouncilPersonaWizardSheet(
    onDismiss: () -> Unit,
    onSave: (name: String, prompt: String, description: String, assistBackend: String?) -> Unit,
    existingPersona: CouncilPersonaForEdit? = null,   // null = create mode
) {
    val stepKeys = listOf(
        R.string.council_wizard_step_focus,
        R.string.council_wizard_step_stance,
        R.string.council_wizard_step_tone,
        R.string.council_wizard_step_antipatterns,
        R.string.council_wizard_step_examples,
    )
    val totalPages = 6  // 5 steps + final tune page
    val pagerState = rememberPagerState(pageCount = { totalPages })
    val scope = rememberCoroutineScope()

    val answers = remember { mutableStateListOf("", "", "", "", "") }
    var personaName by remember { mutableStateOf(existingPersona?.name ?: "") }
    var personaDescription by remember { mutableStateOf(existingPersona?.description ?: "") }
    var selectedBackend by remember { mutableStateOf<String?>(null) }
    var refineInput by remember { mutableStateOf("") }

    // Pre-fill if editing
    LaunchedEffect(existingPersona) {
        if (existingPersona != null) {
            personaName = existingPersona.name
            personaDescription = existingPersona.description
            answers[0] = existingPersona.prompt  // fallback: put full prompt in step 1
        }
    }

    val assembledPrompt = buildString {
        val labels = listOf("Focus", "Stance", "Tone", "Pushback", "Examples")
        answers.forEachIndexed { i, ans ->
            if (ans.isNotBlank()) {
                appendLine("${labels[i]}: $ans")
            }
        }
    }.trim()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxHeight(0.95f),
    ) {
        Column(Modifier.fillMaxSize()) {
            // Title
            Text(
                text = stringResource(R.string.council_wizard_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            // Progress bar
            LinearProgressIndicator(
                progress = { (pagerState.currentPage + 1).toFloat() / totalPages },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                userScrollEnabled = false,
            ) { page ->
                if (page < 5) {
                    // Step page
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = stringResource(stepKeys[page]),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(Modifier.height(8.dp))
                        MicAttachableTextField(
                            value = answers[page],
                            onValueChange = { answers[page] = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 4,
                            maxLines = 10,
                            whisperConfigured = false,
                        )

                        // Backend picker on step 1
                        if (page == 0) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.council_wizard_backend_label),
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("ollama", "openwebui").forEach { backend ->
                                    FilterChip(
                                        selected = selectedBackend == backend,
                                        onClick = {
                                            selectedBackend =
                                                if (selectedBackend == backend) null else backend
                                        },
                                        label = { Text(backend) },
                                    )
                                }
                            }
                        }

                        // AI Refine row
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedTextField(
                                value = refineInput,
                                onValueChange = { refineInput = it },
                                modifier = Modifier.weight(1f),
                                placeholder = {
                                    Text(stringResource(R.string.council_wizard_refine))
                                },
                                singleLine = true,
                            )
                            Button(onClick = { /* TODO: call LLM refine */ }) {
                                Text("→")
                            }
                        }
                    }
                } else {
                    // Final tune page
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.council_wizard_tune),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(Modifier.height(8.dp))
                        // Assembled prompt preview (editable)
                        var editablePrompt by remember(assembledPrompt) {
                            mutableStateOf(assembledPrompt)
                        }
                        OutlinedTextField(
                            value = editablePrompt,
                            onValueChange = { editablePrompt = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            label = { Text(stringResource(R.string.council_wizard_draft_label)) },
                            minLines = 6,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = personaName,
                            onValueChange = { personaName = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Name") },
                            singleLine = true,
                        )
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = personaDescription,
                            onValueChange = { personaDescription = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Description (optional)") },
                            singleLine = true,
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                onSave(personaName, editablePrompt, personaDescription, selectedBackend)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = personaName.isNotBlank(),
                        ) {
                            Text(stringResource(R.string.council_wizard_save))
                        }
                    }
                }
            }

            // Nav row
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                        }
                    },
                    enabled = pagerState.currentPage > 0,
                ) { Text("Back") }
                if (pagerState.currentPage < totalPages - 1) {
                    Button(
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        },
                    ) { Text("Next") }
                }
            }
        }
    }
}

data class CouncilPersonaForEdit(
    val name: String,
    val prompt: String,
    val description: String,
)
