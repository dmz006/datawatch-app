# 03 — Session detail

PWA spec §4 · live `app.js` `renderSessionDetail` (2700–3137), terminal 3138–3575, stats/status 3576–4330, input/keys 4435–4530, voice 4713–5010, saved cmds 5259–5420, rename 1929/5243, response viewer 14890, state override 14843.
Android: `ui/sessions/SessionDetailScreen.kt` (SDS), `SessionDetailViewModel.kt` (SDVM), `TerminalView.kt` (TV), `TerminalToolbar.kt` (TT), `SessionStatsPanel.kt` (SSP), `SessionStatusPanel.kt` (SStP), `ChatTranscriptPanel.kt` (CTP), `SessionLoadingOverlay.kt` (SLO), `assets/xterm/host.html` (HH).
iOS: `screens/sessions/SessionDetailView.swift` (SDV), `TerminalView.swift` (iTV), `VoiceRecorder.swift`, `components/SessionLoadingOverlay.swift` (iSLO), `components/ConnectionStatusBanner.swift` (CSB — defined, not mounted).

## 3.1 Header, info bar, session actions

| Cat | Feature | PWA | Android | iOS | Status | Decision | Notes |
|---|---|---|---|---|---|---|---|
| nav | Header title = `name \|\| task`; nav bar + FAB hidden; back shown | ✓ 2700 | ✓ SDS:295 TopAppBar | ✓ SDV:47 | aligned | | |
| interaction | Tap title → inline rename (Enter/blur confirm, Esc cancel) | ✓ startHeaderRename 1929 → POST /api/sessions/rename 5243 | ~ SDS:1391 RenameDialog (AlertDialog) | ~ SDV:85 alert w/ TextField | misaligned | | Dialog is the platform idiom; copy "Rename session"/"Display name" matches |
| string | Rename success/fail toast "Session renamed"/"Rename failed" | ✓ 5276 | ✗ | ✗ | android-missing | | iOS ignores errors silently SDV:437 |
| element | Backend badge (lowercase backend) | ✓ 2934 backendText | ✓ SDS:1119 InfoBadge primary | ✓ SDV:152 primary | aligned | | |
| element | LLM badge `⚡ llm_ref` green bordered | ✓ 2934 | ✓ SDS:1123 #10B981 | ✓ SDV:156 success | aligned | | |
| element | Compute node badge `⚙ ref` purple | ✓ 2935 #a855f7 | ✓ SDS:1126 #8B5CF6 | ✓ SDV:159 secondary(#A855F7) | misaligned | | Android uses 8B5CF6 vs PWA/iOS A855F7 |
| element | Mode badge only for non-tmux modes (channel/chat/acp) | ✓ 2940 shows only `tmux`?? — live shows badge **only when mode==tmux** | ~ SDS:1133 shows only when mode ∉ {tmux,"",none} | ~ SDV:161 messagingBackend badge | misaligned | needs-decision | PWA and Android invert the condition (D1) |
| element | Agent badge `⬡ agent_id` | ✗ | ✗ | ✓ SDV:164 | pwa-missing | needs-decision | iOS-only (D2) |
| element | "Chrome" badge when `sess.chrome` | ✗ | ✗ (session_chrome string exists in settings) | ✓ SDV:167 | pwa-missing | needs-decision | D2 |
| interaction | State badge clickable → state override | ✓ 2941 showStateOverride 14843 | ✓ SDS:1156 DropdownMenu on pill; StateOverrideDialog 2578 | ✗ | ios-missing | | |
| element | State override options running / waiting_input / complete / error / killed | ✓ 14843 | ✓ SDS:1160 | ✗ | ios-missing | | |
| motion | Running state pill pulses alpha (static for waiting/rate_limited) | ✗ | ✓ SDS:1090–1151 effectiveAlpha | ✗ | pwa-missing | needs-decision | Android-only motion (D3) |
| element | Last-activity age chip (`session-last-activity`) for active sessions | ✓ 2942 title "Time since…" | ~ SDS:1183 colored <30s green/<300s amber/else red | ✗ | misaligned | needs-decision | PWA text only; Android adds 3-colour thresholds (D4) |
| interaction | `■ Stop` (active) · `↻ Restart` + `🗑 Delete` (done), left of state pill | ✓ 2918–2920 btn-stop/restart/delete | ✓ SDS:1199–1237 same glyphs, left-aligned | ~ SDV: stop.circle icon in toolbar; Restart/Delete in bottom bar 294 | misaligned | | PWA drives → iOS actions belong in info bar, with glyph+label |
| string | Stop wording | "Stop session" / `■ Stop` | `■ Stop` bar; dialog "Kill session?" (action_kill) | "Kill session" / "Kill session?" | misaligned | needs-decision | Stop vs Kill (D5) |
| interaction | Stop/Restart/Delete confirmation modals | ✓ killSession 4391 / deleteSession 5406 (showConfirmModal) | ✓ SDS:121 killConfirm/deleteConfirm | ✓ SDV:73/79 | aligned | | |
| element | Delete dialog: memory strategy Keep/Purge/Archive + role filter (#199) | ~ 5406 (verify fields) | ✓ SDS:123 deleteMemoryStrategy/Roles | ✗ | ios-missing | | Confirm PWA has the strategy picker |
| interaction | Timeline button 🕐 (right cluster, first) | ✓ 2949 toggleSessionTimeline | ✓ SDS:1243 | ✗ | ios-missing | | |
| interaction | Response button 📄 (right cluster, second; only when last_response) | ✓ 2950 (always) | ~ SDS:1253 only if hasResponse | ~ SDV:62 toolbar icon, only if lastResponse | misaligned | | PWA always shows; apps hide when empty — PWA drives: show, loading/empty state in modal |
| interaction | Watch/unwatch toggle (session_watch_on/off) in top bar | ✗ | ✓ SDS:408 isWatched | ✗ | pwa-missing | needs-decision | D6 |
| interaction | Docs link button in top bar | ✗ (global help) | ✓ DocsLinkButton pattern | ✓ SDV:61 | pwa-missing | | App-wide convention, see §01 |
| string | Hooks-installed toast for claude-code sessions | ✗ | ✓ SDS:161 status_hooks_installed_toast | ✗ | pwa-missing | needs-decision | D6 |
| token | Info bar: bg2, padding 10px 14px, bottom border | ✓ style.css:2977 | ✓ SDS:1062 Surface | ~ SDV:150 surface, 12/6 padding | misaligned | | Padding differs |
| token | Pill buttons: 10px, radius 10, 1px border, text2 | ✓ style.css:2538 | ~ SDS labelSmall OutlinedButton | ~ SDV badge capsule | misaligned | | Token sheet in §01 |

## 3.2 Banners, strips, overlays

| Cat | Feature | PWA | Android | iOS | Status | Decision | Notes |
|---|---|---|---|---|---|---|---|
| element | Pending schedules strip (`/api/schedules?session_id&state=pending`), per-item time + ✕ cancel | ✓ loadSessionSchedules 3654 | ✓ SDS:1615 SessionSchedulesStrip + SessionSchedulesViewModel | ✗ | ios-missing | | |
| element | Process stats bar (CPU/RAM/Threads/FDs/Net/GPU) above output, 5 s poll of /api/stats envelopes | ✓ 3576 setInterval 5000 | ✗ (Stats sub-tab instead) | ✗ | misaligned | needs-decision | PWA shows inline bar AND Stats sub-tab; Android only sub-tab (D7) |
| element | Connection banner for channel/acp: "Waiting for {mode}… [— answer the input prompt below first] ✕" | ✓ 2783–2811 dismissConnBanner 894 | ✗ (not found) | ✗ (CSB unused) | android-missing | | iOS has CSB component but never mounts it |
| element | Server-unreachable banner "terminal stream paused, last frame shown" (OFFLINE_GRACE 3.5 s debounce) | ✗ (WS status dot global) | ✓ SDS:819 reachable==false; SDVM:627 | ~ iTV reconnect overlay (full-screen, blocks view) | misaligned | needs-decision | Android toast-style vs iOS blocking overlay (D8) |
| element | Rate-limit inline notice (yellow #FEF3C7/#92400E, dismissible, retry-at) | ✗ (state badge only) | ✓ SDS:1330 InlineNotices | ✗ | pwa-missing | needs-decision | D6 |
| element | Needs-input: yellow `.input-bar.needs-input` border (banner removed v6.13.9) | ✓ 2965 | ✓ composer waiting tint | ~ SDV:246 2px blue top rule + tinted field | misaligned | | PWA yellow vs iOS `waiting` blue |
| motion | Connecting splash until first pane_capture: favicon 64px @0.3 + "CONNECTING TO SESSION…" (#00E5A0, 12px, 600, ls 2px) | ✓ 2999–3005 | ~ SLO: EyeOnlyAnimated + bolt (teal/pink), text alpha 900 ms FastOutSlowIn, bolt 220/80/600 ms, fadeOut 400 ms | ~ iSLO: SF `eye` + ring pulse 1.2 s easeInOut, "datawatch" + status | misaligned | needs-decision | Three different splashes (D9) |
| motion | Splash min/max dwell (new session 2 s/15 s; existing 0.5 s/8 s) | ✗ (watchdog only) | ✓ SDS:181–233 | ✗ | pwa-missing | needs-decision | D9 |
| motion | Connect watchdog: retry every 5 s ×3 → "Unable to connect…" + Retry + "Use without terminal" | ✓ 3138 MAX_RETRIES 3 | ✗ | ~ iTV Reconnect overlay only on Error | misaligned | | PWA drives: add retry/attempt text + "Use without terminal" |
| string | Splash stages "connecting…" → "waiting for terminal…" | ~ single uppercase string | ✓ SDS:816 | ✓ iTV:36 | pwa-missing | | Apps show two stages |
| motion | Generating indicator (3 dots, 600 ms alternate fade, 200/400 ms delays) | ✗ removed alpha.29 (927 no-op; CSS 2395 orphaned) | ✗ | ✗ | aligned | | Spec §4.15 is stale |

## 3.3 Output tab bar & modes

| Cat | Feature | PWA | Android | iOS | Status | Decision | Notes |
|---|---|---|---|---|---|---|---|
| element | Channel-mode tabs: Tmux/Chat · Channel · Status(+badge dot) | ✓ 2866–2872 | ✓ SDS:2609 SessionModeTab (tmux/channel/Status) | ✓ Tmux/Chat · Channel · Status (claude backends) | aligned | | |
| element | Tmux-only tabs: Tmux · Status | ✓ 2900 | ✓ | ✗ | ios-missing | | iOS is terminal-only |
| element | Chat-only sessions: no tab bar, `#chatArea` | ✓ 2897 | ✓ SDS chatMode pref + CTP | ✗ | ios-missing | | |
| string | Tab labels "Tmux"/"Chat"/"Channel"/"Status" | ✓ t() keys | ~ "tmux"/"channel"/"Status" (lowercase first two) | ✗ | misaligned | | Case differs |
| element | Status tab badge dot (`tabStatusBadge`, from /api/sessions/{id}/status on mount) | ✓ 2870, 3109, updateSessionStatusBadge 4221 | ✗ (not found) | ✗ | android-missing | | |
| element | `?` channel help popup ("Channel Commands") when Channel tab active | ✓ 2873 showChannelHelp 4525 | ✗ | ✗ | android-missing | | |
| element | Font control `Aa ▾` dropdown (A−, size, A+, Fit) in tab bar right | ✓ 2839–2855 | ✓ TT:125–156 inline DropdownMenu | ~ SDV:197 always-visible row A− px A+, no Fit | misaligned | needs-decision | D10 |
| element | Scroll-mode button `⤒` (U+2912 18px bold) / `⏹` exit | ✓ 2854 toggleScrollMode 3238 | ✓ TT:160 | ✗ | ios-missing | | |
| nav | Mode preference persisted (Terminal default, Chat remembered) | ✗ (per render) | ✓ SDS:131 modePrefs chat_mode | ✗ | pwa-missing | | Android convenience |
| nav | Deep-link open in Status mode (`openInStatusMode`) | ✗ | ✓ SDS:117 | ✗ | pwa-missing | | Used by alerts → status |

## 3.4 Status sub-tab (board)

| Cat | Feature | PWA | Android | iOS | Status | Decision | Notes |
|---|---|---|---|---|---|---|---|
| element | Status/Stats sub-tab strip (blue underline active) | ✓ 2878–2886 switchStatusSubtab | ✓ SDS:147 statusSubStats | ✗ | ios-missing | | |
| data | GET /api/sessions/{id}/status, 5 s while tab open | ✓ 4100 setInterval | ✓ SessionStatusViewModel | ✗ | ios-missing | | |
| element | Hook health pill alive/stale/missing, click re-polls, Docs ↗ when not alive | ✓ 4284–4290 | ✓ SStP:134 HookHealthPill (docs → hooks docs) | ✗ | ios-missing | | |
| string | "● hooks alive / hooks stale / no hooks installed" | ✓ | ~ "Hooks alive / Hooks stale / Hooks missing" | ✗ | misaligned | | Copy drift |
| element | Current focus card (task, last event, idle_since) + empty "No hook events received yet." | ✓ 4304–4309 | ✓ SStP:182 ("No active focus") | ✗ | ios-missing | | Empty copy differs |
| element | Sprint / PRD tree card (JSON pre) → live "Live Task Tree" / "Sprint / Automata" | ✓ 4312, 4339–4342 | ✓ SStP:233 SprintCard + 361 Task Tree + 450 breadcrumb | ✗ | ios-missing | | |
| element | Tests card pass/fail(/skip) | ✓ 4317 | ✓ SStP:273 | ✗ | ios-missing | | |
| element | Git card branch + dirty (+ahead) | ✓ 4324 | ✓ SStP:293 | ✗ | ios-missing | | |
| element | Guardrail verdicts card (+ run guardrail POST /guardrail, "Approved") | ✓ 4281, 4431–4440 | ✓ SStP:413 GuardrailVerdictsCard (read-only) | ✗ | ios-missing | | Android lacks "run guardrail" action → android-missing sub-item |
| element | Parent session link (telemetry) | ✓ renderParentSessionLink 4328 | ✗ (not found) | ✗ | android-missing | | |
| string | "Last 5 events before failure" | ✓ 4458 | ✗ | ✗ | android-missing | | |

## 3.5 Stats sub-tab (cards)

| Cat | Feature | PWA | Android | iOS | Status | Decision | Notes |
|---|---|---|---|---|---|---|---|
| data | /api/observer/envelopes (+ /api/compute/nodes/{id}) 5 s while open | ✓ 3865–3880 | ✓ SessionStatsViewModel + SSP:66 | ✗ | ios-missing | | |
| element | Host card: CPU donut 60px (green/amber/red at 70/90) | ✓ 3942–3963 | ✓ SSP:227–258 CircularProgressIndicator | ✗ | ios-missing | | |
| element | CPU % + 80×18 sparkline (60 pts); RSS + sparkline; Threads/FDs/PID(+N) when >0 | ✓ 3909–3977 | ✓ SSP:262–288 | ✗ | ios-missing | | |
| element | Net ↓/↑ bytes/s when non-zero; GPU %/mem | ✓ | ✓ SSP:294–302 | ✗ | ios-missing | | |
| string | Card title "Process Stats"/"Backend Stats" | ✓ 3885/3888 | ~ "Host" / "Backend Stats — {BACKEND}" | ✗ | misaligned | | |
| element | Container card (ID 12, Image, Runtime) when container present | ✓ 3989 | ✓ SSP:109–121 | ✗ | ios-missing | | |
| element | Compute Node card: Node CPU/Mem, GPU util/temp/power/VRAM, Ollama CPU/RSS, "Open Compute Node →" | ✓ 4012–4054 | ✓ SSP:128 + gpu util/temp sparklines | ✗ | ios-missing | | Android adds GPU sparklines (pwa-missing detail) |
| element | LLM card: ref, note, "Open LLM →" | ✓ 4061–4064 | ✓ SSP:145 (falls back to backend name) | ✗ | ios-missing | | |
| string | No-envelope text "No process envelope yet — observer plugin off…" | ✓ | ~ "No process stats — eBPF may not be active…" | ✗ | misaligned | | |

## 3.6 Channel / Chat / Log modes

| Cat | Feature | PWA | Android | iOS | Status | Decision | Notes |
|---|---|---|---|---|---|---|---|
| element | Channel tab lines → / ← / ⚡ with classes send/reply/notify | ✓ 2760–2765; css 3031–3039 | ✓ SDS EventList/EventRow 1847 (verify glyphs) | ✓ ChannelTabView → / ← / ⚡ | aligned | | |
| data | Seed from /api/channel/history?session_id (dedupe, 1000 cap) | ✓ 2718 | ✓ (SDVM history) | ✓ IosChannel.history + ChannelHub | aligned | | |
| interaction | Send via channel `▶ ch` (POST /api/channel/send) vs tmux `▶` switching with active tab | ✓ 2920–2924, 4565 | ✗ (single send path) | ✗ | android-missing | | |
| element | Chat bubbles: avatar U/AI/S, role label, time, radius 12, 13px; user #3b82f6, assistant #10b981, system #64748b | ✓ 3015–3045; css 3055–3144 | ~ CTP: U/AI, primaryContainer / surfaceVariant | ✗ | misaligned | | Android uses theme colours, not PWA chat palette |
| element | Collapsed "N earlier messages" `<details>` when > 6 | ✓ 3023 | ✗ | ✗ | android-missing | | |
| element | Chat empty state 💬 + "Send a message to begin…" + memory hint | ✓ 3057–3061 | ~ "No messages yet. Waiting for session output…" + Yes/No/Stop chips | ✗ | misaligned | needs-decision | Quick-reply chips are Android-only (D11) |
| element | Chat quick-cmd bar: 📚 memories · 🔍 recall · 🔗 kg query · 🔬 research | ✓ 3050–3055 chatQuickCmd 25201 | ✗ | ✗ | android-missing | | |
| element | Chat markdown (code blocks, inline code, thinking `<details>`, images, mermaid), streaming bubble | ✓ renderChatMarkdown 1245; css 3249–3331 | ~ CTP streaming bubble; markdown? (MarkdownView exists in autonomous) | ✗ | misaligned | | Verify CTP renders markdown |
| element | Log mode lines with acp-status/processing/ready/error classes | ✓ 3072–3085 | ✗ (not found) | ✗ | android-missing | | |

## 3.7 Terminal (xterm)

| Cat | Feature | PWA | Android | iOS | Status | Decision | Notes |
|---|---|---|---|---|---|---|---|
| data | Render from `pane_capture` only; input via `send_input`/`command` on /ws | ✓ 3383+ | ✓ TV:674 + WsOutbound | ✓ iTV (shared host.html) | aligned | | iOS fixed v1.23.115 |
| data | `resize_term` on cols/rows change (200 ms debounce) | ✓ 3489, 3528 | ✓ TV onResize → WsOutbound.sendResizeTerm; SDVM:345 | ✓ iTV bridge resize | aligned | | |
| token | xterm theme bg #0f1117 fg #e2e8f0 cursor #a855f7 selection rgba(168,85,247,.3), scrollback 5000, cursorBlink | ✓ 3511–3534 | ✓ HH:115–143 | ✓ (HH) | aligned | | |
| token | Font family: 'JetBrains Mono','Fira Code' | ✓ 3505 | ~ HH:117 'Roboto Mono','Droid Sans Mono' | ~ (HH) → falls to system mono | misaligned | needs-decision | D12 |
| token | Default font 9px, persisted (`cs_term_font_size` / prefs / UserDefaults) | ✓ 3480 | ✓ TT:91 | ✓ SDV:20 | aligned | | |
| interaction | A−/A+ clamp 5..20 | ✓ changeTermFontSize 3180 (clamp?) | ✓ TT MIN/MAX | ✓ SDV:201/221 | aligned | | Verify PWA clamp |
| interaction | Fit to width (shrink font until no horizontal overflow) | ✓ termFitToWidth 3196 | ✓ HH dwAutoFitToWidth, TT "Fit" | ✗ | ios-missing | | host.html has it; iOS needs the button |
| interaction | Configured min cols/rows (claude 120) honoured; Settings "Terminal dimensions" card | ✓ 3496–3498 configCols | ✓ TV setMinSize + TerminalDimensionsCard | ~ backend default min cols (claude 120 / 80); no dimensions card yet (Settings, D31) | misaligned | | |
| motion | Keyboard-open refit: explicit height + rAF, second pass 350 ms | ✓ 3542–3579 | ✓ HH:357 350 ms; onSizeChanged → dwExplicitSize; safeFit 50/200/600/1200/2500 ms | ✓ iTV onLayout → dwExplicitSize | aligned | | |
| interaction | Scroll mode: tmux-copy-mode, 700 ms pending-refresh window, Esc exits, button swaps to exit | ✓ 3238–3345 | ✓ TT:160–175 + HH dwSetScrollMode/dwScrollPendingRefresh | ✗ | ios-missing | | |
| element | Scroll-mode strip: Page Up/Down, Line Up/Down, ESC | ✓ `.scroll-bar-active` 3261; css 3146–3212 | ✓ TT:190 TerminalScrollModeStrip | ✗ | ios-missing | | |
| interaction | Interactive keyboard: xterm onData → sendkey/send_input | ✓ 3545 | ✓ HH onData → DwBridge.onInput | ✓ iTV onInput | aligned | | |
| interaction | Samsung/IME spurious-Enter suppression (150 ms window), composing-text tracking | n/a | ✓ TV:129–232 | n/a | n/a | | Android-specific IME |
| interaction | Pinch-zoom WebView as escape hatch for 80-col TUIs | n/a | ✓ TV:558 | ✗ | n/a | | iOS scrollView zoom disabled |
| interaction | Terminal search (next/prev/clear) via search addon | ✗ | ✓ TV:325–342 dwSearch* (UI removed v0.42 — controller only) | ✗ | pwa-missing | needs-decision | Dormant on Android; keep/revive? (D13) |
| interaction | Copy selection to clipboard | ✗ (browser native) | ✓ TV:349 dwCopySelection (controller only) | ✗ | n/a | | Native selection on web/iOS |
| interaction | Prepend backlog on open (`dwPrependBacklog`) | ~ 3523 bufferedLines | ~ TV:373 `prepend()` defined but never called | ✗ | misaligned | | Not live on Android either (verified 2026-10-04); iOS gets live frames only |
| element | DATAWATCH_COMPLETE marker lines filtered from captures | ✓ app.js ~609 | ✓ HH:519 | ✓ (HH) | aligned | | |
| element | Terminal exit: black-screen-safe WebView teardown | n/a | ✓ TV:694–705 | ✓ iTV dismantleUIView | n/a | | |

## 3.8 Input bar, keys, saved commands, voice, attachments

| Cat | Feature | PWA | Android | iOS | Status | Decision | Notes |
|---|---|---|---|---|---|---|---|
| element | Input bar shown only when active && input_mode != none | ✓ 2965 | ✓ ReplyComposer gating | ~ SDV:38 shown unless completed/killed/error | misaligned | | iOS ignores input_mode |
| string | Placeholder: "Waiting for connection…" / "Type your response…" / "Send message…" / "Send command or input…" | ✓ 2973 | ~ "Reply…" / "Reply (input required)…" / "Transcribing…" | ~ "Type a reply…" / "Reply or press Enter" | misaligned | | Three copies — PWA drives |
| interaction | Enter sends (not Shift+Enter); empty input sends Enter key | ✓ 4461–4475 | ✓ (send "\r") | ✓ SDV:338 (empty blocked) | misaligned | | iOS disables empty send; PWA sends newline |
| interaction | Send via `send_input` when running/waiting/rate_limited else `command send` | ✓ 4480–4484 | ✓ WsOutbound.sendInput | ✓ sendInput | aligned | | |
| element | Schedule-input button 🕐 → popup (command, when) | ✓ 2925 showScheduleInputPopup 3681 | ✓ ReplyComposer:2747 Icons.Schedule → scheduleOpen | ✗ | ios-missing | | |
| element | Voice button 🎙 (hold-to-record / click toggle) when whisper enabled | ✓ 2926 | ✓ ReplyComposer:2787 | ✓ SDV:262 | aligned | | |
| element | Recording modal: waveform, "Recording…", Cancel / Send | ✓ _showVoiceRecordingModal 4713 (voice-waveform) | ~ ReplyComposer:2440 dialog | ~ SDV:444 mic pulse 0.6 s, Cancel/Send | misaligned | | No waveform on apps; iOS pulse-only |
| string | Transcribing state: placeholder "Transcribing…" + banner "Transcribing voice message…", toast "✓ Transcribed (n chars)" | ✓ 4776–4800 | ✓ ReplyComposer:2697/2714 | ✓ banner + placeholder + ✓ Transcribed note | aligned | | |
| data | POST /api/voice/transcribe (webm/ogg/mp4) | ✓ 4787 | ✓ transcribeAudio | ✓ transcribeAudioData (audio/mp4) | aligned | | |
| element | Image attach 📷 (gallery / camera), upload chip "Uploading…/✓ name", `[image:path]` appended | ✓ 2927 sessionImageInput; "Wait for image upload" | ✓ ReplyComposer:2274–2370, 2608, 2795–2860 | ✗ | ios-missing | | |
| element | Keys strip: ␛ · ↑ ↓ ← → · ⏎ right-aligned | ✓ 2957–2964 | ✓ ReplyComposer:2527–2605 (icons) | ✗ | ios-missing | | |
| interaction | Hold-to-repeat arrows (250 ms delay, 80 ms interval) | ✓ startArrowRepeat 4440 | ✗ onClick only | ✗ | android-missing | | |
| element | Saved commands: dropdown `<select>` with system set (approve/reject/enter/continue/skip/abort/ESC/Ctrl-b/quit) + user `/api/commands`, custom command input | ✓ loadSavedCmdsQuick 5259, sendCustomCmd 5361 | ~ ⌨ "Saved commands" ModalBottomSheet | ✗ | misaligned | needs-decision | Dropdown vs sheet (D14) |
| interaction | Quick inputs Enter / C-c / Escape / C-b via `sendkey` | ✓ 4509–4520, 5376–5390 | ✓ sendCommand sendkey | ✗ | ios-missing | | |
| element | Quick-reply chips Yes / No / Stop | ✗ | ✓ SDS:1824 (chat empty state only) | ✗ | pwa-missing | needs-decision | D11 |
| element | Pending-image / transcribing composer banners | ✓ _composerBanner | ✓ | ✗ | ios-missing | | |

## 3.9 Timeline & response viewer

| Cat | Feature | PWA | Android | iOS | Status | Decision | Notes |
|---|---|---|---|---|---|---|---|
| element | Timeline panel above output, toggle, GET /api/sessions/timeline?id | ✓ 3766 inline panel | ~ SDS:1437 ModalBottomSheet (server lines + local events) | ✗ | misaligned | | Inline vs sheet — platform idiom; PWA drives copy |
| string | "Loading timeline…" / "No timeline events recorded yet." / "Failed to load timeline." / "Timeline" | ✓ 3774–3799 | ~ "Timeline" / "No events yet — open a session…" | ✗ | misaligned | | |
| element | Response viewer: "Last Response" header, markdown, Copy 📋 ("Copied to clipboard"), ✕, loading/error; GET /api/sessions/response?id | ✓ 14890–14990 | ~ SDS:583 LastResponseSheet | ~ SDV:536 plain monospaced text, Done only | misaligned | | iOS lacks markdown + copy + fetch (uses cached field) |

## 3.10 Data sources & lifecycle

| Cat | Feature | PWA | Android | iOS | Status | Decision | Notes |
|---|---|---|---|---|---|---|---|
| data | `subscribe {session_id}` on open; WS frames pane_capture / state / chat_message / channel / rate_limited | ✓ 2748 | ✓ SDVM:304 events() | ✓ IosServiceLocator.subscribeSessionEvents | aligned | | |
| data | Replay pending needs-input popup 200 ms after open | ✓ 2753 | ✗ | ✗ | android-missing | | |
| data | Status board fetched on mount for badge | ✓ 3109 | ✗ | ✗ | android-missing | | |
| data | Offline debounce before showing disconnected | ✗ | ✓ SDVM OFFLINE_GRACE_MS 3500 | ✗ (immediate) | misaligned | | iOS flips overlay on first Error frame |
| nav | Leaving detail: destroyXterm / pause stream / cancel subscription | ✓ 3356 | ✓ SDS pauseStream | ✓ iTV stop() | aligned | | |
| string | i18n: all detail copy via `t()` keys; Android 86 string resources | ✓ | ✓ | ✗ hard-coded English | ios-missing | | See §01 i18n row |

## Coverage
rows: 118 · aligned: 17 · ios-missing: 37 · android-missing: 13 · pwa-missing: 13 · misaligned: 33 · n/a: 4 · needs-decision rows: 19 (14 decisions)

## Decisions needed
1. **Mode badge condition** — PWA shows the mode badge only for `tmux`; Android only for non-tmux (channel/chat/acp); iOS shows the messaging backend. Options: (a) PWA rule (b) Android rule (c) drop badge, rely on tab strip — refs app.js:2940, SDS:1133, SDV:161.
2. **Agent `⬡` and "Chrome" badges** exist only on iOS. Options: (a) add to PWA + Android (b) remove from iOS — SDV:164–169.
3. **Running-state pill pulse** is Android-only motion. Options: (a) adopt in PWA/iOS (b) remove — SDS:1090–1151.
4. **Last-activity age colours** (green <30 s / amber <5 min / red) are Android-only. Options: (a) adopt (b) plain text like PWA — SDS:1183.
5. **Stop vs Kill wording** — PWA "Stop", Android bar "Stop" but dialog "Kill session?", iOS "Kill". Options: (a) "Stop" everywhere (b) keep "Kill" in confirm dialogs — app.js:2919, strings.xml action_kill, SDV:73.
6. **Android-only extras**: watch/unwatch toggle (SDS:408), hooks-installed toast (SDS:161), rate-limit inline notice (SDS:1330), persisted Terminal/Chat mode (SDS:131). Options per item: (a) adopt in PWA (+iOS) (b) remove.
7. **Inline process-stats bar** above output (PWA, 5 s) vs stats only in the Status→Stats sub-tab (Android). Options: (a) add bar to apps (b) drop from PWA — app.js:3576, SSP.
8. **Disconnect presentation** — Android non-blocking banner after 3.5 s grace; iOS full-screen reconnect overlay immediately; PWA relies on global status dot. Options: (a) Android pattern everywhere (b) PWA minimal — SDS:819, iTV reconnectOverlay.
9. **Connecting splash design** — PWA static favicon + teal uppercase text; Android animated eye + lightning bolt with min/max dwell; iOS SF-symbol eye pulse. Options: (a) Android splash becomes canonical (port to PWA/iOS) (b) PWA minimal (c) iOS keeps lightweight — SLO, iSLO, app.js:2999.
10. **Font control** — PWA/Android `Aa▾` dropdown (A−, size, A+, Fit); iOS permanent A−/px/A+ row without Fit. Options: (a) dropdown on iOS (b) keep row, add Fit — SDV:197, TT:125.
11. **Chat quick-reply chips Yes/No/Stop** (Android chat empty state) — PWA has memory quick-cmd bar instead. Options: (a) PWA bar everywhere (b) both (c) remove chips — SDS:1824, app.js:3050.
12. **Terminal font family** — PWA JetBrains Mono/Fira Code; apps Roboto Mono / system mono. Options: (a) bundle JetBrains Mono in both apps (b) accept platform mono — app.js:3505, HH:117.
13. **Terminal search** — Android controller supports search/copy (UI removed v0.42); PWA/iOS none. Options: (a) revive as a feature on all three (b) delete dormant code — TV:325–360.
14. **Saved commands UI** — PWA `<select>` dropdown + custom input in the keys strip; Android ⌨ bottom sheet; iOS none. Options: (a) sheet is the mobile idiom, PWA copy/content (b) literal dropdown — app.js:5259, SDS saved-commands sheet.
