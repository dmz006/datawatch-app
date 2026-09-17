package com.dmzs.datawatchclient.ui.autonomous

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val VIEWABLE_EXTENSIONS = setOf(
    "md", "txt", "json", "yaml", "yml", "go", "js", "ts", "jsx", "tsx",
    "py", "rb", "sh", "css", "html", "xml", "csv", "log", "toml", "ini",
    "conf", "cfg", "sql", "rs", "c", "cpp", "h", "java", "kt", "swift",
)

internal fun isViewable(path: String): Boolean =
    path.substringAfterLast('.').lowercase() in VIEWABLE_EXTENSIONS

private fun isMd(path: String): Boolean =
    path.substringAfterLast('.').lowercase() == "md"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FileViewerSheet(
    state: AutonomousViewModel.FileViewerState,
    onDismiss: () -> Unit,
    onShare: (path: String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        windowInsets = WindowInsets(0),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    state.path.substringAfterLast('/'),
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                IconButton(onClick = { onShare(state.path) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Share, contentDescription = "Share / download", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                }
            }

            // Content
            when {
                state.loading -> Box(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                state.error || state.content == null -> Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Unable to load file",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                isMd(state.path) -> MarkdownView(
                    text = state.content,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )

                else -> LazyColumn(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    item {
                        Text(
                            state.content,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Minimal Compose Markdown renderer — handles the minimum PWA parity spec:
//   headings (#/##/###), fenced code blocks, bold/italic/inline-code,
//   unordered (-/*) and ordered (1.) lists, plain paragraphs.
// ---------------------------------------------------------------------------

private sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class CodeBlock(val lang: String, val lines: List<String>) : MdBlock
    data class ListItem(val ordered: Boolean, val number: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    object Blank : MdBlock
}

private fun parseMd(raw: String): List<MdBlock> {
    val lines = raw.lines()
    val blocks = mutableListOf<MdBlock>()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        when {
            // Fenced code block
            line.trimStart().startsWith("```") -> {
                val lang = line.trimStart().removePrefix("```").trim()
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    codeLines.add(lines[i])
                    i++
                }
                blocks.add(MdBlock.CodeBlock(lang, codeLines))
            }
            // Heading
            line.startsWith("#") -> {
                val level = line.takeWhile { it == '#' }.length.coerceIn(1, 6)
                val text = line.drop(level).trim()
                blocks.add(MdBlock.Heading(level, text))
            }
            // Unordered list
            line.trimStart().let { t -> t.startsWith("- ") || t.startsWith("* ") } -> {
                val text = line.trimStart().drop(2)
                blocks.add(MdBlock.ListItem(false, 0, text))
            }
            // Ordered list
            line.trimStart().matches(Regex("^\\d+\\.\\s.*")) -> {
                val num = line.trimStart().substringBefore('.').toIntOrNull() ?: 1
                val text = line.trimStart().substringAfter(". ")
                blocks.add(MdBlock.ListItem(true, num, text))
            }
            // Blank line
            line.isBlank() -> blocks.add(MdBlock.Blank)
            // Paragraph
            else -> {
                val paraLines = mutableListOf(line)
                while (i + 1 < lines.size && lines[i + 1].isNotBlank() &&
                    !lines[i + 1].startsWith("#") &&
                    !lines[i + 1].trimStart().startsWith("```") &&
                    !lines[i + 1].trimStart().let { t -> t.startsWith("- ") || t.startsWith("* ") } &&
                    !lines[i + 1].trimStart().matches(Regex("^\\d+\\.\\s.*"))
                ) {
                    i++
                    paraLines.add(lines[i])
                }
                blocks.add(MdBlock.Paragraph(paraLines.joinToString(" ")))
            }
        }
        i++
    }
    return blocks
}

/** Apply **bold**, *italic*, `code` inline spans to an AnnotatedString. */
private fun buildInline(text: String) = buildAnnotatedString {
    var idx = 0
    while (idx < text.length) {
        when {
            // Bold **...**
            text.startsWith("**", idx) -> {
                val end = text.indexOf("**", idx + 2)
                if (end == -1) { append(text[idx]); idx++; continue }
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(idx + 2, end)) }
                idx = end + 2
            }
            // Italic *...*
            text[idx] == '*' && idx + 1 < text.length && text[idx + 1] != '*' -> {
                val end = text.indexOf('*', idx + 1)
                if (end == -1 || text.getOrNull(end + 1) == '*') { append(text[idx]); idx++; continue }
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(text.substring(idx + 1, end)) }
                idx = end + 1
            }
            // Inline code `...`
            text[idx] == '`' -> {
                val end = text.indexOf('`', idx + 1)
                if (end == -1) { append(text[idx]); idx++; continue }
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, background = Color(0x22888888))) {
                    append(text.substring(idx + 1, end))
                }
                idx = end + 1
            }
            else -> { append(text[idx]); idx++ }
        }
    }
}

@Composable
private fun MarkdownView(text: String, modifier: Modifier = Modifier) {
    val blocks = parseMd(text)
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    val codeFg = MaterialTheme.colorScheme.onSurfaceVariant

    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(blocks) { block ->
            when (block) {
                is MdBlock.Heading -> {
                    val (size, weight) = when (block.level) {
                        1 -> 22.sp to FontWeight.Bold
                        2 -> 18.sp to FontWeight.SemiBold
                        else -> 15.sp to FontWeight.SemiBold
                    }
                    Text(
                        buildInline(block.text),
                        fontSize = size,
                        fontWeight = weight,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = if (block.level == 1) 8.dp else 4.dp),
                    )
                }
                is MdBlock.CodeBlock -> Box(
                    modifier = Modifier.fillMaxWidth().background(codeBackground, RoundedCornerShape(6.dp))
                        .padding(8.dp),
                ) {
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        Text(
                            block.lines.joinToString("\n"),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = codeFg,
                        )
                    }
                }
                is MdBlock.ListItem -> Row(
                    modifier = Modifier.padding(start = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        if (block.ordered) "${block.number}." else "•",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(18.dp),
                    )
                    Text(
                        buildInline(block.text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                is MdBlock.Paragraph -> Text(
                    buildInline(block.text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                MdBlock.Blank -> Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}
