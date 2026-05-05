package com.dmzs.datawatchclient.ui.autonomous

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.transport.dto.RuleProposalDto
import com.dmzs.datawatchclient.transport.dto.ScanFindingDto
import com.dmzs.datawatchclient.transport.dto.ScanResultDto

@Composable
internal fun ScanResultCard(
    scanResult: ScanResultDto?,
    scanLoading: Boolean,
    onTriggerScan: (() -> Unit)?,
    onCreateFixPrd: (() -> Unit)?,
    onProposeRules: (() -> Unit)?,
    proposedRules: RuleProposalDto?,
    onDismissProposedRules: (() -> Unit)?,
) {
    var findingsExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Security scan", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            if (scanLoading) {
                Text("…", style = MaterialTheme.typography.labelSmall)
            } else if (onTriggerScan != null) {
                TextButton(onClick = onTriggerScan) { Text(stringResource(R.string.scan_run), style = MaterialTheme.typography.labelSmall) }
            }
        }
        if (scanResult != null) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { findingsExpanded = !findingsExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                VerdictBadge(scanResult.verdict)
                Text(
                    stringResource(R.string.scan_findings_count, scanResult.findings.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (scanResult.findings.isNotEmpty()) {
                    Text(if (findingsExpanded) "▴" else "▾", style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.weight(1f))
                if (scanResult.findings.isNotEmpty()) {
                    if (onCreateFixPrd != null) {
                        TextButton(onClick = onCreateFixPrd) { Text(stringResource(R.string.scan_fix_prd), style = MaterialTheme.typography.labelSmall) }
                    }
                    if (onProposeRules != null) {
                        TextButton(onClick = onProposeRules) { Text(stringResource(R.string.scan_propose_rules), style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
            if (findingsExpanded && scanResult.findings.isNotEmpty()) {
                scanResult.findings.forEach { finding -> FindingRow(finding) }
            }
        } else if (!scanLoading) {
            Text(stringResource(R.string.scan_no_result), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
        }
    }

    if (proposedRules != null && onDismissProposedRules != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = onDismissProposedRules,
            title = { Text(stringResource(R.string.scan_proposed_rules_title)) },
            text = { Text(proposedRules.text.ifBlank { proposedRules.diff ?: "" }, style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace) },
            confirmButton = { TextButton(onClick = onDismissProposedRules) { Text(stringResource(R.string.action_close)) } },
            dismissButton = null,
        )
    }
}

@Composable
private fun VerdictBadge(verdict: String) {
    val (color, label) = when (verdict.lowercase()) {
        "pass" -> Color(0xFF22C55E) to stringResource(R.string.scan_verdict_pass)
        "warn" -> Color(0xFFF59E0B) to stringResource(R.string.scan_verdict_warn)
        else -> Color(0xFFEF4444) to stringResource(R.string.scan_verdict_fail)
    }
    Box(
        Modifier.background(color.copy(alpha = 0.18f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun FindingRow(finding: ScanFindingDto) {
    val severityColor = when (finding.severity.lowercase()) {
        "error" -> Color(0xFFEF4444)
        "warning" -> Color(0xFFF59E0B)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 2.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(finding.severity.take(4).uppercase(), style = MaterialTheme.typography.labelSmall, color = severityColor, modifier = Modifier.padding(top = 1.dp))
        Column {
            Text("${finding.file}${finding.line?.let { ":$it" } ?: ""}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(finding.message, style = MaterialTheme.typography.bodySmall, maxLines = 2)
        }
    }
}
