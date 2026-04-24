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
| **P3** — polish / visual fidelity | 8 | 3 ✅ | 5 (opportunistic) |
| WONTFIX / non-gap / upstream-tracked | 12 | — | — |

**Upstream issues filed against `dmz006/datawatch`** (per the bidirectional parity rule):
- [#21](https://github.com/dmz006/datawatch/issues/21) — PWA missing voice-input UI for `/api/voice/transcribe` (Android has it end-to-end).
- [#22](https://github.com/dmz006/datawatch/issues/22) — PWA migrate New bottom-nav tab → FAB + full-screen create, to match Android.

---

## 2. Remaining (P3, opportunistic)

These are not blocking parity and ship when convenient. None are user-reported defects; all were sourced from the original audit walk and tagged P3.

| ID | Title | Owner file | Notes |
|---|---|---|---|
| G14 | Schedule-input popup field alignment vs PWA | `SessionDetailScreen.kt` `ScheduleDialog` | Verify task seed / cron preset / enabled toggle ordering matches PWA. Low-impact. |
| G15 | Terminal toolbar font-size buttons (A+/A−) | `TerminalToolbar.kt` | Font-size works internally; exposing the buttons is polish. |
| G16 | Terminal toolbar "Fit to width" button | `TerminalToolbar.kt` | Fit-to-width already happens automatically on the WebView; button is UX surface. |
| G17 | Terminal toolbar "Scroll mode" button (Ctrl-b `[`) | `TerminalToolbar.kt` + QuickCommandsSheet | Key combo available via the Commands sheet. |
| G27 | Plugins card on Monitor tab | new `PluginsCard.kt` + `/api/plugins` transport | Operator-level feature; defer until a user asks for it. |

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

### v0.35.2 — 2026-04-24 · Inline header rename (current)

| ID | What | Mechanism |
|---|---|---|
| ✅ G11 | Session-detail rename via modal dialog | Tap title → `BasicTextField` swap in-place. Enter or blur commits via `vm.rename`. Mirrors PWA `startHeaderRename` contentEditable. Prior `RenameDialog` still reachable via overflow menu for users who prefer modal. |

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
