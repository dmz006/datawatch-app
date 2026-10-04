# 02 — Sessions list

PWA spec §3 · live `app.js` `renderSessionsView` (1967), `sessionCard` (2386), select/drag (2268–2384), `fetchCurrentStatus` (2524), `showCardCmds` (2612), `showResponseViewer` (14890), `timeAgo` (15454); `style.css` `.session-card*` (369, 2247–2360), `.sessions-watermark` (344), `.select-bar-*` (2110–2165).
Android: `ui/sessions/SessionsScreen.kt` (`SessionsScreen` 134, `SessionsToolbar` 535, `SessionSkeletonList` 825, `EmptyState` 862, `SessionRow` 882, sheets 1732–2190), `SessionsViewModel.kt`, `ui/theme/PwaComponents.kt`, `ui/filters`, `ui/commands`, `ui/gesture`, `ui/profiles`.
iOS: `screens/sessions/SessionsView.swift`, `SessionsViewModel.swift`.

Refs are `file:line`. `A:` = SessionsScreen.kt, `AV:` = SessionsViewModel.kt, `I:` = SessionsView.swift, `IV:` = SessionsViewModel.swift, `P:` = app.js, `C:` = style.css.

| Cat | Feature | PWA | Android | iOS | Status | Decision | Notes |
|---|---|---|---|---|---|---|---|
| nav | Server / profile switcher on list | ✓ P:2095 server-indicator chip (non-local, click→local) + `_injectServerPickerBar` (P:2158) | ~ A:1514 `ServerPickerTitle` dropdown in app bar: profiles · Edit · All servers · Add | ✗ IV:33 uses `profiles.first` only | misaligned | needs-decision | D1 |
| interaction | Toolbar hidden by default; header search icon toggles; state persisted | ✓ P:2035 `cs_filters_collapsed`, P:15351 | ~ A:232 toggle; not persisted; forced open while filter/history active (A:570) | ~ I:68 toggle; not persisted | misaligned | | persistence + "stay open when active" rule |
| element | Filter text input + clear × | ✓ P:2077 | ✓ A:583 | ✓ I:317 | aligned | | |
| data | Filter text match fields | ✓ P:1999 name·task·id·backend_family·llm_ref·compute_node_ref | ~ AV:167 (name/task/id/backend per toolbar doc A:521) | ✓ I:373 + hostnamePrefix | misaligned | | align Android field set; iOS adds hostname |
| element | LLM/backend filter: `LLM (N)` collapsible → short-label badges w/ counts, only when >1 backend | ✓ P:2050–2088 short map claude/oc/acp/oc-p/owui/olla/… | ~ A:611 `LLM (N) ▸` button → chips "backend · n" (full names) + Council chip; shown regardless of count | ✗ | misaligned | | short-label map is PWA design; Council chip → D12 |
| element | State filter: `State (N)` collapsible → 7 real-state chips w/ colour dot, count>0 only, persisted | ✓ P:2056–2075 `cs_session_state_chip` | ✓ `State (N) ▸` → 7 real-state chips w/ colour dot, count>0 + All + selected, persisted `cs_session_state_chip` | ~ I:276 same 4 buckets | misaligned | decided D12a | D2 · Android done 2026-10-04 |
| interaction | Picking a historical state chip auto-enables History | ✓ P:2217 | ✓ `setStateChip` (complete/failed/killed) | ✓ I:391 Done chip sets `showHistory` | aligned | | Android done 2026-10-04 (via D12a) |
| element | `History (N)` toggle: default pool = active + recent (5 min); History = all | ✓ P:2102, P:1972 | ✓ A:690, AV:157 `RECENT_WINDOW_MINUTES=5` | ✓ I:419 `historyChip` + `visiblePool` 5-min window | aligned | | |
| element | `☑` Select button (only when History on and history>0) | ✓ P:2105 | ✓ ☑ toggles select mode, shown only with History on | ✗ | misaligned | decided D15a | D6 · Android done 2026-10-04 |
| element | Tree view toggle (BL348): parent/child grouping, 18px indent, `⚠ orphaned` badge | ✓ P:2098, P:2242 | ✓ Tree toggle `cs_session_tree_view`, `flattenTree` 18 dp/level + orphaned note | ✗ | ios-missing | | iOS ✗ too · Android done 2026-10-04 (android-missing sweep) |
| element | Pending-schedules badge 🕒 N + dropdown with per-item cancel | ✓ P:2094, P:2160 | ✓ toolbar 🕒 N + dropdown with ✕ cancel (`/api/schedules?state=pending`) | ✗ | ios-missing | | iOS ✗ too · Android done 2026-10-04 (android-missing sweep) |
| element | Sort control (Recent / Started / Name / Custom) | ✗ (manual order only) | ✗ removed | ✓ I:283 (no Custom) | pwa-missing | decided D42a | D3 · Android done 2026-10-04 |
| data | List ordering rule | ✓ P:2323 manual `cs_session_order` first, then `updated_at` desc; no state buckets | ✓ manual drag order (persisted `cs_session_order`) then last activity desc; no buckets | ~ I:386 same buckets | misaligned | decided D42a | D3 · Android done 2026-10-04 |
| motion | Toolbar / chip-row expand animation | ✗ (re-render) | ~ A:660 `AnimatedVisibility` (LLM chips only) | ~ I:68 `withAnimation` default | misaligned | | minor |
| element | Empty state copy + icon | ✓ P:2114 💬 "No active sessions" / "Tap the + button to start a session, or send commands via Signal." | ✓ 💬 "No active sessions" + "Tap the + button…" hint | ~ I:433 terminal icon "No sessions — start one in the web UI" | misaligned | decided D35a | D7 · Android done 2026-10-04 |
| element | Toolbar still shown in empty state when history>0 | ✓ P:2113 | ✓ (toolbar independent of list) | ✓ I:276 filter bar independent of list; empty pool shows "show N finished" | aligned | | |
| motion | Skeleton list on first load: 5 shimmer rows, alpha .3↔.7, 900 ms reverse | ✗ | ✓ A:825 | ✓ `SkeletonListView` (5 rows, .3↔.7, 900 ms) | pwa-missing | decided D60a | D8 · iOS done 2026-10-04 |
| element | No-server / no-profile state | n/a | ✓ A:1514 "No server", onboarding | ✓ I:445 `emptyNoProfile` | n/a | | PWA is served by the server |
| element | Transport error surface on list | ~ header daemon light (§01) | ✓ A:286 errorContainer banner | ✓ I:225 `ConnectionStatusBanner` + I:230 `ErrorCard` | misaligned | | minor; copy differs |
| token | Watermark | ✓ C:344 `/favicon.svg` fixed centre, min(85vw,400px), opacity .045 | ~ A:351 launcher foreground, 85% width, alpha .10 | ✗ | misaligned | | align alpha to .045; iOS add |
| token | Card surface | ✓ C:369 bg2, radius `--radius`, 4px left state border, pad 12/14, list gap 8px | ~ `pwaCard` 12dp radius + 1dp border + 4dp `pwaStateEdge` (PwaComponents.kt:125,152) | ~ I:606 plain List row, no state edge | misaligned | | iOS lacks left state edge |
| token | Done-card dimming | ✓ C:2265 complete .7, killed .5; actions/handle stay 1.0 (C:2284) | ~ A:940 .6 for all done, whole row | ~ I:604 .6 whole row | misaligned | | PWA keeps actionable zones full-opacity |
| motion | Left-border pulse: waiting_input `pulse-border` 2s ease-in-out (waiting↔#93c5fd); rate_limited 3s | ✓ C:2255–2262 | ✓ `pwaStateEdge` pulse (waiting 2 s ↔ #93c5fd; rate_limited 3 s ↔ amber-300), static under reduced motion | ✗ | ios-missing | | iOS ✗ too · Android done 2026-10-04 (android-missing sweep); rate_limited peaks to amber (PWA keyframe reuses --waiting) |
| motion | Pressed/hover feedback (`:active` bg3, .2s transitions) | ✓ C:2247, C:375 | ~ Material ripple | ~ system highlight | n/a | | platform-native |
| interaction | Tap card → session detail | ✓ P:2490 | ✓ A:423 | ✓ I:467 | aligned | | |
| interaction | Drag-to-reorder; order persisted | ✓ P:2350 HTML5 DnD, `cs_session_order`; dragging .4, drag-over accent top border (C:2167) | ~ A:370 long-press drag (translationY, shadow 12) + reorderMode ↑↓ arrows; customOrder persisted | ✗ | ios-missing | | Android gesture differs (long-press) |
| element | Drag handle ⋮⋮ (opacity .4) | ✓ P:2503, C:2176 | ~ A:1004 icon only in reorderMode | ✗ | ios-missing | | |
| interaction | Select mode: checkbox on inactive cards; tap toggles | ✓ P:2486 | ✓ A:997 | ✗ | ios-missing | | |
| element | Select bar | ✓ P:2141 fixed bottom bar above nav: `☑ All/None (N)` · `🗑 Delete (N)` (red when enabled) · Cancel; FAB hidden (C:2149) | ✓ `SessionsSelectBar` bottom bar ☑ All/None (N) · 🗑 Delete (N) · Cancel; FAB hidden | ✗ | misaligned | decided D15a | D6 · Android done 2026-10-04 |
| interaction | Bulk delete: confirm → `POST /api/sessions/delete {id, delete_data:true}` per id → toast | ✓ P:2298 | ✓ A:454 dialog, `deleteMany` | ✗ | ios-missing | | |
| interaction | FAB hidden while select bar shown | ✓ C:2149 | ✓ A:264 | n/a (FAB exists; no select mode yet) | aligned | | |
| element | Line 1 text: `name`, else `task` (80 chars, "(no task)") | ✓ P:2391 | ✓ name, else task (80), else "(no task)"; no task line 2 | ~ I:573 name, else task, else id; task line 2 (2 lines) if ≠ name | misaligned | decided D16a | D13 · Android done 2026-10-04 |
| element | Short-id pill (mono, bg3, border, accent2) | ✓ P:2507, C:2316 | ✓ `SessionIdPill` in meta row | ✗ | misaligned | decided D16a | D13 · Android done 2026-10-04 |
| token | State badge style: uppercase text, 1px currentColor border, radius 10, 11px/600 | ✓ P:2502, C:2327 | ~ `PwaStatePill` filled .15 bg, no border (PwaComponents.kt:58) | ~ I:638 filled .15, labels RUNNING/WAITING INPUT/… | misaligned | | align to bordered PWA pill |
| motion | Running badge pulse | ✓ C:2343 700 ms .55↔1.0 ease-in-out alternate; honours reduced-motion | ✓ `rememberRunningPulseAlpha` 700 ms .55↔1.0 ease-in-out; static under reduced motion | ✗ | ios-missing | | align timing to PWA; iOS add · Android done 2026-10-04 (D18 kept) |
| element | `stale-dot` inside badge (no channel activity >2 s, `data-channel-evt`) | ✓ P:2502, P:1612 | — not rendered | ✗ | n/a | | iOS ✗ too · PWA hides `.stale-dot` (`display:none`, GATE 2026-05-10) — nothing visible to mirror |
| element | Active-card action: `■ Stop` (red) | ✓ P:2410 button in header | ✓ A:1237 OutlinedButton → confirm | ~ I:609 trailing swipe "Stop" → alert | misaligned | needs-decision | D4 |
| element | `▶` quick-commands button (waiting_input only) → popup | ✓ P:2412 | ✓ A:1325 "⌨ Commands" → `QuickCommandsSheet` | ✓ I:578 ▶ (waiting only) → QuickCommandsSheet.swift | aligned | | |
| element | Done-card actions: `↺ Restart` + `🗑` (red) | ✓ P:2415 | ✓ A:1346 (+ `deleteSupported` gate) | ~ I:617/626 swipe Delete (trailing) / Restart (leading) | misaligned | needs-decision | D4 |
| interaction | Confirm before Stop / Restart / Delete | ~ delete via `showConfirmModal`; stop/restart direct (verify) | ✓ A:1409–1447 dialogs | ✓ I:91–117 alerts | misaligned | | verify PWA stop/restart confirm |
| element | `🤖 Summary` / `⏳ Summarizing…` button (when summarizer enabled; running → current-status) | ✓ P:2418 `manualSummarize` | ~ re-summarize ↻ only inside `CurrentStatusSheet` (A:1808) | ~ refresh only inside sheet (I:772) | misaligned | needs-decision | D5 |
| element | `⛶` maximize → Dashboard expand mode (BL303) | ✓ P:2424 `openDashExpand` | ✓ row Fullscreen icon → session status (expand) mode | ✗ | ios-missing | | iOS ✗ too · Android done 2026-10-04 (android-missing sweep) |
| element | LLM badge: `llm_ref`, else `backend_family`, accent2 tint + 1px border | ✓ P:2508 | ~ A:1031 backend only, uppercase, accent2 .12, no border | ~ I:494 backend only, uppercase, secondary .12 | misaligned | | prefer llm_ref; PWA style |
| element | Server badge (`sess.server` ≠ local) | ✓ P:2509 | ✓ outlined accent2 badge when `server` ≠ local | ✗ | ios-missing | | iOS ✗; apps show hostname instead (next row) · Android done 2026-10-04 (android-missing sweep); needs Session.server (migration 8) |
| element | Hostname label | ✗ (var unused P:2393) | ~ shown only in All-servers mode | ✓ I:531 muted badge | pwa-missing | decided D16a | D13 · Android done 2026-10-04 |
| element | Worker badge | ✓ P:2510 "⬡ worker", accent2 border, purple .15 bg | ~ A:2212 "⬡ <agentId>" purple A855F7 .15 | ~ I:512 "⬡ <agentId>" secondary .12 | misaligned | | label + colour |
| element | `↳ child of [xxxx]` parent badge | ✓ P:2511 | ✓ `↳ child of [host]` badge | ✗ | ios-missing | | iOS ✗ too; needs `parent_id` in Session DTO · Android done 2026-10-04 (android-missing sweep); Session.parentId (migration 8) |
| element | `⚠ zombie` badge (`claude_alive === false`) | ✓ P:2512 | ✓ amber `⚠ zombie` when `claude_alive == false` | ✗ | ios-missing | | iOS ✗ too; needs `claude_alive` in DTO · Android done 2026-10-04 (android-missing sweep); Session.claudeAlive (migration 8) |
| element | 🎭 Council badge | ✗ | ✓ A:1041 | ✓ `SessionCardView.isCouncil` 🎭 badge + "🎭 Council" chip in the LLM filter row | pwa-missing | decided D64a | D12 · iOS done 2026-10-04 |
| element | `📄 Response` button → last-response viewer | ✓ P:2513 → modal | ✓ A:1101 "View last response" → `LastResponseSheet` | ~ I:540 `doc.text` icon only, not tappable | misaligned | needs-decision | D5 |
| data | Response content freshness | ✓ P:14890 shows cache, then fetches `GET /api/sessions/response?id` (stale badge) | ~ cached `session.lastResponse` only (A:1392) | n/a | misaligned | needs-decision | D5 |
| interaction | Read response aloud (TTS) | ✗ | ✓ A:1732 sheet VolumeUp/Stop | ✗ (TTS only in current-status sheet I:782) | pwa-missing | needs-decision | D5 |
| element | Live elapsed clock on active cards (BL383 `formatElapsed`, tabular, accent2) | ✓ P:2514, P:15468 | ✓ 1 s `formatElapsed` clock, tabular, accent2 | ✗ | ios-missing | | iOS ✗ too · Android done 2026-10-04 (android-missing sweep) |
| element | `AI <age>` summary-age label | ✓ P:2449 inside waiting row, 9px | ~ A:1120 meta row, primary .7 | ~ I:555 meta row | misaligned | | placement |
| string | Time-ago format: just now <5 s · Ns · Nm · Nh · Nd | ✓ P:15454 | ~ `relativeTimeLabel` (not verified) | ✓ I:683 identical | aligned | | verify Android thresholds |
| element | Muted indicator | ✗ | ✗ (state only) | ✓ speaker.slash on the card (local per-profile mute) | pwa-missing | decided D62a | D10 · iOS done 2026-10-04 — push suppression needs a Notification Service Extension |
| interaction | Horizontal swipe ≥64 dp → toggle mute | ✗ | ✓ A:978 | ✓ leading `.swipeActions` Mute/Unmute (full swipe toggles) | pwa-missing | decided D62a | D10 · iOS done 2026-10-04 |
| element | Watch toggle 🔔 (session alerts count toward badge) | ✗ | ✓ A:1055, AV:633 | ✓ bell toggle on card + detail top bar (`LocalSessionPrefs`, per profile) | pwa-missing | decided D61a | D9 · iOS done 2026-10-04 |
| element | Waiting prompt context: last 4 `prompt_context` lines, ANSI-stripped, 100 chars; fallback "Input needed" | ✓ P:2437 | ✓ A:1141 4 lines/100 chars, 3dp waiting bar | ~ I:569 2 lines, no per-line cut, 2pt bar, no fallback text | misaligned | | |
| element | Waiting: short summary (`last_response` italic ≤180) + ▼/▲ envelope for `last_summary_long` + AI age + ✕ panel | ✓ P:2445–2458 | ~ A:1182 "Full summary"/"Less" + ✕; no inline short text | ✗ | misaligned | | iOS ✗ |
| element | Running: `▶ What's it doing?` → current-status | ✓ P:2480 inline | ✓ A:1255 → `CurrentStatusSheet` | ✓ I:583 → `CurrentStatusSheetView` | misaligned | needs-decision | D4b |
| element | Current-status result: text + ▼ long + `↻ <age>` refresh; "Summarizing…" loading | ✓ P:2462–2478 inline in card | ~ A:1808 bottom sheet: refresh, TTS, "▼ More detail"/"▲ Less", ✕ | ~ I:723 sheet: refresh, TTS, More/Less, Done | misaligned | needs-decision | D4b |
| data | `no_change` → "(no change since last refresh)" | ✓ P:2533 | ✓ A:1263 `noChangeStr` | ✓ IosServiceLocator.kt `fetchSessionCurrentStatus` appends "(no change since last refresh)" | aligned | | |
| interaction | Quick commands: System (approve/reject/continue/skip/ESC/Ctrl-b/quit) · Saved (`/api/commands`) · Custom input | ✓ P:2612 `<select>` + custom row | ✓ A:1930 sheet: chips + list + custom field | ✓ QuickCommandsSheet.swift System / Saved (`IosQuickCommands.loadSaved`) / Custom sections | aligned | | |
| interaction | Voice reply 🎤 (Whisper) in quick commands | ✗ | ✓ A:1930 (when `whisper.backend` set, AV:447) | ✓ `QuickCommandsSheet` mic (whisper enabled) → transcript appended to Custom | pwa-missing | decided D63a | D11 · iOS done 2026-10-04 |
| data | Quick-command send: WS `send_input` / `command sendkey` + toast "Sent" | ✓ P:2684 | ✓ `quickReply` → WsOutbound | n/a | aligned | | iOS covered by row above |
| data | List source: WS `sessions` full-list frames | ✓ spec §3.7 | ✓ AV:470 `SessionsHub.fullListFlow` + SQLite | ✓ IV:57 `subscribeGlobalStream` | aligned | | |
| data | `session_state` single-row diff (v8.37) | ? not verified in list path | ✓ AV:486 upsert | ✓ IV:78 `subscribeSessionDiffs` → `upsert` | aligned | | apps aligned; PWA list path still unverified |
| data | REST fallback poll | ✗ (WS only) | ✓ AV:498 30 s + ON_RESUME (A:170) | ✓ IV:24 30 s | pwa-missing | | mobile resilience; no decision |
| interaction | Pull-to-refresh | n/a | ✓ M3 `PullToRefreshContainer` | ✓ I:264 | aligned | | mobile convention · Android done 2026-10-04 (android-missing sweep) |
| data | Recent window (done sessions shown ≤5 min) | ✓ P:1972 `_recentMinutes` | ✓ AV:258 | ✓ I:412 `visiblePool` (`recentWindowMs` 5 min) | aligned | | |
| data | Persistence of chip / collapsed / order / tree prefs | ✓ localStorage ×4 | ~ customOrder persisted; chip + collapsed not | ✗ | misaligned | | |
| string | Session-list strings localised | ✓ `t()` keys (session_filter_ph, sessions_no_active, …) | ✓ strings.xml + 4 locales | ✗ hard-coded | ios-missing | | |
| string | Filter placeholder | ✓ "Filter sessions…" | ✓ `sessions_filter_hint` same | ~ "Filter by name / task / id / backend" | misaligned | | |
| string | Stop / Restart / Delete labels | ✓ "■ Stop" "↺ Restart" "🗑" | ✓ action_stop/restart/delete | ✓ Stop/Restart/Delete | aligned | | glyphs differ only |
| string | "What's it doing?" | ✓ "▶ What's it doing?" | ✓ `sessions_current_status_btn` (ℹ icon) | ✓ (sparkles icon) | aligned | | icon differs |
| element | Session filters CRUD (`/api/filters`) | ✓ P:21124 `showFilterEdit` (Settings) | ✓ `filters/FiltersCard.kt` | ✓ FiltersView (Settings link) | aligned | | lives in Settings — detail in §07 |
| element | Saved commands editor (`/api/commands`) | ~ consumer only here; editor location → §07 | ✓ `commands/SavedCommandsCard.kt` | ✓ SavedCommandsView (Settings link) | aligned | | §07 |
| element | Kind profiles card | ✓ P:15785 `renderProfilesPanel` | ✓ `profiles/KindProfilesCard.kt` | ✗ | ios-missing | | §07 |
| interaction | Three-finger swipe-up gesture (64 dp, 500 ms debounce) | ✗ | ✓ `gesture/ThreeFingerSwipe.kt` | ✗ | pwa-missing | decided D65a | D14 — call site outside this section · iOS not done: the gesture opens the server picker, and iOS has no active-server switch yet (blocked on D1) |
| interaction | Refresh on foreground/resume | ~ (visibilitychange not verified) | ✓ A:170 ON_RESUME | ~ I:84 onAppear → immediate fetch | aligned | | |
| interaction | Rename from list | ✗ (header rename lives in detail) | ~ A:1451 `RenameSessionDialog` defined, no trigger in list | ✗ | n/a | | Android dead code candidate |
| element | Refresh-in-progress spinner in header | ✗ | ✓ A:220 | ✓ I:57 | pwa-missing | | PWA uses header daemon light (§01) |

## Coverage
rows: 83 · aligned: 20 · ios-missing: 15 · android-missing: 0 · pwa-missing: 12 · misaligned: 32 · n/a: 4

Both-apps-missing at audit time: tree view, schedules badge, border pulse, stale-dot, maximize, server badge, parent badge, zombie badge, elapsed clock — 9 rows. Android implemented eight of them on 2026-10-04 (now counted as ios-missing); stale-dot is n/a because the PWA hides it.

## Decisions needed
1. **Server/profile switcher placement** — PWA: toolbar server chip + injected picker bar (federation servers); Android: app-bar title dropdown over app profiles (+ All servers); iOS: none, first profile only. Options: (a) iOS adopts Android title dropdown; (b) iOS uses the segmented profile picker its other tabs use; (c) PWA-style chip in toolbar on both apps. Refs P:2095, A:1514, IV:33.
2. **State filter chips** — PWA: `State (N)` collapsible, 7 real states with colour dots, hide 0-count, persisted; apps: 4 always-visible buckets. Options: (a) adopt PWA on both apps; (b) adopt buckets in PWA; (c) keep per platform. Refs P:2056, A:635, I:276.
3. **Sort / ordering** — PWA: manual drag order then `updated_at` desc, no Sort control; apps: Sort menu (Recent/Started/Name[/Custom]) with state-bucket-first ordering. Options: (a) PWA rule everywhere, drop Sort menu; (b) add Sort menu + bucketing to PWA (app idea); (c) keep. Refs P:2323, AV:199, I:386.
4. **Card lifecycle actions on iOS** — PWA + Android: inline `Stop` / `▶` / `Restart` / `🗑` buttons in the card header; iOS: swipe actions with confirm alerts. Options: (a) inline buttons on iOS (parity); (b) keep swipe (iOS idiom) and add inline too; (c) keep swipe only. Refs P:2410, A:1229, I:609.
5. **Current-status presentation** — PWA: inline in card (text, ▼ long, ↻ age); apps: bottom sheet with TTS play + re-summarize + More/Less. Options: (a) inline on apps; (b) sheet on apps, add TTS + sheet to PWA (app idea); (c) keep. Refs P:2462, A:1808, I:723.
6. **Last-response viewer + Summary button** — PWA: `📄 Response` modal that re-fetches `/api/sessions/response` (stale badge) + `🤖 Summary` button when summarizer enabled; Android: sheet from cached value + TTS, re-summarize only inside status sheet; iOS: non-tappable icon. Options: (a) fresh-fetch viewer everywhere + Summary button on apps; (b) also bring TTS to PWA; (c) keep. Refs P:14890, P:2418, A:1101, I:540.
7. **Select mode UI** — PWA: fixed bottom bar (All/None · Delete · Cancel), entered via `☑` only when History on; Android: long-press → selection app bar + toolbar action row; iOS: none. Options: (a) PWA bottom bar on both apps; (b) Android pattern on iOS; (c) keep. Refs P:2141, A:489/766.
8. **Empty-state copy** — PWA "No active sessions / Tap the + … or send commands via Signal"; Android "No sessions yet. Use `new: <task>`…"; iOS "No sessions — start one in the web UI". Depends on whether iOS gets New Session (§08). Options: (a) PWA copy everywhere (iOS needs FAB first); (b) PWA copy, iOS variant without "+"; (c) keep. Refs P:2114, A:862, I:433.
9. **Skeleton loading list** (Android only: 5 shimmer rows, 900 ms) vs PWA none / iOS spinner. Options: (a) adopt on PWA + iOS (app idea); (b) drop on Android; (c) keep. Refs A:825.
10. **Watch toggle per session** (Android only; alerts counted into badge). Options: (a) adopt on PWA + iOS; (b) keep Android-only; (c) drop. Refs A:1055.
11. **Mute: swipe-to-mute (Android) and muted icon (iOS); PWA has neither in the list.** Options: (a) PWA + iOS adopt swipe + icon; (b) keep as is; (c) drop swipe (discoverability). Refs A:978, I:547.
12. **Voice reply (Whisper 🎤) in quick commands** (Android only). Options: (a) adopt on PWA + iOS; (b) keep Android-only. Refs A:1930.
13. **Council 🎭 badge + Council filter chip** (apps only). Options: (a) add to PWA; (b) drop from apps; (c) keep. Refs A:1041, I:522.
14. **Session identity row** — PWA: name/task line + mono short-id pill, no hostname; Android: name + muted id sub-line + "host ·" in meta; iOS: name/task + hostname badge, no id. Options: (a) PWA layout everywhere, hostname only when multi-server; (b) PWA pill + hostname badge; (c) keep. Refs P:2497–2507, A:1013/1086, I:475/531.
15. **Three-finger swipe-up gesture** (Android only, call site outside the list). Options: (a) document + port; (b) keep Android-only; (c) remove. Refs `gesture/ThreeFingerSwipe.kt`.
