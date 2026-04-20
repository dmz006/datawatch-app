# Terminal — 1:1 PWA parity (B1 re-baseline)

- **Date:** 2026-04-20
- **Version at plan time:** v1.0.1
- **Target ship version:** v1.0.2 (behaviour fix) + v1.1.0 (font-size + toolbar UX)
- **Supersedes:** `docs/plans/2026-04-19-terminal-hardening.md` (that plan addressed a symptom — write-cursor reset — and got it wrong as the primary cause)
- **Source of truth:** `dmz006/datawatch` parent repo PWA — `internal/server/web/app.js`. Snapshot pulled live from `https://ralfthewise:8443/app.js` 2026-04-20 00:17.

## Root cause — correctly diagnosed this time

The mobile client is wired onto the **wrong WebSocket frame type** for terminal display. The datawatch hub sends two streams:

| Frame | Contents | Purpose |
|-------|----------|---------|
| `raw_output` | Incremental PTY bytes (ANSI + cursor escapes, as they leave tmux) | Log-mode fallback only |
| `pane_capture` | Full tmux pane snapshot as an array of pre-rendered lines | Authoritative terminal display |

The PWA (`app.js` lines 263–294, 303–354) routes `raw_output` to nothing on terminal-mode sessions and uses `pane_capture` as its one and only display source:

- **First capture:** `term.reset()` then `term.write(lines.join('\r\n'))` — clean slate.
- **Subsequent captures:** `term.write('\x1b[2J\x1b[3J\x1b[H' + lines.join('\r\n'))` — clear screen + clear scrollback + home + redraw, batched by xterm into one frame.
- Throttled to ~30 FPS.
- Skips frames containing `DATAWATCH_COMPLETE:` marker.
- Freezes on terminal state `complete`/`failed`/`killed`.

The mobile client writes `raw_output` directly to xterm, which dumps cursor-positioned bytes intended for a 120-col tmux pane onto a 39-col xterm — producing the scattered-character mess that looked like a "freeze".

## Secondary issues the parent repo already solved

- **PTY size negotiation.** PWA sends `{"type":"resize_term","data":{"session_id":"...","cols":N,"rows":M}}` after every fit. Server tmux-resizes the pane and replies with a fresh `pane_capture` at the new geometry. This is the missing outbound frame documented as BL18.
- **Minimum cols.** `minCols = configCols || 80`. Claude Code specifically needs 120 cols — the PWA `term.resize(minCols, ...)` after fit and accepts horizontal scroll when the container is narrower. This is how the PWA avoids the wrap-garbage the user is seeing.
- **Font-size toolbar.** `changeTermFontSize(delta)` (line 1714) — ±buttons, clamped 5–20 px, persisted in `localStorage`, applied via `term.options.fontSize` + `fit()`.
- **Interactive keyboard.** `term.onData` → `send('send_input', {session_id, text, raw: true})`. Raw mode forwards escape sequences (arrows, Ctrl-C, etc.) to tmux.
- **Reconnect watchdog.** If no `pane_capture` arrives within 5 s, re-subscribe up to 3 times before showing a retry button.
- **Resize debounce.** 200 ms ResizeObserver debounce on container size change.

## Scope

`:shared` transport + `:composeApp` terminal + `:composeApp` viewmodel. Plan implements Phase 1–3 for v1.0.2 (the fix ships behaviour parity). Phase 4 is v1.1.0 (UX affordances — font toolbar, horizontal scroll gesture, retry watchdog).

## Phases

### Phase 1 — `pane_capture` frame handling (v1.0.2)

1. **DTO.** Add a `pane_capture` case to `WsFrameDto`:
   - `data.session_id: String`
   - `data.lines: List<String>` (or raw strings; server sends pre-joined lines)
2. **Domain event.** Add `SessionEvent.PaneCapture(sessionId, ts, lines, isFirst)` — a new sealed-class case alongside `Output` / `StateChange` / etc.
3. **EventMapper.** Map `pane_capture` → `SessionEvent.PaneCapture`. Leave `raw_output` mapping in place for log-mode fallback but route it to a separate channel (not the terminal).
4. **TerminalView.** On a `PaneCapture`:
   - First capture (keyed per session): call `window.dwReset()` (new host.html export) → then `window.dwWrite(lines.join('\r\n'))`.
   - Subsequent: `window.dwWriteClear(lines.join('\r\n'))` that prepends `\x1b[2J\x1b[3J\x1b[H`.
   - Throttle writes to ≥33 ms apart.
5. **Remove the current LaunchedEffect that flushes `SessionEvent.Output.body`** when a `PaneCapture` is the source — `Output` goes to the chat/alert surfaces only, not terminal.

**Acceptance:** opening session `787e*` renders the Claude Code TUI at its intended geometry (distorted by column-count mismatch but structurally intact — the spinner sits on one line, not scattered across 20).

### Phase 2 — outbound `resize_term` (v1.0.2)

1. **Transport.** `WebSocketTransport` gains an outbound channel: `fun sendResize(sessionId, cols, rows)` exposed via `SharedFlow<OutboundFrame>` or a `Channel<OutboundFrame>` consumed inside the `webSocket { }` block. Frame shape: `{"type":"resize_term","data":{"session_id":"...","cols":N,"rows":M}}`.
2. **Host bridge.** After FitAddon converges, host.html calls a new `DwBridge.onFit(cols, rows)` with the current dimensions.
3. **TerminalView.** Forwards `onFit` → `SessionDetailViewModel.onTerminalFit(cols, rows)` → `WebSocketTransport.sendResize`.
4. **Debounce.** 200 ms on the Kotlin side (match the PWA ResizeObserver debounce).

**Acceptance:** `tcpdump`/logcat shows a `{"type":"resize_term",...}` frame leaves the device within 1 s of opening a session, and a `pane_capture` frame arrives in response with geometry matching our xterm cols/rows.

### Phase 3 — minCols enforcement (v1.0.2)

1. Read `session.console_cols` from REST `GET /api/sessions` (already in our `Session` domain model? Verify — add if missing).
2. In TerminalView: after fit, if `term.cols < minCols`, call `term.resize(minCols, term.rows)` (via new host-side `dwForceCols(cols)`).
3. `minCols = session.console_cols ?: 80`; claude-code backend forces 120 (detect backend via `session.llm_backend`).
4. Enable horizontal scroll inside the xterm container — drop `overflow: hidden` on body, add `overflow-x: auto` on `#term`.

**Acceptance:** for session `787e*` (llm_backend=claude-code, console_cols≥120), xterm renders at 120 cols, container scrolls horizontally on drag, spinner frames land on one logical line.

### Phase 4 — UX affordances (v1.1.0)

1. **Font-size toolbar.** `−` / `+` buttons above the terminal. Clamp 5–20 px. Persist in DataStore. Call `term.options.fontSize = N; fit()` through `dwSetFontSize(N)`.
2. **Retry watchdog.** If no `pane_capture` arrives within 5 s of subscribe, re-subscribe up to 3 times; surface "Unable to connect" with retry button after that.
3. **Interactive keyboard.** Wire `term.onData` → `send_input {raw:true}` through the existing `DwBridge.onInput` placeholder.
4. **Skip transitional frames.** Drop captures containing `DATAWATCH_COMPLETE:`; freeze display on session state `complete`/`failed`/`killed`.

## Non-goals for this plan

- Re-implementing tmux capture-pane on the client. We consume the server's frames verbatim.
- Binary WS frames. Server is text-frame only.
- Offline scrollback buffer beyond xterm's 5000 lines. Per ADR-0013.

## Validation

- `:shared` unit tests for the new `pane_capture` decoder + outbound `resize_term` encoder (MockWebServer test).
- Live test on `787e*` against `ralfthewise`. Evidence: screenshot + `adb logcat DwTerm:V` showing `resize_term` → `pane_capture` round-trip.

## Why the earlier "Phase 1 — write-cursor reset" (`aff6e19`) still stays

Keyed `lastWrittenIndex` on `sessionId` is still correct — it was a real latent bug even if it wasn't the symptom the user reported. Do not revert.

## Why the "pinch-zoom" mitigation (`78aeae1`) stays for now

Pinch-zoom doesn't hurt and will still help users who want to inspect dense output. The Phase-3 horizontal-scroll container and fixed minCols are the real fix for the wrap problem; zoom becomes optional.

## Why the ANR fix (in-flight) stays

`SessionEventRepository.insert` executing SQLCipher on main thread is an orthogonal bug that happened to be masked by the terminal issue. Ship it — it's a pure improvement regardless of how we handle frame routing.
