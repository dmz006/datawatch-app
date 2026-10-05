package com.dmzs.datawatchclient.yaml

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A YAML document that couldn't be parsed; [line] is 1-based (0 = whole document). */
public class YamlParseException(
    public val line: Int,
    public val detail: String,
) : IllegalArgumentException(if (line > 0) "Line $line: $detail" else detail)

/**
 * JsonElement ↔ YAML for the profile editors' "Edit in YAML" view — a port of the
 * PWA's tiny serializer/parser (app.js `yamlStringify` / `parseYAMLNaive`), widened
 * so every value the form can hold survives a round trip: nested maps, lists of
 * scalars or maps, quoted strings, numbers, booleans, null, block scalars (`|`, `>`)
 * and one-line flow collections (`[a, b]`, `{k: v}`). Comments are ignored. Text
 * that starts with `{` or `[` is tried as JSON first, like the PWA. No YAML
 * library: only the block-style subset above is supported (no anchors, tags or
 * multi-document streams).
 *
 * Unlike the PWA serializer, empty strings / nulls / empty collections are kept
 * (`key: ""`, `key: null`, `key: []`, `key: {}`) so switching views never drops keys.
 */
public object ProfileYaml {
    // ---------------------------------------------------------------- stringify

    public fun stringify(element: JsonElement): String {
        val sb = StringBuilder()
        when (element) {
            is JsonObject ->
                if (element.isEmpty()) sb.append("{}\n") else writeMap(sb, element, 0)
            is JsonArray ->
                if (element.isEmpty()) sb.append("[]\n") else writeList(sb, element, 0)
            is JsonPrimitive -> sb.append(scalar(element, 2)).append('\n')
        }
        return sb.toString()
    }

    private fun pad(n: Int): String = " ".repeat(n)

    private fun writeMap(
        sb: StringBuilder,
        obj: JsonObject,
        indent: Int,
    ) {
        for ((k, v) in obj) {
            sb.append(pad(indent)).append(key(k)).append(':')
            writeValueAfterIndicator(sb, v, indent)
        }
    }

    private fun writeList(
        sb: StringBuilder,
        arr: JsonArray,
        indent: Int,
    ) {
        for (v in arr) {
            sb.append(pad(indent)).append('-')
            when {
                v is JsonObject && v.isNotEmpty() -> {
                    // First key shares the dash line; the rest align under it.
                    val inner = StringBuilder()
                    writeMap(inner, v, indent + 2)
                    sb.append(' ').append(inner.substring(indent + 2))
                }
                v is JsonArray && v.isNotEmpty() -> {
                    sb.append('\n')
                    writeList(sb, v, indent + 2)
                }
                else -> writeValueAfterIndicator(sb, v, indent)
            }
        }
    }

    /** Writes the value that follows `key:` or `-` (already emitted), ending the line. */
    private fun writeValueAfterIndicator(
        sb: StringBuilder,
        v: JsonElement,
        indent: Int,
    ) {
        when {
            v is JsonObject && v.isEmpty() -> sb.append(" {}\n")
            v is JsonArray && v.isEmpty() -> sb.append(" []\n")
            v is JsonObject -> {
                sb.append('\n')
                writeMap(sb, v, indent + 2)
            }
            v is JsonArray -> {
                sb.append('\n')
                writeList(sb, v, indent + 2)
            }
            else -> sb.append(' ').append(scalar(v as JsonPrimitive, indent + 2)).append('\n')
        }
    }

    private val plainKey = Regex("^[A-Za-z0-9_][A-Za-z0-9_.\\-/]*$")

    private fun key(k: String): String = if (plainKey.matches(k)) k else quote(k)

    private val numberLike = Regex("^[-+]?(\\d[\\d_]*)?(\\.\\d*)?([eE][-+]?\\d+)?$|^0x[0-9a-fA-F]+$|^0o[0-7]+$|^[-+]?\\.(inf|Inf|INF)$|^\\.(nan|NaN|NAN)$")
    private val reserved =
        setOf("true", "false", "yes", "no", "on", "off", "y", "n", "null", "~")

    private fun scalar(
        p: JsonPrimitive,
        blockIndent: Int,
    ): String {
        if (p is JsonNull) return "null"
        if (!p.isString) return p.content
        val s = p.content
        if (s.contains('\n')) blockScalar(s, blockIndent)?.let { return it }
        return if (isPlainSafe(s)) s else quote(s)
    }

    /** Literal block (`|` / `|-`) for multiline text when it can round-trip exactly. */
    private fun blockScalar(
        s: String,
        indent: Int,
    ): String? {
        if (s.contains('\r') || s.contains('\t')) return null
        val chomp: String
        val body: String
        when {
            s.endsWith("\n\n") -> return null
            s.endsWith("\n") -> {
                chomp = ""
                body = s.dropLast(1)
            }
            else -> {
                chomp = "-"
                body = s
            }
        }
        val lines = body.split('\n')
        // A leading-space first line would be misread as deeper indentation.
        if (lines.first().startsWith(" ") || lines.first().isEmpty()) return null
        if (lines.any { it.isNotEmpty() && it.isBlank() }) return null
        val sb = StringBuilder("|").append(chomp)
        for (l in lines) {
            sb.append('\n')
            if (l.isNotEmpty()) sb.append(pad(indent)).append(l)
        }
        return sb.toString()
    }

    private fun isPlainSafe(s: String): Boolean {
        if (s.isEmpty() || s != s.trim()) return false
        if (s.lowercase() in reserved) return false
        if (numberLike.matches(s)) return false
        if ("-?:,[]{}#&*!|>'\"%@`".indexOf(s[0]) >= 0) return false
        if (s.contains(": ") || s.contains(" #") || s.endsWith(":")) return false
        if (s == "---" || s == "...") return false
        return s.none { it < ' ' || it == '\u007f' }
    }

    private fun quote(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else ->
                    if (c < ' ' || c == '\u007f') {
                        sb.append("\\u").append(c.code.toString(16).padStart(4, '0'))
                    } else {
                        sb.append(c)
                    }
            }
        }
        return sb.append('"').toString()
    }

    // -------------------------------------------------------------------- parse

    /**
     * Parses [text] (YAML, or JSON when it starts with `{`/`[`). An empty document
     * (only blanks/comments) yields [JsonNull]. Throws [YamlParseException].
     */
    public fun parse(text: String): JsonElement {
        val trimmed = text.trim()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            runCatching { return Json.parseToJsonElement(trimmed) }
        }
        return Parser(text).parseDocument()
    }

    private class Line(
        val number: Int,
        var indent: Int,
        var content: String,
        val raw: String,
    )

    private class Parser(text: String) {
        private val lines: List<Line> =
            text.replace("\r\n", "\n").replace('\r', '\n').split('\n').mapIndexed { i, raw ->
                val ws = raw.takeWhile { it == ' ' || it == '\t' }
                Line(number = i + 1, indent = ws.length, content = raw.substring(ws.length).trimEnd(), raw = raw)
            }
        private var pos = 0

        private fun fail(
            line: Line?,
            msg: String,
        ): Nothing = throw YamlParseException(line?.number ?: 0, msg)

        private fun isSkippable(l: Line): Boolean =
            l.content.isEmpty() || l.content.startsWith("#") || l.content == "---" || l.content == "..."

        /** Next meaningful line (skipping blanks/comments), without consuming it. */
        private fun peek(): Line? {
            while (pos < lines.size && isSkippable(lines[pos])) pos++
            val l = lines.getOrNull(pos) ?: return null
            if (l.raw.takeWhile { it == ' ' || it == '\t' }.contains('\t')) {
                fail(l, "tabs are not allowed for indentation")
            }
            return l
        }

        fun parseDocument(): JsonElement {
            val first = peek() ?: return JsonNull
            val value = parseBlock(first.indent)
            peek()?.let { fail(it, "unexpected content (check the indentation)") }
            return value
        }

        private fun isSeqItem(l: Line): Boolean = l.content == "-" || l.content.startsWith("- ")

        private fun parseBlock(indent: Int): JsonElement {
            val l = peek() ?: return JsonNull
            if (isSeqItem(l)) return parseSequence(l.indent)
            if (findMappingColon(l.content) != null) return parseMapping(l.indent)
            // A lone scalar document / value.
            pos++
            return inlineValue(l.content, l, indent)
        }

        private fun parseMapping(indent: Int): JsonObject {
            val out = LinkedHashMap<String, JsonElement>()
            while (true) {
                val l = peek() ?: break
                if (l.indent < indent) break
                if (l.indent > indent) fail(l, "unexpected indentation")
                if (isSeqItem(l)) fail(l, "a list item can't appear here (expected \"key: value\")")
                val colon = findMappingColon(l.content) ?: fail(l, "expected \"key: value\"")
                val key = parseKey(l.content.substring(0, colon).trim(), l)
                if (out.containsKey(key)) fail(l, "duplicate key \"$key\"")
                val rest = l.content.substring(colon + 1).trim()
                pos++
                out[key] = valueAfterIndicator(rest, l, indent, allowSameIndentSeq = true)
            }
            return JsonObject(out)
        }

        private fun parseSequence(indent: Int): JsonArray {
            val out = ArrayList<JsonElement>()
            while (true) {
                val l = peek() ?: break
                if (l.indent < indent) break
                if (l.indent > indent) fail(l, "unexpected indentation")
                if (!isSeqItem(l)) break
                val after = l.content.substring(1)
                val rest = after.trimStart()
                if (rest.isEmpty() || rest.startsWith("#")) {
                    pos++
                    out.add(valueAfterIndicator("", l, indent, allowSameIndentSeq = false))
                    continue
                }
                val innerIndent = indent + 1 + (after.length - rest.length)
                if (rest == "-" || rest.startsWith("- ") || findMappingColon(rest) != null) {
                    // "- key: v" / "- - x": re-read this line as the first line of a
                    // nested block that starts at the column after the dash.
                    l.indent = innerIndent
                    l.content = rest
                    out.add(parseBlock(innerIndent))
                } else {
                    pos++
                    out.add(inlineValue(rest, l, indent))
                }
            }
            return JsonArray(out)
        }

        /** Value for `key:` / `-` whose inline remainder is [rest]. */
        private fun valueAfterIndicator(
            rest: String,
            l: Line,
            indent: Int,
            allowSameIndentSeq: Boolean,
        ): JsonElement {
            if (rest.isNotEmpty() && !rest.startsWith("#")) return inlineValue(rest, l, indent)
            val next = peek() ?: return JsonNull
            return when {
                next.indent > indent -> parseBlock(next.indent)
                allowSameIndentSeq && next.indent == indent && isSeqItem(next) -> parseSequence(indent)
                else -> JsonNull
            }
        }

        private fun inlineValue(
            rest: String,
            l: Line,
            indent: Int,
        ): JsonElement {
            if (rest.startsWith("|") || rest.startsWith(">")) return blockScalar(rest, l, indent)
            if (rest.startsWith("[") || rest.startsWith("{")) {
                val text = stripComment(rest)
                val flow = Flow(text, l)
                val v = flow.value()
                flow.end()
                return v
            }
            if (rest.startsWith("&") || rest.startsWith("*") || rest.startsWith("!")) {
                fail(l, "anchors, aliases and tags aren't supported")
            }
            return scalarValue(rest, l, indent)
        }

        private fun scalarValue(
            text: String,
            l: Line,
            indent: Int,
        ): JsonElement {
            if (text.startsWith("\"") || text.startsWith("'")) {
                val (s, end) = readQuoted(text, 0, l)
                val tail = text.substring(end).trim()
                if (tail.isNotEmpty() && !tail.startsWith("#")) fail(l, "unexpected text after the closing quote")
                return JsonPrimitive(s)
            }
            val plain = stripComment(text).trim()
            // Plain multi-line continuation: deeper-indented lines fold into this scalar.
            val sb = StringBuilder(plain)
            while (true) {
                val n = peek() ?: break
                if (n.indent <= indent || isSeqItem(n) || findMappingColon(n.content) != null) break
                sb.append(' ').append(stripComment(n.content).trim())
                pos++
            }
            return resolvePlain(sb.toString())
        }

        private fun blockScalar(
            header: String,
            l: Line,
            parentIndent: Int,
        ): JsonElement {
            val folded = header[0] == '>'
            val ind = stripComment(header.substring(1)).trim()
            var chomp = ' '
            var explicit = 0
            for (c in ind) {
                when {
                    c == '-' || c == '+' -> chomp = c
                    c in '1'..'9' -> explicit = c - '0'
                    else -> fail(l, "bad block scalar header \"$header\"")
                }
            }
            val body = ArrayList<String>()
            var contentIndent = if (explicit > 0) parentIndent + explicit else -1
            while (pos < lines.size) {
                val raw = lines[pos].raw
                if (raw.isBlank()) {
                    body.add("")
                    pos++
                    continue
                }
                val ws = raw.takeWhile { it == ' ' }.length
                if (contentIndent < 0) {
                    if (ws <= parentIndent) break
                    contentIndent = ws
                }
                if (ws < contentIndent) break
                body.add(raw.substring(contentIndent).trimEnd('\r'))
                pos++
            }
            // Trailing blank lines belong to chomping, not content.
            var trailing = 0
            while (body.isNotEmpty() && body.last().isEmpty()) {
                body.removeAt(body.size - 1)
                trailing++
            }
            val text =
                if (folded) {
                    val sb = StringBuilder()
                    for ((i, s) in body.withIndex()) {
                        if (i > 0) {
                            val prev = body[i - 1]
                            when {
                                s.isEmpty() -> sb.append('\n')
                                prev.isEmpty() -> Unit
                                s.startsWith(" ") || prev.startsWith(" ") -> sb.append('\n')
                                else -> sb.append(' ')
                            }
                        }
                        sb.append(s)
                    }
                    sb.toString()
                } else {
                    body.joinToString("\n")
                }
            val result =
                when {
                    body.isEmpty() -> if (chomp == '+') "\n".repeat(trailing) else ""
                    chomp == '-' -> text
                    chomp == '+' -> text + "\n" + "\n".repeat(trailing)
                    else -> text + "\n"
                }
            return JsonPrimitive(result)
        }

        // ---- flow collections (single line) ----

        private inner class Flow(val s: String, val l: Line) {
            var i = 0

            private fun ws() {
                while (i < s.length && s[i] == ' ') i++
            }

            fun end() {
                ws()
                if (i < s.length) fail(l, "unexpected \"${s.substring(i)}\" after the closing bracket")
            }

            fun value(): JsonElement {
                ws()
                if (i >= s.length) fail(l, "unterminated [ ] or { } (flow collections must fit on one line)")
                return when (s[i]) {
                    '[' -> list()
                    '{' -> map()
                    '"', '\'' -> {
                        val (str, end) = readQuoted(s, i, l)
                        i = end
                        JsonPrimitive(str)
                    }
                    else -> resolvePlain(plain())
                }
            }

            private fun plain(): String {
                val start = i
                while (i < s.length && s[i] != ',' && s[i] != ']' && s[i] != '}' &&
                    !(s[i] == ':' && (i + 1 >= s.length || s[i + 1] == ' ' || s[i + 1] == ',' || s[i + 1] == '}'))
                ) {
                    i++
                }
                return s.substring(start, i).trim()
            }

            private fun list(): JsonArray {
                i++
                val out = ArrayList<JsonElement>()
                ws()
                if (i < s.length && s[i] == ']') {
                    i++
                    return JsonArray(out)
                }
                while (true) {
                    out.add(value())
                    ws()
                    if (i >= s.length) fail(l, "unterminated [ ] (flow collections must fit on one line)")
                    when (s[i]) {
                        ',' -> {
                            i++
                            ws()
                            if (i < s.length && s[i] == ']') {
                                i++
                                return JsonArray(out)
                            }
                        }
                        ']' -> {
                            i++
                            return JsonArray(out)
                        }
                        else -> fail(l, "expected , or ] in list")
                    }
                }
            }

            private fun map(): JsonObject {
                i++
                val out = LinkedHashMap<String, JsonElement>()
                ws()
                if (i < s.length && s[i] == '}') {
                    i++
                    return JsonObject(out)
                }
                while (true) {
                    ws()
                    val k: String =
                        if (i < s.length && (s[i] == '"' || s[i] == '\'')) {
                            val (str, end) = readQuoted(s, i, l)
                            i = end
                            str
                        } else {
                            plain()
                        }
                    ws()
                    if (i >= s.length || s[i] != ':') fail(l, "expected \":\" after \"$k\" in { }")
                    i++
                    if (out.containsKey(k)) fail(l, "duplicate key \"$k\"")
                    ws()
                    out[k] = if (i < s.length && (s[i] == ',' || s[i] == '}')) JsonNull else value()
                    ws()
                    if (i >= s.length) fail(l, "unterminated { } (flow collections must fit on one line)")
                    when (s[i]) {
                        ',' -> i++
                        '}' -> {
                            i++
                            return JsonObject(out)
                        }
                        else -> fail(l, "expected , or } in map")
                    }
                }
            }
        }

        // ---- scalars & keys ----

        private fun parseKey(
            raw: String,
            l: Line,
        ): String {
            if (raw.isEmpty()) fail(l, "missing key before \":\"")
            if (raw.startsWith("\"") || raw.startsWith("'")) {
                val (s, end) = readQuoted(raw, 0, l)
                if (raw.substring(end).isNotBlank()) fail(l, "bad quoted key")
                return s
            }
            if (raw.startsWith("? ") || raw == "?") fail(l, "complex keys aren't supported")
            return raw
        }

        /** Index of the `:` that ends a mapping key in [content], or null if it isn't a mapping entry. */
        fun findMappingColon(content: String): Int? {
            if (content.isEmpty() || content.startsWith("#")) return null
            var i = 0
            if (content[0] == '"' || content[0] == '\'') {
                val q = content[0]
                i = 1
                while (i < content.length) {
                    if (q == '"' && content[i] == '\\') {
                        i += 2
                        continue
                    }
                    if (content[i] == q) {
                        if (q == '\'' && i + 1 < content.length && content[i + 1] == '\'') {
                            i += 2
                            continue
                        }
                        break
                    }
                    i++
                }
                i++
                while (i < content.length && content[i] == ' ') i++
                return if (i < content.length && content[i] == ':' &&
                    (i + 1 == content.length || content[i + 1] == ' ')
                ) {
                    i
                } else {
                    null
                }
            }
            if (content[0] == '[' || content[0] == '{') return null
            while (i < content.length) {
                val c = content[i]
                if (c == '#' && i > 0 && content[i - 1] == ' ') return null
                if (c == ':' && (i + 1 == content.length || content[i + 1] == ' ')) return i
                i++
            }
            return null
        }

        private fun stripComment(s: String): String {
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '"' || c == '\'') {
                    i = runCatching { readQuoted(s, i, null).second }.getOrDefault(s.length)
                    continue
                }
                if (c == '#' && (i == 0 || s[i - 1] == ' ')) return s.substring(0, i).trimEnd()
                i++
            }
            return s
        }

        /** Reads a quoted string starting at [start]; returns (value, index after closing quote). */
        fun readQuoted(
            s: String,
            start: Int,
            l: Line?,
        ): Pair<String, Int> {
            val q = s[start]
            val sb = StringBuilder()
            var i = start + 1
            while (i < s.length) {
                val c = s[i]
                if (q == '\'') {
                    if (c == '\'') {
                        if (i + 1 < s.length && s[i + 1] == '\'') {
                            sb.append('\'')
                            i += 2
                            continue
                        }
                        return sb.toString() to i + 1
                    }
                    sb.append(c)
                    i++
                    continue
                }
                if (c == '"') return sb.toString() to i + 1
                if (c == '\\') {
                    if (i + 1 >= s.length) break
                    val e = s[i + 1]
                    i += 2
                    when (e) {
                        'n' -> sb.append('\n')
                        't' -> sb.append('\t')
                        'r' -> sb.append('\r')
                        '0' -> sb.append('\u0000')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000c')
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        ' ' -> sb.append(' ')
                        'u' -> {
                            val hex = s.substring(i, minOf(i + 4, s.length))
                            val code = hex.takeIf { it.length == 4 }?.toIntOrNull(16) ?: fail(l, "bad \\u escape")
                            sb.append(code.toChar())
                            i += 4
                        }
                        else -> fail(l, "unknown escape \\$e")
                    }
                    continue
                }
                sb.append(c)
                i++
            }
            fail(l, "missing closing $q quote (quoted strings must fit on one line)")
        }

        private val intRe = Regex("^[-+]?\\d+$")
        private val floatRe = Regex("^[-+]?(\\d+\\.\\d*|\\.\\d+|\\d+)([eE][-+]?\\d+)?$")

        fun resolvePlain(s: String): JsonElement =
            when {
                s.isEmpty() || s == "~" || s == "null" || s == "Null" || s == "NULL" -> JsonNull
                s == "true" || s == "True" || s == "TRUE" -> JsonPrimitive(true)
                s == "false" || s == "False" || s == "FALSE" -> JsonPrimitive(false)
                intRe.matches(s) -> s.removePrefix("+").toLongOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(s)
                floatRe.matches(s) -> s.toDoubleOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(s)
                else -> JsonPrimitive(s)
            }
    }
}
