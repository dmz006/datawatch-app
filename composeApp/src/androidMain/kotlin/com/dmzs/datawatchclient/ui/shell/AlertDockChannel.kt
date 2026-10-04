package com.dmzs.datawatchclient.ui.shell

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

/** Severity of an in-app dock entry — mirrors the PWA `showToast` types. */
public enum class DockLevel { Info, Success, Warning, Error }

/**
 * One message in the alert dock (PWA `_alertDock.alerts` entry). [count] is
 * the ×N coalesce counter. [fromServerAlert] marks entries created from a live
 * WS `alert` frame (parity D51a) — those are already counted by the server
 * alert badge, so the header pill does not count them twice.
 */
public data class DockEntry(
    val id: Long,
    val tsMs: Long,
    val level: DockLevel,
    val message: String,
    val family: String,
    val count: Int = 1,
    val fromServerAlert: Boolean = false,
)

/**
 * App-wide alert-dock state (PWA alpha.29 #271).
 *
 * - Parity D41a: Android no longer shows toasts. Every client-side message
 *   goes through [post], lands in the dock and bumps the header 🔔 pill.
 * - Parity D47a: 🔕 is a real dock mute for this app session ([mute]).
 * - Parity D51a: live WS `alert` frames are posted here too.
 *
 * Deliberate difference from the PWA: an app error ([DockLevel.Error], not
 * from a server alert) is never dropped by mute and opens the dock, so it is
 * never silent. The PWA surfaces those through its persistent `showError`
 * banner instead.
 */
public object AlertDockChannel {
    private const val MAX_ENTRIES = 100
    private const val COALESCE_WINDOW_MS = 60_000L

    private val _open = MutableStateFlow(false)
    public val open: StateFlow<Boolean> = _open.asStateFlow()

    private val _muted = MutableStateFlow(false)
    public val muted: StateFlow<Boolean> = _muted.asStateFlow()

    private val _entries = MutableStateFlow<List<DockEntry>>(emptyList())
    public val entries: StateFlow<List<DockEntry>> = _entries.asStateFlow()

    private val nextId = AtomicLong(0L)

    /** Pill click — un-mutes and opens when muted (PWA `toggleAlertDock`). */
    public fun toggle() {
        if (_muted.value) {
            _muted.value = false
            _open.value = true
            return
        }
        _open.value = !_open.value
    }

    public fun close() {
        _open.value = false
    }

    /** ✕ — clears dock entries and closes (PWA `dismissAlertDock`). */
    public fun dismiss() {
        _entries.value = emptyList()
        _open.value = false
    }

    /** 🔕 — mute for this app session (PWA `muteAlertDock`). */
    public fun mute() {
        _muted.value = true
        dismiss()
    }

    /** Drop one entry (PWA `dismissAlertCard`). */
    public fun remove(id: Long) {
        _entries.update { list -> list.filterNot { it.id == id } }
    }

    /**
     * Replacement for `Toast.makeText(...).show()`; safe from any thread.
     * Same-family messages within 60 s coalesce into one entry with a ×N
     * counter, matching PWA `pushToAlertDock`.
     */
    public fun post(
        message: String,
        level: DockLevel = DockLevel.Info,
        fromServerAlert: Boolean = false,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        if (message.isBlank()) return
        val appError = level == DockLevel.Error && !fromServerAlert
        if (_muted.value && !appError) return
        val family = familyOf(message)
        _entries.update { list ->
            val head = list.firstOrNull()
            if (head != null && head.family == family && head.level == level &&
                head.fromServerAlert == fromServerAlert &&
                nowMs - head.tsMs < COALESCE_WINDOW_MS
            ) {
                listOf(head.copy(count = head.count + 1, tsMs = nowMs, message = message)) + list.drop(1)
            } else {
                val entry =
                    DockEntry(
                        id = nextId.getAndIncrement(),
                        tsMs = nowMs,
                        level = level,
                        message = message,
                        family = family,
                        fromServerAlert = fromServerAlert,
                    )
                (listOf(entry) + list).take(MAX_ENTRIES)
            }
        }
        if (appError) _open.value = true
    }

    /** Pill contribution: ×N total of entries the server badge doesn't already count. */
    public fun localCount(list: List<DockEntry>): Int = list.filterNot { it.fromServerAlert }.sumOf { it.count }

    /** PWA family key: strip a leading `[prefix] `, keep text before the first — : or ,. */
    internal fun familyOf(message: String): String {
        val stripped = message.replace(Regex("^\\[[^\\]]*\\]\\s*"), "")
        val m = Regex("^([^—:,]+?)(?:\\s*[—:,].*)?$", RegexOption.DOT_MATCHES_ALL).find(stripped)
        return m?.groupValues?.getOrNull(1)?.trim()?.ifEmpty { null } ?: stripped
    }

    /** Test hook. */
    internal fun resetForTest() {
        _open.value = false
        _muted.value = false
        _entries.value = emptyList()
    }
}
