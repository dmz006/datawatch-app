# Terminal-emulation audit — 2026-04-21

**Source:** parent `dmz006/datawatch` — `internal/server/web/app.js`
(current `/tmp/pwa-app.js`) + git history of commits touching it.
**User directive (2026-04-21):** mobile terminal must *fully learn* from
parent's accumulated terminal fixes and function like PWA sessions.

---

## Key parent commits shaping today's PWA terminal

| SHA | Version | Lesson |
|---|---|---|
| 61cedd5 | v0.13.0 | Initial xterm.js introduction for TUI output |
| 2f6560d | v0.13.1 | xterm.js raw output pipeline |
| 0fcdf09 | v0.13.2 | Raw output buffer per session (capped) |
| c22869d | v0.13.3 | xterm TUI rendering + interactive keyboard |
| e0dc9d3 | v0.13.5 | Initial-render fix — raw subscribe output, no line joining |
| e0f2bb0 | v0.13.6 | **Live TUI streaming — periodic drain every 2s** |
| 92ef564 | v0.13.6 | **Visible font toolbar + fit-to-width button + horizontal scroll** |
| f00f534 | v0.13.6 | **Seed ESC/Up/Down keys + font size control + smaller default font** |
| ac060d1 | v0.14.0 | **tmux resize sync + inline confirm modals** |
| c1a6eda | v0.14.0 | **Terminal wrap fix — tmux 80x24 default + pane_capture after resize** |
| 85d719a | v0.14.0 | Remove needs-input banner + **debounce resize** |
| 41cf705 | v0.14.1 | **Per-LLM console size — claude 120x40, configurable per backend** |
| 8538622 | v0.19.0 | Per-LLM config split, terminal fixes |
| 0393e26 | — | **Terminal rendering — single display source**, alert format |
| 669bfe0 | — | **Flicker-free terminal rendering** |
| e9900da | B1 | **xterm.js stability — 79ms load (was 20s), crash resilience** |
| 5d744e3 | v2.3.2 | **tmux history scrolling + ESC button + backlog plans** |
| 5879fb8 | — | **tmux scroll mode uses copy-mode command directly** |
| 78a9e19 | — | **scroll mode pauses pane_capture, uses Escape to exit** |
| 9d69ddd | — | **allow pane_capture in scroll mode so web terminal updates** (reversal) |
| a85d8bd | — | Remove scroll commands from saved commands dropdown |
| 343a99c | B25 | **Trust prompt invisible — surface full prompt + key tip** |
| a1bb415 | v4.0.2 | **B32 tmux 2nd-Enter + B33 yellow "Input Required" banner** |
| e8908a1 | v4.0.7 | B35/B36/B37 bug fixes |

---

## PWA terminal behaviours (from current app.js)

### 1. pane_capture write pipeline (lines 316–356)

```js
case 'pane_capture':
  // Buffer if terminal not ready yet (subscribe fires before initXterm)
  if (!state.terminal && ...) {
    state._pendingPaneCapture = msg.data;
    break;
  }
  // Throttle: max ~30fps to prevent xterm.js buffer overload
  if (now - state._lastPaneWrite < 33) break;

  // Freeze once session is complete/failed/killed — prevents flash of
  // the shell prompt the tmux session shows after the LLM exits.
  if (state in ['complete','failed','killed']) break;

  // Skip transitional completion-marker frame
  if (capLines.some(l => l.includes('DATAWATCH_COMPLETE:'))) break;

  if (!state._termHasContent) {
    // First frame — dismiss splash, reset
    splash.remove();
    clearTimeout(state._termWatchdog);
    state.terminal.reset();
    state.terminal.write(capLines.join('\r\n'));
    state._termHasContent = true;
  } else {
    // \x1b[2J clear screen + \x1b[3J clear scrollback + \x1b[H home
    state.terminal.write('\x1b[2J\x1b[3J\x1b[H' + capLines.join('\r\n'));
  }
```

### 2. raw_output routing (lines 276–307)

- Keep `rawOutputBuffer[sid]` — ANSI-preserved, capped at 500 lines
- Terminal mode: **do NOT write raw_output to xterm** — pane_capture is
  the authoritative display source
- Log mode: append stripped lines to `.log-viewer-mode` div
- Chat mode: skip — structured chat_message events drive rendering

### 3. initXterm (lines 2010–2100)

- `configCols / configRows` from session config (defaults 80 / 24; 120 /
  40 for claude-code per `41cf705`)
- `scrollback: 5000`
- After `term.open(container)`:
  - `syncTmuxSize()` sends `send('resize_term', {session_id, cols, rows})`
  - If `term.cols < minCols && fits`: `term.resize(minCols, rows)`
  - If `term.cols < minCols && configCols >= 120`: force anyway,
    container scrolls horizontally (critical for claude-code)
- Debounced resize observer on container re-fires fit + sync

### 4. termFitToWidth (line 1890)

- Auto-shrink font 5..current until `viewport.scrollWidth <= clientWidth + 2`
- Persist font size; update toolbar label

### 5. Scroll-mode (lines 1912–1975)

- Enter: `send('command', {text: 'tmux-copy-mode <fullId>'})`
- UI: hide input bar, show scroll bar with PageUp/PageDown/ESC buttons
- PageUp/Down: `send('command', {text: 'sendkey <fullId>: PPage' | 'NPage'})`
- Exit: `send('command', {text: 'sendkey <fullId>: Escape'})`
- Periodic safety check every 2s: if input bar hidden & no scroll bar,
  restore via `restoreInputBar()`
- Parent commit history shows scroll mode vs pane_capture went back and
  forth: **9d69ddd** allowed pane_capture to continue in scroll mode
  (final state — updates flow, viewport stays where user put it)

### 6. Watchdog

- If no pane_capture within 5s of session open: re-subscribe via WS
- Prevents stuck loading splash

### 7. Transitional frames

- `DATAWATCH_COMPLETE:` marker — server emits when LLM exits but
  before session state transitions. PWA skips that frame to avoid
  flashing the shell prompt.

### 8. Trust-prompt key tip (B25, 343a99c)

- When prompt looks like "Do you trust this file? [y/N]": surface the
  imperative + action lines (not just last_prompt). Mobile has
  `prompt_context` multi-line preview already ✅.

---

## Mobile terminal status

| PWA feature | Mobile state | Notes |
|---|---|---|
| pane_capture first=reset+write | ✅ `dwPaneCapture(isFirst=true)` | |
| pane_capture redraw=`\x1b[2J\x1b[3J\x1b[H` + write | ✅ | |
| **30fps throttle** | ❌ | mobile writes every frame |
| **Freeze on complete/failed/killed** | ❌ | terminal keeps receiving pane_captures |
| **Skip DATAWATCH_COMPLETE marker frame** | ❌ | |
| pending-frame buffering | ✅ via `lastWrittenIndex` reset on session switch | |
| **raw_output ignored in terminal mode** | ✅ partial — we have a legacy path that fires only when pane_capture never arrives | good default |
| Theme palette | ✅ aligned v0.23.0 | |
| scrollback 5000 | ✅ | |
| **configCols / configRows from session** | ❌ | fixed at 80x24; claude renders broken on phone width |
| **resize_term WS on fit settle** | ❌ **BIGGEST GAP** | WsTransport currently read-only; tmux stays at server default |
| **Force minCols resize with horizontal scroll** | ❌ | |
| User font ± | ✅ `dwSetFontSize` | |
| Fit button (manual) | ✅ `dwFit` | |
| **Auto fit-to-width (shrink font until it fits)** | ❌ | only manual ± |
| Jump to bottom | ✅ `dwScrollToBottom` | |
| **Scroll-mode (tmux copy-mode + PageUp/Down/ESC)** | ❌ | |
| **Watchdog — re-subscribe after 5s silence** | ❌ | |
| onKey → send_input WS | ❌ | not wired; REST reply covers composer |
| Pending pane_capture before xterm ready | ✅ reset semantics handle it | |
| Trust prompt multi-line | ✅ via `prompt_context` preview | |
| CA-cert install link | ✅ in Add Server flow | |
| Backlog load | ✅ v0.12 | |
| Font size persisted | ✅ SharedPrefs | |

---

## Work breakdown — terminal sprint (promoted to next)

Order by impact × cost. Each bullet is one commit-worth.

1. **Add WS outbound path** — refactor `WebSocketTransport` or build a
   long-lived `WsHub` keyed per-profile with both subscribe + send. Takes
   the most work; everything else depends on it.
2. **resize_term** — after `safeFit()` settles in `host.html`, post a
   message via JSBridge → Kotlin → WS. TUIs finally render at the
   correct tmux pane width.
3. **configCols from session** — read backend-specific default (claude →
   120; default → server's `session.console_cols`). Exposed via an
   attribute on `ServerInfo` or a new `/api/session/config?id=` read.
4. **30fps pane_capture throttle** — simple time-gate in `dwPaneCapture`.
5. **Freeze on terminal state** — `TerminalView` already has
   `lastWrittenIndex` hook; extend to consult `session.state` and skip.
6. **Skip DATAWATCH_COMPLETE frames** — one-line filter.
7. **Watchdog** — 5-second LaunchedEffect after session open; if no
   PaneCapture arrived, call `resetPaneCaptureSeen()` + nudge WS.
8. **Auto fit-to-width** — iterate font size until `viewport.scrollWidth`
   fits. Needs a round-trip JS-to-Kotlin call.
9. **Scroll-mode** — new terminal-toolbar button; sends `command`
   `tmux-copy-mode <id>`. Shows Page Up / Page Down / ESC chips. Exit
   sends `sendkey <id>: Escape`.
10. **send_input WS** (optional) — for a future "live keyboard" mode.
    Composer's `replyToSession` REST path stays the default.

---

## Non-goals

- Split-pane multiple sessions on one screen — PWA doesn't have this
  either.
- Local tmux emulation (mobile-as-server) — out of scope.
- Full xterm.js control-sequence extensions (OSC hyperlinks, sixel) —
  not used by datawatch backends.
