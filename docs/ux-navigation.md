# UX — Navigation

*Last updated 2026-04-22 for v0.33.0.*

Server-first shell (ADR-0020), breadcrumb primary + chips alternative
(ADR-0021), unlimited server tabs (ADR-0022).

## Top-level shell

```
┌─────────────────────────────────────────┐  header-h 56
│ [🔽 primary]  datawatch              ⋮  │  ← top bar, server pill on left opens tree drawer
├─────────────────────────────────────────┤
│                                         │
│   Current tab's content                 │
│                                         │
│                                         │
├─────────────────────────────────────────┤
│ Sessions  Channels  Stats  Settings     │  ← bottom nav, 4 tabs, nav-h 60
└─────────────────────────────────────────┘
```

Top-bar pill shows the active server. Tap → server-tree drawer slides in from the left:

```
┌───────────────────────────┐
│ Servers                   │
│                           │
│ ● primary           ✓     │  ← active
│   ├ us-west-2             │  ← proxied
│   │  ├ workstation-a3f2   │
│   │  └ workstation-b7c1   │
│   └ eu-central-1          │
│      └ build-box          │
│                           │
│ ○ laptop                  │
│ ○ raspberry               │
│                           │
│ + Add server              │
│ ⚙ All-servers view        │
└───────────────────────────┘
```

"All-servers view" toggles to a unified session list across every enabled profile; a small
badge on each row indicates origin server + proxy path.

## Proxy drill-down — Primary variant (ADR-0021, breadcrumb)

Inside a session sourced through the proxy chain:

```
┌─────────────────────────────────────────┐
│ ←  primary › us-west-2 › workstation-a3f2│  ← breadcrumb replaces title
├─────────────────────────────────────────┤
│ Session  a3f2  · running · 4m           │
│                                         │
│   [chat messages + pending prompt…]     │
│                                         │
├─────────────────────────────────────────┤
│ [🎙]  Type reply…                 [send]│
└─────────────────────────────────────────┘
```

Tapping any segment in the breadcrumb pops to that server's session list.

## Proxy drill-down — Alternative variant (breadcrumb + chips, for comparison)

Same breadcrumb on top, plus horizontal chip row under the title showing sibling servers
at the same proxy level for fast switching:

```
┌─────────────────────────────────────────┐
│ ←  primary › us-west-2 › workstation-a3f2│
├─────────────────────────────────────────┤
│ [ a3f2 ]  b7c1   c4d9   e2f5  ▸         │  ← chip row, scrollable, current highlighted
├─────────────────────────────────────────┤
│ Session  a3f2  · running · 4m           │
│   [chat messages…]                      │
└─────────────────────────────────────────┘
```

Both variants ship behind a setting in v0.x while we live-test. The one that wins becomes
the default and the losing pattern is removed. User picks during pre-MVP review.

## Server tabs (unlimited)

The server-tree drawer is the canonical way to switch servers. A lightweight "recent servers"
horizontal strip can appear above the bottom nav when ≥2 servers are enabled:

```
┌─────────────────────────────────────────┐
│ Sessions tab content                    │
├─────────────────────────────────────────┤
│ [primary] laptop  raspberry  +          │  ← optional quick-switch strip
├─────────────────────────────────────────┤
│ Sessions  Channels  Stats  Settings     │
└─────────────────────────────────────────┘
```

The strip is swipeable. A long-press on a tab opens a context menu (mute all sessions,
open in all-servers view, disable profile).

## Bottom-nav tabs

### Sessions

- Default view. Lists active + recent sessions for the current server (or all-servers).
- Each row: session id (mono), state pill (running/waiting/rate-limited/completed), last
  activity, task summary, mute indicator.
- Swipe right on a row: mute 10m. Swipe left: show actions (kill, share, open terminal).
- FAB: voice capture (press-and-hold) + secondary text input for typing.

### Channels

- Lists configured messaging backends on the current server (Signal, Telegram, Slack, etc.).
- Each: status (connected / configured / error), last message, test button, configure button.
- "Add channel" deep-links to Settings if the channel has no configuration yet.

### Stats

- Live dashboard fed by `/api/stats` + WebSocket: CPU, memory, disk, GPU, tmux sessions,
  uptime, session counts.
- Read-only (ADR-0019). Tapping a card expands to a chart + per-process breakdown where
  available.

### Settings

As of v0.33.0 Settings is grouped into nine sections (all rendered
through the generic `ConfigFieldsPanel` where applicable — v0.26.0):

- **General** — Servers, Security (biometric unlock), Schedules,
  Saved commands, Session backup, BehaviourPreferencesCard
  (input/output mode, recent window, max concurrent, scrollback).
- **LLM** — backend picker, BackendConfigDialog (model / base URL /
  API key), DetectionFiltersCard (prompt / completion / rate-limit /
  input-needed patterns + debounce / cooldown — v0.32).
- **Memory** — stats, list, search, delete, remember, export (SAF
  v0.22), KG timeline + graph (v0.17).
- **Comms** — ChannelsCard (list / test / enable — v0.18), federation
  peers (v0.20), CertInstallCard (v0.32).
- **Profiles** — active profile selector (v0.15),
  KindProfilesCard with ProfileEditDialog for Project / Cluster kinds
  (v0.32).
- **Detection** — same DetectionFiltersCard exposed as a dedicated
  subsection.
- **Monitor** — DaemonLogCard (v0.16), InterfacesCard (v0.16),
  RestartDaemonCard (v0.16), UpdateDaemonCard with progress bar
  (v0.22.1 / v0.32), Daemon config read-only viewer (v0.12).
- **Operations** — session reorder mode control (v0.31) + behaviour
  preferences shortcut.
- **About** — version, build SHA, license, package id,
  "Connected to" row (v0.11), NotificationsCard (v0.29),
  ApiLinksCard (v0.29), McpToolsCard rendering
  `/api/mcp/docs` (v0.32).

### Session reorder mode (v0.31.0)

The Sessions tab TopAppBar has a ⇅ icon that enters reorder mode.
Drag handles appear on each row; drag to set a Custom sort order,
tap ⇅ again to persist via `POST /api/sessions/reorder`.

## Home-screen quick commands (ADR-0015)

Top-level shortcut tiles on the Sessions home:

```
┌─────────┬─────────┬─────────┐
│   📋    │   💬    │   📊    │
│  List   │  Reply  │  Stats  │
└─────────┴─────────┴─────────┘
```

Each tile wraps an MCP tool invocation via SSE with inline result streaming. Long-press a
tile to edit parameters before invoking.

## Accessibility

- TalkBack labels on every interactive element (server switcher pill announces active server
  + unread count).
- Dynamic type up to 200% without layout breakage (tested in Compose previews).
- High-contrast theme option in Appearance (beyond dark/light/Material You).
- Voice access equivalent of every primary flow.

## Deep links

| Link | Result |
|---|---|
| `dwclient://server/<profile_id>` | Opens Sessions for that server |
| `dwclient://session/<profile_id>/<session_id>` | Session detail |
| `dwclient://voice/new` | Directly opens voice capture (used by quick-tile + ASSIST) |
| `dwclient://settings` | Root settings |

All deep links require signature permission for cross-app invocation; same-app invocation
is unrestricted.
