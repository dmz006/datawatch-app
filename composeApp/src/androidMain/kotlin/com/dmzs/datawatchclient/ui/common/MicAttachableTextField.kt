package com.dmzs.datawatchclient.ui.common

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
}
