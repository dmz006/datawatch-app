# PWA ↔ Android ↔ iOS parity matrix — master (2026-10-04)

Rules: PWA drives design unless an app idea is better; every judgment call below is the
user's — nothing here is pre-decided. Schema and section scopes: [`README.md`](README.md).
Section files: [`sections/`](sections/).

## 1. Summary

| # | Section | rows | aligned | ios-missing | android-missing | pwa-missing | misaligned | n/a | decisions |
|---|---|---|---|---|---|---|---|---|---|
| 01 | [Shell, nav, splash, tokens](sections/01-shell-nav-tokens.md) | 80 | 17 | 15 | 3 | 5 | 35 | 5 | 19 |
| 02 | [Sessions list](sections/02-sessions-list.md) | 83 | 9 | 15 | 11 | 12 | 33 | 3 | 15 |
| 03 | [Session detail](sections/03-session-detail.md) | 118 | 17 | 37 | 13 | 13 | 33 | 4 | 14 |
| 04 | [Alerts](sections/04-alerts.md) ¹ | 65 | 10 | 15 | 0 | 7 | 25 | 8 | 12 |
| 05 | [Automata](sections/05-automata.md) ¹ | 99 | 9 | 41 | 5 | 7 | 36 | 1 | 14 |
| 06 | [Observer](sections/06-observer.md) ¹ | 93 | 4 | 48 | 5 | 11 | 24 | 1 | 10 |
| 07 | [Settings](sections/07-settings.md) | 97 | 8 | 58 | 5 | 4 | 22 | 0 | 11 |
| 08 | [New Session, modals, post-spec views](sections/08-modals-and-post-spec.md) | 72 | 5 | 50 | 6 | 5 | 3 | 3 | 7 |
| | **Total** | **707** | **79** | **279** | **48** | **64** | **211** | **25** | **102 → 92** |

¹ Coverage line in the section file disagrees with its table; numbers above are recounted from
the rows (04 stated 63 rows / 22 misaligned / 17 ios-missing; 05 stated 96 / 31 / 44; 06 stated
5 aligned / 52 ios-missing / 10 pwa-missing / 20 misaligned). Minor: in 01, 02, 07, 08 one or two
rows have a pipe inside a cell so a status cell shifts — the stated counts were kept. 03's
status counts sum to 117 of 118 rows.

Only **11 %** of feature rows are aligned across all three clients; **39 %** are missing on iOS;
**30 %** exist everywhere but differ.

## 2. Decisions needed (92)

Each decision lists source refs (`SS-Dn` = section file, decision n). "**PWA default**" marks the
option that follows the PWA; it is not a recommendation. 102 section decisions → 92: eleven
duplicates merged, one (03-D6) split.

### Design / visual

- **D1** Sessions header title — (a) "Datawatch" on all **PWA default** · (b) server-name picker title (Android) · (c) iOS "Sessions" + server chip. `01-D3`
- **D2** Server/profile picker placement — (a) PWA toolbar chip + picker bar on both apps **PWA default** · (b) Android header-title dropdown on iOS · (c) iOS segmented control. `01-D4` `02-D1`
- **D3** Header alert pill — colours: (a) PWA as-is, blue tint bg / purple border **PWA default** · (b) PWA fixed to blue border, apps copy. Behaviour: (a) iOS builds the alert dock and matches PWA **PWA default** · (b) all clients: pill = unread count, tap navigates · (c) iOS bell accepted as n/a. `01-D5` `04-D9`
- **D4** Chrome metrics — (a) native M3/UIKit chrome with PWA colours only · (b) custom bars reproducing PWA metrics (56/60px, 1px borders, accent2 FAB, tab top border) **PWA default**. `01-D7`
- **D5** Nav icons — (a) emoji glyphs (🖥 🤖 ⚠ 📡 ☷ ⚙) **PWA default** · (b) SF Symbols on iOS · (c) SF Symbols / Material icons everywhere. `01-D8`
- **D6** `--accent` — (a) PWA + iOS adopt #8B5CF6 (Android, WCAG AA) · (b) Android reverts to #7C3AED **PWA default**. `01-D15`
- **D7** UI typeface — (a) mono (JetBrains Mono) UI on mobile **PWA default** · (b) platform sans as accepted difference; also: iOS Dynamic Type yes/no. `01-D16`
- **D8** Terminal font — (a) bundle JetBrains Mono in both apps **PWA default** · (b) platform mono. `03-D12`
- **D9** Brand casing — (a) "datawatch" everywhere · (b) "Datawatch" everywhere. (PWA itself mixes: header/manifest capitalised, splash lowercase.) `01-D17`
- **D10** Loading / connecting splash standard — (a) Android animated eye (+ bolt, dwell timers) canonical on all three · (b) PWA minimal (static favicon / CSS spinner) **PWA default** · (c) iOS lightweight SF-symbol variant. `01-D19` `03-D9`
- **D11** Wide-screen layout — (a) accept per-platform (PWA 480px card + expand, Android two-pane, iOS split view) · (b) one two-pane tablet layout for both apps and expanded PWA. `01-D10`
- **D12** Session state filter chips — (a) PWA collapsible `State (N)` with 7 states on both apps **PWA default** · (b) app buckets adopted in PWA · (c) keep. `02-D2`
- **D13** Session card lifecycle actions on iOS — (a) inline Stop/▶/Restart/🗑 buttons **PWA default** · (b) inline + swipe · (c) swipe only. `02-D4`
- **D14** Current-status presentation — (a) inline in card **PWA default** · (b) bottom sheet with TTS + re-summarize, sheet/TTS added to PWA · (c) keep. `02-D5`
- **D15** Select-mode UI — (a) PWA fixed bottom bar **PWA default** · (b) Android long-press + selection app bar on iOS · (c) keep. `02-D7`
- **D16** Session identity row — (a) PWA layout (name/task + id pill, hostname only multi-server) **PWA default** · (b) PWA pill + hostname badge · (c) keep. `02-D14`
- **D17** Session mode badge condition — (a) tmux only **PWA default** · (b) non-tmux only (Android) · (c) drop, rely on tab strip. `03-D1`
- **D18** Running-state pill pulse (Android only) — (a) adopt in PWA + iOS · (b) remove **PWA default**. `03-D3`
- **D19** Last-activity age colours (Android only) — (a) adopt · (b) plain text **PWA default**. `03-D4`
- **D20** Terminal font control — (a) `Aa▾` dropdown on iOS **PWA default** · (b) keep iOS A−/px/A+ row, add Fit. `03-D10`
- **D21** Saved-commands UI in session — (a) bottom sheet with PWA content · (b) literal dropdown + custom input **PWA default**. `03-D14`
- **D22** Automata terminology — (a) "Automata \| Templates" **PWA default** · (b) "PRDs" everywhere · (c) per-platform. `05-D1`
- **D23** PRD status pill colours + pulse — (a) apps adopt PWA state-badge tokens + pulse **PWA default** · (b) PWA adopts app hex map. `05-D4`
- **D24** PRD detail tabs — (a) PWA five tabs (Overview/Stories/Decisions/Rules/Scan), Graph/Progress as cards **PWA default** · (b) PWA adopts Graph + Progress tabs. `05-D7`
- **D25** Automata type registry placement — (a) Settings › Automata **PWA default** · (b) both places · (c) keep iOS "Types" tab. `05-D5` `07-D10`
- **D26** Observer docs links — (a) per-card links on apps **PWA default** · (b) single page link · (c) PWA drops per-card. `06-D1`
- **D27** Collapsible cards (Observer + Settings) — (a) add to both apps with persisted state **PWA default** · (b) static on mobile · (c) PWA removes collapse. `06-D2` `07-D2`
- **D28** Observer card order — (a) apps adopt PWA order (nested stats block) **PWA default** · (b) PWA adopts Android flattened order · (c) new agreed order. `06-D3`
- **D29** Gauge colour thresholds — (a) replicate PWA per-metric values verbatim **PWA default** · (b) unify all incl. PWA on ≥70 warn / ≥90 error · (c) ≥50/≥80. `06-D5`
- **D30** iOS Observer summary tiles — (a) replace with PWA grid + stats panel **PWA default** · (b) keep as header above PWA layout · (c) promote to PWA/Android. `06-D10`
- **D31** iOS Settings structure — (a) six-tab bar **PWA default** · (b) native grouped list with the six groups as sections · (c) tabs on iPad, list on iPhone. `07-D1`
- **D32** Settings density — (a) iOS compact scale matching PWA **PWA default** · (b) PWA/Android move to platform-native density. `07-D4`
- **D33** Secrets-vault status placement — (a) Android moves it into Secrets card **PWA default** · (b) PWA splits it out. `07-D5`
- **D34** Dashboard design — (a) iOS rebuilds the PWA/Android card grid **PWA default** · (b) iOS multi-server overview kept and added to PWA/Android as a card · (c) both. `08-D5`
- **D35** Empty-state copy (sessions, alerts) — (a) PWA strings everywhere **PWA default** · (b) adopt Android's per-tab copy in PWA + iOS · (c) keep. `02-D8` `04-D5`
- **D36** Alert-dock expand animation — (a) add ~150–200 ms transition to PWA · (b) PWA static, strip Android animation **PWA default**. `04-D10`

### Behaviour / semantics

- **D37** Splash gating — (a) PWA rule (first launch / version change / >24 h) on apps **PWA default** · (b) every cold launch (Android) adopted everywhere · (c) keep. `01-D1`
- **D38** Status-dot gestures — (a) apps add long-press reconnect + hidden debug entry **PWA default** · (b) PWA adopts the tap-sheet · (c) leave. `01-D6`
- **D39** Alerts tab badge at zero — (a) hide at 0 **PWA default** · (b) Android always-on dimmed + muted state. `01-D9`
- **D40** Restore last view on cold start — (a) last tab + open session **PWA default** · (b) tab only · (c) leave. `01-D11`
- **D41** Android toasts — (a) retire, alert dock only **PWA default** · (b) keep. `01-D18`
- **D42** Session sort/ordering — (a) PWA manual drag + `updated_at`, drop Sort menu **PWA default** · (b) add Sort menu + bucketing to PWA · (c) keep. `02-D3`
- **D43** Last-response viewer + Summary button — (a) fresh-fetch viewer + 🤖 Summary on apps **PWA default** · (b) also TTS in PWA · (c) keep. `02-D6`
- **D44** Stop vs Kill wording — (a) "Stop" everywhere **PWA default** · (b) "Kill" kept in confirm dialogs. `03-D5`
- **D45** Inline process-stats bar in session detail — (a) add to apps **PWA default** · (b) drop from PWA. `03-D7`
- **D46** Disconnect presentation — (a) Android non-blocking banner after grace everywhere · (b) PWA minimal (global status dot) **PWA default**. `03-D8`
- **D47** 🔕 button — (a) apps implement real dock mute **PWA default** · (b) PWA changes 🔕 to dismiss-all · (c) remove 🔕 from apps. `04-D1`
- **D48** Dismiss-all — (a) apps delete too **PWA default** · (b) PWA stops deleting · (c) keep both, label differently. `04-D2`
- **D49** Alert read model — (a) apps auto-ack on page open and drop per-alert read UI (✓, unread dot) **PWA default** · (b) PWA adopts explicit read state + per-alert read UI. `04-D3` `04-D4`
- **D50** Swipe-to-dismiss alerts — (a) per-group · (b) per-alert · (c) both · (d) none **PWA default**. `04-D6`
- **D51** Live WS `alert` frames on mobile — (a) badge + in-app toast **PWA default** · (b) badge only · (c) keep 5 s polling. `04-D11`
- **D52** PRD Pause/Resume (in spec + app.js, unreachable) — (a) remove from spec and app.js · (b) wire up everywhere. `05-D11`
- **D53** Markdown / Mermaid libraries — (a) vendor into every client (as done for xterm) · (b) keep runtime CDN **PWA default**. `05-D12`
- **D54** Stats refresh model — (a) WS-first, 30 s REST fallback, 8 s grid · (b) PWA one-shot + WS **PWA default** · (c) 10 s grid everywhere. `06-D4`
- **D55** Observer peer row action — (a) PWA 📊 snapshot modal + × remove on apps **PWA default** · (b) PWA adopts navigate-to-node-detail · (c) both. `06-D7`
- **D56** Schedule edit UX — (a) PWA gets a proper edit modal · (b) apps mimic PWA's two prompts **PWA default**. `06-D9`
- **D57** Restart-needed signalling — (a) PWA adopts Android banner · (b) apps adopt inline link **PWA default** · (c) keep. `07-D3`
- **D58** OpenCode model list in New Session — (a) PWA groups by provider · (b) Android flattens **PWA default**. `08-D2`

### Scope — feature on one client only (adopt everywhere, keep where it is, or drop)

For every item here the **PWA default** is "PWA unchanged" (not adopted in the PWA).

- **D59** Android splash extras (status line, "Replay splash") — (a) adopt everywhere · (b) Android-only · (c) drop. `01-D2`
- **D60** Skeleton shimmer list (Android) — (a) adopt in PWA + iOS · (b) drop · (c) keep. `02-D9`
- **D61** Watch sessions / automata + watched-badge filter (Android) — (a) adopt in PWA + iOS · (b) Android-only · (c) drop. `02-D10` `03-D6` `04-D7` `05-D14`
- **D62** Swipe-to-mute (Android) + muted icon (iOS) — (a) PWA + iOS adopt both · (b) keep as is · (c) drop swipe. `02-D11`
- **D63** Whisper 🎤 voice reply in quick commands (Android) — (a) adopt in PWA + iOS · (b) Android-only. `02-D12`
- **D64** Council 🎭 badge + filter chip (apps) — (a) add to PWA · (b) drop from apps · (c) keep. `02-D13`
- **D65** Three-finger swipe-up gesture (Android) — (a) document + port · (b) Android-only · (c) remove. `02-D15`
- **D66** Agent ⬡ and "Chrome" badges in session header (iOS) — (a) add to PWA + Android · (b) remove from iOS. `03-D2`
- **D67** Other Android session-detail extras — hooks-installed toast, rate-limit inline notice, persisted Terminal/Chat mode — per item: (a) adopt · (b) remove. `03-D6`
- **D68** Chat quick-reply chips Yes/No/Stop (Android) — (a) PWA memory quick-cmd bar everywhere · (b) both · (c) remove chips. `03-D11`
- **D69** Terminal search/copy (dormant Android code) — (a) revive on all three · (b) delete. `03-D13`
- **D70** Alert-rule "Recent Firings" list (Android) — (a) add to PWA Settings · (b) drop from Android. `04-D8`
- **D71** Parent-PRD ↗ link on PRD card (Android) — (a) add to PWA · (b) drop. `05-D2`
- **D72** Inline Reject/Revise on PRD list card (Android) — (a) add to PWA · (b) remove. `05-D3`
- **D73** Wizard "memory promote to" field (Android) — (a) add to PWA + iOS · (b) remove. `05-D6`
- **D74** Approve-with-note (Android) — (a) add to PWA + iOS · (b) drop. `05-D8`
- **D75** Edit PRD permission_mode (Android) — (a) add to PWA · (b) remove. `05-D9`
- **D76** Repair depends_on button (Android #202) — (a) add to PWA + iOS · (b) Android-only. `05-D10`
- **D77** Memory recall / scopes / lifecycle UI (Android BL385–387) — (a) port to PWA + iOS · (b) trim Android to PWA scope · (c) first confirm whether PWA has it under other names. `05-D13` `08-D6`
- **D78** Android-only Observer cards (server info, session ring + max_sessions, Ollama, envelopes, backend health, eBPF degraded banner, add-memory) — (a) add to PWA · (b) mobile-only · (c) remove. `06-D6`
- **D79** Config Viewer + Raw config editor (Android) — (a) add to PWA + iOS · (b) mobile-only ops tools · (c) remove. `07-D6`
- **D80** Subsystem reload + MCP channel/tools cards in About (Android) — (a) add to PWA · (b) app-only · (c) drop. `07-D11`
- **D81** Saved-command library in New Session task field (Android) — (a) adopt in PWA + iOS · (b) drop · (c) mobile-only. `08-D1`
- **D82** "Resume previous session" field (Android) — (a) adopt in PWA + iOS · (b) remove · (c) mobile-only. `08-D3`
- **D83** Inline file viewer for story/task file chips (Android #181) — (a) adopt in PWA + iOS · (b) mobile-only. `08-D7`

### Platform-specific

- **D84** Deep-link scheme — (a) `dwclient://` on both · (b) `datawatch://` on both; plus: pursue universal links (needs owned domain + AASA)? `01-D12`
- **D85** Android platform integrations (assist/voice intents, widgets, QS tile) — (a) iOS equivalents (App Intents/Siri, WidgetKit) are parity items · (b) out of scope. `01-D13`
- **D86** iOS first run — (a) Android Splash → Onboarding → Add Server · (b) per-tab empty states · (c) PWA-style minimal on both apps **PWA default**. `01-D14`
- **D87** iOS push before APNs ships — (a) wait for APNs · (b) interim local notifications from polling while app is open. `04-D12`
- **D88** Push card — (a) PWA shows delivery tier too · (b) tier is app-only · (c) iOS card = APNs registration status + test. `07-D9`
- **D89** Memory maintenance (eviction/spellcheck/extract/schema) on phones — (a) full parity · (b) dry-run only · (c) PWA/desktop only. `06-D8`
- **D90** iOS encryption-status card — (a) show Data Protection class + Keychain state · (b) n/a. `07-D7`
- **D91** Certificate pinning (TOFU, iOS-only today) — (a) Android adopts · (b) iOS-only · (c) also a "PINNED" badge on both. `07-D8`
- **D92** PWA debug panel — (a) n/a on mobile · (b) port behind a developer toggle. `08-D4`

## 3. Mechanical backlog (no decision attached)

iOS work, ordered sessions → detail → automata → observer → settings → alerts → dashboard/post-spec →
shell. Chunks are ≈1–2 days. Refs are section files; feature names match the row's Feature cell.
Where a chunk touches a decision, it is noted — build the decided variant.

**Sessions list (+ New Session, which is the list's FAB)**
- **B1** New Session surface + FAB — name, task, directory, profile/cluster, LLM picker + compute node, permission/model/effort, Chrome flag, recent-done restart, submit (`08` New Session rows; empty-state copy depends on D35).
- **B2** History toggle + 5-min recent window, toolbar in empty state, select mode + bulk delete with confirm (`02`; UI variant per D15).
- **B3** Drag-to-reorder with persisted order + drag handle (`02`; see D42).
- **B4** Card extras: ▶ quick-commands popup (waiting_input), "no change since last refresh", WS `session_state` single-row diff (`02`).
- **B5** Session filters CRUD, saved-commands editor, kind profiles; localised list strings (`02`).

**Session detail**
- **B6** State badge → state-override dropdown, delete dialog with memory strategy, timeline button + timeline (`03`, `08` state override).
- **B7** Tab bar per mode (channel / tmux-only / chat-only), Channel tab lines + history seed (`03`).
- **B8** Status sub-tab: `/status` 5 s poll while open, hook-health pill, current focus, sprint/PRD tree, tests, git, guardrail verdicts (`03`).
- **B9** Stats sub-tab: envelopes poll, Host (donut + sparklines), Container, Compute Node, LLM cards (`03`).
- **B10** Terminal: Fit to width, configured min cols/rows, scroll mode + strip, prepend backlog on open (`03`).
- **B11** Composer: pending-schedules strip + schedule popup, image attach + upload/transcribing banners, keys strip, sendkey quick inputs; i18n of detail copy (`03`).

**Automata**
- **B12** List: all-servers aggregated scope, filter bar (status/type badges), template badge, lifecycle strip, pin, stories tree on card (`05`).
- **B13** Batch mode: select toggle, batch bar (Run/Approve/Cancel/Archive/Delete), batch-delete confirm (`05`).
- **B14** Launch wizard + ⚡ FAB: title, workspace/profile, execution backend/model/effort, planning backend + decomposition model (`05`).
- **B15** Templates tab: cards, Use (instantiate) / edit / clone PRD → template, create/edit form (`05`).
- **B16** Detail actions: reset to draft, delete with memory strategy, edit title/spec, set LLM, run scan/rules, view sessions, settings panel, scope warnings (`05`).
- **B17** Detail panels: capacity, active-session card, status graphs, terminal-state hint, Decisions tab (`05`; tab layout per D24).
- **B18** Story/task ops: story approve/reject/cancel, task retry/cancel/requeue/edit/remove, planned/output file chips (`05`).
- **B19** Live `prd_update` WS patching; orchestrator graphs + pipeline manager cards; i18n (`05`).

**Observer** (card order per D28, thresholds per D29, refresh per D54)
- **B20** Per-system grid (local + peers), GPU temp thresholds, GPU-probe-failed card, network label, infrastructure card (`06`).
- **B21** Stats-panel extras: RTK savings + update badge, episodic memory, `max_sessions` denominator, eBPF status + per-process network table, plugins list (`06`).
- **B22** Peer resources: per-peer metrics, parallel snapshots, 8 s refresh, group-by-node, dot colours, A/B/C shape badge, "attached to ComputeNode", cluster nodes (`06`).
- **B23** Channel/comm: MCP bridge status, channel diagnostics, Matrix test, web-search stats + history (`06`).
- **B24** Memory browser + maintenance: stats cards, search/list/export, results list, eviction confirm (`06`; maintenance scope per D89).
- **B25** Schedules, cooldown, session analytics, audit log, knowledge graph, daemon log, federation peers (`06`).

**Settings** (structure per D31)
- **B26** Scaffold: tab persistence, notifications card, theme Dark/Light/System, language override (`07`).
- **B27** General: auto-update, session card (17 keys), docs search, session templates, device aliases, tooling lifecycle, file service, discussion scopes (`07`).
- **B28** Comms: auth card, remote servers + federated peers, web server, MCP server, per-backend comm cards + Signal device linking, proxy resilience, routing rules, channel routing (`07`).
- **B29** Compute: LLM registry, compute node add/edit, cost rates, cluster profiles, memory (18 keys), RTK, container workers, Tailscale, secrets store (`07`).
- **B30** Detection filters, alert rules (CRUD), saved commands, output filters cards (`07`).
- **B31a** Automata settings I: identity, algorithm mode, evals, council panel, project profiles (`07`).
- **B31b** Automata settings II: pipeline manager, orchestrator, guardrail library + profiles, pipelines config, skill registries, automata defaults (`07`).
- **B32** Plugins + About: plugin config/status, store/uptime/daemon status, orphaned tmux + kill all, update check + restart daemon, API links (`07`).

**Alerts** (read model per D49, dismiss per D48)
- **B33** Server picker, alert → open session, tab/filter persistence, sort toggle, by-session group cards with collapse (`04`).
- **B34** Quick reply on prompt alerts (saved commands), detection settle window, alert rules card + add form, i18n (`04`).

**Dashboard / post-spec / modals**
- **B35** Modal + toast infrastructure: confirm variants, generic modal, toast (4 types, stacking), backend-config popup, filter edit, remote-server forms, web-search provider form, peer snapshot modal (`08`; toast fate per D41).
- **B36** Dashboard stat tiles + cards (constellation, sparklines, events, gantt, heatmap, guardrails, smoke) + add/edit/expand (`08`; **blocked on D34**).
- **B37** Post-spec views not covered above: orchestrator graphs view, guardrail verdicts inline, council live run, status board, sessions tree + parent link, algorithm mode, Ollama marketplace, identity wizard, compute telemetry/capacity (`08`; de-duplicate against B27–B31 when scheduling).

**Shell / tokens**
- **B38** Splash scene (Earthrise artwork, text block, compact scene in About, eye breathe motion), identity-wizard button, tab gating on autonomous config, nav hidden in detail, Sessions FAB (`01`; gating per D37).
- **B39** Daemon self-update check in UI, live system colour-scheme switching, alert-pill blue token, locales en/de/es/fr/ja + shell copy (`01`).

### Android work (`android-missing`)

- **01**: "Updated to vX" splash badge · light palette · reduced-motion support.
- **02**: History auto-enable from historical chip · tree view (BL348) · pending-schedules badge · waiting_input border pulse · stale-dot · ⛶ maximize → dashboard expand · server badge · parent badge · zombie badge · live elapsed clock · pull-to-refresh.
- **03**: rename toast · channel/acp "Waiting for…" banner · Status tab badge dot · channel help popup · parent session link · last-5-events-before-failure · send via channel `▶ ch` · collapsed earlier messages · chat quick-cmd bar · log-mode line classes · hold-to-repeat arrows · replay pending needs-input popup · status board fetch on mount.
- **05**: filter-bar text search · current-position line on card · running/planning pill pulse · wizard "Use template" link + skills · Archive action.
- **06**: GPU-probe-failed card · cross-host view · remove peer · pagination · pipelines live block · identity panel.
- **07**: certificate pinning (see D91) · profile-row security badges · exit hooks card · work queue card · branding/splash card.
- **08**: directory browser · compute kind-migration modal · channel help · debug panel (see D92) · project profiles + YAML editors · secrets vault card.

### Spec drift (fix `docs/plans/2026-05-12-pwa-full-spec.md`, no decision)

- Removed from live PWA: §1.6 version-staleness reload; §4.15 generating indicator (alpha.29); `rate_limited` in §10.3 state-override options (live: running, waiting_input, complete, killed, failed). §6.6 Pause/Resume is defined but unreachable (D52).
- Missing from spec: §9 Chrome integration checkbox; post-spec views — Dashboard, Orchestrator graphs, guardrail verdicts + library, council live runs, status board, sessions tree, discussion scopes, algorithm mode, Ollama marketplace, profiles/cluster editors, web search stats/providers, identity wizard, Tailscale, skills registry, secrets vault, compute telemetry/capacity.

## 4. Cross-cutting themes

- **Design tokens from one source.** Colours, type and motion differ in small ways everywhere (accent, alert pill, PRD pills, thresholds); generate Kotlin + Swift tokens from the PWA CSS variables once D6/D7/D23/D29 are decided.
- **i18n.** PWA ships 5 locales via `t()`; Android 1,363 strings × 4 extra locales; iOS has none — every iOS chunk should land with string keys, not literals.
- **WS-first data.** PWA and Android consume `sessions`, `session_state`, `stats`, `alert`, `prd_update` frames; iOS only stats/sessions so far. PrdHub and alert frames are the gaps (B19, D51).
- **Shared modal/toast/confirm infrastructure on iOS** (B35) unblocks most destructive actions across sections — build it first.
- **"App had a better idea" backlog.** 64 `pwa-missing` rows + 25 scope decisions: Android has accumulated features (watch, memory UI, file viewer, skeletons, config viewer) the PWA never got; each needs a keep/port/drop call.
- **Motion and splash** are inconsistent on all three (D10, D18, D23, D36, D37); decide once, then implement per platform.
- **Alert semantics** (D47–D51) differ in meaning, not just look — the highest-risk area for user confusion.
- **Settings is the largest iOS gap** (58 rows, ~120 config keys); a generic config-field renderer (as Android's `configfields`) is cheaper than 20 bespoke cards.
