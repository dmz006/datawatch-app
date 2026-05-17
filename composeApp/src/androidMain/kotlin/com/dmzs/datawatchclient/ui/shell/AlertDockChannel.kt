package com.dmzs.datawatchclient.ui.shell

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

public object AlertDockChannel {
    private val _open = MutableStateFlow(false)
    public val open: StateFlow<Boolean> = _open.asStateFlow()

    public fun toggle() { _open.value = !_open.value }
    public fun close() { _open.value = false }
}
