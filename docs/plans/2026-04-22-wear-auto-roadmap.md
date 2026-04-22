# Wear OS + Android Auto — full surface roadmap (2026-04-22)

Captured after landing B30 (multi-server picker), B32 (Monitor tab),
B33 (About) and the Monitor widget / Monitor tile in v0.34.0. This
doc enumerates every reasonable follow-on for both surfaces so the
backlog has a single place to triage from — ordered rough by
user-impact × implementation cost.

All items pre-1.0. Nothing here blocks 1.0; each is additive polish
or feature work.

## Design constraints (both surfaces)

- **Phone owns auth.** Watch and car never hold a bearer token. Data
  travels through the Wearable Data Layer (Wear) or the phone's
  SQLCipher DB + HTTP client (Auto, same-process as `:shared`).
- **ADR-0031 Play compliance** still applies to `publicMessaging`
  Auto flavour: static templates only, no free-form UI, TTS-only
  input. `devPassenger` is the escape hatch for internal builds.
- **Datawatch dark palette** (teal accent #00E5A0, amber warning
  #FFB020, surface #0F1419) on both surfaces — never stock
  Material. Tracked as a global feedback memory.

## Wear OS

### Now shipped (v0.34.0)

| Surface | Status | Notes |
|---------|--------|-------|
| Activity — Monitor page (default) | ✓ | CPU load, memory, disk, uptime from `/datawatch/stats` DataItem. |
| Activity — Sessions page | ✓ | running / waiting / total tiles. |
| Activity — Server picker | ✓ | Tap → MessageClient `/datawatch/setActive`; phone flips `ActiveServerStore`. |
| Activity — About page | ✓ | Shared `Version.VERSION`. |
| Tile — Sessions | ✓ | Reads `/datawatch/counts`. Tap → open activity. |
| Tile — Monitor | ✓ | Reads `/datawatch/stats`. Tap → open activity. |

### Next up (proposed)

| ID | Surface | Why | Cost |
|----|---------|-----|------|
| W-2 | **Complication — pending input count** | Shows on the watch face as a number; glanceable without opening a tile. Reuses `/datawatch/counts`. | S |
| W-3 | **Ongoing notification — pending input** | Wear extends phone notifications already; add per-session inline reply actions (Yes / No / Continue / Stop) using existing WS `send_input`. Depends on a MessageClient reply path phone-side. | M |
| W-4 | **Quick reply buttons on Sessions page** | Tap a waiting row → dedicated reply screen with Y/N/C/S buttons. Uses MessageClient to round-trip send_input via phone. | M |
| W-5 | **Last pane-capture image on Sessions tap** | Phone publishes a downsampled pane_capture snapshot per active session; watch renders. Image bytes over Data Layer are heavy — do lossy JPEG ≤ 32 KB. | L |
| W-6 | **Voice quick reply** | Wear's SpeechRecognizer → phone send_input. Distraction-free, optional. | M |
| W-7 | **Rotary input on sessions list** | Scroll sessions via bezel / crown. Low-hanging fruit once the list exists. | S |
| W-8 | **Tile — Waiting sessions list** | Third tile surface — up to 3 rows of waiting sessions with names + timestamps. Read `/datawatch/waiting` (new DataItem path). | M |
| W-9 | **Wear watch-face complication pack** | Sessions, Waiting, CPU%, Memory%. Four complications. | M |
| W-10 | **Server-switch complication** | Short-tap complication that cycles active server — mirrors widget cycle on phone. | S |

### Later / parked

- **Standalone Wear (no phone).** Currently phone owns auth and
  DataLayer; standalone would need Wear to run its own Ktor client
  + SQLCipher-lite. Big lift, small audience pre-1.0.
- **LTE build.** Depends on standalone.

## Android Auto

### Now shipped (v0.34.0)

| Surface | Status | Notes |
|---------|--------|-------|
| `AutoMonitorScreen` (default entry) | ✓ | CPU load, memory, disk, VRAM, sessions, uptime, active server marker. |
| `AutoSummaryScreen` (Sessions) | ✓ | Running / Waiting / Total rows. Reachable via ActionStrip. |
| `WaitingSessionsScreen` | ✓ | Per-row tap → `SessionReplyScreen`. |
| `SessionReplyScreen` | ✓ | Yes / No / Continue / Stop quick-reply. |
| `AutoServerPickerScreen` | ✓ | Lists enabled profiles; writes `ActiveServerStore`. |
| `AutoAboutScreen` | ✓ | Version + build + surface. |
| Datawatch palette (GREEN / YELLOW spans) | ✓ | Everywhere applicable. |

### Next up (proposed)

| ID | Surface | Why | Cost |
|----|---------|-----|------|
| A-2 | **Voice reply via TTS + SpeechRecognizer** | B31's voice half. ADR-0031 explicitly allows this on messaging template. | M |
| A-3 | **Incoming-prompt TTS** | When a session enters waiting_input, announce the prompt through Android Auto audio channel. Read `session.promptContext`. | M |
| A-4 | **Notifications → CarAppService navigation** | Tap an ntfy notification in Auto → deep-link into `SessionReplyScreen` for that session. | M |
| A-5 | **Multi-server monitor overview** | Single list with one row per enabled server, each showing CPU + memory + session counts. Driver picks which fleet to drill into. | M |
| A-6 | **Fleet health aggregate** | Across all enabled servers: total waiting count, "any server unreachable" flag. | S |
| A-7 | **Saved commands as ActionStrip** | Start a pre-defined session via one tap from `AutoMonitorScreen`. Ties into `SavedCommand` domain. | M |
| A-8 | **Scheduled events quick-view** | List the next 3 scheduled events from `/api/schedules` so drivers can see what's about to fire. | S |
| A-9 | **Map template with server locations** | If `ServerProfile` gains optional lat/lon, show markers with session counts. Likely over-scoped pre-1.0. | L |
| A-10 | **Dark/light palette switch** | Honour Auto's system day/night mode. | S |

### Later / parked

- **Full passenger template.** `devPassenger` already hosts a
  pre-MVP placeholder. Proper passenger surface (full-bleed UI,
  free input, terminal mirror) is ADR-0031 Sprint 4. Post-1.0.
- **AAOS-native build.** Different template set. Not on roadmap.

## Phone — widget / quick-tile extensions (related)

Widgets are separate from Wear/Auto but share the `ActiveServerStore`
and session-count plumbing, so folding them into this roadmap keeps
the multi-surface story coherent.

| ID | Surface | Why | Cost |
|----|---------|-----|------|
| P-1 | **Widget — aggregated "all servers" mode** | If `ActiveServerStore.SENTINEL_ALL_SERVERS` is set, the widget shows total running / waiting across every enabled server instead of one. | S |
| P-2 | **Widget — resizable multi-row layout** | When the user stretches the widget vertically, show one row per server. Needs RemoteViewsService + adapter. | M |
| P-3 | **Widget config activity** | Let the user pick which server to pin on a per-widget-instance basis (rather than tracking the app's active selection). | M |
| P-4 | **Quick-tile — start voice session** | Already prototyped as `VoiceQuickTileService`; wire it to `/api/voice/transcribe`. | S |
| P-5 | **Quick-tile — waiting count** | Quick-settings tile that badges the count of sessions waiting on input. | S |

## Cross-surface plumbing that would unblock the above

- **MessageClient reply path.** Watch / complications → phone →
  WS `send_input`. Partially done (setActive today). Generalise to
  `sendInput`, `ackPrompt`, `stopSession` paths.
- **Downsampled pane_capture DataItem.** For W-5 and any future
  terminal mirror on either surface, a capped-size JPEG stream over
  Data Layer is cheaper than raw text.
- **Stats DataItem expansion.** Adding GPU, per-envelope CPU, and
  backend-reachability to `/datawatch/stats` unlocks richer tiles
  + complications without each surface polling REST.

## Release discipline

Any item that lands should:

1. Bump `shared/src/commonMain/.../Version.kt` and `gradle.properties`
   (CI `check-version` enforces parity; user feedback memory too).
2. Move the entry from "Open" to "Closed" in
   `docs/plans/README.md` with the version it landed in.
3. Append a changelog entry in [Keep a Changelog] format.
4. Rebuild `:composeApp:assembleDevDebug` + `:wear:assembleDebug`
   + `:auto:assemblePublicMessagingDebug` before commit.
5. Install on phone + watch from the built APKs when device
   testing is feasible.

[Keep a Changelog]: https://keepachangelog.com/en/1.1.0/
