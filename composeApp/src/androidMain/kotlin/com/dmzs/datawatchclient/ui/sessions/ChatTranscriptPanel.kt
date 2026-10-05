package com.dmzs.datawatchclient.ui.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Spacer
import kotlinx.datetime.toLocalDateTime
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.domain.SessionEvent
import kotlinx.datetime.Instant
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ExperimentalLayoutApi

/**
 * Chat-mode transcript renderer. Used by sessions whose
 * `outputMode == "chat"` (OpenWebUI / Ollama / any chat-transcript
 * backend), where the server emits `chat_message` WS frames instead of
 * `pane_capture`. Mirrors PWA `appendChatBubble` / chat-area rendering
 * in `internal/server/web/app.js` ~L647–L770.
 *
 * Behaviour per-role (from PWA):
 *  - `user`: right-aligned bubble, primary-container background.
 *  - `assistant`: left-aligned bubble, surface-variant background.
 *    Streaming chunks accumulate into a single live bubble; the
 *    `streaming=false` finaliser seals it.
 *  - `system`: centred italic label. Transient "processing..." /
 *    "thinking..." / "ready..." content is rendered as a live
 *    indicator that the next assistant frame replaces, and not kept
 *    in the persistent transcript (matches PWA app.js:610).
 *
 * History is in-memory only — same as the PWA, which does not persist
 * chat bubbles server-side. A late subscriber gets the replay burst
 * from [com.dmzs.datawatchclient.storage.SessionEventRepository.observeChat].
 */
private data class ChatEntry(
    val role: SessionEvent.ChatMessage.Role,
    val content: String,
    val ts: Instant,
    val isStreaming: Boolean = false,
)

@Composable
public fun ChatTranscriptPanel(
    sessionId: String,
    modifier: Modifier = Modifier,
    /** PWA `chatQuickCmd(prefix)`: prefill the composer with a memory command. */
    onQuickCmd: (String) -> Unit = {},
) {
    val messages = remember(sessionId) { mutableStateListOf<ChatEntry>() }
    val streamingIndex = remember(sessionId) { mutableStateListOf<Int>() }
    val chatFlow =
        remember(sessionId) {
            ServiceLocator.sessionEventRepository.observeChat(sessionId)
        }
    // Separate system "transient" indicator outside the persistent list.
    val transientSystem = remember(sessionId) { mutableStateListOf<String>() }

    LaunchedEffect(sessionId) {
        chatFlow.collect { ev ->
            when (ev.role) {
                SessionEvent.ChatMessage.Role.Assistant ->
                    if (ev.streaming) {
                        // Accumulate into the live streaming bubble. If none
                        // exists yet, start one; otherwise append chunk.
                        val idx = streamingIndex.firstOrNull()
                        if (idx != null && idx in messages.indices) {
                            val prev = messages[idx]
                            messages[idx] =
                                prev.copy(content = prev.content + ev.content)
                        } else {
                            messages += ChatEntry(ev.role, ev.content, ev.ts, isStreaming = true)
                            streamingIndex += (messages.lastIndex)
                        }
                    } else {
                        // Streaming complete: seal any live bubble using the
                        // finaliser's content when non-empty (some servers
                        // echo the full body on close, others send empty).
                        val idx = streamingIndex.firstOrNull()
                        if (idx != null && idx in messages.indices) {
                            val prev = messages[idx]
                            val finalBody = ev.content.ifEmpty { prev.content }
                            messages[idx] = prev.copy(content = finalBody, isStreaming = false)
                            streamingIndex.clear()
                        } else if (ev.content.isNotBlank()) {
                            messages += ChatEntry(ev.role, ev.content, ev.ts, isStreaming = false)
                        }
                    }

                SessionEvent.ChatMessage.Role.User ->
                    messages += ChatEntry(ev.role, ev.content, ev.ts)

                SessionEvent.ChatMessage.Role.System -> {
                    val lc = ev.content.trim().lowercase()
                    val isTransient =
                        lc == "processing..." ||
                            lc == "thinking..." ||
                            lc.startsWith("ready")
                    if (isTransient) {
                        transientSystem.clear()
                        if (!lc.startsWith("ready")) transientSystem += ev.content
                    } else {
                        messages += ChatEntry(ev.role, ev.content, ev.ts)
                    }
                }
            }
            // Drop oldest once we exceed 200, matching PWA app.js:639.
            if (messages.size > 200) {
                val drop = messages.size - 200
                repeat(drop) { messages.removeAt(0) }
                // Shift streamingIndex since messages shifted.
                val newStream = streamingIndex.map { it - drop }.filter { it >= 0 }
                streamingIndex.clear()
                streamingIndex.addAll(newStream)
            }
        }
    }

    val listState = rememberLazyListState()
    val shouldAutoScroll by remember {
        derivedStateOf { messages.isNotEmpty() }
    }
    LaunchedEffect(messages.size, shouldAutoScroll) {
        if (shouldAutoScroll) {
            listState.animateScrollToItem((messages.size - 1).coerceAtLeast(0))
        }
    }

    if (messages.isEmpty() && transientSystem.isEmpty()) {
        androidx.compose.foundation.layout.Column(
            modifier = modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // PWA `.chat-empty`: 💬 (36px, .3) + chat_empty_hint (13px) + chat_memory_hint (11px).
            Text(
                "💬",
                fontSize = 36.sp,
                modifier = Modifier.alpha(0.3f),
            )
            Text(
                androidx.compose.ui.res.stringResource(com.dmzs.datawatchclient.R.string.chat_empty_hint),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                androidx.compose.ui.res.stringResource(com.dmzs.datawatchclient.R.string.chat_memory_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            ChatQuickCmdBar(onQuickCmd)
        }
        return
    }

    // BL82 — collapse all but the last 4 messages once there are more than 6.
    var earlierExpanded by remember(sessionId) { mutableStateOf(false) }
    val collapsible = messages.size > CHAT_COLLAPSE_THRESHOLD
    val olderCount = if (collapsible) messages.size - CHAT_RECENT_KEPT else 0
    val shown = if (collapsible && !earlierExpanded) messages.takeLast(CHAT_RECENT_KEPT) else messages.toList()

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (collapsible) {
            item(key = "earlier-header") {
                androidx.compose.material3.TextButton(onClick = { earlierExpanded = !earlierExpanded }) {
                    Text(
                        (if (earlierExpanded) "▾ " else "▸ ") + "💬 " +
                            androidx.compose.ui.res.stringResource(
                                com.dmzs.datawatchclient.R.string.chat_earlier_messages,
                                olderCount,
                            ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(shown, key = { "${it.ts.toEpochMilliseconds()}-${it.hashCode()}" }) { entry ->
            ChatBubble(entry)
        }
        if (transientSystem.isNotEmpty()) {
            item(key = "transient-${transientSystem.lastOrNull() ?: ""}") {
                TransientSystemRow(transientSystem.lastOrNull() ?: "")
            }
        }
        item(key = "chat-cmd-bar") { ChatQuickCmdBar(onQuickCmd) }
    }
}

internal const val CHAT_COLLAPSE_THRESHOLD: Int = 6
internal const val CHAT_RECENT_KEPT: Int = 4

/** PWA `.chat-cmd-bar`: 📚 memories · 🔍 recall · 🔗 kg query · 🔬 research. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatQuickCmdBar(onQuickCmd: (String) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        listOf(
            "📚 memories" to "memories",
            "🔍 recall" to "recall: ",
            "🔗 kg query" to "kg query ",
            "🔬 research" to "research: ",
        ).forEach { (label, prefix) ->
            androidx.compose.material3.OutlinedButton(
                onClick = { onQuickCmd(prefix) },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(28.dp),
            ) {
                Text(label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** PWA `.chat-user` / `.chat-assistant` / `.chat-system` palette (style.css). */
internal data class ChatBubbleStyle(
    val avatar: String,
    val label: String,
    val avatarBg: Color,
    val roleColor: Color,
    val bubbleBg: Color,
    val bubbleBorder: Color,
)

internal fun chatBubbleStyle(role: SessionEvent.ChatMessage.Role): ChatBubbleStyle =
    when (role) {
        SessionEvent.ChatMessage.Role.User ->
            ChatBubbleStyle(
                avatar = "U",
                label = "You",
                avatarBg = Color(0xFF3B82F6),
                roleColor = Color(0xFF60A5FA),
                bubbleBg = Color(0xFF3B82F6).copy(alpha = 0.15f),
                bubbleBorder = Color(0xFF3B82F6).copy(alpha = 0.25f),
            )
        SessionEvent.ChatMessage.Role.Assistant ->
            ChatBubbleStyle(
                avatar = "AI",
                label = "Assistant",
                avatarBg = Color(0xFF10B981),
                roleColor = Color(0xFF34D399),
                bubbleBg = Color(0xFF10B981).copy(alpha = 0.10f),
                bubbleBorder = Color(0xFF10B981).copy(alpha = 0.20f),
            )
        SessionEvent.ChatMessage.Role.System ->
            ChatBubbleStyle(
                avatar = "S",
                label = "System",
                avatarBg = Color(0xFF64748B),
                roleColor = Color(0xFF94A3B8),
                bubbleBg = Color(0xFF94A3B8).copy(alpha = 0.08f),
                bubbleBorder = Color(0xFF94A3B8).copy(alpha = 0.15f),
            )
    }

/**
 * PWA `.chat-bubble`: header (22 px avatar · uppercase 10 px role · 9 px time)
 * inside the bubble; user right-aligned with a 2 px bottom-right corner,
 * assistant / system left-aligned (assistant 2 px bottom-left, system radius 8).
 */
@Composable
private fun ChatBubble(entry: ChatEntry) {
    val style = chatBubbleStyle(entry.role)
    val isUser = entry.role == SessionEvent.ChatMessage.Role.User
    val isSystem = entry.role == SessionEvent.ChatMessage.Role.System
    val shape =
        when {
            isUser -> RoundedCornerShape(12.dp, 12.dp, 2.dp, 12.dp)
            isSystem -> RoundedCornerShape(8.dp)
            else -> RoundedCornerShape(12.dp, 12.dp, 12.dp, 2.dp)
        }
    val widthFraction = if (isUser) 0.85f else if (isSystem) 0.80f else 0.90f
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = style.bubbleBg,
            shape = shape,
            border = androidx.compose.foundation.BorderStroke(1.dp, style.bubbleBorder),
            modifier = Modifier.fillMaxWidth(widthFraction),
        ) {
            Column(
                modifier =
                    if (isSystem) {
                        Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    } else {
                        Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                    AvatarDot(style.avatar, style.avatarBg)
                    Text(
                        style.label.uppercase(),
                        modifier = Modifier.padding(start = 6.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp,
                        color = style.roleColor,
                    )
                    if (entry.isStreaming) {
                        Text(
                            "  · typing",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        chatTimeLabel(entry.ts),
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.alpha(0.6f),
                    )
                }
                // PWA renderChatMarkdown: assistant content only (code blocks,
                // inline code, headings, lists); user/system stay plain text.
                if (entry.role == SessionEvent.ChatMessage.Role.Assistant && !entry.isStreaming) {
                    com.dmzs.datawatchclient.ui.autonomous.MarkdownView(entry.content)
                } else {
                    Text(
                        entry.content,
                        fontSize = if (isSystem) 12.sp else 13.sp,
                        lineHeight = if (isSystem) 18.sp else 21.sp,
                        color =
                            if (isSystem) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                    )
                }
            }
        }
    }
}

/** PWA `toLocaleTimeString([], {hour:'2-digit', minute:'2-digit'})` in the device zone. */
private fun chatTimeLabel(ts: Instant): String {
    val local = ts.toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
    return local.hour.toString().padStart(2, '0') + ":" + local.minute.toString().padStart(2, '0')
}

@Composable
private fun AvatarDot(
    label: String,
    bg: Color,
) {
    Box(
        modifier = Modifier.size(22.dp).background(bg, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
private fun TransientSystemRow(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            "· $text",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
