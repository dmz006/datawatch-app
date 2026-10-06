package com.dmzs.datawatchclient.ui.autonomous

import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.viewinterop.AndroidView

// ---------------------------------------------------------------------------
// Shared Compose Markdown renderer — handles PWA parity spec:
//   headings (#/##/###), fenced code blocks, bold/italic/inline-code,
//   GFM tables, Mermaid diagrams, unordered/ordered lists, paragraphs.
// Used by FileViewerSheet and PrdDetailDialog.
// ---------------------------------------------------------------------------

internal sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class CodeBlock(val lang: String, val lines: List<String>) : MdBlock
    data class MermaidBlock(val diagram: String) : MdBlock
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MdBlock
    data class ListItem(val ordered: Boolean, val number: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    object Blank : MdBlock
}

internal fun parseMd(raw: String): List<MdBlock> {
    val lines = raw.lines()
    val blocks = mutableListOf<MdBlock>()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        when {
            // Fenced code block (including mermaid)
            line.trimStart().startsWith("```") -> {
                val lang = line.trimStart().removePrefix("```").trim()
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    codeLines.add(lines[i])
                    i++
                }
                if (lang.equals("mermaid", ignoreCase = true)) {
                    blocks.add(MdBlock.MermaidBlock(codeLines.joinToString("\n")))
                } else {
                    blocks.add(MdBlock.CodeBlock(lang, codeLines))
                }
            }
            // GFM table: line starts with | and next non-blank is a separator row
            line.trimStart().startsWith("|") && i + 1 < lines.size &&
                lines[i + 1].trimStart().startsWith("|") &&
                lines[i + 1].replace("|", "").replace("-", "").replace(":", "").replace(" ", "").isEmpty() -> {
                val headers = line.split("|").drop(1).dropLast(1).map { it.trim() }
                i += 2 // skip header + separator
                val rows = mutableListOf<List<String>>()
                while (i < lines.size && lines[i].trimStart().startsWith("|")) {
                    rows.add(lines[i].split("|").drop(1).dropLast(1).map { it.trim() })
                    i++
                }
                blocks.add(MdBlock.Table(headers, rows))
                i-- // outer loop will i++
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
                    !lines[i + 1].trimStart().startsWith("|") &&
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

/** Apply **bold**, *italic* / _italic_, `code` inline spans to an AnnotatedString. */
internal fun buildInline(text: String) = buildAnnotatedString {
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
            // Italic _..._ — word-bounded so snake_case stays literal
            // (council replies end with "_(via: node)_").
            text[idx] == '_' && !text.getOrElse(idx - 1) { ' ' }.isLetterOrDigit() &&
                text.getOrNull(idx + 1)?.isWhitespace() == false -> {
                var end = text.indexOf('_', idx + 1)
                while (end != -1 && text.getOrElse(end + 1) { ' ' }.isLetterOrDigit()) end = text.indexOf('_', end + 1)
                if (end == -1 || end == idx + 1) { append(text[idx]); idx++; continue }
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

/**
 * @param scrollable Pass true when MarkdownView is the outermost scrollable in its
 *   container (e.g. FileViewerSheet). Pass false (default) when it is embedded inside
 *   a LazyColumn or Column+verticalScroll, where the parent handles scrolling.
 */
@Composable
internal fun MarkdownView(text: String, modifier: Modifier = Modifier, scrollable: Boolean = false) {
    val blocks = parseMd(text)
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    val codeFg = MaterialTheme.colorScheme.onSurfaceVariant
    val scrollState = rememberScrollState()
    val columnMod = if (scrollable) modifier.verticalScroll(scrollState) else modifier

    Column(modifier = columnMod, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        blocks.forEach { block ->
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
                is MdBlock.MermaidBlock -> MermaidView(
                    diagram = block.diagram,
                    modifier = Modifier.fillMaxWidth(),
                )
                is MdBlock.Table -> GfmTableView(
                    headers = block.headers,
                    rows = block.rows,
                    modifier = Modifier.fillMaxWidth(),
                )
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

@Composable
internal fun GfmTableView(
    headers: List<String>,
    rows: List<List<String>>,
    modifier: Modifier = Modifier,
) {
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val headerBg = MaterialTheme.colorScheme.surfaceVariant
    val cols = headers.size.coerceAtLeast(1)
    // Fixed column width avoids weight(1f)/fillMaxWidth() inside horizontalScroll's
    // unbounded constraint, which would collapse all cells to 0 width.
    val colW = 100.dp

    Row(modifier = modifier.horizontalScroll(rememberScrollState())) {
        Column(modifier = Modifier.border(1.dp, borderColor, RoundedCornerShape(4.dp))) {
            // Header row
            Row(modifier = Modifier.background(headerBg).height(IntrinsicSize.Min)) {
                headers.forEachIndexed { ci, h ->
                    Text(
                        h,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .width(colW)
                            .fillMaxHeight()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                    if (ci < cols - 1) {
                        Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(borderColor))
                    }
                }
            }
            // Header/body separator — built from explicit column widths to stay within scroll bounds
            Row {
                repeat(cols) { ci ->
                    Box(modifier = Modifier.width(colW).height(1.dp).background(borderColor))
                    if (ci < cols - 1) {
                        Box(modifier = Modifier.width(1.dp).height(1.dp).background(borderColor))
                    }
                }
            }
            // Data rows
            rows.forEachIndexed { ri, row ->
                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    val paddedRow = if (row.size < cols) row + List(cols - row.size) { "" } else row
                    paddedRow.take(cols).forEachIndexed { ci, cell ->
                        Text(
                            cell,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .width(colW)
                                .fillMaxHeight()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                        if (ci < cols - 1) {
                            Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(borderColor))
                        }
                    }
                }
                if (ri < rows.size - 1) {
                    Row {
                        repeat(cols) { ci ->
                            Box(modifier = Modifier.width(colW).height(1.dp).background(borderColor))
                            if (ci < cols - 1) {
                                Box(modifier = Modifier.width(1.dp).height(1.dp).background(borderColor))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MermaidView(diagram: String, modifier: Modifier = Modifier) {
    val html = remember(diagram) {
        val escaped = diagram
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
        """<!DOCTYPE html>
<html><head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<script src="mermaid.min.js"></script>
<style>body{margin:0;background:#fff}svg{max-width:100%;height:auto}</style>
</head><body>
<div class="mermaid">$escaped</div>
<script>mermaid.initialize({startOnLoad:true,theme:'default',securityLevel:'strict'});</script>
</body></html>"""
    }
    // Height follows the rendered diagram (iOS MermaidBlock does the same via a
    // message handler). No addJavascriptInterface: poll document height with
    // evaluateJavascript after load, until mermaid has drawn its SVG.
    var heightDp by remember(diagram) { androidx.compose.runtime.mutableIntStateOf(0) }
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                @Suppress("SetJavaScriptEnabled")
                settings.domStorageEnabled = true
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                // Mermaid is bundled (ADR-0051) — no network for this view.
                settings.blockNetworkLoads = true
                isVerticalScrollBarEnabled = false
                webViewClient =
                    object : android.webkit.WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) {
                            // Wait until mermaid has drawn its SVG and the height
                            // is stable across two polls, then size to it.
                            var last = -1
                            fun poll(tries: Int) {
                                view.evaluateJavascript(
                                    "(function(){var s=document.querySelector('.mermaid svg');" +
                                        "if(!s)return 0;return Math.ceil(s.getBoundingClientRect().bottom)+8;})()",
                                ) { v ->
                                    val h = v?.toIntOrNull() ?: 0
                                    if (h > 0 && h == last) {
                                        heightDp = h
                                    } else if (tries > 0) {
                                        last = h
                                        view.postDelayed({ poll(tries - 1) }, 150)
                                    } else if (h > 0) {
                                        heightDp = h
                                    }
                                }
                            }
                            poll(40)
                        }
                    }
            }
        },
        update = { wv ->
            // Bundled Mermaid (assets/mermaid, ADR-0051): no CDN, works offline.
            if (wv.tag != html) {
                wv.tag = html
                wv.loadDataWithBaseURL("file:///android_asset/mermaid/", html, "text/html", "utf-8", null)
            }
        },
        modifier = modifier.fillMaxWidth().height(if (heightDp > 0) heightDp.dp else 120.dp),
    )
}
