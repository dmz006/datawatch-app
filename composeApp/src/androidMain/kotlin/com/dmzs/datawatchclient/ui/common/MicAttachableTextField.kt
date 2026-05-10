package com.dmzs.datawatchclient.ui.common

<<<<<<< HEAD
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
public fun MicAttachableTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
    readOnly: Boolean = false,
    skipMic: Boolean = false,
    whisperConfigured: Boolean = false,
    onMicClick: (() -> Unit)? = null,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            label = label,
            placeholder = placeholder,
            minLines = minLines,
            maxLines = maxLines,
            readOnly = readOnly,
        )
        if (minLines >= 2 && !readOnly && !skipMic && whisperConfigured && onMicClick != null) {
            IconButton(
                onClick = onMicClick,
                modifier = Modifier.padding(start = 4.dp),
            ) {
                Icon(Icons.Filled.Mic, contentDescription = "Voice input")
            }
        }
    }
=======
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A text field that can optionally attach microphone (Whisper) input.
 * When [whisperConfigured] is true and a recording is available, the
 * transcript is injected via [onValueChange]. For now the mic attachment
 * is a UI stub — the actual Whisper integration wires via the voice
 * subsystem. Sprint 7 / S7-4.
 */
@Composable
fun MicAttachableTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
    placeholder: @Composable (() -> Unit)? = null,
    label: @Composable (() -> Unit)? = null,
    whisperConfigured: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        minLines = minLines,
        maxLines = maxLines,
        placeholder = placeholder,
        label = label,
    )
>>>>>>> worktree-agent-a30d0add550b00344
}
