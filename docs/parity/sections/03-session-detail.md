# 03 — Session detail

PWA spec §4 · live `app.js` `renderSessionDetail` (2700–3137), terminal 3138–3575, stats/status 3576–4330, input/keys 4435–4530, voice 4713–5010, saved cmds 5259–5420, rename 1929/5243, response viewer 14890, state override 14843.
Android: `ui/sessions/SessionDetailScreen.kt` (SDS), `SessionDetailViewModel.kt` (SDVM), `TerminalView.kt` (TV), `TerminalToolbar.kt` (TT), `SessionStatsPanel.kt` (SSP), `SessionStatusPanel.kt` (SStP), `ChatTranscriptPanel.kt` (CTP), `SessionLoadingOverlay.kt` (SLO), `assets/xterm/host.html` (HH).
iOS: `screens/sessions/SessionDetailView.swift` (SDV), `SessionDetailExtras.swift` (SDX), `SessionOpsSheets.swift`, `SessionStatusView.swift`, `SessionStatsView.swift`, `ChatTranscriptView.swift`, `ChannelTabView.swift`, `TerminalView.swift` (iTV), `VoiceRecorder.swift`, `components/SessionLoadingOverlay.swift` (iSLO), `components/ConnectionStatusBanner.swift` (CSB — mounted only on the sessions list).

Re-audited 2026-10-04 against current code after the user's decisions (master §2).

## 3.1 Header, info bar, session actions

| Cat | Feature | PWA | Android | iOS | Status | Decision | Notes |
|---|---|---|---|---|---|---|---|
| nav | Header title = `name`, else `task`; nav bar + FAB hidden; back shown | ✓ 2700 | ✓ SDS:295 TopAppBar | ✓ SDV:47 | aligned | | |
| interaction | Tap title → inline rename (Enter/blur confirm, Esc cancel) | ✓ startHeaderRename 1929 → POST /api/sessions/rename 5243 | ~ SDS RenameDialog (AlertDialog) | ~ SDV `.alert("Rename session")` w/ TextField | aligned | | Native dialog per D4a (iOS/Android controls); copy "Rename session"/"Display name" matches |
| string | Rename success/fail toast "Session renamed"/"Rename failed" | ✓ 5276 | ✓ SDVM session_renamed / session_rename_failed → alert dock (D41a) | ✓ SDV `performRename` → dock "Session renamed" / "Rename failed: …" (D41a); title updates | aligned | | iOS gives no feedback either way |
| element | Backend badge (lowercase backend) | ✓ 2934 backendText | ✓ SDS:1119 InfoBadge primary | ✓ SDV:152 primary | aligned | | |
| element | LLM badge `⚡ llm_ref` green bordered | ✓ 2934 | ✓ SDS:1123 #10B981 | ✓ SDV:156 success | aligned | | |
| element | Compute node badge `⚙ ref` purple | ✓ 2935 #a855f7 | ✓ SDS SessionInfoBar DwAccent #7C3AED | ✓ SDV metaBadge `primary` (#7C3AED / light #2563EB) | aligned | per D6b | PWA badge uses var(--accent) = #7C3AED dark (fallback #a855f7); Android hard-codes 8B5CF6 (D6b), iOS uses accent2 |
| element | Mode badge only for non-tmux modes (channel/chat/acp) | ✓ renderSessionDetail: badge only when sessionMode == tmux | ✓ SessionInfoBar shows only when mode == tmux | ✓ SDV `infoBadges` "tmux" badge only when `SessionMode.of == tmux` | misaligned | decided D17a | iOS still on the old inverse rule · D17a done on iOS |
| element | Agent badge `⬡ agent_id` | ✗ (⬡ worker badge on list card only, app.js renderSessionCard) | ✓ `SessionHeaderBadge` ⬡ agent | ✓ SDV metadataBar `⬡ agentId` | pwa-missing | decided D66a | Android also missing · → #172 · Android also missing · → #172 · Android done (D66a) (2026-10-04) |
| element | "Chrome" badge when `sess.chrome` | ✗ | ✓ accent2 "Chrome" badge | ✓ SDV metadataBar "Chrome" badge | pwa-missing | decided D66a | Android shows it as subtitle text · → #172 · Android shows it as subtitle text · → #172 · Android done (D66a) (2026-10-04) |
| interaction | State badge clickable → state override | ✓ 2941 showStateOverride 14843 | ✓ SDS:1156 DropdownMenu on pill; StateOverrideDialog 2578 | ✓ SDV `stateMenu` Menu on state pill → IosSessionOps.overrideState | aligned | | |
| element | State override options running / waiting_input / complete / error / killed | ✓ 14843 | ✓ PWA five (running / waiting_input / complete / killed / failed) | ✓ SDV `overrideStates` (running / waiting_input / complete / killed / failed) | misaligned | | PWA wire list is running/waiting_input/complete/killed/failed; Android offers 7 |
| motion | Running state pill pulses alpha (static for waiting/rate_limited) | ✓ style.css `.state-badge-running` `dw-running-pulse` 700 ms .55↔1.0 | ✓ SessionInfoBar `rememberRunningPulseAlpha` (PWA timing, reduced-motion aware) | ✓ SDV `stateMenu` `RunningPulse` 700 ms .55↔1.0, Reduce Motion aware | aligned | decided D18a | iOS done 2026-10-04 |
| element | Last-activity age chip (`session-last-activity`) for active sessions | ✓ 2942 + `updateLastActivityClocks` 15510: text2 age text + dot coloured green <30 s / amber <5 min / red | ✓ SessionInfoBar last-activity dot + age, 1 s tick | ✓ SDV `LastActivityIndicator` (dot <30 s / <5 min / red + age, 1 s tick; active only) | aligned | decided D19a | |
| interaction | `■ Stop` (active) · `↻ Restart` + `🗑 Delete` (done), left of state pill | ✓ 2918–2920 btn-stop/restart/delete | ✓ SessionInfoBar same glyphs, left of the pill | ✓ `SessionActionButtons` in the info bar: ■ Stop (active) · ↻ Restart + 🗑 Delete (done) | aligned | | iOS actions belong in the info bar with glyph+label |
| string | Stop wording | "Stop session" / `■ Stop` | ✓ `■ Stop` bar; dialog "Stop session?" / Stop (all locales) | ✓ "Stop session?" / Stop, a11y "■ Stop" | aligned | decided D44a | iOS still says Kill · D44a done on iOS |
| interaction | Stop/Restart/Delete confirmation modals | ✓ killSession 4391 / deleteSession 5406 (showConfirmModal) | ✓ SDS killConfirm/deleteConfirm | ✓ SDV kill alert + `SessionDeleteSheet` | aligned | | |
| element | Delete dialog: memory strategy Keep/Purge/Archive + role filter (#199) | ~ 5406 (verify fields) | ✓ SDS:123 deleteMemoryStrategy/Roles | ✓ SessionOpsSheets `SessionDeleteSheet` (Keep/Purge/Archive + role filter + scope) | aligned | | PWA picker confirmed in app.js deleteSession (memory_strategy, role filter, archive scope) |
| interaction | Timeline button 🕐 (right cluster, first) | ✓ 2949 toggleSessionTimeline | ✓ SDS:1243 | ✓ SDV toolbar `clock` button (first in trailing cluster) | aligned | | |
| interaction | Response button 📄 (right cluster, second; only when last_response) | ✓ 2950 (always) | ✓ always shown in SessionInfoBar | ✓ info bar 📄 always shown (fresh fetch) | misaligned | decided D43a | PWA always shows; apps hide when empty · iOS follows PWA (always) · PWA always shows; apps hide when empty · Android done (D43a); iOS still hides when empty (2026-10-04) |
| interaction | Watch/unwatch toggle (session_watch_on/off) in top bar | ✗ | ✓ SDS isWatched toggle | ✓ SDV `watchButton` (bell in top bar) | pwa-missing | decided D61a | → #172 |
| interaction | Docs link button in top bar | ✗ (global help) | ✓ SDS `DocsLinkAction` | ✓ SDV `DocsLinkButton(anchor: "sessions")` | pwa-missing | | App-wide convention, see §01; no decision |
| string | Hooks-installed toast for claude-code sessions | ✗ | ✓ SDS status_hooks_installed_toast | ✓ SDV `applyDetailExtras` one-time `SessionToast` | pwa-missing | decided D67a | → #172 |
| token | Info bar: bg2, padding 10px 14px, bottom border | ✓ style.css:2977 | ✓ SessionInfoBar Surface | ~ SDV metadataBar surface, 12/6 padding | aligned | | Native metrics per D4a/D32; colours match |
| token | Pill buttons: 10px, radius 10, 1px border, text2 | ✓ style.css:2538 | ~ SDS labelSmall TextButton | ~ SDV badge capsule | aligned | | Native controls per D4a; token sheet in §01 |

## 3.2 Banners, strips, overlays

| Cat | Feature | PWA | Android | iOS | Status | Decision | Notes |
|---|---|---|---|---|---|---|---|
| element | Pending schedules strip (`/api/schedules?session_id&state=pending`), per-item time + ✕ cancel | ✓ loadSessionSchedules 3654 | ✓ SDS `SessionSchedulesStrip` + SessionSchedulesViewModel | ✓ `PendingSchedulesStrip` (`IosSessionComposer.pendingSchedules`, ✕ cancel, reload after schedule sheet) | aligned | | |
| element | Process stats bar (CPU/RAM/Threads/FDs/Net/GPU) above output, 5 s poll of /api/stats envelopes | ✓ fetchStats setInterval 5000 | ✓ `ProcessStatsBar` above the output, 5 s poll of /api/stats envelopes | ✓ `InlineProcessStatsBar` CPU/RAM/Threads/FDs/Net/GPU, 5 s poll (IosSessionStats) | aligned | decided D45a | Android also missing · iOS done (D45a); Android still missing · Android also missing · Android done (D45a) (2026-10-04) |
| element | Connection banner for channel/acp: "Waiting for {mode}… [— answer the input prompt below first] ✕" | ✓ 2783–2811 dismissConnBanner 894 | ✓ SDS `ConnStatusBanner` until `channel_ready`, waiting note, ✕ dismiss | ✓ `ChannelConnectionBanner` until `channel_ready`, waiting note, ✕ dismiss | aligned | | iOS CSB is a list-level reachability banner, not this |
| element | Server-unreachable banner "terminal stream paused, last frame shown" (OFFLINE_GRACE 3.5 s debounce) | ✗ (WS status dot global) | ✗ removed (header reachability dot only) | ✓ no overlay — transport auto-reconnects, last frame stays, header status dot (D46b); long-press dot resubscribes | misaligned | decided D46b | iOS still blocks the terminal on disconnect · D46b done on iOS |
| element | Rate-limit inline notice (yellow #FEF3C7/#92400E, dismissible, retry-at) | ✗ (state badge only) | ✓ SDS InlineNotices | ✓ SDX `RateLimitNotice` (state + live `rate_limited` events, retry-at) | pwa-missing | decided D67a | → #172 |
| element | Needs-input: yellow `.input-bar.needs-input` border (banner removed v6.13.9) | ✓ style.css `.input-bar.needs-input` border var(--waiting) blue + rgba(59,130,246,.05) | ✓ composer waiting tint | ✓ SDV composerBar 2px `waiting` rule + waiting @8 % field | aligned | | PWA CSS comments say "yellow" but the rule uses --waiting (blue) (Android colour unverified) |
| motion | Connecting splash until first pane_capture: favicon 64px @0.3 + "CONNECTING TO SESSION…" (#00E5A0, 12px, 600, ls 2px) | ✓ `DWSplashArt.startSessionLoading` eye + bolt canvas | ✓ SLO EyeOnlyAnimated + bolt | ✓ iSLO `SplashEyeView(bolt: true)` + "datawatch" + status (static under Reduce Motion) | aligned | decided D10a | Feature text above (favicon/uppercase) is the pre-D10 PWA |
| motion | Splash min/max dwell (new session 2 s/15 s; existing 0.5 s/8 s) | ✗ (watchdog only) | ✓ SDS min 2 s/0.5 s, max 15 s/8 s | ✗ iTV hides overlay on first frame | pwa-missing | decided D10a | Part of the Android splash adopted everywhere; iOS also missing · → #172 |
| motion | Connect watchdog: retry every 5 s ×3 → "Unable to connect…" + Retry + "Use without terminal" | ✓ MAX_RETRIES 3 | ✓ 5 s × 3 re-subscribe → "Unable to connect…" + Retry + Use without terminal | ~ iTV Reconnect overlay only on Error | ios-missing | | PWA drives; iOS partial (no auto-retry / "Use without terminal") · Android done (2026-10-04) |
| string | Splash stages "connecting…" → "waiting for terminal…" | ~ single uppercase string | ✓ SDS | ✓ iTV "connecting…" → "waiting for terminal…" | pwa-missing | decided D10a | Apps show two stages · → #172 |
| motion | Generating indicator (3 dots, 600 ms alternate fade, 200/400 ms delays) | ✗ removed alpha.29 (927 no-op; CSS 2395 orphaned) | ✗ | ✗ | aligned | | Spec §4.15 is stale |

## 3.3 Output tab bar & modes

| Cat | Feature | PWA | Android | iOS | Status | Decision | Notes |
|---|---|---|---|---|---|---|---|
| element | Channel-mode tabs: Tmux/Chat · Channel · Status(+badge dot) | ✓ 2866–2872 | ✓ SDS:2609 SessionModeTab (tmux/channel/Status) | ✓ Tmux/Chat · Channel · Status (claude backends) | aligned | | |
| element | Tmux-only tabs: Tmux · Status | ✓ 2900 | ✓ | ✓ SDV `detailTabs` (Tmux · Status for non-channel backends) | aligned | | |
| element | Chat-only sessions: no tab bar, `#chatArea` | ✓ 2897 | ✓ SDS serverChatMode + CTP | ✓ SDV hides the output tab bar when `outputMode == chat` | aligned | | |
| string | Tab labels "Tmux"/"Chat"/"Channel"/"Status" | ✓ t() keys | ✓ session_detail_tab_tmux/_channel "Tmux"/"Channel" (de Kanal, es/fr Canal, ja チャンネル per PWA locales) | ✓ SDV `detailTabs` "Tmux"/"Chat"/"Channel"/"Status" | aligned |  | Android copy aligned 2026-10-04 |
| element | Status tab badge dot (`tabStatusBadge`, from /api/sessions/{id}/status on mount) | ✓ 2870, 3109, updateSessionStatusBadge 4221 | ✓ SDS Status tab hook-health ● + `statusTabBadge` | ✓ SDV `statusTabBadge` (● hook health + 🟢/🟠/⚪), fetched on mount + every 10 s | aligned | | |
| element | `?` channel help popup ("Channel Commands") when Channel tab active | ✓ 2873 showChannelHelp 4525 | ✓ SDS `?` → `ChannelHelpDialog` while Channel tab active | ✓ SDV `?` (questionmark.circle) in the tab bar while Channel is active → `ChannelHelpSheet` (PWA copy) | aligned | | iOS done 2026-10-04 |
| element | Font control `Aa ▾` dropdown (A−, size, A+, Fit) in tab bar right | ✓ 2839–2855 | ✓ TT `Aa▾` DropdownMenu | ✓ `TerminalFontMenu` Aa▾ (Fit + 5–20 px) in the tab bar right | aligned | decided D20a | iOS still on the permanent row · D20a done on iOS |
| element | Scroll-mode button `⤒` (U+2912 18px bold) / `⏹` exit | ✓ 2854 toggleScrollMode 3238 | ✓ TT:160 | ✓ SDV `terminalFontBar` ⤒ / ⏹ 18pt bold → `toggleScrollMode` | aligned | | |
| nav | Mode preference persisted (Terminal default, Chat remembered) | ✗ (per render) | ✓ SDS modePrefs chat_mode | ✓ SDV `savedDetailTab` (`dw.session.detail.tab`) | pwa-missing | decided D67a | iOS remembers the output tab (no Terminal/Chat toggle) · → #172 |
| nav | Deep-link open in Status mode (`openInStatusMode`) | ✗ | ✓ SDS `openInStatusMode` | ✗ | pwa-missing | | Used by alerts → status; no decision; iOS also lacks it |

## 3.4 Status sub-tab (board)

| Cat | Feature | PWA | Android | iOS | Status | Decision | Notes |
|---|---|---|---|---|---|---|---|
| element | Status/Stats sub-tab strip (blue underline active) | ✓ 2878–2886 switchStatusSubtab | ✓ SDS:147 statusSubStats | ✓ SDV `statusSubtabStrip` (waiting-blue underline) | aligned | | |
| data | GET /api/sessions/{id}/status, 5 s while tab open | ✓ 4100 setInterval | ✓ SessionStatusViewModel | ✓ SessionStatusView 5 s poll while visible (IosSessionStatus) | aligned | | |
| element | Hook health pill alive/stale/missing, click re-polls, Docs ↗ when not alive | ✓ 4284–4290 | ✓ SStP:134 HookHealthPill (docs → hooks docs) | ✓ SessionStatusView `hookHealth` (tap ● re-polls, "Set up" docs link when not alive) | aligned | | |
| string | "● hooks alive / hooks stale / no hooks installed" | ✓ | ✓ status_hooks_* "hooks alive / hooks stale / no hooks installed" (PWA locale copy) | ✓ SessionStatusView "hooks alive / hooks stale / no hooks installed" | aligned |  | Android copy aligned 2026-10-04 |
| element | Current focus card (task, last event, idle_since) + empty "No hook events received yet." | ✓ 4304–4309 | ✓ SStP focus card; no last event → italic `status_no_events_yet` copy ("No hook events received yet. Hooks auto-install…") | ✓ SessionStatusView `focusBody` ("No hook events received yet.") | aligned |  | Android uses the full PWA locale string; iOS the short fallback · Android copy aligned 2026-10-04 |
| element | Sprint / PRD tree card (JSON pre) → live "Live Task Tree" / "Sprint / Automata" | ✓ 4312, 4339–4342 | ✓ SStP:233 SprintCard + 361 Task Tree + 450 breadcrumb | ✓ SessionStatusView `sprintBody` (breadcrumb, Live Task Tree / Sprint / Automata) | aligned | | |
| element | Tests card pass/fail(/skip) | ✓ 4317 | ✓ SStP:273 | ✓ SessionStatusView `testsBody` | aligned | | |
| element | Git card branch + dirty (+ahead) | ✓ 4324 | ✓ SStP:293 | ✓ SessionStatusView `gitBody` | aligned | | |
| element | Guardrail verdicts card (+ run guardrail POST /guardrail, "Approved") | ✓ 4281, 4431–4440 | ✓ GuardrailVerdictsCard: approve on blocked + ▶ sast/secrets/deps run chips | ✓ `GuardrailVerdictsBody`: approve on blocked (✓ once approved) + ▶ sast/secrets/deps run chips, results to the dock | aligned | | Android done (2026-10-04) · iOS done 2026-10-04 |
| element | Parent session link (telemetry) | ✓ renderParentSessionLink 4328 | ✓ SStP status_parent_session link → opens parent | ✓ SDV info bar `↑ parent` badge → opens parent | aligned | | iOS placement: info bar, not status tab (2026-10-04) |
| string | "Last 5 events before failure" | ✓ 4458 | ✓ SStP `FailedDrilldown` (last 5 of `failed_task_buf`) | ✓ SessionStatusView `FailedDrilldownView` under failed tasks (last 5 of `failed_task_buf`) | aligned | | iOS done 2026-10-04 |

## 3.5 Stats sub-tab (cards)

| Cat | Feature | PWA | Android | iOS | Status | Decision | Notes |
|---|---|---|---|---|---|---|---|
| data | /api/observer/envelopes (+ /api/compute/nodes/{id}) 5 s while open | ✓ 3865–3880 | ✓ SessionStatsViewModel + SSP:66 | ✓ SessionStatsView 5 s poll while visible (IosSessionStats) | aligned | | |
| element | Host card: CPU donut 60px (green/amber/red at 70/90) | ✓ 3942–3963 | ✓ SSP:227–258 CircularProgressIndicator | ✓ SessionStatsView `Donut` 60pt, 70/90 thresholds | aligned | | |
| element | CPU % + 80×18 sparkline (60 pts); RSS + sparkline; Threads/FDs/PID(+N) when >0 | ✓ 3909–3977 | ✓ SSP:262–288 | ✓ SessionStatsView `metricRow` 80×18 sparklines (60 pts), Threads/FDs/PID(+N) | aligned | | |
| element | Net ↓/↑ bytes/s when non-zero; GPU %/mem | ✓ | ✓ SSP:294–302 | ✓ SessionStatsView hostCard Net / GPU rows | aligned | | |
| string | Card title "Process Stats"/"Backend Stats" | ✓ 3885/3888 | ✓ "Process Stats" (no envelope) / "Backend Stats — {label}" (PWA locale titles) | ~ SessionStatsView card("Host") | ios-missing |  | iOS still says Host · Android copy aligned 2026-10-04 |
| element | Container card (ID 12, Image, Runtime) when container present | ✓ 3989 | ✓ SSP:109–121 | ✓ SessionStatsView Container card | aligned | | |
| element | Compute Node card: Node CPU/Mem, GPU util/temp/power/VRAM, Ollama CPU/RSS, "Open Compute Node →" | ✓ 4012–4054 | ✓ SSP:128 + gpu util/temp sparklines | ~ SessionStatsView `computeNodeCard` (Node CPU/Mem, GPU util/temp sparklines, power, VRAM, Ollama); no "Open Compute Node →" | misaligned | | Apps add GPU sparklines (pwa-missing detail); iOS lacks the open link |
| element | LLM card: ref, note, "Open LLM →" | ✓ 4061–4064 | ✓ SSP:145 (falls back to backend name) | ~ SessionStatsView LLM card (ref, backend fallback); no note, no "Open LLM →" | misaligned | | |
| string | No-envelope text "No process envelope yet — observer plugin off…" | ✓ | ✓ session_detail_stats_no_data = PWA `session_stats_no_envelope_body` ("The observer hasn't attributed a process tree…") | ~ "No process stats for this session yet." | ios-missing |  | iOS copy differs · Android copy aligned 2026-10-04 |

## 3.6 Channel / Chat / Log modes

| Cat | Feature | PWA | Android | iOS | Status | Decision | Notes |
|---|---|---|---|---|---|---|---|
| element | Channel tab lines → / ← / ⚡ with classes send/reply/notify | ✓ 2760–2765; css 3031–3039 | ✓ SDS EventList/EventRow 1847 (verify glyphs) | ✓ ChannelTabView → / ← / ⚡ | aligned | | |
| data | Seed from /api/channel/history?session_id (dedupe, 1000 cap) | ✓ 2718 | ✓ (SDVM history) | ✓ IosChannel.history + ChannelHub | aligned | | |
| interaction | Send via channel `▶ ch` (POST /api/channel/send) vs tmux `▶` switching with active tab | ✓ 2920–2924, 4565 | ✓ composer `▶ ch` → `sendChannelMessage` on Channel tab (not waiting) | ✓ composer `▶ ch` → `IosSessionComposer.sendChannel` on the Channel tab (not waiting) | aligned | | |
| element | Chat bubbles: avatar U/AI/S, role label, time, radius 12, 13px; user #3b82f6, assistant #10b981, system #64748b | ✓ 3015–3045; css 3055–3144 | ~ CTP: U/AI, primaryContainer / surfaceVariant | ~ ChatTranscriptView bubbles (user primary @25 %, assistant surface, radius 12); no avatar/role label/time | misaligned | | Apps use theme colours, not PWA chat palette |
| element | Collapsed "N earlier messages" `<details>` when > 6 | ✓ 3023 | ✓ CTP collapses all but last 4 when > 6 | ✗ | ios-missing | | |
| element | Chat empty state 💬 + "Send a message to begin…" + memory hint | ✓ 3057–3061 | ✓ CTP + chat-view empty: 💬 (.3) + "Send a message to begin the conversation" + memory hint | ✓ ChatTranscriptView 💬 "Send a message to begin the conversation" + memory hint | aligned | decided D68b | Yes/No/Stop chips on prompts tracked below · Android copy aligned 2026-10-04 |
| element | Chat quick-cmd bar: 📚 memories · 🔍 recall · 🔗 kg query · 🔬 research | ✓ 3050–3055 chatQuickCmd 25201 | ✓ 📚/🔍/🔗/🔬 bar prefills composer | ✓ SDX `ChatMemoryCmdBar` above keys strip (chat mode) | aligned | decided D68b | |
| element | Chat markdown (code blocks, inline code, thinking `<details>`, images, mermaid), streaming bubble | ✓ renderChatMarkdown 1245; css 3249–3331 | ~ CTP streaming bubble, plain text (no markdown) | ~ ChatTranscriptView inline-only AttributedString markdown + streaming bubble; no code blocks / thinking / images / mermaid | misaligned | | |
| element | Log mode lines with acp-status/processing/ready/error classes | ✓ 3072–3085 | ✓ SDS `LogModeView` for output_mode=log, PWA class colours | ✓ SDV `SessionLogView` for output_mode=log (ANSI-stripped, blank lines dropped, PWA class colours; input over the session socket) | aligned | | iOS done 2026-10-04 |

## 3.7 Terminal (xterm)

| Cat | Feature | PWA | Android | iOS | Status | Decision | Notes |
|---|---|---|---|---|---|---|---|
| data | Render from `pane_capture` only; input via `send_input`/`command` on /ws | ✓ 3383+ | ✓ TV:674 + WsOutbound | ✓ iTV (shared host.html) | aligned | | iOS fixed v1.23.115 |
| data | `resize_term` on cols/rows change (200 ms debounce) | ✓ 3489, 3528 | ✓ TV onResize → WsOutbound.sendResizeTerm; SDVM:345 | ✓ iTV bridge resize | aligned | | |
| token | xterm theme bg #0f1117 fg #e2e8f0 cursor #a855f7 selection rgba(168,85,247,.3), scrollback 5000, cursorBlink | ✓ 3511–3534 | ✓ HH:115–143 | ✓ (HH) | aligned | | |
| token | Font family: 'JetBrains Mono','Fira Code' | ✓ 3505 | ✓ HH bundled JetBrains Mono (xterm/fonts) | ✓ (HH, shared assets) | aligned | per D8a | done 2026-10-04: v2.304 bundled; monthly font-update.yml keeps it current |
| token | Default font 9px, persisted (`cs_term_font_size` / prefs / UserDefaults) | ✓ 3480 | ✓ TT:91 | ✓ SDV:20 | aligned | | |
| interaction | A−/A+ clamp 5..20 | ✓ changeTermFontSize 3180 (clamp?) | ✓ TT MIN/MAX | ✓ SDV:201/221 | aligned | | Verify PWA clamp |
| interaction | Fit to width (shrink font until no horizontal overflow) | ✓ termFitToWidth 3196 | ✓ HH dwAutoFitToWidth, TT "Fit" | ✓ SDV "Fit" → TerminalController.fitToWidth (dwAutoFitToWidth) | aligned | | |
| interaction | Configured min cols/rows (claude 120) honoured; Settings "Terminal dimensions" card | ✓ 3496–3498 configCols | ✓ TV setMinSize + TerminalDimensionsCard | ~ `TerminalController.defaultMinCols(backend)` (claude 120 / 80); Settings has `session.console_cols` but the terminal doesn't read it | misaligned | | |
| motion | Keyboard-open refit: explicit height + rAF, second pass 350 ms | ✓ 3542–3579 | ✓ HH:357 350 ms; onSizeChanged → dwExplicitSize; safeFit 50/200/600/1200/2500 ms | ✓ iTV onLayout → dwExplicitSize | aligned | | |
| interaction | Scroll mode: tmux-copy-mode, 700 ms pending-refresh window, Esc exits, button swaps to exit | ✓ 3238–3345 | ✓ TT + HH dwSetScrollMode/dwScrollPendingRefresh | ✓ SDV `toggleScrollMode` (tmux-copy-mode, 700 ms dwScrollPendingRefresh, ⏹ exit) | aligned | | |
| element | Scroll-mode strip: Page Up/Down, ESC | ✓ `.scroll-bar-active` app.js:3280–3282 (▲ Page Up · ▼ Page Down · ESC — Exit Scroll) | ✓ TT TerminalScrollModeStrip (Page Up · Page Down · ESC / Exit) | ✓ SDV `scrollStrip` ▲ Page Up / ▼ Page Down / ESC — Exit Scroll | aligned | | Re-checked 2026-10-04: no client has Line Up/Down (PWA strip is Page Up/Down + ESC; TT's "Line Up/Down" was only a stale doc comment) — nothing to add on iOS |
| interaction | Interactive keyboard: xterm onData → sendkey/send_input | ✓ 3545 | ✓ HH onData → DwBridge.onInput | ✓ iTV onInput | aligned | | |
| interaction | Samsung/IME spurious-Enter suppression (150 ms window), composing-text tracking | n/a | ✓ TV:129–232 | n/a | n/a | | Android-specific IME |
| interaction | Pinch-zoom WebView as escape hatch for 80-col TUIs | n/a | ✓ TV:558 | ✗ | n/a | | iOS scrollView zoom disabled |
| interaction | Terminal search (next/prev/clear) via search addon | ✗ | ✓ 🔍 toggle → `TerminalSearchBar` (▲ ▼ 📋 ✕) | ✓ SDX `TerminalSearchBar` (dwSearchNext/Prev/Clear) | pwa-missing | decided D69a | Android UI still dormant · → #172 · Android done (D69a) (2026-10-04) |
| interaction | Copy selection to clipboard | ✗ (browser native) | ✓ 📋 in TerminalSearchBar → dwCopySelection | ✓ SDX `TerminalSearchBar` copy: dwCopySelection, else visible rows | n/a | decided D69a | Native selection on web; Android controller only (ties to search UI) · Android UI done (D69a) (2026-10-04) |
| interaction | Prepend backlog on open (`dwPrependBacklog`) | ~ 3523 bufferedLines | ~ TV:373 `prepend()` defined but never called | ✗ | misaligned | | Not live on Android either (verified 2026-10-04); iOS gets live frames only |
| element | DATAWATCH_COMPLETE marker lines filtered from captures | ✓ app.js ~609 | ✓ HH:519 | ✓ (HH) | aligned | | |
| element | Terminal exit: black-screen-safe WebView teardown | n/a | ✓ TV:694–705 | ✓ iTV dismantleUIView | n/a | | |

## 3.8 Input bar, keys, saved commands, voice, attachments

| Cat | Feature | PWA | Android | iOS | Status | Decision | Notes |
|---|---|---|---|---|---|---|---|
| element | Input bar shown only when active && input_mode != none | ✓ 2965 | ✓ ReplyComposer gating | ✓ SDV `inputAllowed` (active && input_mode != none) | misaligned | | iOS ignores input_mode |
| string | Placeholder: "Waiting for connection…" / "Type your response…" / "Send message…" / "Send command or input…" | ✓ 2973 | ✓ PWA copy (waiting / response / message / command) | ~ SDV `composerPlaceholder` PWA copy; no "Waiting for connection…" | misaligned | | Android copy differs — PWA drives · Android copy differs — PWA drives · Android done; iOS lacks "Waiting for connection…" (2026-10-04) |
| interaction | Enter sends (not Shift+Enter); empty input sends Enter key | ✓ 4461–4475 | ✓ (send "\r") | ✓ send button always enabled; empty input sends "\r" | misaligned | | iOS blocks empty send; PWA sends Enter |
| interaction | Send via `send_input` when running/waiting/rate_limited else `command send` | ✓ 4480–4484 | ✓ WsOutbound.sendInput | ✓ sendInput | aligned | | |
| element | Schedule-input button 🕐 → popup (command, when) | ✓ 2925 showScheduleInputPopup 3681 | ✓ ReplyComposer:2747 Icons.Schedule → scheduleOpen | ✓ SDV `clock.badge` → ScheduleInputSheet (command, when, cron) | aligned | | |
| element | Voice button 🎙 (hold-to-record / click toggle) when whisper enabled | ✓ 2926 | ✓ ReplyComposer:2787 | ✓ SDV composerBar mic (whisperEnabled) | aligned | | |
| element | Recording modal: waveform, "Recording…", Cancel / Send | ✓ _showVoiceRecordingModal 4713 (voice-waveform) | ~ ReplyComposer dialog | ~ SDV `recordingOverlay` mic pulse 0.6 s, Cancel/Send | misaligned | | No waveform on apps |
| string | Transcribing state: placeholder "Transcribing…" + banner "Transcribing voice message…", toast "✓ Transcribed (n chars)" | ✓ 4776–4800 | ✓ ReplyComposer:2697/2714 | ✓ banner + placeholder + ✓ Transcribed note | aligned | | |
| data | POST /api/voice/transcribe (webm/ogg/mp4) | ✓ 4787 | ✓ transcribeAudio | ✓ transcribeAudioData (audio/mp4) | aligned | | |
| element | Image attach 📷 (gallery / camera), upload chip "Uploading…/✓ name", `[image:path]` appended | ✓ 2927 sessionImageInput; "Wait for image upload" | ✓ ReplyComposer:2274–2370, 2608, 2795–2860 | ~ SDV PhotosPicker (gallery only, no camera) → upload banner "Uploading image…/✓ name", `[image:path]` appended, "Wait for image upload" guard | misaligned | | |
| element | Keys strip: ␛ · ↑ ↓ ← → · ⏎ right-aligned | ✓ 2957–2964 | ✓ ReplyComposer:2527–2605 (icons) | ✓ SDV `keysStrip` ␛ · ↑ ↓ ← → · ⏎ right-aligned (sendKey) | aligned | | |
| interaction | Hold-to-repeat arrows (250 ms delay, 80 ms interval) | ✓ startArrowRepeat 4440 | ✓ `RepeatArrowButton` 250 ms / 80 ms | ✓ `KeyGlyphButton` 250 ms / 80 ms | aligned | | |
| element | Saved commands: dropdown `<select>` with system set (approve/reject/enter/continue/skip/abort/ESC/Ctrl-b/quit) + user `/api/commands`, custom command input | ✓ loadSavedCmdsQuick 5259, sendCustomCmd 5361 | ✓ "Commands…" dropdown (System · Saved · Custom…) + inline custom row; Guardrails group omitted | ✓ `SavedCommandsRow` "Commands…" Menu (System · Saved · Custom…) + inline custom row; Guardrails group omitted (as Android) | aligned | decided D21b | D21b done on iOS |
| interaction | Quick inputs Enter / C-c / Escape / C-b via `sendkey` | ✓ 4509–4520, 5376–5390 | ✓ sendCommand sendkey | ✓ System set sends Enter / C-c / Escape / C-b via sendkey | aligned | | |
| element | Quick-reply chips Yes / No / Stop | ✗ | ✓ SDS QuickReplyChip Yes/No/Stop (chat empty state) | ✓ SDV `QuickReplyChips` Yes / No / Stop above the composer while the session waits on a prompt (sends `yes`/`no`/`stop` + Enter) | pwa-missing | decided D68b | D68b keeps chips + bar · iOS done 2026-10-04 · → #172 |
| element | Pending-image / transcribing composer banners | ✓ _composerBanner | ✓ | ✓ SDV `imageBanner` + "Transcribing voice message…" banner | aligned | | |

## 3.9 Timeline & response viewer

| Cat | Feature | PWA | Android | iOS | Status | Decision | Notes |
|---|---|---|---|---|---|---|---|
| element | Timeline panel above output, toggle, GET /api/sessions/timeline?id | ✓ 3766 inline panel | ~ SDS ModalBottomSheet (server lines + local events) | ~ SessionOpsSheets `SessionTimelineSheet` sheet (GET /api/sessions/timeline) | aligned | | Sheet vs inline panel is the native idiom per D4a; copy tracked below |
| string | "Loading timeline…" / "No timeline events recorded yet." / "Failed to load timeline." / "Timeline" | ✓ 3774–3799 | ✓ SDS timeline sheet: "Timeline" / "Loading timeline…" / "No timeline events recorded yet." / "Failed to load timeline." (local-cache fallback kept) | ✓ SessionTimelineSheet (PWA strings verbatim) | aligned |  | Android copy aligned 2026-10-04 |
| element | Response viewer: "Last Response" header, markdown, Copy 📋 ("Copied to clipboard"), ✕, loading/error; GET /api/sessions/response?id | ✓ 14890–14990 | ✓ fresh fetch + (updating…) + markdown + 📋 copy (+ TTS, D43a) | ✓ `SessionResponseSheet` fresh GET /api/sessions/response (cached first, "(updating…)"), inline markdown, 📋 copy → dock, 🤖 Summary | aligned | decided D43a | Neither app fresh-fetches; iOS lacks markdown + copy · iOS done (D43a); Android still cached-only · Neither app fresh-fetches; iOS lacks markdown + copy · Android done (D43a) (2026-10-04) |

## 3.10 Data sources & lifecycle

| Cat | Feature | PWA | Android | iOS | Status | Decision | Notes |
|---|---|---|---|---|---|---|---|
| data | `subscribe {session_id}` on open; WS frames pane_capture / state / chat_message / channel / rate_limited | ✓ 2748 | ✓ SDVM:304 events() | ✓ IosServiceLocator.subscribeSessionEvents | aligned | | |
| data | Replay pending needs-input popup 200 ms after open | ✓ 2753 | ✓ `SessionStateWatcher` stash → dock message ~200 ms after open (≤ 1 h) | ✗ | ios-missing | | |
| data | Status board fetched on mount for badge | ✓ 3109 | ✓ `statusVm.refreshStatus()` on entry | ✓ `loadStatusBadge` on appear | aligned | | |
| data | Offline debounce before showing disconnected | ✗ | ✓ SDVM OFFLINE_GRACE_MS 3500 | ✓ nothing blocking is shown on disconnect (D46b) | aligned | decided D46b | iOS flips the blocking overlay on first Error frame |
| nav | Leaving detail: destroyXterm / pause stream / cancel subscription | ✓ 3356 | ✓ SDS pauseStream | ✓ iTV stop() | aligned | | |
| string | i18n: all detail copy via `t()` keys; Android 86 string resources | ✓ | ✓ | ✓ SwiftUI literal keys + L() with de/es/fr/ja Localizable.strings (detail strings present) | aligned | | |

## Coverage
rows: 118 · aligned: 82 · ios-missing: 5 · android-missing: 0 · pwa-missing: 12 · misaligned: 15 · n/a: 4

## Decisions (resolved 2026-10-04)
1. Mode badge condition → **D17a** tmux only (PWA rule). Android done; iOS still inverse.
2. Agent ⬡ / Chrome badges → **D66a** add to PWA + Android (→ #172; Android agent badge pending).
3. Running-state pill pulse → **D18a** on all three (live PWA pulses). iOS detail pill pending.
4. Last-activity dot colours → **D19a** on all three (live PWA colours the dot). Done everywhere.
5. Stop vs Kill → **D44a** "Stop" everywhere. iOS still "Kill".
6. Android-only extras → watch **D61a**, hooks toast / rate-limit notice / persisted mode **D67a** — adopt everywhere (PWA → #172).
7. Inline process-stats bar → **D45a** add to both apps.
8. Disconnect presentation → **D46b** PWA minimal (status dot); iOS overlay to be removed.
9. Connecting splash → **D10a** eye + bolt everywhere (incl. dwell + two stages; PWA → #172).
10. Font control → **D20a** `Aa▾` dropdown on iOS.
11. Chat quick-reply chips → **D68b** chips + PWA memory bar everywhere.
12. Terminal font family → **D8a** bundle JetBrains Mono in both apps.
13. Terminal search/copy → **D69a** revive on all three (Android UI + PWA pending).
14. Saved-commands UI → **D21b** literal dropdown + custom input.
