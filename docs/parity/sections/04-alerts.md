# 04 — Alerts

PWA spec §5 (+ §1.2 pill, §11 dock) · live `app.js` `renderAlertsView` 19162–19570, `handleAlert` 879, `renderAlertPill` 936, `pushToAlertDock` 15004, `renderAlertDock` 15044, `updateAlertBadge` 16304, alert rules 21179–21260 · Android `ui/alerts/*`, `ui/alertrules/AlertRulesCard.kt`, `ui/notifications/NotificationsCard.kt`, `ui/detection/DetectionFiltersCard.kt`, `push/*` · iOS `screens/alerts/AlertsView.swift`, `components/AlertsBellButton.swift`, `notifications/NotificationService.swift`.

Refs: `js:` = app.js line · `A:` = Android file:line · `I:` = iOS file:line.

| Cat | Feature | PWA | Android | iOS | Status | Decision | Notes |
|---|---|---|---|---|---|---|---|
| data | List source GET /api/alerts (single server) | ✓ js:19169 on view render | ✓ A:AlertsViewModel.kt:234 5 s poll while mounted | ✓ I:AlertsView.swift:98 5 s sequential poll while visible | aligned | | PWA fetches once per render + ↻; apps poll |
| data | Multi-server aggregate GET /api/alerts/aggregated | ✓ js:19169 when activeServer=='all' | ~ A:AlertsViewModel.kt:200–226 client-side merge of per-profile listAlerts | ✗ profiles.first only | misaligned | | Android merges client-side instead of aggregated endpoint; iOS no multi-server |
| data | Server picker bar on Alerts view | ✓ js:19168 `_injectServerPickerBar` | ✓ A:AlertsScreen.kt:380 AlertsTopBar dropdown incl. All servers | ✗ | ios-missing | | |
| data | Saved commands GET /api/commands for quick reply | ✓ js:19171 | ✓ A:AlertsScreen.kt:744 onQuickReply | ✓ I:AlertsView `IosQuickCommands.loadSaved` on appear | aligned | |  |
| data | Sessions GET /api/sessions for liveness (Active vs Historical) | ✓ js:19172 refreshes state.sessions | ✓ A:AlertsViewModel.kt grouping by session state | ✓ I:AlertsView.swift `AlertsViewModel.isActive` — listSessions each poll, Active = live & not done | aligned | | iOS Active = unread, Historical = read — different model from PWA (session done/alive); mechanical fix to PWA rule |
| data | Auto-ack all on opening Alerts page POST /api/alerts {all:true} | ✓ js:19191 | ✓ `AlertsViewModel.ackAllOnOpen` on screen open | ✗ | misaligned | decided D49a | D3 · Android done 2026-10-04 |
| data | Dismiss all = POST {all:true, delete:true} | ✓ js:19535 | ✓ `deleteAllAlerts()` (new transport call) | ~ I:IosServiceLocator.kt:336 markAlertRead(all=true) | misaligned | decided D48a | D2 · Android done 2026-10-04 |
| data | Per-alert mark-read POST /api/alerts {id} | ✗ (auto-ack makes it moot) | ~ VM fn kept, no UI | ✓ I:AlertsView.swift:32 dismiss(alert) | pwa-missing | decided D49a | D4 · Android done 2026-10-04 |
| data | WS `alert` frame → unread++ + toast | ✓ js:879 handleAlert, showToast 4 s | ✓ global stream routes `alert` → `AlertsHub` → dock entry (`LiveAlertFeed`); badge from REST poll | ✗ | misaligned | decided D51a | D11 — shared EventMapper maps `alert` (EventMapper.kt:69) but neither app consumes it · Android done 2026-10-04 |
| data | Unread count source | state.alertUnread (WS increments, reset on page open) | AlertsView.unreadCount from REST | AlertsView.unreadCount from REST → @AppStorage dw.alert.badge | misaligned | | Falls out of D3/D11 |
| data | Alert rules GET/POST/DELETE /api/alert-rules, POST …/enable/disable | ✓ js:21179–21260 (Settings) | ✓ A:AlertRulesCard.kt:73–125 | ✓ I:IosAlertRules.kt list/create/delete/setEnabled | aligned | | Lives in Settings on PWA+Android; listed here per README §04 |
| data | Alert rule firings GET (listAlertRuleFirings) | ✗ | ✓ A:AlertRulesCard.kt:76,132 "Recent Firings (N)" ×20 | ✗ | pwa-missing | needs-decision | D8 |
| data | Detection filters (/api/filters, detection.*_patterns, settle/repeat timing) | ✓ Settings → Detection section | ✓ A:DetectionFiltersCard.kt | ~ I:RulesEditorsViews.swift `FiltersView` (/api/filters CRUD + toggle); detection.*_patterns + settle/repeat timing missing | misaligned | |  |
| data | Push delivery: UnifiedPush SSE self-registration (Tier 1) | n/a (browser) | ✓ A:push/UnifiedPushSseService.kt | ✗ | n/a | | Android-specific tier; iOS path is APNs |
| data | Push delivery: ntfy fallback service | n/a | ✓ A:push/NtfyFallbackService.kt | ✗ | n/a | | FCM removed v0.33.17 |
| data | Push delivery: Web Push distributor endpoints (user-entered) | ✓ (browser push) | ✓ A:PushNotificationsCard (Settings) | ✗ | ios-missing | | iOS could register a UnifiedPush-style endpoint? platform question — see D12 |
| data | Push delivery: APNs token registration POST /api/devices/register platform=ios | n/a | n/a (FCM removed) | ~ I:NotificationService.swift:42 registerApnsToken — server #185/BL335 pending | n/a | | Blocked on server |
| data | Push payload deep-link routing (session_waiting/input_needed → session; alert → alerts) | n/a | ✓ A:NotificationPoster.kt:131 deepLinkIntent | ✓ I:NotificationService.swift:49 | aligned | | |
| data | Notification dedup (SSE + ntfy same event) | n/a | ✓ A:NotificationPoster.kt:41–56 (256-entry LRU) | ✗ | n/a | | |
| data | Waiting-state settle window before notifying (45 s) + prompt dedup | ✓ server detection.alert_settle | ✓ A:SessionStateWatcher.kt:47 SETTLE_MS mirrors server | ✗ | ios-missing | | iOS has no poll-sourced notifications yet |
| nav | Alerts in bottom nav with unread badge (99+ cap) | ✓ js:16304 `#alertBadge` | ~ not verified in shell/BottomNavBar | ✓ I:RootView.swift:37 `.badge(alertBadgeCount)` (no 99+ cap) | misaligned | | Verify Android nav badge; iOS cap missing |
| nav | Header alert pill 🔔 N always visible | ✓ js:936 states muted/0/N, `--accent2` border when N≥1 | ✓ A:HeaderComponents.kt:102–134 | ~ I:AlertsBellButton.swift bell + red unread badge | misaligned | needs-decision | D9 — count semantics (dock total vs unread) and click target differ |
| nav | Pill click → toggle dock | ✓ js toggleAlertDock | ✓ A:HeaderComponents.kt:134 AlertDockChannel.toggle() | ✗ navigates to Alerts tab | misaligned | needs-decision | D9 |
| nav | Alert → open session (session-detail) | ✓ js:19253 sessNavBtn / group header link | ✓ A:AlertsScreen.kt onOpenSession (group header name) | ✓ I:AlertsView `sessionLabel` NavigationLink → SessionDetailView (group header + chrono rows) | aligned | |  |
| element | Tab bar Active / Historical / System with counts | ✓ js:19480 `output-tab` buttons | ✓ A:AlertsScreen.kt:131–153 Tab() | ✓ I:AlertsView `tabRow` underline buttons "Tab (N)" via tabCount | aligned | | Verify iOS shows counts in label |
| data | Tab + per-tab filter state persisted | ✓ localStorage cs_alerts_active_tab + _alertsPersistTabState | ✓ A:AlertsViewModel.kt:255–275 prefs alerts_<tab>_chip/sort/search | ✓ I:AlertsView UserDefaults dw.alerts.activeTab + dw.alerts.tab.<tab> search/chip/sort | aligned | |  |
| element | Default tab = first with entries (Active→Historical→System) | ✓ spec §5.1 | ~ A restores last tab | ~ I restores last persisted tab (like Android) | misaligned | | Minor |
| element | Filter bar row 1: "🔔 N alerts" + ⏷/🕒 sort + ✕ + 🔕 + ↻ | ✓ js:19500–19525 | ✓ A:AlertsScreen.kt:160–215 | ✓ I:AlertsView `filterBar` 🔔 N + ⏷/🕒 sort + ✕ + 🔕 + ↻ | aligned | | iOS lacks sort |
| interaction | Sort toggle by session ↔ chronological (persisted per tab) | ✓ js setAlertsSort | ✓ A:AlertsViewModel.kt SortMode | ✓ I:AlertsViewModel `SortMode` session/chrono, persisted per tab | aligned | |  |
| interaction | ✕ dismiss all | ✓ js dismissAlertsAll (delete) | ✓ ControlBtn("✕") → delete-all | ✓ I controlBtn("✕") dismissAll | misaligned | decided D48a | D2 semantics · Android done 2026-10-04 |
| interaction | 🔕 button | ✓ muteAlertDock — session-scoped dock mute, no server call | ✓ ControlBtn("🔕") → `AlertDockChannel.mute()` | ~ I:AlertsView.swift controlBtn("🔕") { dismissAll() } | misaligned | decided D47a | D1 — both apps repurpose mute as dismiss · Android done 2026-10-04 |
| interaction | ↻ refresh | ✓ | ✓ | ✓ | aligned | | |
| element | Chips all / 🟡 prompts / 🔴 errors / 🟠 warn / ⚪ info with counts | ✓ js:19505–19509 | ✓ A:AlertsScreen.kt chip row, counts search-filtered | ~ I:AlertsView `severityChip` emoji + "All/Prompt/Error/Warn/Info ×N" (casing/wording differs) | misaligned | | iOS casing/wording differs ("prompts"→"Prompt"); align to PWA |
| token | Chip colors: text2 / warning / error / warning / text2; selected = filled | ✓ js chipBtn | ✓ A chipBorderColor + chipBg | ✓ I severityChip | aligned | | |
| element | Prompt category rule: session waiting_input OR title matches needs input / prompt / waiting (regex, case-insensitive) | ✓ js catOf | ✓ A:AlertsScreen.kt:633–636 + AlertsViewModel.isPromptAlert | ~ I: type contains "input"/"prompt" (no session-state input) | misaligned | | iOS lacks session state so waiting_input sessions aren't "prompt" |
| interaction | Live text search (title+body) | ✓ js alertsSearchInput | ✓ A OutlinedTextField alert_search_ph | ✓ I filterText + clear button | aligned | | |
| element | By-session group card: header bg2, name link, state text (🟠 waiting input / 🟢 running / ✅ state), "N alerts · 🟡 P", "last HH:MM:SS" mono | ✓ js:19470–19485 | ✓ A:AlertGroupCard 499–624 | ✓ I:AlertsView `groupHeader` (name link, state text, "N alerts · 🟡 P", "last HH:MM:SS" mono) | aligned | |  |
| interaction | Group header tap collapses/expands | ✓ js toggles display | ✓ A onToggleExpand ▼/▶ | ✓ I:AlertsView `collapsed` set, ▼/▶ chevron | aligned | |  |
| element | Group ordering waiting → running → others; System card last | ✓ js stateRank | ✓ A sorted by last alert ts (not stateRank) | ✓ I:AlertsViewModel `groups` stateRank waiting → running → others, System last | misaligned | | Android orders by recency, PWA by state rank |
| element | Chronological mode: flat newest-first with tiny session link | ✓ js:19430–19445 | ✓ A flatChrono A:AlertsScreen.kt:303 | ✓ I:AlertsView chrono list with per-row session link | aligned | |  |
| element | Alert row: 3px left border + bg tint by category (prompt amber .08 / error red .06 / else border+transparent) | ✓ js renderRow | ✓ A:AlertCard 625–700 | ✓ I:AlertRow border/bg | aligned | | |
| element | Kind badge 🟡 PROMPT (amber bg, dark fg) / 🔴 ERROR (red bg, white) / ⚪ level (bg2) | ✓ js kindBadge | ✓ A badgeText/Bg/Fg | ~ I adds 🟠 WARNING badge (PWA shows warn as ⚪ warn) | misaligned | | iOS invents a WARNING badge variant |
| element | Time: HH:MM:SS mono, opacity .55 | ✓ js | ✓ A formatAlertTime | ✓ I alertTime monospaced | aligned | | |
| element | Title 13px/600, body 12px text2 | ✓ | ✓ | ✓ (bodyMedium/labelSmall) | aligned | | iOS clamps title 2 lines, body 3 lines; PWA unclamped |
| element | Quick reply `<select>` of saved commands on prompt alerts → alertSendCmd | ✓ js:19417–19422 | ✓ A:AlertCard onQuickReply (alerts_quick_reply_ph) | ~ I:AlertsView `quickReply` Menu (approve/reject/continue/skip/ESC + saved commands) on latest alert of a waiting session group only; not in chronological mode | misaligned | |  |
| element | Per-alert ✓ mark-read control | ✗ | ✗ removed | ~ I unread dot + dimmed read rows | pwa-missing | decided D49a | D4 · Android done 2026-10-04 |
| interaction | Swipe-left dismisses a session group (80 dp threshold) | ✗ | ✗ removed | ✗ | pwa-missing | decided D50d | D6 · Android done 2026-10-04 |
| interaction | Swipe-left dismisses a single alert | ✗ | ✗ | ✓ I:AlertsView.swift:302 swipeActions | pwa-missing | needs-decision | D6 |
| data | Watched-session filter (badge counts only watched sessions) | ✗ | ✓ A:AlertsViewModel.kt:175–182, 480–493 | ✗ | pwa-missing | needs-decision | D7 |
| element | Alert dock panel (header chips per type, collapse chevron, ✕, 🔕; body cards with ×N, 3-line clamp, left rail) | ✓ js:15044 | ~ A:AlertDockOverlay.kt (pill + category pills + expand + dismiss + mute); feed/coalescing not verified | ✗ | misaligned | | Verify what populates Android dock rows |
| data | Dock coalescing: family key, 60 s window, ×N, max 100 | ✓ js:15004–15040 | ~ not verified | ✗ | misaligned | | |
| data | Dock mute persisted per browser session (sessionStorage cs_alert_muted) | ✓ js:15204 | ~ A onMute callback "suppresses for session" | ✗ | misaligned | | |
| motion | Dock expand/collapse: Android chevron rotate animateFloatAsState 0→180 + AnimatedVisibility; PWA no animation (panel created/removed) | ✗ | ✗ static (no chevron rotation / AnimatedVisibility) | ✗ | pwa-missing | decided D36b | D10 — app had an idea; adopt in PWA? · Android done 2026-10-04 |
| motion | Alert card hover bg3 transition .15 s | ✓ style.css:2232–2242 | n/a (touch) | n/a | n/a | | |
| motion | Toast on WS alert, 4 s | ✓ js:891 showToast | ✓ dock entry (toasts retired, D41a) | ✗ | misaligned | decided D51a | D11 · Android done 2026-10-04 |
| motion | Loading state | ✓ spinner "Loading…" (common_loading) | ~ not verified | ✓ I LoadingIndicator "Loading alerts…" | misaligned | | |
| string | Empty state copy | "No alerts." (common_no_alerts) | "No alerts." (`alerts_empty`) on every tab | "No <tab> alerts" + "No server connected" | misaligned | decided D35a | D5 — PWA single string vs Android per-tab copy · Android done 2026-10-04 |
| string | Error state copy | "Failed to load alerts." (alerts_load_error) | ✓ alerts_load_error | ✗ raw error.localizedDescription | misaligned | | |
| string | i18n of alert strings | ✓ t() keys | ✓ 36 keys × de/es/fr/ja | ✗ hardcoded English | ios-missing | | |
| string | Tab labels Active / Historical / System | ✓ | ✓ alerts_*_tab_label | ✓ rawValue | aligned | | |
| element | Alert Rules settings card: list rows (on/off pill, name, "metric op threshold → action", description tag), ⏸/▶ toggle, ✕ delete | ✓ js:21179–21212 | ✓ A:AlertRulesCard.kt AlertRuleRow (Switch + delete) | ✗ | ios-missing | | Android uses Switch where PWA uses ⏸/▶ icon buttons — minor |
| element | Add Alert Rule form: name, description, metric, operator, threshold, source filter, window s, action kind, cooldown s | ✓ js:21232–21250 inline fields | ✓ A:AddAlertRuleDialog (dialog) | ✗ | ios-missing | | PWA inline form vs Android dialog — layout differs |
| element | Notifications card (OS notification settings shortcut, tier line "Alerts via datawatch push (optimal)…") | ✗ | ✓ A:NotificationsCard.kt + alert_tier_* strings | ✗ | n/a | | Android OS-specific |
| element | Notification channels: Input needed HIGH (Reply RemoteInput + Play), Completed, Rate limited, Errors HIGH, MessagingStyle | n/a | ✓ A:NotificationChannels.kt, NotificationPoster.kt:98–117 | ✗ no local notifications | n/a | | iOS equivalents (categories/actions) arrive with APNs #185 |
| element | Car head-unit notification actions (CarAppExtender Play/Reply) | n/a | ✓ A:NotificationPoster.kt:235 | n/a | n/a | | |

## Coverage
rows: 65 · aligned: 20 · ios-missing: 6 · android-missing: 0 · pwa-missing: 7 · misaligned: 24 · n/a: 8

## Decisions needed
1. **🔕 button semantics** — PWA: mute the alert dock for this browser session (no server call, js:15204). Android (A:AlertsScreen.kt ControlBtn("🔕") { dismissAll() }) and iOS (I:AlertsView.swift controlBtn("🔕") { dismissAll() }) both call dismiss-all instead. Options: (a) apps implement a real dock mute like PWA; (b) PWA changes 🔕 to dismiss; (c) remove 🔕 from the apps (they have no dock on iOS).
2. **Dismiss-all semantics** — PWA deletes (POST {all:true, delete:true}, js:19535); Android/iOS only ack (markAlertRead all=true). Options: (a) apps send delete too; (b) PWA stops deleting; (c) keep both and label them differently.
3. **Auto-ack on open** — PWA marks every alert read the moment the Alerts page renders (js:19191), so its unread badge means "arrived since you last looked". Apps keep alerts unread until acted on. Options: (a) apps auto-ack on tab open (PWA rule); (b) PWA drops auto-ack and adopts explicit read state.
4. **Per-alert read affordance** — Android "✓" button and iOS unread dot + dimming exist only because of D3. Resolve with D3: (a) drop if auto-ack adopted; (b) add to PWA if explicit read state adopted.
5. **Empty-state copy** — PWA single "No alerts."; Android per-tab ("No sessions need input. You're caught up." / "No historical alerts."); iOS "No <tab> alerts". Options: (a) PWA string everywhere; (b) adopt Android per-tab copy in PWA + iOS (app had an idea).
6. **Swipe-to-dismiss** — Android swipes a whole session group; iOS swipes a single alert; PWA has neither (hover web). Options: (a) standardise on per-group; (b) per-alert; (c) both; (d) none. Also decides what swipe does given D2.
7. **Watched-session badge filter** (Android only, A:AlertsViewModel.kt:175–182). Options: (a) adopt in PWA + iOS; (b) keep Android-only; (c) remove.
8. **Alert-rule firings list** (Android "Recent Firings (N)", A:AlertRulesCard.kt:132; PWA has rules but no firings). Options: (a) add to PWA Settings; (b) drop from Android.
9. **Header bell/pill** — PWA pill shows dock total (coalesced live toasts) and toggles the dock; Android mirrors it; iOS bell shows server unread count and navigates to the Alerts tab (no dock). Options: (a) iOS builds the dock and matches PWA; (b) all clients switch the pill to unread + navigate; (c) accept iOS difference as n/a (no dock on iOS).
10. **Dock expand animation** — Android animates chevron + AnimatedVisibility (A:AlertDockOverlay.kt:65,149); PWA creates/removes the panel with no transition. Options: (a) add ~150–200 ms transition to PWA; (b) leave PWA static and strip the Android animation.
11. **Live WS alerts on mobile** — PWA consumes WS `alert` (badge++ and 4 s toast); both apps poll REST every 5 s and show no in-app toast (push notifications cover the foreground case on Android). Options: (a) apps consume the already-mapped `alert` frame for badge + a toast; (b) badge only, no toast (push handles it); (c) keep polling.
12. **iOS push path before APNs ships** — PWA/Android have a working push story (browser push / UnifiedPush+ntfy); iOS has only the APNs stub blocked on server BL335. Options: (a) wait for APNs; (b) interim poll-sourced local notifications on iOS (SessionStateWatcher port) so waiting_input alerts reach the lock screen while the app is open.
