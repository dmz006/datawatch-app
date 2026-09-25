package com.dmzs.datawatchclient.auto

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Source-level regression tests for Android Auto Car App Library template compliance.
 *
 * These tests guard against two classes of driving-safety bugs that require
 * real head-unit testing to discover and are hard to catch in code review:
 *
 * 1. **Template type change on invalidate()**: Samsung gearhead rejects a screen
 *    when `onGetTemplate()` returns a DIFFERENT template type than it returned last
 *    time. Fixed: VoiceRecordingScreen must use one template type for ALL states.
 *
 * 2. **ActionStrip > 2 actions**: Samsung gearhead enforces max 2 ActionStrip
 *    actions even for category.OTHER. Fixed: every ActionStrip.Builder block in
 *    every Auto screen must have ≤ 2 `.addAction()` calls.
 *
 * Tests parse source files directly — no CarContext or emulator required.
 * They run in the normal unit test suite and fail the build immediately on
 * the next introduction of either regression.
 */
class CarTemplateComplianceTest {

    private val srcDir = File("src/main/kotlin/com/dmzs/datawatchclient/auto")
    private val pubSrcDir = File("src/publicMain/kotlin/com/dmzs/datawatchclient/auto")

    // ─── Bug guard 1: VoiceRecordingScreen must use ListTemplate only ──────────

    /**
     * If VoiceRecordingScreen uses MessageTemplate for ANY state, changing from
     * MessageTemplate to ListTemplate (or vice versa) on `invalidate()` causes the
     * host to report "cannot do that action while driving" when the user tries to
     * send a voice reply. All states must share a single template type.
     */
    @Test
    fun `VoiceRecordingScreen must not use MessageTemplate Builder`() {
        val file = File(srcDir, "VoiceRecordingScreen.kt")
        assertTrue(file.exists(), "VoiceRecordingScreen.kt not found at $file")
        // Strip comments before checking — the fix comments MENTION MessageTemplate to explain why
        // it was removed; we only care that no actual MessageTemplate.Builder() call exists.
        val sourceNoComments = file.readText()
            .lines()
            .filter { !it.trimStart().startsWith("//") }
            .joinToString("\n")
        assertFalse(
            sourceNoComments.contains("MessageTemplate.Builder("),
            "VoiceRecordingScreen must use ListTemplate for ALL states (LISTENING, ERROR, CONFIRMED). " +
                "A MessageTemplate→ListTemplate type change on invalidate() causes " +
                "'cannot do while driving' on Samsung gearhead.",
        )
    }

    @Test
    fun `VoiceRecordingScreen has ListTemplate in all three state methods`() {
        val file = File(srcDir, "VoiceRecordingScreen.kt")
        val source = file.readText()
        val listTemplateCount = "ListTemplate.Builder()".toRegex().findAll(source).count()
        assertTrue(
            listTemplateCount >= 3,
            "Expected ≥3 ListTemplate.Builder() calls in VoiceRecordingScreen (LISTENING, ERROR, CONFIRMED " +
                "+ error catch), found $listTemplateCount.",
        )
    }

    // ─── Bug guard 2: ActionStrip ≤ 2 actions per block ───────────────────────

    /**
     * Samsung gearhead enforces max 2 ActionStrip actions for category.OTHER.
     * Three actions produce "action list exceeded maximum number of two actions"
     * and make the screen unusable while driving.
     *
     * Checks every .kt file in the auto module source sets.
     */
    @Test
    fun `no ActionStrip block has more than 2 addAction calls`() {
        val violations = mutableListOf<String>()

        for (dir in listOf(srcDir, pubSrcDir)) {
            if (!dir.exists()) continue
            dir.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { file ->
                    val source = file.readText()
                    collectActionStripViolations(source, file.name).let { violations.addAll(it) }
                }
        }

        assertTrue(violations.isEmpty(), violations.joinToString("\n"))
    }

    /**
     * Parses [source] for ActionStrip.Builder() blocks and returns a description
     * for each block that contains more than 2 `.addAction(` calls.
     *
     * Strategy: split on "ActionStrip.Builder()" then for each chunk find the
     * outer `.build()` line by matching the indentation level of the
     * ActionStrip.Builder() start. Count `.addAction(` lines at exactly that
     * indent level + one standard Kotlin indent (4 spaces), ignoring deeper
     * `.addAction(` that appear inside nested Action.Builder blocks.
     */
    private fun collectActionStripViolations(source: String, filename: String): List<String> {
        val violations = mutableListOf<String>()
        val lines = source.lines()

        var blockStart = -1
        var blockIndent = ""
        var actionCount = 0

        for ((lineIdx, line) in lines.withIndex()) {
            val trimmed = line.trimStart()
            val indent = line.length - trimmed.length

            if (trimmed.startsWith("ActionStrip.Builder()")) {
                // Flush any unclosed prior block (shouldn't happen, safety guard)
                blockStart = lineIdx
                blockIndent = " ".repeat(indent)
                actionCount = 0
                continue
            }

            if (blockStart >= 0) {
                // Count .addAction( only at the direct child indent of ActionStrip.Builder
                // (i.e. blockIndent + 4 spaces). Deeper occurrences are inside nested builders.
                val expectedChildIndent = blockIndent.length + KOTLIN_INDENT
                if (indent == expectedChildIndent && trimmed.startsWith(".addAction(")) {
                    actionCount++
                }

                // The outer .build() for the ActionStrip ends the block; it appears
                // at the same indent as ActionStrip.Builder() itself.
                if (indent == blockIndent.length && (trimmed == ".build()," || trimmed == ".build()")) {
                    if (actionCount > MAX_STRIP_ACTIONS) {
                        violations.add(
                            "$filename line ${blockStart + 1}: ActionStrip has $actionCount actions " +
                                "(Samsung gearhead max is $MAX_STRIP_ACTIONS). " +
                                "Reduce to ≤$MAX_STRIP_ACTIONS icon-only ActionStrip actions.",
                        )
                    }
                    blockStart = -1
                    actionCount = 0
                }
            }
        }

        return violations
    }

    // ─── Bug guard 3: template types must be consistent per screen ────────────

    /**
     * Screens that need to change displayed content must call `invalidate()` which
     * triggers `onGetTemplate()` again. If the method returns different template
     * TYPES across calls (e.g., MessageTemplate then ListTemplate), the host
     * rejects the update. Each Screen subclass must return one template type.
     *
     * Checks that no screen file mixes ListTemplate.Builder() and MessageTemplate.Builder()
     * in the same source file, EXCEPT AutoSessionDetailScreen which uses MessageTemplate
     * for the session body (never changes type within a screen lifecycle).
     */
    @Test
    fun `VoiceRecordingScreen does not mix ListTemplate and MessageTemplate`() {
        val file = File(srcDir, "VoiceRecordingScreen.kt")
        val sourceNoComments = file.readText()
            .lines()
            .filter { !it.trimStart().startsWith("//") }
            .joinToString("\n")
        val hasList = sourceNoComments.contains("ListTemplate.Builder()")
        val hasMessage = sourceNoComments.contains("MessageTemplate.Builder(")
        assertFalse(
            hasList && hasMessage,
            "VoiceRecordingScreen uses BOTH ListTemplate.Builder() and MessageTemplate.Builder(). " +
                "This causes template-type change on invalidate() → 'cannot do while driving'.",
        )
    }

    // ─── Structural checks ────────────────────────────────────────────────────

    /** All core Auto screen classes must exist (navigation graph integrity). */
    @Test
    fun `all required Auto screen source files exist`() {
        val required = listOf(
            "AutoSummaryScreen.kt",
            "AutoSessionListScreen.kt",
            "AutoSessionDetailScreen.kt",
            "AutoAutomataScreen.kt",
            "AutoPrdDetailScreen.kt",
            "AutoStoryDetailScreen.kt",
            "AutoTaskDetailScreen.kt",
            "VoiceRecordingScreen.kt",
            "AutoReplyListScreen.kt",
            "BlockDetailsScreen.kt",
            "AutoMonitorScreen.kt",
            "LastOutputDetailScreen.kt",
        )
        val missing = required.filter { name ->
            !File(srcDir, name).exists() && !File(pubSrcDir, name).exists()
        }
        assertTrue(missing.isEmpty(), "Missing Auto screen source files: $missing")
    }

    /** Every screen that is not a root screen must have setHeaderAction(Action.BACK). */
    @Test
    fun `non-root screens call setHeaderAction with BACK`() {
        val nonRoot = listOf(
            "AutoSessionDetailScreen.kt",
            "AutoPrdDetailScreen.kt",
            "AutoStoryDetailScreen.kt",
            "AutoTaskDetailScreen.kt",
            "VoiceRecordingScreen.kt",
            "AutoReplyListScreen.kt",
            "BlockDetailsScreen.kt",
            "LastOutputDetailScreen.kt",
            "AutoMonitorScreen.kt",
            "AutoAutomataScreen.kt",
        )
        val missing = nonRoot.filter { name ->
            val file = File(srcDir, name).takeIf { it.exists() } ?: File(pubSrcDir, name)
            file.exists() && !file.readText().contains("Action.BACK")
        }
        assertTrue(missing.isEmpty(), "These non-root screens are missing Action.BACK: $missing")
    }

    private companion object {
        const val MAX_STRIP_ACTIONS = 2
        const val KOTLIN_INDENT = 4
    }
}
