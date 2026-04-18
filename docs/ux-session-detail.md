# UX — Session Detail

Bottom-sheet pattern (ADR-0023): chat is the spine, auxiliary views slide up from the
bottom. xterm.js in WebView for terminal (ADR-0024).

## Layout

```
┌─────────────────────────────────────────┐
│ ←  primary › us-west-2 › workstation-a3f2│  breadcrumb (ADR-0021)
├─────────────────────────────────────────┤
│ Session  a3f2  · running · 4m ago       │  title + state + last activity
├─────────────────────────────────────────┤
│                                         │
│  [llm]  Running tests… 43 passed        │
│  [user] replied: "continue"             │
│  [sys]  rate-limit reset at 18:42       │
│  [llm]  ⏳ waiting for prompt           │  ← pending prompt highlighted
│                                         │
│                                         │
│                                         │
├─────────────────────────────────────────┤
│ [ 📟 Terminal ] [ 📋 Logs ] [ 🧠 Memory ] [ ⏱ Timeline ] │ ← sheet pickers
├─────────────────────────────────────────┤
│ [🎙]  Type reply…                 [send]│  composer (input-bar-h 60)
└─────────────────────────────────────────┘
```

## Bottom-sheet variants

Tapping a picker chip opens a sheet that covers ~60% of the screen; dragging up expands it
full-screen, down dismisses. Only one sheet open at a time.

### Terminal sheet

- xterm.js WebView loaded from `assets/xterm/`.
- Connects to `/ws?session=<id>` and streams ANSI frames.
- Toolbar: clear, copy all, search, font size, toggle wrap.
- On disconnect: greyed-out with reconnect button; no local replay.

### Logs sheet

- Scrollable list of timeline events: session created, prompt detected, reply sent,
  rate-limit, resumed, completed, error.
- Each row timestamped, tap to expand details.
- Search + date filter.

### Memory sheet

- Three tabs: Recall, Remember, Graph.
- Recall: search box → calls `memory_recall` MCP tool → streams results.
- Remember: pinned facts + "Add fact" composer.
- Graph: small KG view around this session (nodes = entities, edges = relations), tap
  node to drill.

### Timeline sheet

- Chronological record of state transitions for this session.
- Pure view of the `timeline_entry` table rows for this session, with source (server or
  local observation).

## Chat message rendering

- Role color: user = accent purple, llm = neutral on surface, system = text2 italic.
- Monospace body for code fences; tap-and-hold on any message → copy, share, remember
  (add to memory), reply-to (quotes it in composer).
- Pending-prompt highlighted with accent border + "Reply" chip appears inline.

## Composer

- Text field, mic button, send button.
- Press-and-hold mic → voice capture, release → transcribe pipeline (ADR-0006).
- Tapping mic toggles continuous mode (tap again to stop).
- Attachments (post-MVP): file pick, image pick, paste clipboard.

## State handling

- Session state pill colors: running = success green, waiting = accent, rate-limited =
  warning amber, completed = text2, killed = text2 strikethrough, error = error red.
- Online/offline banner appears above the breadcrumb when the server is unreachable, per
  ADR-0013 rules.

## Parity tie-ins

- "Kill session" (ADR-0019): kebab menu → Kill → confirm modal "This stops the tmux session.
  Cannot be undone." No biometric in v1.
- "Override state": kebab menu → Change state → dropdown. Confirm modal.
- "Edit schedule from session": kebab menu → Schedule this session → opens Schedules tab
  pre-filled.

## Wear parity

Notification-based reply maps to `session_reply`. Rich Wear app (w4) shows session list +
dictation composer — same flow, smaller surface.

## Auto parity

Public build: inbound prompt → TTS readout; voice reply dictates body → `session_reply`.
No terminal / logs / memory / timeline surfaces (not Messaging-template-compatible).

Internal build: all four sheets available via the full passenger UI (ADR-0031).
