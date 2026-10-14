package com.dmzs.datawatchclient.ui.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.diagnostics.BugReportBuilder
import com.dmzs.datawatchclient.diagnostics.BugReportSharer
import com.dmzs.datawatchclient.diagnostics.CrashReporter
import com.dmzs.datawatchclient.ui.theme.PwaCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Next-launch prompt for a pending crash / ANR report: Send, View or Dismiss.
 * Send and Dismiss both delete the report; View shows the redacted text.
 * Hosted at the app root; [enabled] keeps it off the splash screen.
 */
@Composable
public fun CrashReportPrompt(enabled: Boolean) {
    val pending by CrashReporter.pending.collectAsState()
    val file = pending.firstOrNull()
    if (!enabled || file == null) return
    val context = LocalContext.current
    val text by produceState<String?>(initialValue = null, file) {
        value = withContext(Dispatchers.IO) { CrashReporter.read(file) }
    }
    var viewing by remember(file) { mutableStateOf(false) }
    val report = text ?: return

    if (viewing) {
        ReportTextDialog(
            text = report,
            onSend = {
                BugReportSharer.share(context, report)
                CrashReporter.delete(file)
            },
            onClose = { viewing = false },
        )
        return
    }
    AlertDialog(
        onDismissRequest = { /* explicit choice only */ },
        title = { Text(stringResource(R.string.crash_report_title)) },
        text = { Text(stringResource(R.string.crash_report_body)) },
        confirmButton = {
            TextButton(onClick = {
                BugReportSharer.share(context, report)
                CrashReporter.delete(file)
            }) { Text(stringResource(R.string.crash_report_send)) }
        },
        dismissButton = {
            TextButton(onClick = { viewing = true }) { Text(stringResource(R.string.crash_report_view)) }
            TextButton(onClick = { CrashReporter.delete(file) }) { Text(stringResource(R.string.crash_report_dismiss)) }
        },
    )
}

/** Scrollable, selectable report text with Send and Close. */
@Composable
internal fun ReportTextDialog(
    text: String,
    onSend: () -> Unit,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.crash_report_view_title)) },
        text = {
            SelectionContainer {
                Text(
                    text,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    modifier =
                        Modifier
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState()),
                )
            }
        },
        confirmButton = { TextButton(onClick = onSend) { Text(stringResource(R.string.crash_report_send)) } },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.crash_report_close)) } },
    )
}

/**
 * Settings › About › Support: "Send bug report". Builds a report with no crash
 * (recent app log plus the user's note) and shares it the same way as a crash.
 */
@Composable
public fun BugReportCard() {
    var open by remember { mutableStateOf(false) }
    PwaCard(id = "support", title = stringResource(R.string.bug_report_card_title)) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { open = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(stringResource(R.string.bug_report_row_title), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.bug_report_row_summary),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (open) BugReportDialog(onClose = { open = false })
}

@Composable
private fun BugReportDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    var note by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<String?>(null) }
    val shown = preview
    if (shown != null) {
        ReportTextDialog(
            text = shown,
            onSend = {
                BugReportSharer.share(context, shown)
                onClose()
            },
            onClose = { preview = null },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.bug_report_row_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.bug_report_dialog_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { if (it.length <= BugReportBuilder.MAX_NOTE_CHARS) note = it },
                    label = { Text(stringResource(R.string.bug_report_what_happened)) },
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                BugReportSharer.share(context, BugReportBuilder.bugReport(note))
                onClose()
            }) { Text(stringResource(R.string.crash_report_send)) }
        },
        dismissButton = {
            TextButton(onClick = { preview = BugReportBuilder.bugReport(note) }) {
                Text(stringResource(R.string.crash_report_view))
            }
            TextButton(onClick = onClose) { Text(stringResource(R.string.bug_report_cancel)) }
        },
    )
}
