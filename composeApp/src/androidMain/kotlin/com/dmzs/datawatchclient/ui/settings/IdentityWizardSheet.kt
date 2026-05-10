package com.dmzs.datawatchclient.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.transport.dto.IdentityDto
import kotlinx.coroutines.launch

private const val WIZARD_PAGE_COUNT = 6

/**
 * 6-page wizard for editing the Identity profile. Pages:
 *  0: Role (singleLine)
 *  1: North-star goals (multiline, one per line)
 *  2: Current projects (multiline, one per line)
 *  3: Values (multiline, one per line)
 *  4: Current focus (multiline)
 *  5: Context notes + Finish button
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun IdentityWizardSheet(
    initial: IdentityDto,
    onDismiss: () -> Unit,
    onFinish: (IdentityDto) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pagerState = rememberPagerState(pageCount = { WIZARD_PAGE_COUNT })
    val scope = rememberCoroutineScope()

    // Mutable copies for each field
    var role = initial.role
    var northStarText = initial.northStarGoals.joinToString("\n")
    var projectsText = initial.currentProjects.joinToString("\n")
    var valuesText = initial.values.joinToString("\n")
    var currentFocus = initial.currentFocus
    var contextNotes = initial.contextNotes

    // Use remember for the mutable text fields so they survive recompositions
    val roleState = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(role) }
    val northStarState = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(northStarText) }
    val projectsState = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(projectsText) }
    val valuesState = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(valuesText) }
    val focusState = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(currentFocus) }
    val notesState = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(contextNotes) }

    val pageTitles = listOf(
        stringResource(R.string.identity_wizard_page_role),
        stringResource(R.string.identity_wizard_page_goals),
        stringResource(R.string.identity_wizard_page_projects),
        stringResource(R.string.identity_wizard_page_values),
        stringResource(R.string.identity_wizard_page_focus),
        stringResource(R.string.identity_wizard_page_notes),
    )

    fun buildDto() = IdentityDto(
        role = roleState.value.trim(),
        northStarGoals = northStarState.value.lines().map { it.trim() }.filter { it.isNotEmpty() },
        currentProjects = projectsState.value.lines().map { it.trim() }.filter { it.isNotEmpty() },
        values = valuesState.value.lines().map { it.trim() }.filter { it.isNotEmpty() },
        currentFocus = focusState.value.trim(),
        contextNotes = notesState.value.trim(),
        updatedAt = initial.updatedAt,
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // Header
            Text(
                pageTitles.getOrElse(pagerState.currentPage) { "" },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            // Progress bar
            LinearProgressIndicator(
                progress = { (pagerState.currentPage + 1).toFloat() / WIZARD_PAGE_COUNT },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            )

            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false,
            ) { page ->
                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    when (page) {
                        0 -> OutlinedTextField(
                            value = roleState.value,
                            onValueChange = { roleState.value = it },
                            label = { Text(stringResource(R.string.identity_role_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        1 -> OutlinedTextField(
                            value = northStarState.value,
                            onValueChange = { northStarState.value = it },
                            label = { Text(stringResource(R.string.identity_wizard_page_goals)) },
                            placeholder = { Text(stringResource(R.string.identity_wizard_one_per_line)) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 4,
                        )
                        2 -> OutlinedTextField(
                            value = projectsState.value,
                            onValueChange = { projectsState.value = it },
                            label = { Text(stringResource(R.string.identity_wizard_page_projects)) },
                            placeholder = { Text(stringResource(R.string.identity_wizard_one_per_line)) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 4,
                        )
                        3 -> OutlinedTextField(
                            value = valuesState.value,
                            onValueChange = { valuesState.value = it },
                            label = { Text(stringResource(R.string.identity_wizard_page_values)) },
                            placeholder = { Text(stringResource(R.string.identity_wizard_one_per_line)) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 4,
                        )
                        4 -> OutlinedTextField(
                            value = focusState.value,
                            onValueChange = { focusState.value = it },
                            label = { Text(stringResource(R.string.identity_focus_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 4,
                        )
                        5 -> OutlinedTextField(
                            value = notesState.value,
                            onValueChange = { notesState.value = it },
                            label = { Text(stringResource(R.string.identity_notes_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 4,
                        )
                    }
                }
            }

            // Navigation buttons
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (pagerState.currentPage > 0) {
                    TextButton(onClick = {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                    }) { Text(stringResource(R.string.identity_wizard_back)) }
                }
                Spacer(Modifier.weight(1f))
                if (pagerState.currentPage < WIZARD_PAGE_COUNT - 1) {
                    Button(onClick = {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }) { Text(stringResource(R.string.identity_wizard_next)) }
                } else {
                    Button(onClick = { onFinish(buildDto()) }) {
                        Text(stringResource(R.string.identity_wizard_finish))
                    }
                }
            }
        }
    }
}
