package com.dmzs.datawatchclient.di

import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.domain.Session
import com.dmzs.datawatchclient.transport.ws.WsOutbound
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * tmux copy-mode enter / exit for Swift, mirroring Android
 * `SessionDetailViewModel.scrollModeCommand`: sent over REST `/api/command` so the
 * server acknowledges it (a WS `command` frame is dropped silently while the socket
 * reconnects, leaving tmux in copy-mode and the terminal looking hung). Falls back
 * to the WS frame when REST fails. [onResult] gets true when the server accepted it.
 */
public object IosScrollMode {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    public fun command(
        profile: ServerProfile,
        session: Session,
        enter: Boolean,
        onResult: (Boolean) -> Unit,
    ) {
        val text: String =
            if (enter) "tmux-copy-mode ${session.fullId}" else "sendkey ${session.fullId}: Escape"
        scope.launch {
            val r: Result<String> = IosServiceLocator.transportFor(profile).runCommand(text)
            val ok: Boolean =
                if (r.isSuccess) {
                    val out: String = r.getOrNull().orEmpty()
                    !(out.startsWith("Error") || out.contains("not found"))
                } else {
                    WsOutbound.sendCommand(session.id, text)
                }
            onResult(ok)
        }
    }
}
