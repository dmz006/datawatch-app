package com.dmzs.datawatchclient.ui.autonomous

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.transport.dto.GuardrailVerdictDto
import com.dmzs.datawatchclient.transport.dto.PrdDto

/** PWA `renderVerdicts` badge colours: pass / warn / block, else grey. */
internal fun storyVerdictColor(outcome: String): Color =
    when (outcome.lowercase()) {
        "pass" -> Color(0xFF10B981)
        "warn" -> Color(0xFFF59E0B)
        "block" -> Color(0xFFEF4444)
        else -> Color(0xFF6B7280)
    }

/**
 * PWA `prd-story-verdicts` (story body, first row): one solid badge per
 * guardrail verdict — "guardrail: outcome", white 9 sp on the outcome colour —
 * tapping a badge toggles the inline drill-down (guardrail — outcome [severity],
 * summary, issues or "no issues"), PWA `toggleVerdictDrilldown`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StoryVerdictsRow(verdicts: List<GuardrailVerdictDto>) {
    if (verdicts.isEmpty()) return
    var open by remember(verdicts) { mutableStateOf<Int?>(null) }
    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            verdicts.forEachIndexed { i, v ->
                Text(
                    "${v.guardrail.ifBlank { "?" }}: ${v.outcome.ifBlank { "?" }}",
                    fontSize = 9.sp,
                    color = Color.White,
                    modifier =
                        Modifier
                            .background(storyVerdictColor(v.outcome), RoundedCornerShape(6.dp))
                            .clickable { open = if (open == i) null else i }
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
        }
        val v = open?.let { verdicts.getOrNull(it) }
        if (v != null) StoryVerdictDrilldown(v)
    }
}

@Composable
private fun StoryVerdictDrilldown(v: GuardrailVerdictDto) {
    val border = MaterialTheme.colorScheme.outlineVariant
    Row(modifier = Modifier.fillMaxWidth().padding(top = 3.dp).height(IntrinsicSize.Min)) {
        Box(modifier = Modifier.width(2.dp).fillMaxHeight().background(border))
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
        ) {
            val head =
                "${v.guardrail.ifBlank { "?" }} — ${v.outcome.ifBlank { "?" }}" +
                    if (v.severity.isNotBlank()) " [${v.severity}]" else ""
            Text(head, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            if (v.summary.isNotBlank()) {
                Text(v.summary, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
            }
            if (v.issues.isEmpty()) {
                Text(
                    "• " + stringResource(R.string.prd_verdict_no_issues),
                    fontSize = 10.sp,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            } else {
                v.issues.forEach { issue ->
                    Text("• $issue", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

/**
 * PWA `_renderDetailOverview` meta entries: `automata_detail_depth` (only when
 * depth > 0) and `automata_detail_created` (`_fmtDate` → locale date-time, "—").
 */
@Composable
internal fun PrdDepthCreatedMeta(prd: PrdDto) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (prd.depth > 0) {
            PrdMetaLine(stringResource(R.string.automata_detail_depth), prd.depth.toString())
        }
        PrdMetaLine(stringResource(R.string.automata_detail_created), formatPrdDate(prd.createdAt))
    }
}

@Composable
private fun PrdMetaLine(
    label: String,
    value: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** PWA `_fmtDate`: empty → "—"; parseable → locale date-time; else raw. */
internal fun formatPrdDate(ts: String?): String {
    if (ts.isNullOrBlank() || ts.startsWith("0001-")) return "—"
    val inst = runCatching { kotlinx.datetime.Instant.parse(ts) }.getOrNull() ?: return ts
    return java.text.DateFormat
        .getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.MEDIUM)
        .format(java.util.Date(inst.toEpochMilliseconds()))
}
