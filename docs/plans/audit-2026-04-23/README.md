# 2026-04-23 PWA parity master inventory

> **🎉 Parity milestone reached 2026-04-24 at v0.35.2.** All P0, P1, and
> P2 rows in the original gap matrix are closed. Remaining rows are
> P3 polish items that ship opportunistically. See §3 "Remaining (P3)"
> for the shortlist and §5 "Closed — by release" for what shipped where.

**Original goal.** Close every observable parity gap between the
datawatch PWA (live at `https://localhost:8443`, source at
`/home/dmz/workspace/datawatch`) and the Android phone client. Wear,
Auto, and iOS are out of scope.

---

## 1. Summary

| Severity | Originally identified | Closed in 0.34.6 → 0.35.2 | Still open |
|---|---|---|---|
| **P0** — user-flagged, app broken | 3 | 3 ✅ | 0 |
| **P1** — user-flagged, still broken on 0.34.6 | 5 | 5 ✅ | 0 |
| **P2** — structural parity | 7 | 5 ✅ + 2 wontfix | 0 |
| **P3** — polish / visual fidelity | 8 | 6 ✅ | 2 (opportunistic) |
| WONTFIX / non-gap / upstream-tracked | 12 | — | — |

Post-milestone arc (v0.35.3–v0.35.5, all 2026-04-24) closed the Wear
feature backlog: round-bezel cards, colour-gauge Monitor with GPU
stats, and tap-to-reply voice flow on Sessions. Parity itself stays
closed — those rows aren't in the PWA matrix, they're Wear-only work
tracked outside this audit. See §5 "Closed — by release" for the
per-version change log.

**Upstream issues filed against `dmz006/datawatch`** (per the bidirectional parity rule):
- [#21](https://github.com/dmz006/datawatch/issues/21) — PWA missing voice-input UI for `/api/voice/transcribe` (Android has it end-to-end).
- [#22](https://github.com/dmz006/datawatch/issues/22) — PWA migrate New bottom-nav tab → FAB + full-screen create, to match Android.
- [#23](https://github.com/dmz006/datawatch/issues/23) — PWA Sessions list — mirror Android v0.35.3 layout (inline filter toggle, reachability dot right-side, FAB lower).
- [#24](https://github.com/dmz006/datawatch/issues/24) — PWA terminal toolbar collapse toggle on session-info-bar (match Android v0.35.6).

---

## 2. Remaining (P3, opportunistic)

These are not blocking parity and ship when convenient. None are user-reported defects.

| ID | Title | Owner file | Notes |
|---|---|---|---|
| G14 | Schedule-input popup field alignment vs PWA | `SessionDetailScreen.kt` `ScheduleDialog` | Verify task seed / cron preset / enabled toggle ordering matches PWA. Low-impact. |
| G27 | Plugins card on Monitor tab | new `PluginsCard.kt` + `/api/plugins` transport | Operator-level feature; defer until a user asks for it. |

**Closed P3 rows** (verified 2026-04-24 during v0.35.5 backlog sweep —
were already implemented in `TerminalToolbar.kt:72-110` when the
original audit was written but the audit inherited a stale row):

- ✅ G15 — Terminal toolbar A+ / A− font-size buttons (`TerminalToolbar.kt:72-91`).
- ✅ G16 — Terminal toolbar "Fit to width" button (`TerminalToolbar.kt:96`).
- ✅ G17 — Terminal toolbar "Scroll mode" toggle + navigation strip
  (`TerminalToolbar.kt:99-163`, PgUp / PgDn / ↑ / ↓ / ESC).

Any new gap surfaced during implementation or review gets added here with the same schema.

---

## 3. Sibling inventory docs

Source-of-truth deep dives — do not duplicate their content here.

| File | What it covers |
|---|---|
| [`pwa-inventory.md`](pwa-inventory.md) | Every PWA screen + dialog, every field with config key + endpoint + app.js line, every button + onclick, every data source. 1629 lines, organised per-screen. Generated 2026-04-23 from `/home/dmz/workspace/datawatch/internal/server/web/app.js` and live `/api/*` responses. |
| [`android-inventory.md`](android-inventory.md) | Every Android screen, field/binding table, VM function → endpoint table, dialogs, data sources. 673 lines. Generated 2026-04-23 from `composeApp/src/androidMain/**`. **Caveat:** a few endpoint rows describe REST-style paths (`DELETE /api/sessions/{id}`) — the *actual* transport is `POST /api/sessions/kill` with `{"id": fullId}`. Use `server-contract.md` as the authoritative endpoint source. |
| [`server-contract.md`](server-contract.md) | Authoritative table of every PWA-used endpoint with method, request body key names, response shape, handler location. |
| [`audit-validation.md`](audit-validation.md) | Each claim in the prior 2026-04-22 audit — CONFIRMED / CLOSED / REDEFINED / WONTFIX with source ref. |
| `/home/dmz/workspace/pwa-audit/out/*.png` | 40+ live PWA screenshots captured via Playwright. Not committed — live reference only. |

---

## 4. PWA ↔ Android structural map (final state)

### 4.1 Navigation

| Surface | PWA | Android (v0.35.2) | Disposition |
|---|---|---|---|
| Top-level views | 5: `sessions`, `new`, `alerts`, `settings`, `session-detail` (app.js:969) | 3 home tabs + 2 full routes: `home/sessions`, `home/alerts`, `home/settings`, `sessions/new`, `sessions/{id}` | Android's FAB + full-screen create is the target; PWA adopting via dmz006/datawatch#22. |
| Bottom nav items | 4 (Sessions / New / Alerts / Settings) | 3 (Sessions / Alerts / Settings) + FAB for New | Android pattern wins. |
| Settings sub-tabs | 5: Monitor / General / Comms / LLM / About | 5: Monitor / General / Comms / LLM / About | ✅ Parity. |
| Back button behavior | History API push; popstate returns | Nav controller pop; `BackHandler` in session detail | ✅ Parity. |

### 4.2 Endpoint contract

See [`server-contract.md`](server-contract.md). Key points post-v0.35.0:
- Every session-mutation endpoint (`kill` / `state` / `rename` / `restart` / `delete`) uses `POST … {"id": fullId}`.
- Every LLM backend's config lives under its own top-level section (`ollama.*`, `openwebui.*`, `session.*` for claude-code, `shell_backend.*`, `opencode_acp.*`, …). No `backends.<name>.*` prefix.
- `/api/alerts` returns `{alerts: [{id, level, title, body, session_id, read, created_at}], unread_count}`. Android parses all fields (v0.34.8).
- WS `chat_message` frames (`{session_id, role, content, streaming}`) are parsed into `SessionEvent.ChatMessage` (v0.34.6).

---

## 5. Closed — by release

Chronologically, what shipped in each release.

### v0.34.6 — 2026-04-23 · P0 (`e53176a`)
Seven files, ~700 LOC. Commit: `release: v0.34.6 — P0: session-id contract, delete UI, chat mode`.

| ID | What | Mechanism |
|---|---|---|
| ✅ G1 | `/api/sessions/kill` + state/rename/restart/delete all 404'd | Fixed body key `session_id` → `id`, plumb `Session.fullId` through every mutation VM call site. |
| ✅ G2 | Stop badge on the Sessions list — same 404 root cause | `fullIdFor(sessionId)` helper applied to all five mutation call sites in `SessionsViewModel`. |
| ✅ G3 | No delete button after kill | Inline Delete OutlinedButton on terminal-state rows + SessionInfoBar Delete + confirm dialog. |
| ✅ G4 | Chat-mode sessions blank | `SessionEvent.ChatMessage`, `EventMapper.buildChatMessage`, new `ChatTranscriptPanel` composable, 4.sqm migration for `output_mode`/`input_mode`. |

### v0.34.7 — 2026-04-24 · P1 fix pass (`d9cc9ad`)

| ID | What | Mechanism |
|---|---|---|
| ✅ G5 | New-Session LLM picker stuck on old server's backends | `(profiles, activeId)` LaunchedEffect now re-syncs `selectedProfileId` until user makes a manual pick (`userPickedServer` flag). |
| ✅ G8 | Session-detail composer hidden behind soft keyboard | Moved `imePadding()` from outer Column to the composer Row directly so only the input bar lifts. |

### v0.34.8 — 2026-04-24 · Alerts rebuild + Settings restart (`be81c53`)

| ID | What | Mechanism |
|---|---|---|
| ✅ G7 | Alerts screen rebuilt to PWA structure | New AlertsViewModel groups `/api/alerts` by `session_id` (synthetic `__system__` bucket), classifies Active/Inactive by live session state. New AlertsScreen: Active/Inactive TabRow, per-session collapsible cards (active default-expand / inactive default-collapse), per-alert cards with level-colored left border, quick-reply button on first alert of waiting-input sessions. Swipe-left-to-mute retained. |
| ✅ — | AlertDto used wrong field names | Server emits `level/title/body`; DTO was expecting `type/severity/message`. Every live row was silently failing decode. DTO now matches live shape with back-compat. Domain `Alert` gains `title` field. |
| ✅ — | Settings save + restart UX | `RestartNeededBanner` at top of every Settings tab. Green note when `server.auto_restart_on_config=true`; amber banner + "Restart now" button calling `POST /api/restart` when off. |

### v0.34.9 — 2026-04-24 · Drag-drop reorder (`ddee3db`)

| ID | What | Mechanism |
|---|---|---|
| ✅ G6 | Sessions list hamburger-menu reorder | Long-press drag on row via `detectDragGesturesAfterLongPress`. `dragOffsetY / 72 dp.toInt()` rows applied via `vm.moveSessionByOffset`. Auto-seeds Custom sort on first drag. Reorder icon removed from top bar. |

### v0.35.0 — 2026-04-24 · LLM config paths + compact inputs + eBPF (`9f464bf`)

| ID | What | Mechanism |
|---|---|---|
| ✅ G45 | LLM backend config writing to nonexistent paths | Rewrote `LlmBackendSchemas` with a `section(name)` + `enabledKey(name)` helper resolving canonical server paths (`ollama.*`, `session.claude_enabled`, `shell_backend.*`, `opencode_acp.*`). Updated `LlmConfigCard` toggles + `isBackendConfigured` + `readBackendEnabled`. Updated `NewSessionScreen.backendEnabled` filter. Dropped dead schemas (`anthropic`, `openai`, `groq`, `openrouter`, `xai`); added missing ones (`aider`, `goose`, `gemini`, `opencode-acp`, `opencode-prompt`, `shell`). |
| ✅ G9 / G57 | Settings inputs dwarfed their own text | Swapped M3 `OutlinedTextField` for a purpose-built `CompactInput` (36 dp tall, 13 sp text, 1 dp border, 6 dp vertical content padding). Preserves password masking, number keyboards, placeholder. |
| ✅ G26 | Monitor tab eBPF Degraded banner | Renders when `ebpf_enabled=true && ebpf_active=false`, shows server's `ebpf_message` with amber error-container background. Added `ebpfEnabled` + `ebpfMessage` to `StatsDto`. |
| ✅ G19-G25, G28 | Monitor cards coverage | Verified Network / Daemon / Infrastructure / RTK / Memory / Ollama / Session Statistics were already present from prior releases. No code change — parity confirmation only. |

### v0.35.1 — 2026-04-24 · Session-detail dropdown + Response (`c8dddf4`)

| ID | What | Mechanism |
|---|---|---|
| ✅ G12 | State override opens as full-screen AlertDialog | Replaced with DropdownMenu anchored to the state pill inside `SessionInfoBar`. Matches PWA `showStateOverride(sessionId, element)`. |
| ✅ G13 | Response button not on session detail | Added to `SessionInfoBar` when `session.lastResponse` is non-blank. Opens existing `LastResponseSheet` (now `internal` so reachable from detail). |

### v0.35.2 — 2026-04-24 · Inline header rename

| ID | What | Mechanism |
|---|---|---|
| ✅ G11 | Session-detail rename via modal dialog | Tap title → `BasicTextField` swap in-place. Enter or blur commits via `vm.rename`. Mirrors PWA `startHeaderRename` contentEditable. Prior `RenameDialog` still reachable via overflow menu for users who prefer modal. |

### v0.35.3 — 2026-04-24 · Sessions UX polish + Wear round cards + release-workflow fix

Not a parity row close — this ships the UX polish the user asked for
post-milestone plus an upstream design-sync issue for the PWA.

| What | Mechanism |
|---|---|
| Sessions list — filter/sort/history collapse behind a single icon | `SessionsScreen.kt`: `toolbarExpanded` state hoisted; `actions` slot holds search icon + reachability dot. Matches PWA header direction-wise; upstream issue [#23](https://github.com/dmz006/datawatch/issues/23) tracks the mirror change on PWA. |
| Session detail — drop redundant `tmux` mode badge | `SessionInfoBar` filters out `tmux`, `""`, `none` from the mode chip since the tmux/channel TabRow renders it a row above. |
| Response button relocates to composer row under mic | `ReplyComposer` `hasResponse` + `onResponse` params; `SessionInfoBar` drops its copy. Mic 40 dp / Response 36 dp stacked column. |
| Terminal toolbar hugs badges | `TerminalToolbar` vertical padding dropped from 2 dp → 0 dp. Closes the "empty line" gap to `SessionInfoBar`. |
| Wear — circular bordered card per page | `WearMainActivity`: `CircleShape` background + 1.5 dp primary-tinted border. Matches Samsung Galaxy Watch bezel. |
| Auto — MagicNumber detekt cleanups | `AutoMonitorScreen.kt` byte/time constants named. |
| Release workflow — fix stale `bundlePublicRelease` → `bundlePublicTrackRelease` | `.github/workflows/release.yml`. v0.35.2 release never published binaries because of this. Also adds R8 `-dontwarn` rules for Ktor's `IntellijIdeaDebugDetector` + slf4j so the shrink step stops failing. |
| Security — phone slideshow GIF purged from git history | `git-filter-repo --path docs/media/phone-slideshow.gif --invert-paths` + force-push main + `v0.35.3` tag. Previous GIF captured the user's live home screen; `capture-phone.sh` now bans the home-screen step. README drops to 2-column (Watch + PWA) slideshow table until a clean re-capture runs. |

### v0.35.4 — 2026-04-24 · Wear Monitor redesign

| What | Mechanism |
|---|---|
| CPU / Memory / Disk / GPU gauge rings on Wear Monitor | `WearMainActivity.MonitorPage`: 2-up grid of `CircularProgressIndicator` with threshold colours (green < 60 % → amber 60–80 % → red ≥ 80 %). Value printed inside the ring; label below. GPU gauge hides gracefully when phone doesn't publish GPU stats. |
| GPU stats publish path | `WearSyncService.publishStats` adds `gpuUtilPct`, `gpuTempC`, `gpuMemUsedMb`, `gpuMemTotalMb`, `gpuName` to the `/datawatch/stats` DataItem. |
| Uptime + VRAM summary | Moved below the gauge grid as captions instead of list rows. |

### v0.35.5 — 2026-04-24 · Wear Sessions tap-popup + voice reply

| What | Mechanism |
|---|---|
| Per-session list on Wear Sessions page | New `/datawatch/sessions` DataItem from phone publishes top-12 by `lastActivityAt` (id, title, backend, state, last-line). Watch renders coloured state-badge rows — running/waiting/rate-limited/other. |
| Tap → `SessionDetailPopup` | Circular card with title + state + last-line preview + 🎤 + Send + ✕. Scaffold wraps `verticalScroll` so overflow is bezel-scrollable. |
| Voice transcription | Mic launches system `RecognizerIntent.ACTION_RECOGNIZE_SPEECH`. Transcript appears inside the popup for confirmation before send. |
| Reply round-trip | Watch `MessageClient.sendMessage("/datawatch/reply", "sessionId\ntext")` → phone `WearSyncService` opens a transient WS subscription to that session, emits `send_input` via `WsOutbound`, cancels after drain. The only path — server rejects REST `/api/sessions/reply` (404, pre-v0.34.6 regression context). |

### v0.35.6 — 2026-04-24 · Composer reshuffle + voice-reply fix + terminal toolbar toggle

| What | Mechanism |
|---|---|
| Sessions FAB lowered | `SessionsScreen.kt`: `Modifier.offset(y = 36.dp)` on FAB pushes past Scaffold inset reserve — thumb reach on 6.8" screen. |
| Sessions header dead strip removed | `ServerPickerTitle` drops its `vertical = 4.dp` padding; TopAppBar already vertically centres so the extra padding made the title look hollow. |
| Composer under-mic → Saved Commands | `ReplyComposer` swaps `hasResponse` / `onResponse` for `onSavedCommands`; Response badge returns to `SessionInfoBar`. New `QuickCommandsSheet` state hoisted in detail screen; `SessionDetailViewModel.fetchSavedCommands()` uses `profileCache`. |
| Quick-commands custom input gets a mic | `QuickCommandsSheet` (now `internal`) — mic next to Send runs `VoiceRecorder` + `transcribeAudio`, appends into the custom text field for review before send. |
| Terminal toolbar hides behind badge-row toggle | `SessionInfoBar` new params `terminalToolbarVisible` + `onToggleTerminalToolbar`; `Aa ▾ / Aa ▴` TextButton in badge row. Detail screen `remember(sessionId) { mutableStateOf(false) }` — per-session, hidden by default. Upstream issue [dmz006/datawatch#24](https://github.com/dmz006/datawatch/issues/24) for PWA parity. |
| Voice reply routed to session's own profile | `ReplyComposer`: resolve profile via `SessionRepository.observeForProfileAny(sessionId)` first, fall back to ActiveServerStore. Fixes "voice use to work but isn't anymore" — cross-server / all-servers mode was routing transcribe to the wrong Whisper instance. |
| Voice-failure toast surfaces root cause | Walks the cause chain (3 deep) so Ktor-wrapped `CancellationException` no longer masks 404 / TLS / bearer-missing messages. Also adds a "No enabled server profile" toast when resolution fails entirely. |
| Kill Orphans moves to About | `SettingsScreen`: `KillOrphansCard` relocates from Monitor → About beside `UpdateDaemonCard` + `RestartDaemonCard` (daemon-admin cluster). Monitor keeps read-oriented cards only. |

### v0.35.7 — 2026-04-28 · PWA v5.1.0–v5.2.0 alignment + data freshness

| What | Mechanism | Closes |
|---|---|---|
| Terminal toolbar always renders | Reverts v0.35.6's `Aa ▾ / Aa ▴` toggle. `SessionInfoBar` + detail screen drop the `terminalToolbarVisible` state plumbing. | [#8](https://github.com/dmz006/datawatch-app/issues/8) — also obsoletes upstream `dmz006/datawatch#24` |
| History label rename | `SessionsToolbar`: "Show / Hide history (N)" → "History (N)" | [#9](https://github.com/dmz006/datawatch-app/issues/9) v5.1.0 |
| Tmux arrow-key row | `ReplyComposer`: 4-AssistChip `LazyRow` above the text field, ANSI escape sequences via `WsOutbound.sendInput` | [#9](https://github.com/dmz006/datawatch-app/issues/9) v5.2.0 |
| About — Play Store row | `AboutCard`: new row "Play Store / (pending submission)" | [#9](https://github.com/dmz006/datawatch-app/issues/9) v5.2.0 |
| About — `ConfigViewerCard` removed | `SettingsScreen` About branch: drops the raw-YAML viewer to align with PWA About surface. | (alignment) |
| Live `last_response` refetch | `SessionDetailViewModel.refreshFromServer()` — triggers on Response button tap; daemon's `Manager.GetLastResponse` re-captures from live tmux for `running` / `waiting_input`. | [#9](https://github.com/dmz006/datawatch-app/issues/9) BL178 |
| Input-Required banner refresh on bulk WS | `startStream` now triggers `refreshFromServer()` on every `SessionEvent.StateChange`. | (PWA v5.26.49 mirror — closes the "yellow box doesn't show up after re-enter" complaint) |

### v0.35.8 — 2026-04-28 · Wear voice via phone-relayed Whisper + popup polish (current)

| What | Mechanism |
|---|---|
| Wear sessions row prefers `lastResponse` | `WearSyncService.publishSessions`: lastLine = lastResponse → lastPrompt → taskSummary → "" |
| Wear popup mic to right edge, Send to left | `SessionDetailPopup` rebuilt: centre column holds title/state/last-line/transcript; mic anchors `Alignment.CenterEnd`; Send chip `Alignment.CenterStart`. Stop icon `■` replaces 🎤 while recording. |
| State-aware popup labels | "Listening…" (red) while recording, "…transcribing" while waiting on phone, transcript text once Whisper replies. |
| `RecognizerIntent` replaced with phone-relayed Whisper | New `WearVoiceRecorder` (mirror of phone's `VoiceRecorder`) records m4a/AAC. Watch ships bytes via MessageClient `/datawatch/audio` (`sessionId\n<bytes>`). Phone's `WearSyncService.forwardWatchAudio` resolves session profile + posts to `/api/voice/transcribe`, replies on `/datawatch/transcript`. Watch listener re-uses existing `/datawatch/reply` for the send_input forward once user confirms. |

---

## 6. Prior-audit disposition (2026-04-22)

See [`audit-validation.md`](audit-validation.md). Summary:
- B34 (state-pill menu), B35 (quick-command palette), B40 (prompt templates — not a thing), B42 (in-app alerts panel) → **already Android parity; closed.**
- B36 (quick-reply buttons) → **merged into G7 (Alerts rebuild), shipped 0.34.8.**
- B37 (TLS cert selector), B41 (language selector) → **WONTFIX: feature doesn't exist in PWA.**
- B38 (raw YAML editor) → unchanged backlog (BL14); neither client has it.
- B39 (LLM sliders) → **redefined as G45 schema cleanup, shipped 0.35.0.**

---

## 7. Non-gaps / WONTFIX / upstream-tracked

| Claim | Reason | Upstream ref |
|---|---|---|
| Language selector | Not in PWA. Parent is English only. | — |
| TLS cert selector | Misread of B37. PWA has TLS toggle/port/auto-generate fields (Android has all of them) + `/api/cert` download (Android's CertInstallCard). No selector exists. | — |
| REST-style `DELETE /api/sessions/{id}` | Android inventory agent extrapolated; server is `POST` with body key `id`. v0.34.6 is correct; don't regress. | — |
| "Prompt templates UI" | Not a PWA thing; Saved Commands fills the role (parity). | — |
| In-app alerts panel as a new feature | Android already has the Alerts tab. Real gap was G7 (structure), shipped. | — |
| Voice transcribe | PWA lacks it. Contract gap filed upstream. | **[dmz006/datawatch#21](https://github.com/dmz006/datawatch/issues/21)** |
| New-session tab vs FAB | Android FAB pattern is the target; PWA migrating. | **[dmz006/datawatch#22](https://github.com/dmz006/datawatch/issues/22)** |
| Home-screen widgets | Platform-specific; PWA can't have them. Non-gap. | — |
| Biometric unlock | Android-only requirement. Non-gap. | — |
| Wear / Auto surfaces | Out of scope per user directive. | — |
| `/api/sessions/reply` in Android | Server doesn't expose this; composer uses WS `send_input`. Android-side cleanup, not a parity gap. | — |
| G41 Signal QR device linking | User 2026-04-24: "already on phone, do not need signal setup. that can be a server only function." | — |

---

## 8. Next

With parity closed, subsequent work is driven by user requests or new
server features rather than a backlog walk. The P3 shortlist in §2 is
the current holdover list — items get picked up opportunistically or
dropped if they age out.

New parity gaps discovered later: add a Gnn row to §2, file an upstream
issue if the gap is in the "Android-has, PWA-lacks" direction
(`feedback_file_upstream_issues`), and ship it.
