# 04 — Alerts

PWA spec §5 (+ §1.2 pill, §11 dock) · live `app.js` `renderAlertsView` 19162–19570, `handleAlert` 879, `renderAlertPill` 936, `pushToAlertDock` 15004, `renderAlertDock` 15044, `updateAlertBadge` 16304, alert rules 21179–21260 · Android `ui/alerts/*`, `ui/alertrules/AlertRulesCard.kt`, `ui/notifications/NotificationsCard.kt`, `ui/detection/DetectionFiltersCard.kt`, `push/*` · iOS `screens/alerts/AlertsView.swift`, `components/AlertsBellButton.swift`, `notifications/NotificationService.swift`.

Refs: `js:` = app.js line · `A:` = Android file:line · `I:` = iOS file:line.

| Cat | Feature | PWA | Android | iOS | Status | Decision | Notes |
|---|---|---|---|---|---|---|---|
| data | List source GET /api/alerts (single server) | ✓ js:19169 on view render | ✓ A:AlertsViewModel.kt:234 5 s poll while mounted | ✓ I:AlertsView.swift `refreshAsync` 5 s sequential poll while visible | aligned | | PWA fetches once per render + ↻; apps poll |
| data | Multi-server aggregate GET /api/alerts/aggregated | ✓ js:19169 when activeServer=='all' | ~ A:AlertsViewModel.kt:200–226 client-side merge of per-profile listAlerts | ✓ picker-bar "All" chip; AlertsViewModel.refreshAllAsync merges every enabled server's /api/alerts (sequential), each alert + session card tagged with its server; links/replies route to the owning server | aligned | decided D2a | Android merge is functionally equivalent · iOS-E 2026-10-04: client-side merge like Android |
| data | Server picker bar on Alerts view | ✓ js:19168 `_injectServerPickerBar` | ✓ A:AlertsScreen.kt AlertsTopBar dropdown incl. All servers | ✓ `ServerPickerBar(showsAll: true)` incl. All chip (iOS-E 2026-10-04) | aligned | decided D2a | All chip tracked on aggregate row |
| data | Saved commands GET /api/commands for quick reply | ✓ js:19171 | ✓ `AlertsViewModel.loadSavedCommands` | ✓ I:AlertsView `IosQuickCommands.loadSaved` on appear | aligned | | Android quick reply is a navigate button |
| data | Sessions GET /api/sessions for liveness (Active vs Historical) | ✓ js:19172 refreshes state.sessions | ✓ A:AlertsViewModel.kt grouping by session state | ✓ I:AlertsView.swift:87 `isActive` — listSessions each poll, Active = live & not done | aligned | | iOS now uses the PWA liveness rule |
| data | Auto-ack all on opening Alerts page POST /api/alerts {all:true} | ✓ js:19191 | ✓ A:AlertsScreen.kt:89 `vm.ackAllOnOpen()` | ✓ I:AlertsView.swift:250 `acknowledgeAll` after each fetch while open (48eaea28) | aligned | decided D49a | |
| data | Dismiss all = POST {all:true, delete:true} | ✓ js:19535 | ✓ A:AlertsViewModel.kt:601 `deleteAllAlerts()` | ✓ I:AlertsView.swift:280 `deleteAllAlerts` | aligned | decided D48a | |
| data | Per-alert mark-read POST /api/alerts {id} | ✗ (auto-ack makes it moot) | ~ A:AlertsViewModel.kt:571 fn kept, no UI | ✓ removed (no per-alert read UI / call) | aligned | decided D49a | iOS should drop with per-alert swipe (D50d) · D49a/D50d done on iOS |
| data | WS `alert` frame → unread++ + toast | ✓ js:879 handleAlert, showToast 4 s | ✓ WebSocketTransport `tryRouteAlertFrame` → `AlertsHub` → A:AppRoot.kt:594 `LiveAlertFeed` dock entry | ✓ `IosAlertFeed` → `LiveAlertFeed` (badge +1 with watched filter, dock entry, id de-dup) | aligned | decided D51a | D51a done on iOS |
| data | Unread count source | state.alertUnread (WS increments, reset on page open) | ~ REST unreadCount + dock ×N pill contributions (AlertDockChannel) | ✓ REST unreadCount → `dw.alert.badge`, bumped by live WS `alert` frames (LiveAlertFeed, D51a) | aligned | decided D51a | iOS lacks the WS increment path · iOS-F 2026-10-05: already done (stale row) |
| data | Alert rules GET/POST/DELETE /api/alert-rules, POST …/enable/disable | ✓ js:21179–21260 (Settings) | ✓ A:AlertRulesCard.kt:73–125 | ✓ I:IosAlertRules.kt list/create/delete/setEnabled | aligned | | Lives in Settings on all three |
| data | Alert rule firings GET (listAlertRuleFirings) | ✗ | ✓ A:AlertRulesCard.kt:76,132 "Recent Firings (N)" ×20 | ✓ I:AlertRulesView.swift:67 "Recent Firings (N)" ×20 | pwa-missing | decided D70a | → #172 |
| data | Detection filters (/api/filters, detection.*_patterns, settle/repeat timing) | ✓ Settings → Detection section | ✓ A:DetectionFiltersCard.kt | ✓ I:SettingsCatalog.swift:370–377 detection.* patterns + alert_settle/repeat; `FiltersView` /api/filters CRUD | aligned | | |
| data | Push delivery: UnifiedPush SSE self-registration (Tier 1) | n/a (browser) | ✓ A:push/UnifiedPushSseService.kt | ✗ | n/a | | Android-specific tier; iOS path is APNs |
| data | Push delivery: ntfy fallback service | n/a | ✓ A:push/NtfyFallbackService.kt | ✗ | n/a | | FCM removed v0.33.17 |
| data | Push delivery: Web Push distributor endpoints (user-entered) | ✓ (browser push) | ✓ A:PushNotificationsCard (Settings) | ✗ (APNs card instead) | aligned | decided D88c | iOS push card = APNs status + test, per D88c |
| data | Push delivery: APNs token registration POST /api/devices/register platform=ios | n/a | n/a (FCM removed) | ~ I:NotificationService.swift:42 registerApnsToken — server #185/BL335 pending | n/a | | Blocked on server |
| data | Push payload deep-link routing (session_waiting/input_needed → session; alert → alerts) | n/a | ✓ A:NotificationPoster.kt:131 deepLinkIntent | ✓ I:NotificationService.swift:49 | aligned | | |
| data | Notification dedup (SSE + ntfy same event) | n/a | ✓ A:NotificationPoster.kt:41–56 (256-entry LRU) | ✗ | n/a | | |
| data | Waiting-state settle window before notifying (45 s) + prompt dedup | ✓ server detection.alert_settle | ✓ A:SessionStateWatcher.kt:47 SETTLE_MS mirrors server | ✓ `LocalAlertWatcher` 45 s settle + prompt dedup + cold-start seed → foreground local notifications (D87b) | aligned | decided D87b | Interim poll-sourced local notifications not built · interim until APNs |
| nav | Alerts in bottom nav with unread badge (99+ cap) | ✓ js:16304 `#alertBadge` | ✓ BottomNavBar badge hidden at 0 (D39a), `alertBadgeLabel` 99+ cap | ✓ I:RootView `.badge(alertBadgeText)` hidden at 0, `99+` cap | aligned |  | Both apps lack the 99+ cap · iOS-F 2026-10-05: both apps cap at 99+ · Android-I 2026-10-05: Android has the cap (stale note); iOS still lacks it |
| nav | Header alert pill 🔔 N always visible | ✓ js:936 states muted/0/N, `--accent2` border when N≥1 | ✓ A:HeaderComponents.kt:102–134 | ✓ `AlertsBellButton` pill (muted / 0 / N) toggles the dock | aligned | decided D3a | iOS to build the pill + dock · D3a done on iOS |
| nav | Pill click → toggle dock | ✓ js toggleAlertDock | ✓ A:HeaderComponents.kt:134 AlertDockChannel.toggle() | ✓ AlertsBellButton → `AlertDock.toggle()` (muted: un-mute + open) | aligned | decided D3a | iOS-F 2026-10-05: already done (stale row) |
| nav | Alert → open session (session-detail) | ✓ js:19253 sessNavBtn / group header link | ✓ A:AlertsScreen.kt onOpenSession (group header name) | ✓ I:AlertsView `sessionLabel` NavigationLink → SessionDetailView | aligned | | |
| element | Tab bar Active / Historical / System with counts | ✓ js:19480 `output-tab` buttons | ✓ A:AlertsScreen.kt:131–153 Tab() | ✓ I:AlertsView `tabRow` "Tab (N)" via tabCount | aligned | | |
| data | Tab + per-tab filter state persisted | ✓ localStorage cs_alerts_active_tab + _alertsPersistTabState | ✓ A:AlertsViewModel.kt:255–275 prefs alerts_<tab>_chip/sort/search | ✓ I:AlertsView UserDefaults dw.alerts.activeTab + dw.alerts.tab.<tab> | aligned | | |
| element | Default tab = first with entries (Active→Historical→System) | ✓ spec §5.1 | ✓ restores last tab (default Active) | ~ I restores last persisted tab | aligned | | Minor (unverified: live PWA may also restore persisted tab) · Android-I 2026-10-05: Live PWA restores `cs_alerts_active_tab` (default 'active', app.js:19329) — both apps match |
| element | Filter bar row 1: "🔔 N alerts" + ⏷/🕒 sort + ✕ + 🔕 + ↻ | ✓ js:19500–19525 | ✓ A:AlertsScreen.kt:184–225 | ✓ I:AlertsView `filterBar` | aligned | | |
| interaction | Sort toggle by session ↔ chronological (persisted per tab) | ✓ js setAlertsSort | ✓ A:AlertsViewModel.kt SortMode | ✓ I:AlertsViewModel `SortMode`, persisted per tab | aligned | | |
| interaction | ✕ dismiss all | ✓ js dismissAlertsAll (delete) | ✓ A:AlertsScreen.kt:222 → deleteAllAlerts | ✓ I:AlertsView.swift:435 → deleteAllAlerts | aligned | decided D48a | |
| interaction | 🔕 button | ✓ muteAlertDock — session-scoped dock mute, no server call | ✓ A:AlertsScreen.kt:224 `AlertDockChannel.mute()` | ✓ AlertsView 🔕 → `AlertDock.mute()` | aligned | decided D47a | iOS still dismiss-all; needs dock mute · D47a done on iOS |
| interaction | ↻ refresh | ✓ | ✓ | ✓ | aligned | | |
| element | Chips all / 🟡 prompts / 🔴 errors / 🟠 warn / ⚪ info with counts | ✓ js:19505–19509 | ✓ A:AlertsScreen.kt chip row | ✓ I:AlertsView `severityChip` "all / 🟡 prompts / 🔴 errors / 🟠 warn / ⚪ info ×N", PWA chipBtn colours (bg2 + colour border, filled when active) | aligned |  | iOS casing/wording differs from PWA · iOS-F 2026-10-05: PWA alert_chip_* copy + styling |
| token | Chip colors: text2 / warning / error / warning / text2; selected = filled | ✓ js chipBtn | ✓ A chipBorderColor + chipBg | ✓ I severityChip | aligned | | |
| element | Prompt category rule: session waiting_input OR title matches needs input / prompt / waiting (regex, case-insensitive) | ✓ js catOf | ✓ A:AlertsScreen.kt + AlertsViewModel.isPromptAlert | ✓ `AlertsViewModel.isPrompt/category` (PWA catOf: waiting_input session or title regex; exclusive) | aligned | | iOS has sessions loaded but ignores waiting_input + title |
| interaction | Live text search (title+body) | ✓ js alertsSearchInput | ✓ A OutlinedTextField alert_search_ph | ✓ I filterText + clear button | aligned | | |
| element | By-session group card: header bg2, name link, state text, "N alerts · 🟡 P", "last HH:MM:SS" mono | ✓ js:19470–19485 | ✓ A:AlertGroupCard | ✓ I:AlertsView `groupHeader` | aligned | | |
| interaction | Group header tap collapses/expands | ✓ js toggles display | ✓ A onToggleExpand ▼/▶ | ✓ I:AlertsView `collapsed` set | aligned | | |
| element | Group ordering waiting → running → others; System card last | ✓ js:19631 stateRank | ✓ `alertGroupStateRank` waiting → running → others, then recency | ✓ I:AlertsViewModel `groups` stateRank | aligned | | Android orders by recency |
| element | Chronological mode: flat newest-first with tiny session link | ✓ js:19430–19445 | ✓ A flatChrono AlertsScreen.kt:303 | ✓ I:AlertsView chrono list with per-row session link | aligned | | |
| element | Alert row: 3px left border + bg tint by category | ✓ js renderRow | ✓ A:AlertCard | ✓ I:AlertRow border/bg | aligned | | |
| element | Kind badge 🟡 PROMPT / 🔴 ERROR / ⚪ level (bg2) | ✓ js kindBadge | ✓ A badgeText/Bg/Fg | ✓ I:AlertsView 🟡 PROMPT / 🔴 ERROR / ⚪ warn / ⚪ info on bg2; warn rows use plain border + transparent bg like PWA | aligned |  | PWA shows warn as ⚪ warn · iOS-F 2026-10-05: Android renders `⚪ warning` (severity name) vs PWA `a.level` |
| element | Time: HH:MM:SS mono, opacity .55 | ✓ js | ✓ A formatAlertTime | ✓ I alertTime monospaced | aligned | | |
| element | Title 13px/600, body 12px text2 | ✓ | ✓ | ✓ (bodyMedium/labelSmall) | aligned | | iOS clamps title 2 / body 3 lines; Dynamic Type per D7b |
| element | Quick reply `<select>` of saved commands on prompt alerts → alertSendCmd | ✓ js:19413 grouped (latest alert, waiting) + js:19615 chrono (prompt) | ✓ "Quick reply… ▾" saved-commands menu → send to session (grouped: latest alert of waiting session; chrono: prompt alerts) | ✓ "Quick reply… ▾" saved-commands Menu → send to session (grouped: latest alert of a waiting session; chrono: prompt alerts); hidden when no saved commands | aligned |  | Android sends nothing; iOS adds built-ins, no chrono · Android aligned; iOS adds built-ins, no chrono (2026-10-04) · Task-O 2026-10-05: iOS built-in replies removed (PWA lists saved commands only) and chrono prompt alerts get the menu |
| element | Per-alert ✓ mark-read control / unread dot | ✗ | ✗ removed | ✗ no unread dot / read dimming (D49a) | aligned | decided D49a | iOS should drop read UI · iOS-F 2026-10-05: already removed (stale row) |
| interaction | Swipe-left dismisses a session group (80 dp threshold) | ✗ | ✗ removed | ✗ | aligned | decided D50d | |
| interaction | Swipe-left dismisses a single alert | ✗ | ✗ | ✗ removed 2026-10-04 | aligned | decided D50d | D50d done on iOS |
| data | Watched-session filter (badge counts only watched sessions) | ✗ | ✓ A:AlertsViewModel.kt:175–182, 480–493 | ✓ I:AlertsView.swift:20 `LocalSessionPrefs.badgeCount` | pwa-missing | decided D61a | → #172 |
| element | Alert dock panel (header chips per type, collapse chevron, ✕, 🔕; body cards with ×N, 3-line clamp, left rail) | ✓ js:15044 | ✓ A:AlertDockOverlay.kt + AlertDockChannel.kt | ✓ `AlertDockPanel` (type ×N chips, ⌄, ✕, 🔕; cards with rail, ×N, ✕, 3-line clamp ▸ more) | aligned | decided D3a | |
| data | Dock coalescing: family key, 60 s window, ×N, max 100 | ✓ js:15004–15040 | ✓ A:AlertDockChannel.kt:42–43 MAX_ENTRIES 100, 60 s window | ✓ `AlertDock.post` family key, 60 s, ×N, max 100 | aligned | decided D3a | |
| data | Dock mute persisted per browser session (sessionStorage cs_alert_muted) | ✓ js:15204 | ✓ A:AlertDockChannel.kt:48,77 in-memory per app session | ✓ AlertDock.muted in-memory per app run (D47a, same as Android) | aligned | decided D47a | iOS-F 2026-10-05: already done (stale row) |
| motion | Dock expand/collapse animation | ✗ static (panel created/removed) | ✗ static | n/a (no dock) | aligned | decided D36b | |
| motion | Alert card hover bg3 transition .15 s | ✓ style.css:2232–2242 | n/a (touch) | n/a | n/a | | |
| motion | Toast on WS alert, 4 s | ✓ js:891 showToast | ✓ dock entry (toasts retired, D41a) | ✓ dock entry (D41a/D51a) | aligned | decided D51a | |
| motion | Loading state | ✓ spinner "Loading…" (common_loading) | ✓ spinner + "Loading…" (common_loading) | ✓ LoadingIndicator "Loading…" | aligned |  | Minor copy drift · iOS-E 2026-10-04: iOS adopts common_loading · Android-I 2026-10-05: Android aligned |
| string | Empty state copy | "No alerts." (common_no_alerts) | ✓ "No alerts." (`alerts_empty`) every tab | ✓ "No alerts." every tab | aligned | decided D35a | iOS to adopt "No alerts." · iOS-E 2026-10-04: per D35a |
| string | Error state copy | "Failed to load alerts." (alerts_load_error) | ✓ banner "Failed to load alerts." (alerts_load_error) | ✓ ErrorCard "Failed to load alerts." (alerts_load_error) | aligned |  | Neither app uses alerts_load_error copy · iOS-E 2026-10-04: iOS now PWA copy; Android still banner · Android-I 2026-10-05: Android aligned |
| string | i18n of alert strings | ✓ t() keys | ✓ alert keys × de/es/fr/ja | ✓ I:Resources/*.lproj/Localizable.strings (alerts strings) | aligned | | |
| string | Tab labels Active / Historical / System | ✓ | ✓ alerts_*_tab_label | ✓ rawValue (localized) | aligned | | |
| element | Alert Rules settings card: list rows (on/off pill, name, "metric op threshold → action", description tag), ⏸/▶ toggle, ✕ delete | ✓ js:21179–21212 | ~ A:AlertRulesCard.kt AlertRuleRow (Switch + delete) | ✓ I:AlertRulesView.swift ⏸/▶ + rule text | aligned | | Android Switch vs ⏸/▶ — minor |
| element | Add Alert Rule form: name, description, metric, operator, threshold, source filter, window s, action kind, cooldown s | ✓ js:21232–21250 inline fields | ✓ A:AddAlertRuleDialog (dialog) | ✓ I:AlertRulesView.swift:94–110 form section | aligned | | Layout differs per platform (D4a) |
| element | Notifications card (OS notification settings shortcut, tier line) | ✗ | ✓ A:NotificationsCard.kt + alert_tier_* strings | ✗ | n/a | | Android OS-specific; iOS push card per D88c in §07 |
| element | Notification channels: Input needed HIGH (Reply RemoteInput + Play), Completed, Rate limited, Errors HIGH, MessagingStyle | n/a | ✓ A:NotificationChannels.kt, NotificationPoster.kt:98–117 | ✗ no local notifications | n/a | | iOS categories/actions arrive with APNs #185 / D87b |
| element | Car head-unit notification actions (CarAppExtender Play/Reply) | n/a | ✓ A:NotificationPoster.kt:235 | n/a | n/a | | |

## Coverage
rows: 65 · aligned: 54 · ios-missing: 0 · android-missing: 0 · pwa-missing: 2 · misaligned: 1 · n/a: 8

## Decisions (resolved 2026-10-04)
1. 🔕 semantics → **D47a** apps implement a real dock mute (Android done; iOS pending, needs dock).
2. Dismiss-all → **D48a** apps delete too (done on both).
3. Auto-ack on open → **D49a** apps auto-ack (done on both).
4. Per-alert read affordance → **D49a** drop it (Android done; iOS still shows unread dot + swipe mark-read).
5. Empty-state copy → **D35a** PWA "No alerts." everywhere (Android done; iOS pending).
6. Swipe-to-dismiss → **D50d** none (Android done; iOS still swipes single alerts).
7. Watched-session badge filter → **D61a** adopt in PWA + iOS (iOS done; PWA → #172).
8. Alert-rule firings → **D70a** add to PWA (iOS done; PWA → #172).
9. Header bell/pill → **D3a** PWA pill as-is; iOS builds the alert dock (pending).
10. Dock expand animation → **D36b** static everywhere (done).
11. Live WS alerts → **D51a** badge + in-app toast/dock (Android done; iOS pending).
12. iOS push before APNs → **D87b** interim poll-sourced local notifications (iOS pending).
