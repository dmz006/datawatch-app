package com.dmzs.datawatchclient.util

/**
 * Strips TUI noise from a captured-response blob so the operator-facing
 * Response viewer shows the actual LLM prose rather than spinners,
 * status timers, footer hints, and box-drawing borders.
 *
 * Mirrors the PWA filter that landed in datawatch v5.26.31 after a
 * three-pass operator regression cycle:
 *
 *   - v5.26.15: first filter (too aggressive, killed prose framed in
 *     TUI borders)
 *   - v5.26.23: prose-detection gate `hasWord3` (too charitable, let
 *     TUI noise leak)
 *   - **v5.26.31**: apply the noise predicates (labeled borders,
 *     embedded status timers, spinner counters, pure digit lines,
 *     pure box-drawing) **unconditionally** — drop any line where
 *     any of them fire, regardless of word count.
 *
 * datawatch-app issue #15.
 */
public object ResponseNoiseFilter {
    /**
     * Strip noise from a captured response. Empty / blank input
     * returns "". Lines are split on `\n`, filtered, and rejoined.
     * Trailing blanks are collapsed so a clean prose block doesn't
     * get padded by stripped-out borders.
     */
    public fun strip(text: String): String {
        if (text.isBlank()) return ""
        val kept = mutableListOf<String>()
        for (raw in text.split('\n')) {
            if (isResponseNoiseLine(raw)) continue
            kept += raw
        }
        // Collapse runs of >1 blank line (created where adjacent
        // noise lines used to be) to a single blank, then trim.
        val coalesced = StringBuilder()
        var prevBlank = false
        for (line in kept) {
            val blank = line.isBlank()
            if (blank && prevBlank) continue
            coalesced.append(line).append('\n')
            prevBlank = blank
        }
        return coalesced.toString().trim()
    }

    internal fun isResponseNoiseLine(line: String): Boolean {
        val s = line
        if (s.isBlank()) return false // blanks handled by coalescer
        return isPureBoxDrawing(s) ||
            isLabeledBorder(s) ||
            hasEmbeddedStatusTimer(s) ||
            isSpinnerCounter(s) ||
            isPureDigitLine(s) ||
            matchesNoisePattern(s)
    }

    /** Line is composed exclusively of box-drawing / spacing glyphs. */
    internal fun isPureBoxDrawing(s: String): Boolean {
        var any = false
        for (c in s) {
            when {
                c.isWhitespace() -> { /* allowed */ }
                c in BOX_DRAWING -> any = true
                else -> return false
            }
        }
        return any
    }

    /**
     * Box-drawing border with a short label inside, e.g.
     * `─── Status ───` or `╭─ Tool ─╮`. The non-box portion has
     * 1–3 short tokens — anything longer is real prose framed in a
     * border, which the v5.26.15 over-aggressive pass got wrong.
     */
    internal fun isLabeledBorder(s: String): Boolean {
        var box = 0
        val nonBoxBuf = StringBuilder()
        for (c in s) {
            if (c.isWhitespace()) {
                nonBoxBuf.append(' ')
                continue
            }
            if (c in BOX_DRAWING) box++ else nonBoxBuf.append(c)
        }
        if (box < 4) return false // need at least a small border run
        val text = nonBoxBuf.toString().trim()
        if (text.isEmpty()) return false // pure border, isPureBoxDrawing handles
        val tokens = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        // Borders frame ≤ 3 short tokens; longer text is real prose.
        return tokens.size <= 3 && tokens.all { it.length <= 24 }
    }

    /**
     * Line carries a status timer like `0:00:12`, `00:01:23`,
     * `12s`, or `1m 4s`. Common in Claude Code's footer + every
     * tool-using TUI's `elapsed: NN` line.
     */
    internal fun hasEmbeddedStatusTimer(s: String): Boolean {
        return TIMER_PATTERNS.any { it.containsMatchIn(s) }
    }

    /**
     * Line is a spinner glyph followed by a counter / status word.
     * Examples: `⠋ Thinking…`, `⠹ Processing 13`, `(elapsed 2.3s)`.
     */
    internal fun isSpinnerCounter(s: String): Boolean {
        val trimmed = s.trim()
        if (trimmed.isEmpty()) return false
        val firstChar = trimmed[0]
        if (firstChar !in SPINNER_GLYPHS && firstChar !in BOX_DRAWING) {
            return false
        }
        val tail = trimmed.drop(1).trim()
        if (tail.isEmpty()) return true
        // The tail should be short — spinner followed by a 1-3 word
        // status, not paragraphs.
        val tokens = tail.split(Regex("\\s+")).filter { it.isNotEmpty() }
        return tokens.size <= 3
    }

    /**
     * Line that is only digits, separators, and whitespace. Common
     * for percentage-only progress lines like `42` or `12 / 100`.
     */
    internal fun isPureDigitLine(s: String): Boolean {
        var sawDigit = false
        for (c in s) {
            when {
                c.isDigit() -> sawDigit = true
                c.isWhitespace() -> { /* allowed */ }
                c in DIGIT_LINE_SEPARATORS -> { /* allowed */ }
                else -> return false
            }
        }
        return sawDigit
    }

    /**
     * Catch-all for known noise strings the PWA broadened in
     * v5.26.31. Add patterns sparingly — every entry here drops
     * matching lines unconditionally.
     */
    internal fun matchesNoisePattern(s: String): Boolean = NOISE_PATTERNS.any { it.containsMatchIn(s) }

    // Box-drawing block: U+2500..U+257F, plus a few common ASCII
    // border chars often paired with them.
    private val BOX_DRAWING: Set<Char> =
        buildSet {
            for (cp in 0x2500..0x257F) add(cp.toChar())
            addAll(setOf('|', '-', '=', '+', '─', '│', '╭', '╮', '╯', '╰'))
        }

    // Subset of the Braille spinner glyphs used by ora / cli-spinners.
    private val SPINNER_GLYPHS: Set<Char> =
        setOf(
            '⠋', '⠙', '⠹', '⠸', '⠼', '⠴', '⠦', '⠧', '⠇', '⠏',
            '◐', '◓', '◑', '◒', '◴', '◷', '◶', '◵',
            '|', '/', '-', '\\',
        )

    private val DIGIT_LINE_SEPARATORS: Set<Char> =
        setOf('/', '%', ',', '.', ':', '·', '-')

    private val TIMER_PATTERNS: List<Regex> =
        listOf(
            // 0:01:23 / 00:01:23 / 1:23
            Regex("""\b\d{1,2}:\d{2}(:\d{2})?\b"""),
            // 12s, 12.3s, 1m 4s, 1h 2m
            Regex("""\b\d+(\.\d+)?\s*[smh]\b"""),
            // elapsed/eta/took prefixes
            Regex("""(?i)\b(elapsed|eta|took|esc to interrupt)\b"""),
        )

    private val NOISE_PATTERNS: List<Regex> =
        listOf(
            Regex("""(?i)tokens?:\s*\d"""),
            Regex("""(?i)context:\s*\d"""),
            Regex("""(?i)\(esc\s+to\s+interrupt\)"""),
            Regex("""(?i)\bpress\s+(esc|ctrl)"""),
            Regex("""(?i)approve\?\s*\(y/n\)"""),
        )
}
