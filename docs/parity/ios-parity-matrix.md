# PWA ↔ Android ↔ iOS parity matrix — master (2026-10-04)

Rules: PWA drives design unless an app idea is better; every judgment call is the user's —
all 92 were answered on 2026-10-04 (§2). Schema and section scopes: [`README.md`](README.md).
Section files: [`sections/`](sections/).

## Remaining work (as of 2026-10-04)

Ordered by user impact within each platform. Refs: `SS` section file › row Feature.

### iOS

1. ~~**Alert dock** — header pill (muted/0/N), tap toggles dock, 60 s coalescing ×N, session mute; 🔕 mutes instead of dismissing; ad-hoc toasts route to the dock (D3a/D41a/D47a). `04` › Header alert pill, Alert dock panel, Dock coalescing, 🔕 button · `01` › Alert pill states, Toasts retired · `08` › Toast~~ — done 2026-10-04 (AlertDock + pill + panel; inline notices in screens outside the session/alerts pass still to route)
2. ~~**Live WS `alert` frames** → badge + dock entry (D51a). `04` › WS `alert` frame, Toast on WS alert~~ — done 2026-10-04 (IosAlertFeed → LiveAlertFeed)
3. ~~**Interim local notifications** from polling with 45 s settle window (D87b). `04` › Waiting-state settle window~~ — done 2026-10-04 (LocalAlertWatcher, foreground only)
4. ~~**Deep links** — register `datawatch://` and fix `AppRouter` path parsing (D84b). `01` › Deep link to a session~~ — done 2026-10-04
5. ~~**Session-detail controls** — "Stop" wording (D44a); ■ Stop · ↻ Restart · 🗑 Delete in info bar; remove full-screen disconnect overlay (D46b); saved-commands dropdown + custom input (D21b); hide tab bar in session detail. `03` › Stop wording, Server-unreachable banner, Saved commands · `01` › Bottom nav hidden in session detail~~ — done 2026-10-04
6. ~~**Composer/channel gaps** — send via channel `▶ ch`, pending-schedules strip, rename feedback, respect `input_mode`. `03` › Send via channel, Pending schedules strip, Rename toast, Input bar shown only when…~~ — done 2026-10-04
7. ~~**Automata detail + pills** — PWA five tabs (D24a); PWA status-pill colours + pulse (D23a); Archive; tab label "Automata" (D22a).~~ **Done 2026-10-04** (segmented Overview·Stories·Decisions·Rules·Scan, statusPill tokens + Reduce-Motion-aware pulse, Archive, "Automata | Templates"). `05` › Detail tabs, Status pill, Actions: Archive, Tab strip
8. ~~**New Session directory browser**.~~ **Done 2026-10-04** (`DirectoryBrowserSheet`, also used by the Automaton wizard). `08` › Directory browser
9. ~~**Sessions list** — historical state chip must auto-enable History (regression); tree view + parent/zombie badges; pending-schedules badge; card left state edge + border pulse.~~ **Done 2026-10-04**. `02` › Picking a historical state chip…, Tree view toggle, parent/zombie badge, Pending-schedules badge, Card surface
10. ~~**Biometric lock toggle is a no-op** (`.biometricLocked` never applied). `07` › Security card~~ — done 2026-10-04
11. ~~**Restore last tab + open session; status-dot long-press reconnect** (D40a/D38a). `01` › Restore last view, Dot gestures~~ — done 2026-10-04
12. **Alerts cleanup** — drop per-alert read UI + single-alert swipe (D49a/D50d); All-servers aggregate (D2a); prompt rule incl. `waiting_input`. `04` › Per-alert mark-read, Swipe-left single alert, Multi-server aggregate, Prompt category rule — **partial:** per-alert read UI + swipe removed and catOf prompt rule done 2026-10-04; All-servers aggregate (D2a) still open
13. **Session-detail med items** — Aa▾ font dropdown (D20a), tmux-only mode badge (D17a), running pill pulse (D18a), inline process-stats bar (D45a), JetBrains Mono terminal (D8a), connection banner, status-tab badge dot + fetch on mount, parent link, last-5-events, log mode, hold-to-repeat, fresh-fetch response viewer + 🤖 Summary (D43a). `03`, `02` › Response content freshness, 🤖 Summary — **partial:** done 2026-10-04: Aa▾, tmux-only badge, inline stats bar, connection banner, status-tab badge + fetch on mount, parent link (info bar), hold-to-repeat, fresh-fetch viewer + 🤖 Summary, running pill pulse (D18a). JetBrains Mono done 2026-10-04 (shared xterm/fonts). Last-5-events (`FailedDrilldownView` in SessionStatusView) and log mode (`SessionLogView`) done 2026-10-04 (iOS-D) — item complete
14. **Automata wizard + list** — ~~intent auto-detect, advanced toggles, Browse, template/skills links; stories tree, position line, type/template badges, left border; Pause/Resume (D52b); markdown/Mermaid spec render (D53b)~~ **done 2026-10-04** (Mermaid: WKWebView + CDN mermaid@10.9.6, source fallback offline; Pause/Resume POSTs fail until the server adds the actions). Open: All-servers scope (with item 15 picker), Settings deep link + `?` help tip in the wizard. `05`
15. **Shell** — hide Automata/Dashboard tabs until autonomous enabled; identity-wizard 🤖 button; ~~`ServerPickerBar` instead of segmented control on Observer/Automata/Dashboard (D2a)~~ (done); tab order Sessions·Automata·Alerts. `01`, `06` › Server picker (tab gating + PWA order done — iOS-A)
16. **Settings depth** — ~~Exit Hooks + Work Queue cards; CRUD on list cards (session templates edit, council runs, profiles edit, guardrail profiles, skill registries edit, fed-peer form + Test, channel routing, web-search providers, Tailscale key, discussion scopes, Ollama marketplace, kind migration)~~ (done 2026-10-04). Left: template **Use** (needs New Session prefill), council live SSE + 🤖 persona wizard + subsystem config, skill registry browse/sync, remote-server test/enable. `07` `~` rows · `08`
17. ~~**Observer** — "Cross-host view"; federation "Test peer".~~ (done) `06`
18. **Low** — "Updated to vX" splash badge, accent2 active-tab tint + FAB, 99+ badge cap, Reduce-Motion on dot pulse, empty/error copy (D35a), console_cols, Line Up/Down, C-c/C-b quick inputs, camera attach, Yes/No/Stop chips (D68b), chat collapse, channel `?` help. — **iOS-D 2026-10-04:** Yes/No/Stop chips and channel `?` help done; C-c/C-b quick inputs were already done (System set in the Commands… menu, `03` row aligned); Line Up/Down dropped — neither the PWA nor Android has it (`03` › Scroll-mode strip re-checked, aligned). Rest still open.
19. **Later** — App Intents / WidgetKit (D85a). `01` › Platform integrations

### Android

Status 2026-10-04 (Android remaining-work sweep): items 1–11 done except where noted; see the per-row
"Android done 2026-10-04" notes in the section files.

1. ~~**Server picker bar** (PWA chip + bar, not title dropdown) on Sessions/Observer (D2a).~~ Done — `ServerPickerBar` on Sessions/Automata (+All), Observer, Dashboard; title dropdowns removed.
2. ~~**Current status inline in card**, not a bottom sheet (D14a).~~ Done.
3. ~~**Observer to PWA rules** — card order with nested stats block (D28a); 📊 peer snapshot modal (D55a); memory maintenance dry-run only (D89b); thresholds verbatim (D29a); one-shot + WS refresh, 8 s grid (D54b).~~ Done.
4. ~~**Automata detail tabs** — Rules + Scan, Graph/Progress as cards (D24a); progress bar on list cards; PWA sort.~~ Done (Scan/Rules tabs always shown — `PrdDto` has no scan/rules flags).
5. ~~**Fresh-fetch response viewer + 🤖 Summary** (D43a).~~ Done.
6. ~~**Shell** — `datawatch://` (D84b, `dwclient://` alias for one release); restore last tab + session (D40a); status-dot long-press reconnect (D38a); minimal first run (D86c).~~ Done (D38a update-check half n/a: app updates are store-managed).
7. ~~**Alerts quick reply** = saved-commands select; group order by state rank.~~ Done (sent via the session-reply endpoint).
8. **Session detail** — ~~inline process-stats bar (D45a); terminal search UI (D69a); agent ⬡ + Chrome badges (D66a); connect watchdog; 5-state override list; composer placeholder copy.~~ Done. JetBrains Mono terminal (D8a) done 2026-10-04 (bundled, monthly update check).
9. ~~**Observer extras** — schedule edit via two prompts (D56b) + select-all; channel diagnostics, Matrix Test, web-search history; memory role/since filters; RTK update badge; duplicate Pipelines/Identity cards removed.~~ Done.
10. ~~**Settings** — installed-plugin enable/disable/reload; missing config keys; vault status inside Secrets card (D33a); inline restart link (D57b); mount `McpToolsCard`.~~ Done (plugin "test" omitted — the PWA Plugin Manager has no test action). `goose`/`opencode` config-card drift (07 › row 81) still open.
11. ~~**Dashboard stat strip** (D34a); flat OpenCode model list (D58b).~~ Done.
12. **Low** — ~~accent2 alert-pill border (D3a)~~, ~~99+ cap~~ done; open: accent #7C3AED compute badge (D6b), splash fade-out, LLM/worker badge copy, copy/wording drift in 02/03/06.

### Both apps

- "Approve" on blocked guardrail verdicts + "Run guardrail" action. `08` › Guardrail verdicts inline · `03` › Guardrail verdicts card — **Android done 2026-10-04**; **iOS done 2026-10-04** (iOS-D, `GuardrailVerdictsBody`).
- Backend setup hint ⚠ "not installed or configured". `08` — **Android done 2026-10-04**; **iOS done 2026-10-04** (iOS-D, `BackendSetupHint`).

### PWA (server repo)

1. **#172 — app extras to the PWA (D59–D83)**: watch sessions/automata + watched-badge filter (D61a), swipe-to-mute + muted icon (D62a), Whisper reply (D63a), council badge/filter (D64a), skeleton list (D60a), three-finger swipe (D65a), hooks toast / rate-limit notice / persisted mode (D67a), agent/Chrome badges (D66a), terminal search (D69a), Yes/No/Stop chips (D68b), alert-rule Recent Firings (D70a), PRD extras (D71a–D76a), memory UI (D77a), Observer server-info/envelopes/backend-health/add-memory (D78a), config viewer + raw editor (D79a), subsystem reload + MCP cards (D80a), New Session library + resume (D81a/D82a), inline file viewer (D83a), splash status line + replay (D59a). Rows tagged "→ #172".
2. **PRD Pause/Resume** wired up (D52b) — unreachable in all three today. `05` › Actions: Pause / Resume
3. **Lowercase "datawatch"** in header, manifest and title (D1a/D9a). `01` › Sessions-list header title, Brand/manifest, Brand casing

## 1. Summary

| # | Section | rows | aligned | ios-missing | android-missing | pwa-missing | misaligned | n/a |
|---|---|---|---|---|---|---|---|---|
| 01 | [Shell, nav, splash, tokens](sections/01-shell-nav-tokens.md) | 80 | 40 | 16 | 0 | 8 | 12 | 4 |
| 02 | [Sessions list](sections/02-sessions-list.md) | 83 | 45 | 9 | 0 | 7 | 15 | 7 |
| 03 | [Session detail](sections/03-session-detail.md) | 118 | 53 | 21 | 0 | 12 | 28 | 4 |
| 04 | [Alerts](sections/04-alerts.md) | 65 | 32 | 11 | 0 | 2 | 12 | 8 |
| 05 | [Automata](sections/05-automata.md) | 99 | 48 | 17 | 0 | 8 | 26 | 0 |
| 06 | [Observer](sections/06-observer.md) | 93 | 77 | 3 | 0 | 4 | 8 | 1 |
| 07 | [Settings](sections/07-settings.md) | 97 | 59 | 6 | 0 | 5 | 26 | 1 |
| 08 | [New Session, modals, post-spec views](sections/08-modals-and-post-spec.md) | 71 | 46 | 16 | 0 | 4 | 0 | 5 |
| | **Total** | **706** | **400** | **99** | **0** | **50** | **127** | **30** |

Full re-audit 2026-10-04 against current code after all 92 decisions were answered. Status now
means: `aligned` = equivalent **or** the difference is what a decision prescribes (noted "per Dxx");
`*-missing` = that platform lacks something a decision says it should have; `misaligned` = all
have it but differ in an unsanctioned way. Every section's Coverage line matches its table (06
excludes its 11 sub-heading rows). No `needs-decision` cells remain.

Before → after (table counts): aligned 287 → **361** · ios-missing 90 → **85** · android-missing
0 → **15** · pwa-missing 59 → **50** · misaligned 241 → **165** · n/a 29 → **30**. android-missing
rose because decisions (D2a, D14a, D24a, D28a, D55a, D89b …) now prescribe PWA behaviour Android
does not yet follow; several iOS rows moved from `misaligned` to `ios-missing` for the same reason.

**Android remaining-work sweep (2026-10-04, later):** aligned 361 → **400** · ios-missing 85 →
**99** · android-missing 15 → **0** · misaligned 165 → **127**. ios-missing rose because rows
where Android now follows the decided PWA behaviour and iOS does not moved out of `misaligned`.

**57 %** of feature rows are aligned; **14 %** are missing on iOS; **18 %** still differ.

## 2. Decisions (resolved 2026-10-04)

All 92 answered by the user on 2026-10-04. Outcomes: D1a (lowercase "datawatch") · D2a · D3a/a ·
D4a (native chrome + PWA colours) · D5b (SF Symbols on iOS) · D6b · D7b (system font + Dynamic
Type) · D8a · D9a (lowercase) · D10a (eye + bolt overlay, matches live PWA) · D11a · D12a–D17a ·
D18a, D19a (match live PWA) · D20a · D21b · D22a–D30a · D31b (native grouped Settings, six PWA
groups as sections) · D32 (iOS-native density) · D33a · D34a · D35a · D36b · D37a–D45a · D46b ·
D47a–D49a · D50d · D51a · D52b (wire Pause/Resume everywhere) · D53b · D54b · D55a · D56b · D57b ·
D58b · **D59–D83 → all three platforms** (PWA side tracked in server issue #172; D68b) · D84b
(`datawatch://`) · D85a (App Intents/WidgetKit are parity, later) · D86c · D87b · D88c · D89b ·
D90a · D91a · D92a. Section files record the local → master mapping under "Decisions (resolved
2026-10-04)". The original option text is kept below for reference.

Source refs: `SS-Dn` = section file, decision n. "**PWA default**" marks the option that follows
the PWA. 102 section decisions → 92: eleven duplicates merged, one (03-D6) split.

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
shell. Status in brackets as of the 2026-10-04 re-audit (done = shipped, small leftovers noted). Chunks are ≈1–2 days. Refs are section files; feature names match the row's Feature cell.
Where a chunk touches a decision, it is noted — build the decided variant.

**Sessions list (+ New Session, which is the list's FAB)**
- **B1** [partial — form + FAB shipped; directory browser missing (08)] New Session surface + FAB — name, task, directory, profile/cluster, LLM picker + compute node, permission/model/effort, Chrome flag, recent-done restart, submit (`08` New Session rows; empty-state copy depends on D35).
- **B2** [partial — shipped; regression: historical state chip no longer auto-enables History (02)] History toggle + 5-min recent window, toolbar in empty state, select mode + bulk delete with confirm (`02`; UI variant per D15).
- **B3** [done — long-press drag outside edit mode unverified on device] Drag-to-reorder with persisted order + drag handle (`02`; see D42).
- **B4** [done] Card extras: ▶ quick-commands popup (waiting_input), "no change since last refresh", WS `session_state` single-row diff (`02`).
- **B5** [partial — filters + saved commands + i18n done; profile create/edit missing (08)] Session filters CRUD, saved-commands editor, kind profiles; localised list strings (`02`).

**Session detail**
- **B6** [done] State badge → state-override dropdown, delete dialog with memory strategy, timeline button + timeline (`03`, `08` state override).
- **B7** [partial — tabs + Channel done; chat-only sessions still show tab bar (03)] Tab bar per mode (channel / tmux-only / chat-only), Channel tab lines + history seed (`03`).
- **B8** [partial — status board done; tab badge dot, fetch-on-mount, last-5-events drill-down missing (03)] Status sub-tab: `/status` 5 s poll while open, hook-health pill, current focus, sprint/PRD tree, tests, git, guardrail verdicts (`03`).
- **B9** [done — "Open Compute Node / LLM →" links missing (low)] Stats sub-tab: envelopes poll, Host (donut + sparklines), Container, Compute Node, LLM cards (`03`).
- **B10** [partial — Fit + scroll mode done; console_cols, Line Up/Down missing (03)] Terminal: Fit to width, configured min cols/rows, scroll mode + strip, prepend backlog on open (`03`).
- **B11** [partial — schedule input, image attach, keys strip done; pending-schedules strip, C-c/C-b quick inputs, camera source missing (03)] Composer: pending-schedules strip + schedule popup, image attach + upload/transcribing banners, keys strip, sendkey quick inputs; i18n of detail copy (`03`).

**Automata**
- **B12** [partial — filter bar/pin/lifecycle done; All-servers scope, stories tree, type/template badges, left border missing (05)] List: all-servers aggregated scope, filter bar (status/type badges), template badge, lifecycle strip, pin, stories tree on card (`05`).
- **B13** [done] Batch mode: select toggle, batch bar (Run/Approve/Cancel/Archive/Delete), batch-delete confirm (`05`).
- **B14** [partial — wizard shipped; intent auto-detect, advanced toggles, Browse, template/skills links missing (05)] Launch wizard + ⚡ FAB: title, workspace/profile, execution backend/model/effort, planning backend + decomposition model (`05`).
- **B15** [done — built-in badge + use count missing (low)] Templates tab: cards, Use (instantiate) / edit / clone PRD → template, create/edit form (`05`).
- **B16** [partial — Archive and Pause/Resume (D52b) missing (05)] Detail actions: reset to draft, delete with memory strategy, edit title/spec, set LLM, run scan/rules, view sessions, settings panel, scope warnings (`05`).
- **B17** [partial — panels done; tab layout not yet D24a five tabs (05)] Detail panels: capacity, active-session card, status graphs, terminal-state hint, Decisions tab (`05`; tab layout per D24).
- **B18** [done — story profile/LLM controls minor (05)] Story/task ops: story approve/reject/cancel, task retry/cancel/requeue/edit/remove, planned/output file chips (`05`).
- **B19** [done — decompose SSE stream missing (low)] Live `prd_update` WS patching; orchestrator graphs + pipeline manager cards; i18n (`05`).

**Observer** (card order per D28, thresholds per D29, refresh per D54)
- **B20** [done] Per-system grid (local + peers), GPU temp thresholds, GPU-probe-failed card, network label, infrastructure card (`06`).
- **B21** [done] Stats-panel extras: RTK savings + update badge, episodic memory, `max_sessions` denominator, eBPF status + per-process network table, plugins list (`06`).
- **B22** [partial — "Cross-host view" missing (06)] Peer resources: per-peer metrics, parallel snapshots, 8 s refresh, group-by-node, dot colours, A/B/C shape badge, "attached to ComputeNode", cluster nodes (`06`).
- **B23** [done] Channel/comm: MCP bridge status, channel diagnostics, Matrix test, web-search stats + history (`06`).
- **B24** [done — dry-run maintenance per D89b] Memory browser + maintenance: stats cards, search/list/export, results list, eviction confirm (`06`; maintenance scope per D89).
- **B25** [done — federation "Test peer" missing (low)] Schedules, cooldown, session analytics, audit log, knowledge graph, daemon log, federation peers (`06`).

**Settings** (structure per D31)
- **B26** [done] Scaffold: tab persistence, notifications card, theme Dark/Light/System, language override (`07`).
- **B27** [partial — session templates Use/Edit, docs-search sources, discussion scopes read-only (07)] General: auto-update, session card (17 keys), docs search, session templates, device aliases, tooling lifecycle, file service, discussion scopes (`07`).
- **B28** [partial — federation peer form, channel routing read-only, web-search provider form, Tailscale auth key missing (07/08)] Comms: auth card, remote servers + federated peers, web server, MCP server, per-backend comm cards + Signal device linking, proxy resilience, routing rules, channel routing (`07`).
- **B29** [partial — LLM + compute forms done; profile edit, Ollama models/marketplace, kind-migration missing (07/08)] Compute: LLM registry, compute node add/edit, cost rates, cluster profiles, memory (18 keys), RTK, container workers, Tailscale, secrets store (`07`).
- **B30** [done] Detection filters, alert rules (CRUD), saved commands, output filters cards (`07`).
- **B31a** [partial — council runs/wizard, identity wizard, eval history missing (07/08)] Automata settings I: identity, algorithm mode, evals, council panel, project profiles (`07`).
- **B31b** [partial — guardrail profile create/edit, skill registry browse/sync missing; Exit Hooks + Work Queue cards missing (07)] Automata settings II: pipeline manager, orchestrator, guardrail library + profiles, pipelines config, skill registries, automata defaults (`07`).
- **B32** [done — App Store / release links minor] Plugins + About: plugin config/status, store/uptime/daemon status, orphaned tmux + kill all, update check + restart daemon, API links (`07`).

**Alerts** (read model per D49, dismiss per D48)
- **B33** [partial — multi-server aggregate missing (04)] Server picker, alert → open session, tab/filter persistence, sort toggle, by-session group cards with collapse (`04`).
- **B34** [done — quick-reply wording minor (04)] Quick reply on prompt alerts (saved commands), detection settle window, alert rules card + add form, i18n (`04`).

**Dashboard / post-spec / modals**
- **B35** [partial — confirm/forms done; alert dock as toast sink (D41a/D3a) missing (01/04/08)] Modal + toast infrastructure: confirm variants, generic modal, toast (4 types, stacking), backend-config popup, filter edit, remote-server forms, web-search provider form, peer snapshot modal (`08`; toast fate per D41).
- **B36** [done — rebuilt as PWA card grid (D34a)] Dashboard stat tiles + cards (constellation, sparklines, events, gantt, heatmap, guardrails, smoke) + add/edit/expand (`08`; **blocked on D34**).
- **B37** [partial — sessions tree, council live run, identity wizard, Ollama marketplace missing (08)] Post-spec views not covered above: orchestrator graphs view, guardrail verdicts inline, council live run, status board, sessions tree + parent link, algorithm mode, Ollama marketplace, identity wizard, compute telemetry/capacity (`08`; de-duplicate against B27–B31 when scheduling).

**Shell / tokens**
- **B38** [partial — splash art + replay done; tab gating, nav hidden in detail, identity-wizard button missing (01)] Splash scene (Earthrise artwork, text block, compact scene in About, eye breathe motion), identity-wizard button, tab gating on autonomous config, nav hidden in detail, Sessions FAB (`01`; gating per D37).
- **B39** [partial — update check, theme switching, locales done; alert-pill token pending with alert pill (01)] Daemon self-update check in UI, live system colour-scheme switching, alert-pill blue token, locales en/de/es/fr/ja + shell copy (`01`).

### Android work (`android-missing`)

- **01**: "Updated to vX" splash badge · light palette · reduced-motion support.
- **02**: History auto-enable from historical chip · tree view (BL348) · pending-schedules badge · waiting_input border pulse · stale-dot · ⛶ maximize → dashboard expand · server badge · parent badge · zombie badge · live elapsed clock · pull-to-refresh.
- **03**: rename toast · channel/acp "Waiting for…" banner · Status tab badge dot · channel help popup · parent session link · last-5-events-before-failure · send via channel `▶ ch` · collapsed earlier messages · chat quick-cmd bar · log-mode line classes · hold-to-repeat arrows · replay pending needs-input popup · status board fetch on mount.
- **05**: filter-bar text search · current-position line on card · running/planning pill pulse · wizard "Use template" link + skills · Archive action.
- **06**: GPU-probe-failed card · cross-host view · remove peer · pagination · pipelines live block · identity panel.
- **07**: certificate pinning (see D91) · profile-row security badges · exit hooks card · work queue card · branding/splash card.
- **08**: directory browser · compute kind-migration modal · channel help · debug panel (see D92) · project profiles + YAML editors · secrets vault card.

**Status 2026-10-04 (Android android-missing sweep):** every item above is implemented on
Android, or resolved without code: already present — history auto-enable, running/planning pill
pulse, schedules pagination, directory browser, kind-migration modal, secrets vault card,
certificate pinning (D91a); n/a — stale-dot (the PWA hides it), branding/splash card (removed
from the PWA in v6.12.0), debug panel (D92a); partial — project profiles are form editors, no
raw-YAML editor. The section tables carry the per-row Android status. 06 per-card docs links
(D26a) and collapsible Observer/Settings cards (D27a) landed in 5a209324. **2026-10-04 re-audit:**
the sweep above is complete, but decided-PWA behaviour Android does not yet follow (D2a picker
bar, D14a inline status, D24a tabs, D28a/D29a/D54b/D55a/D89b Observer, D38a/D40a/D84b/D86c shell)
is now tracked as `android-missing` / `misaligned` rows — see *Remaining work* at the top.

### Spec drift (fix `docs/plans/2026-05-12-pwa-full-spec.md`, no decision)

- Removed from live PWA: §1.6 version-staleness reload; §4.15 generating indicator (alpha.29); `rate_limited` in §10.3 state-override options (live: running, waiting_input, complete, killed, failed). §6.6 Pause/Resume is defined but unreachable (D52).
- Missing from spec: §9 Chrome integration checkbox; post-spec views — Dashboard, Orchestrator graphs, guardrail verdicts + library, council live runs, status board, sessions tree, discussion scopes, algorithm mode, Ollama marketplace, profiles/cluster editors, web search stats/providers, identity wizard, Tailscale, skills registry, secrets vault, compute telemetry/capacity.

## 4. Cross-cutting themes

- **Design tokens from one source.** Colours, type and motion differ in small ways everywhere (accent, alert pill, PRD pills, thresholds); generate Kotlin + Swift tokens from the PWA CSS variables once D6/D7/D23/D29 are decided.
- **i18n.** PWA ships 5 locales via `t()`; Android 1,363 strings × 4 extra locales; iOS now ships de/es/fr/ja `.lproj` (~1,379 keys) — new iOS work must land with string keys, not literals.
- **WS-first data.** PWA and Android consume `sessions`, `session_state`, `stats`, `alert`, `prd_update` frames; iOS now consumes `prd_update` too — the WS `alert` frame (D51a) is the remaining iOS gap.
- **iOS alert dock** (D3a/D41a/D47a/D51a) is now the single biggest cross-section gap: pill, dock panel, mute, WS alerts and the toast sink all hang off it.
- **"App had a better idea" backlog.** D59–D83 sent every app-only extra to all three; the PWA half is server issue #172 (50 `pwa-missing` rows, most tagged → #172).
- **Motion and splash** decided (D10a, D18a, D23a, D36b, D37a); remaining work is iOS PRD pill pulse/colours and splash dwell timing.
- **Alert semantics** decided (D47a–D51a); Android follows them, iOS still has per-alert read UI and no dock.
- **Settings** is now mostly covered on iOS via the generic config/list card renderer; what remains is CRUD depth on list cards (07 `~` rows).
