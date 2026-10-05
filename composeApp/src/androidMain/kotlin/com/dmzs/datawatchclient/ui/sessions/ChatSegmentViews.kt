package com.dmzs.datawatchclient.ui.sessions

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.util.ChatContentSplitter
import com.dmzs.datawatchclient.util.ChatImageSegment
import com.dmzs.datawatchclient.util.ChatMarkdownSegment
import com.dmzs.datawatchclient.util.ChatThinkingSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** PWA `.chat-thinking` purple (rgba(168,85,247,…)). */
private val ThinkingPurple = Color(0xFFA855F7)

/** Max bytes fetched for one inline chat image. */
private const val CHAT_IMAGE_MAX_BYTES: Int = 8 * 1024 * 1024

/**
 * Completed assistant bubble body — PWA `renderChatMarkdown`: markdown plus
 * collapsible thinking sections and inline images (shared [ChatContentSplitter]).
 */
@Composable
internal fun ChatAssistantContent(content: String) {
    val segments = remember(content) { ChatContentSplitter.split(content) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        segments.forEach { seg ->
            when (seg) {
                is ChatMarkdownSegment -> com.dmzs.datawatchclient.ui.autonomous.MarkdownView(seg.text)
                is ChatThinkingSegment -> ChatThinkingBlock(seg.text)
                is ChatImageSegment -> ChatInlineImage(seg)
            }
        }
    }
}

/** PWA `<details class="chat-thinking"><summary>🧠 Thinking...</summary>` — closed by default. */
@Composable
private fun ChatThinkingBlock(text: String) {
    var open by rememberSaveable(text) { mutableStateOf(false) }
    val shape = RoundedCornerShape(6.dp)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .background(ThinkingPurple.copy(alpha = 0.06f), shape)
                .border(BorderStroke(1.dp, ThinkingPurple.copy(alpha = 0.15f)), shape),
    ) {
        Text(
            (if (open) "▾ " else "▸ ") + stringResource(R.string.chat_thinking_summary),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 10.dp, vertical = 6.dp),
        )
        if (open) {
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(ThinkingPurple.copy(alpha = 0.10f)))
            Text(
                text,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * PWA `.chat-image`: image at full bubble width (radius 6, 4 dp margin) with
 * the alt text as a 9 sp caption; hidden on load error like the PWA `onerror`.
 * Only absolute http(s) URLs are fetched (platform TLS validation applies).
 */
@Composable
private fun ChatInlineImage(seg: ChatImageSegment) {
    var bitmap by remember(seg.url) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(seg.url) { mutableStateOf(!seg.loadable) }
    if (seg.loadable) {
        LaunchedEffect(seg.url) {
            val bmp = withContext(Dispatchers.IO) { runCatching { fetchChatImage(seg.url) }.getOrNull() }
            if (bmp == null) failed = true else bitmap = bmp
        }
    }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        val bmp = bitmap
        if (bmp != null && !failed) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = seg.alt.ifBlank { null },
                contentScale = ContentScale.FillWidth,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(6.dp)),
            )
        }
        if (seg.alt.isNotBlank()) {
            Text(seg.alt, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun fetchChatImage(url: String): Bitmap? {
    val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
    conn.connectTimeout = 10_000
    conn.readTimeout = 15_000
    conn.instanceFollowRedirects = true
    return try {
        if (conn.responseCode !in 200..299) return null
        val bytes =
            conn.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > CHAT_IMAGE_MAX_BYTES) return null
                    out.write(buf, 0, n)
                }
                out.toByteArray()
            }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    } finally {
        conn.disconnect()
    }
}
