# UX — Session Detail

*Last updated 2026-05-01 for v0.57.0.*

Terminal-primary (ADR-0024 xterm.js in WebView). Three inline view tabs —
**tmux**, **channel**, **stats** — replace the old bottom-sheet picker row
(removed post-v0.33). Timeline is still accessible via the overflow menu.

---

## Layout — phone (narrow, < 600 dp)

### tmux tab (default)

```
┌─────────────────────────────────────────────┐
│ ←  workstation-a3f2              [⋮ menu]   │  TopAppBar — tap title to rename
├─────────────────────────────────────────────┤
│  ● running   ⠿⠿⠿ generating   [📄 response]│  state badges + generating indicator
├─────────────────────────────────────────────┤
│ ⚠ INPUT REQUIRED                            │  amber banner — conditional
│   What should I do about the test failure?  │  shows prompt text from promptContext
├─────────────────────────────────────────────┤
│ [tmux] [channel] [stats]   [Aa] [⊡] [📜]  │  tab pills + terminal toolbar
├─────────────────────────────────────────────┤
│                                             │
│  $ claude --resume                          │
│  ✓ Running tests… 43 passed, 2 failed       │  xterm.js — pane_capture ANSI stream
│  ⏳ Waiting for input…                      │
│                                             │
│                                             │
├─────────────────────────────────────────────┤
│  ⏰ in 10 min: "continue"   [✕]            │  schedules strip — conditional
├─────────────────────────────────────────────┤
│ [📅][🎙]  Type reply…              [▶][✉] │  composer
└─────────────────────────────────────────────┘
```

### channel tab

```
├─────────────────────────────────────────────┤
│ [tmux] [channel] [stats]                    │  no toolbar on channel/stats
├─────────────────────────────────────────────┤
│                                             │
│  ╭─────────────────────────────────────╮   │
│  │ assistant  Running tests… 43 passed │   │  role-labelled chat bubbles
│  ╰─────────────────────────────────────╯   │
│   ╭──────────────────╮                     │
│   │ user  "continue" │                     │
│   ╰──────────────────╯                     │
│  ╭─────────────────────────────────────╮   │
│  │ ⏳ Waiting — what should I do?      │   │  latest prompt highlighted
│  │ [Yes] [No] [Continue] [Stop]        │   │  quick-reply chips inline
│  ╰─────────────────────────────────────╯   │
├─────────────────────────────────────────────┤
│ [📅][🎙]  Type reply…              [▶][✉] │
└─────────────────────────────────────────────┘
```

### stats tab (v0.57.0 — B11)

Shows the observer envelope for the current session. Self-hides data rows
when no matching envelope exists (eBPF not active, or daemon predates
observer v4.1.0).

```
├─────────────────────────────────────────────┤
│ [tmux] [channel] [stats]                    │
├─────────────────────────────────────────────┤
│  ┌───────────────────────────────────────┐  │
│  │ Process Stats                         │  │
│  │                                       │  │
│  │    ╭──────╮                           │  │
│  │   ╱  42%  ╲   CPU      42.3%         │  │  ring colour: green/amber/red
│  │   ╲        ╱   RSS      1.2 GB       │  │  at 70%/90% thresholds
│  │    ╰──────╯    Threads  24            │  │
│  │                FDs      512           │  │
│  │                                       │  │
│  │  Net ↓   125.4 KB/s                  │  │  only when eBPF active
│  │  Net ↑    78.2 KB/s                  │  │
│  │                                       │  │
│  │  GPU      12.0%    GPU Mem  2.0 GB   │  │  only when GPU present
│  │  PID      3401 (+3)                  │  │
│  └───────────────────────────────────────┘  │
└─────────────────────────────────────────────┘
```

### scroll-mode overlay (replaces composer when 📜 active)

```
├─────────────────────────────────────────────┤
│         [ PgUp ]   [ PgDn ]                 │
│       [  ↑  ]  [ ↓ ]  [ESC]               │  full-width nav strip
└─────────────────────────────────────────────┘
```

---

## Layout — foldable / tablet (wide, ≥ 600 dp — BL7)

Sessions list and session detail render side-by-side. Tapping a session
sets `selectedSessionId` state; no nav push occurs. BottomNavBar remains
on the left pane (360 dp).

```
┌──────────────────────┬──────────────────────────────────────────┐
│  Sessions   (360 dp) │  Session detail  (remaining width)        │
│                      │                                           │
│  ● workstation-a3f2  │ ←  workstation-a3f2           [⋮ menu]  │
│  ○ staging-worker    │ ● running  ⠿⠿⠿ generating               │
│  ● home-server       │ ⚠ INPUT REQUIRED — What should I do?     │
│                      │ [tmux] [channel] [stats]   [Aa][⊡][📜]  │
│                      │ ┌─────────────────────────────────────┐  │
│                      │ │ $ claude --resume                   │  │
│                      │ │ ✓ 43 passed, 2 failed               │  │
│                      │ │ ⏳ Waiting for input…               │  │
│                      │ └─────────────────────────────────────┘  │
│ ─── BottomNavBar ─── │ [📅][🎙]  Type reply…          [▶][✉] │
└──────────────────────┴──────────────────────────────────────────┘
```

Right pane shows "Select a session from the list" when nothing is selected.
New Session (`+` FAB on Sessions tab) still uses full-screen nav over the
two-pane layout.

---

## TopAppBar overflow menu

| Item | Condition | Action |
|---|---|---|
| Rename | always | inline header editor (tap title also works) |
| Kill | state = running / waiting | confirm dialog → `killSession` |
| Restart | state = terminal | confirm dialog → `restartSession` |
| Delete | state = terminal | confirm dialog → `deleteSession` |
| Change state | always | dropdown override |
| Timeline | always | `TimelineSheet` modal bottom sheet |
| Saved commands | always | `SavedCommandsSheet` |

---

## Tab bar

Three pill buttons flush-left; terminal toolbar controls right-aligned on
the same row (only when tmux tab is active and session is not chat-mode).

| Tab | Content | Toolbar visible |
|---|---|---|
| tmux | xterm.js WebView, pane_capture ANSI stream | yes |
| channel | Chat bubble list, quick-reply chips | no |
| stats | SessionStatsPanel (observer envelope) | no |

Chat-mode sessions (output_mode=chat — OpenWebUI, Ollama) force the
channel tab's `ChatTranscriptPanel` regardless of user toggle.

---

## Terminal toolbar (tmux tab only)

Inline right of tab pills:

- **Aa** — font size picker (decrements through 9/11/13/15 px)
- **⊡** — Fit (calls `dwFit` JS bridge to re-sync xterm dimensions)
- **📜** — Scroll mode toggle; activates PgUp/PgDn overlay, freezes
  pane_capture writes so scrollback isn't stomped by live frames

---

## Composer

Normal mode (scroll mode off):

```
[📅]  [🎙]  Type reply…  [▶ Commands]  [📄 Response]  [✉ Send]
```

- **📅** — Schedule reply dialog (seeds with composer text)
- **🎙** — Voice capture (press-hold = continuous; tap = single shot)
- **▶ Commands** — Quick commands sheet: System presets / Saved / Custom / Arrow-key chips
- **📄 Response** — Last-response bottom sheet (shown when `hasResponse`)
- **✉ Send** — `sendReply` via WS `send_input`

Scroll mode replaces the entire composer row with PgUp / PgDn / ↑ / ↓ / ESC buttons.

---

## State pill colours

| State | Colour |
|---|---|
| running | success green |
| waiting | teal accent |
| rate_limited | amber warning |
| completed | onSurfaceVariant |
| killed | onSurfaceVariant |
| error | error red |

GeneratingIndicator (3 animated dots + "generating" label) appears below
the terminal while state = running.

---

## Stats tab — data source

`SessionStatsPanel` reads `StatsViewModel.state.stats.envelopes` (polled
every 5 s + live WS overlay via `StatsHub`). Matches the first entry where
`kind == "session"` and `id` prefix-matches `sessionId`. Fields rendered:

| Field | Source |
|---|---|
| CPU % ring | `envelope.cpuPct` |
| RSS | `envelope.rssBytes` |
| Threads / FDs | `envelope.threads`, `envelope.fds` |
| Net ↓ / ↑ | `envelope.netRxBps`, `envelope.netTxBps` |
| GPU % / GPU mem | `envelope.gpuPct`, `envelope.gpuMemBytes` |
| PID | `envelope.rootPid` (+ count if pids.size > 1) |

Rendered only when the server emits `envelopes[]` and eBPF probes are
loaded (`ebpfActive == true`).

---

## Wear parity

Sessions page → session detail popup → "↩ Continue" / "✕ Stop" quick-reply
chips for waiting sessions (v0.56.0). Wake-on-alert: watch receives
`/datawatch/alert` MessageClient message when a session enters waiting_input.

## Auto parity

Public build: voice reply + Yes/No/Continue/Stop quick-reply on
`SessionReplyScreen`. No terminal / stats surfaces (Messaging template
constraints — ADR-0031).

Internal / devPassenger build: full passenger UI (ADR-0031 Sprint 4 — post-1.0).
