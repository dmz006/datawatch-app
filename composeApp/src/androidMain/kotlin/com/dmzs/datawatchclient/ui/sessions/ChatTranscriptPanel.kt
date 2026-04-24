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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.domain.SessionEvent
import kotlinx.datetime.Instant

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
        Box(
            modifier = modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Waiting for the first chat message…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(messages, key = { "${it.ts.toEpochMilliseconds()}-${it.hashCode()}" }) { entry ->
            ChatBubble(entry)
        }
        if (transientSystem.isNotEmpty()) {
            item(key = "transient-${transientSystem.lastOrNull() ?: ""}") {
                TransientSystemRow(transientSystem.lastOrNull() ?: "")
            }
        }
    }
}

@Composable
private fun ChatBubble(entry: ChatEntry) {
    when (entry.role) {
        SessionEvent.ChatMessage.Role.User ->
            UserOrAssistantBubble(
                avatar = "U",
                label = "You",
                content = entry.content,
                ts = entry.ts,
                alignEnd = true,
                background = MaterialTheme.colorScheme.primaryContainer,
                foreground = MaterialTheme.colorScheme.onPrimaryContainer,
                isStreaming = false,
            )

        SessionEvent.ChatMessage.Role.Assistant ->
            UserOrAssistantBubble(
                avatar = "AI",
                label = "Assistant",
                content = entry.content,
                ts = entry.ts,
                alignEnd = false,
                background = MaterialTheme.colorScheme.surfaceVariant,
                foreground = MaterialTheme.colorScheme.onSurfaceVariant,
                isStreaming = entry.isStreaming,
            )

        SessionEvent.ChatMessage.Role.System ->
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    entry.content,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
    }
}

@Composable
private fun UserOrAssistantBubble(
    avatar: String,
    label: String,
    content: String,
    ts: Instant,
    alignEnd: Boolean,
    background: androidx.compose.ui.graphics.Color,
    foreground: androidx.compose.ui.graphics.Color,
    isStreaming: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
    ) {
        if (!alignEnd) AvatarDot(avatar)
        Column(
            modifier = Modifier.padding(horizontal = 6.dp).fillMaxWidth(0.82f),
            horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
        ) {
            Row {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "  " + ts.toString().substringAfter('T').substringBefore('.'),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (isStreaming) {
                    Text(
                        "  · typing",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            Surface(
                color = background,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(top = 2.dp),
            ) {
                Text(
                    content,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = foreground,
                    fontFamily = if (label == "Assistant") FontFamily.Monospace else null,
                )
            }
        }
        if (alignEnd) AvatarDot(avatar)
    }
}

@Composable
private fun AvatarDot(label: String) {
    Box(
        modifier =
            Modifier
                .size(28.dp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    CircleShape,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
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
