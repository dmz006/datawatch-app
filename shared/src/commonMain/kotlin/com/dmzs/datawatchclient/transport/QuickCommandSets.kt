package com.dmzs.datawatchclient.transport

import com.dmzs.datawatchclient.domain.SavedCommand

/** One quick command: menu label and the value sent (text, or a special `__esc__` / `__ctrlb__`). */
public data class QuickCmd(
    val label: String,
    val value: String,
)

/**
 * The web UI's quick-command sets, verbatim (datawatch app.js), shared by
 * Android and iOS so the menus match:
 *  - [CARD_SYSTEM]: session-card "Commands…" (`loadCardCmds`), Saved shows all;
 *  - [DETAIL_SYSTEM]: session-detail "Commands…" (`loadSavedCmdsQuick`), Saved hides
 *    server-seeded commands, then a Guardrails group, then Custom…;
 *  - [CHAT_PREFIXES]: chat-mode `.chat-cmd-bar` buttons that prefill the composer.
 */
public object QuickCommandSets {
    public const val ESC: String = "__esc__"
    public const val CTRL_B: String = "__ctrlb__"

    public val CARD_SYSTEM: List<QuickCmd> =
        listOf(
            QuickCmd("approve", "yes"),
            QuickCmd("reject", "no"),
            QuickCmd("continue", "continue"),
            QuickCmd("skip", "skip"),
            QuickCmd("ESC", ESC),
            QuickCmd("tmux prefix (Ctrl-b)", CTRL_B),
            QuickCmd("quit", "/exit"),
        )

    public val DETAIL_SYSTEM: List<QuickCmd> =
        listOf(
            QuickCmd("approve", "yes"),
            QuickCmd("reject", "no"),
            QuickCmd("enter", "\n"),
            QuickCmd("continue", "continue"),
            QuickCmd("skip", "skip"),
            QuickCmd("abort", "\u0003"),
            QuickCmd("ESC", ESC),
            QuickCmd("tmux prefix (Ctrl-b)", CTRL_B),
            QuickCmd("quit", "/exit"),
        )

    /** Detail "Guardrails" group: `▶ <name>` runs that guardrail on the session. */
    public val GUARDRAILS: List<String> = listOf("sast-scan", "secrets-scan", "deps-scan")

    public val CHAT_PREFIXES: List<QuickCmd> =
        listOf(
            QuickCmd("📚 memories", "memories"),
            QuickCmd("🔍 recall", "recall: "),
            QuickCmd("🔗 kg query", "kg query "),
            QuickCmd("🔬 research", "research: "),
        )

    /** Saved group for the card menu: every saved command (web UI shows all there). */
    public fun cardSaved(list: List<SavedCommand>): List<QuickCmd> = list.map { QuickCmd(it.name.ifBlank { it.command }, it.command) }

    /** Saved group for the detail menu: user-saved only (server-seeded ones duplicate System). */
    public fun detailSaved(list: List<SavedCommand>): List<QuickCmd> =
        list.filter { !it.seeded }.map { QuickCmd(it.name.ifBlank { it.command }, it.command) }

    /** tmux key name for a special value (`sendkey <id>: <name>`), or null for plain text. */
    public fun sendKeyName(value: String): String? =
        when (value) {
            ESC -> "Escape"
            CTRL_B -> "C-b"
            else -> null
        }
}
