# 06 — Observer page

**PWA reference:** `renderObserverView()` app.js:23323 (view id `observer`, header "Observer", FAB hidden). Cards are collapsible `settingsSectionHeader()` sections with per-key persisted state and a docs link per header. Loaders fire on view render (app.js:23523–23533): `loadSystemStatsGrid`, `loadStatsPanel`, `loadSchedulesList`, `loadCooldownStatus`, `loadAnalyticsPanel`, `loadAuditPanel`, `loadKgPanel`, `loadCommBackendsStatus`, `renderObserverPeersCard`.
**Android:** `ui/observer/ObserverScreen.kt:92–114` (flat column of 21 cards on the shared collapsible `PwaCard`; `StatsScreenContent` embeds the stats sub-cards). **iOS:** `screens/observer/ObserverView.swift` + `ObserverStatsSection` / `ObserverPeersViews` / `ObserverOpsSections` / `ObserverComponents` (parity B20–B25, PWA card order) backed by `shared/.../di/IosObserver.kt`. Re-audited against code 2026-10-04.
Spec §7 is stale: live PWA adds per-system grid (BL379), eBPF/network, plugins, peer resources, observer peers, cluster, channel bridge + diagnostics, comm backends, Matrix, web-search stats, RTK, episodic-memory stats inside §7.1.

| Cat | Feature | PWA | Android | iOS | Status | Decision | Notes |
|---|---|---|---|---|---|---|---|
| nav | Header title "Observer", no FAB | ✓ app.js:23323 | ✓ title "Observer" | ✓ HeaderView "Observer" | aligned | decided D2a | Android header title is the server dropdown; D2a wants title + picker bar · Android header title is the server dropdown; D2a wants title + picker bar · per D2a (2026-10-04) · Android-I 2026-10-05: Android verified: TopAppBar title "Observer" + ServerPickerBar below (D2a) |
| nav | Server picker in header (multi-profile) | ✓ `_injectServerPickerBar` app.js:23721 | ✓ `ServerPickerBar` | ✓ shared `ServerPickerBar` (D2a) | aligned | decided D2a | Android still header dropdown; iOS done · PWA has the picker bar here too; both apps should use the picker bar (iOS ServerPickerBar exists for Sessions/Alerts) · Android done (D2a); iOS segmented picker (2026-10-04) · Android-I 2026-10-05: Android verified: ServerPickerBar |
| nav | Per-card docs link (settingsSectionHeader 3rd arg) | ✓ e.g. `flow/observer-flow.md`, `memory.md` | ✓ per-card "?" link in the shared PwaCard header (PWA defsLink slug), page link kept in TopAppBar | ✓ per-card DocsLinkButton (ObsSection, defsLink slug) | aligned | decided D26a | per D26a · Android 5a209324 |
| interaction | Collapsible cards with disclosure chevron, state persisted | ✓ `secContent(key)` + localStorage | ✓ PwaCard + PwaCardCollapseStore (theme/PwaCard.kt; PWA keys, default expanded) | ✓ ObsSection + ObserverCollapseStore (UserDefaults) | aligned | decided D27a | per D27a · Android 5a209324 |
| motion | Chevron rotate on collapse `transition:transform 0.15s` | ✓ app.js:23371 | ✓ PwaCard chevron rotate tween(150) (PwaCard.kt:100) | ✓ chevron.right rotate easeInOut 0.15 s | aligned | decided D27a |  |
| element | Card order | grid → System Statistics → Memory Browser → Memory Maintenance → Scheduled Events → Global Cooldown → Session Analytics → Audit Log → Knowledge Graph → Daemon Log → Federated Peers | ✓ nested collapsible System Statistics block (grid, stats, eBPF, plugins, peer resources, cluster, MCP channel, diagnostics, comm incl. Matrix) → Memory → Maintenance → Schedules → Cooldown → Analytics → Audit → KG → Daemon log → Federated peers | ✓ PWA order, nested stats block (ObserverView.observerContent) | aligned | decided D28a | Android still flattened; must adopt PWA order · per D28a (2026-10-04) |
| element | Loading state per card ("Loading…" placeholder) | ✓ per block | ✓ no full-screen spinner; cards show muted "Loading…" (`PwaLoadingText`, common_loading) | ✓ per-block "Loading…" (ObsMuted) | aligned |  | Android-I 2026-10-05: Android aligned |
| element | Empty/no-server state | n/a (always connected) | ✓ banner "Add a server in Settings to monitor metrics." | ✓ emptyStateView eye.slash "No server connected" | aligned | | |
| **7.1 System Statistics** | | | | | | | |
| data | `/api/stats` fetch for statistics panel | ✓ loadStatsPanel app.js:19587 | ✓ StatsViewModel.doRefresh | ✓ ServiceLocatorAsync.getStats | aligned | | |
| data | WS `stats` frame → live overlay | ✓ `case 'stats'` app.js:590 → state.statsData | ✓ StatsHub.flow (v1.23.113) | ✓ subscribeGlobalStream onStats (v1.23.116) | aligned | | |
| data | REST fallback cadence | one-shot on view render (+WS) | ✓ one-shot on render + WS (30 s re-poll dropped) | ✓ one-shot + WS | aligned | decided D54b | Android must drop the 30 s re-poll · per D54b (2026-10-04) |
| data | Per-system grid refresh | 8 s setInterval app.js:19896 | ✓ 8 s | ✓ 8 s (ObserverViewModel liveIntervalNs) | aligned |  | Android 10 s → 8 s |
| element | Per-system grid (local + each observer peer, one card) | ✓ `#perSystemGrid` app.js:19773 | ✓ SystemStatsGridCard (LocalSystemCard, PeerSystemCard) | ✓ ObserverSystemGrid ← IosObserver.loadSystems | aligned | | |
| element | Grid card: name + "local" badge + dot | ✓ sysCard() | ✓ Text("local") | ✓ SystemCardView | aligned | | |
| element | Grid CPU bar: pct · load1/5/15 | ✓ app.js:19822 | ✓ StatBar "CPU" loadStr | ✓ pct · load1/5/15 | aligned | | |
| token | Grid CPU color thresholds | >80 error / >50 warning / success | ✓ >80 / >50 (strict) | ✓ >80 / >50 / success (PWA verbatim) | aligned | decided D29a | iOS = PWA; Android uses ≥ (boundary differs) · per D29a (2026-10-04) |
| element | Grid RAM bar used/total | ✓ `bar('RAM')` >85 error | ✓ StatBar "RAM" | ✓ used/total, >85 error | aligned | | |
| element | Grid GPU util / temp / power / VRAM per GPU | ✓ app.js:19857–19872 | ✓ labels "GPU util/temp/VRAM" | ✓ util/temp/power/VRAM per GPU | aligned | | |
| token | GPU temp thresholds ≥80 error / ≥60 warning | ✓ | ✓ (PeerResources/grid) | ✓ tempTone | aligned | | |
| element | Statistics panel bars: CPU Load (load/cores), Memory, Disk, Swap (if >0), GPU util+temp, GPU VRAM | ✓ renderStatsData app.js:20473–20486 | ✓ SystemStatisticsCard | ✓ IosObserver.buildStatsPanel bars | aligned | | |
| token | Statistics panel thresholds: CPU >80/>50, Memory >85, Disk >90, GPU >80 | ✓ app.js:20475–20486 | ✓ per-metric PWA values (`statsMetricTone`) | ✓ PWA per-metric values (D29a) | aligned | decided D29a | iOS = PWA; Android ≥90/≥70 · per D29a (2026-10-04) |
| element | GPU probe failed card (red, grid-column 1/-1) | ✓ app.js:20492 | ✓ `GpuProbeFailedCard` StatsScreen.kt:993 | ✓ GpuProbeFailedCard ObserverStatsSection.swift:141 | aligned |  |  |
| element | Network card "(datawatch)" vs "(system)" label by ebpf_active, ↓ Download / ↑ Upload | ✓ app.js:20498 | ✓ NetworkCard | ✓ | aligned | | |
| element | Daemon card: Memory RSS, Uptime (h m / m s) | ✓ app.js:20507 | ✓ DaemonCard | ✓ RSS, goroutines, FDs, uptime h m / m s | aligned | | |
| element | Infrastructure card | ✓ app.js:20519 | ✓ InfrastructureCard | ✓ | aligned | | |
| element | RTK Token Savings (version, saved tokens, update badge → copy cmd) | ✓ app.js:20542 | ✓ RtkCard StatsScreen.kt:231 (version, hooks, saved, avg %, commands; no update badge) | ✓ (latest version from raw /api/stats) | aligned |  | update badge tracked next row |
| interaction | RTK update badge click → copy upgrade command (toast) | ✓ data-cmd BL223 | ✓ "→ update available" in RtkCard, tap copies the upgrade one-liner (no latest-version text: StatsDto lacks it) | ✓ tap → UIPasteboard + toast (ObserverStatsSection.swift:128) | aligned |  |  |
| element | Episodic Memory stats inside stats panel | ✓ app.js:20555 | ✓ MemoryStatsCard | ✓ | aligned | | |
| element | Server info card (hostname, version, host, port) | ✗ (lives in Settings → About) | ✓ ServerInfoCard StatsScreen.kt:461 | ✓ (IosObserver.loadServerContext) | pwa-missing | decided D78a | → #172 (PWA Observer) |
| element | Session Statistics ring (total/max_sessions) + running/waiting/complete/failed counts | ✓ donut in renderStatsData (app.js:20659) | ✓ ring success colour | ✓ donut active/max + counts (PWA success colour) | aligned | decided D78a | Android ring colours by threshold; PWA/iOS do not |
| data | `session.max_sessions` from /api/config for ring denominator | ✓ state._maxSessions (used elsewhere) | ✓ StatsViewModel maxSessions | ✓ loadServerContext | aligned | | |
| element | Ollama Server card | ✓ renderStatsData Ollama card (app.js:20756) | ✓ OllamaStatsCard | ✓ inside stats panel (IosObserver.kt:484) | aligned | decided D78a |  |
| element | Process envelopes card | ✗ | ✓ EnvelopesCard StatsScreen.kt:352 | ✓ Process Envelopes card (IosObserver.kt:495) | pwa-missing | decided D78a | → #172 |
| element | Backend health card | ✗ | ✓ BackendHealthCard StatsScreen.kt:387 | ✓ Backend Health card (IosObserver.kt:506) | pwa-missing | decided D78a | → #172 |
| element | eBPF "Degraded" banner (built without eBPF / not active) | ✓ renderStatsData banner (app.js:20672) | ✓ StatsScreen:67–92 | ✓ EbpfBanner | aligned | decided D78a |  |
| element | eBPF status line (live / configured+cap / cap missing / off, colored dot) | ✓ loadEBPFStatus app.js:19905 (`/api/stats?v=2`) | ✓ EBpfStatusCard | ✓ ObserverEbpfBlocks | aligned | | |
| element | Network Traffic per-process table (Process / In / Out) | ✓ loadEBPFNetworkTraffic | ✓ EBpfNetworkCard "Network (by process)" | ✓ NetTrafficTable (top 10) | aligned | | |
| string | "No eBPF data available" | ✓ t('ebpf_no_data') | ✓ EBpfNetworkCard always renders; empty → `ebpf_no_data` "No eBPF data available" (.7) | ✓ "No eBPF data available" | aligned |  | Android copy aligned 2026-10-04 |
| element | Installed plugins list (version + status) | ✓ loadPluginsStatus `/api/plugins` | ✓ PluginsCard | ✓ ObserverPluginsBlock | aligned | | |
| element | Peer Resources: per peer CPU %, Mem used/total, GPU util/temp/power/VRAM chips, shape tag, "no snapshot" | ✓ loadPeerResourceOverview app.js:19701 | ✓ PeerResourcesCard (obs_cn_* strings) | ✓ ObserverPeerResourcesBlock | aligned | | |
| data | Peer snapshots `/api/observer/peers/{name}/stats` in parallel | ✓ | ✓ | ✓ shared with grid (loadSystems) | aligned | | |
| element | Peer Resources refresh 8 s | ✓ | ✓ delay(8_000) | ✓ | aligned | | aligned cadence |
| interaction | Peer row tap → compute node / peer detail | ✗ (snapshot modal instead) | ✗ (PeerResourcesCard rows no longer clickable) | ✗ | aligned | decided D55a | per D55a — no client navigates; snapshot modal instead |
| element | Observer peers block: filter pills with counts (all/free/attached…) | ✓ All/Agents/Standalone/Cluster with counts, cs_peer_filter (app.js:20221–20240) | ✓ All/Agents/Standalone/Cluster with counts | ✓ All/Agents/Standalone/Cluster + counts, cs_peer_filter | aligned |  | Android pills lack counts and PWA order |
| interaction | Group-by-compute-node toggle | ✓ togglePeerGroupByNode | ✓ peer_group_by_node | ✓ meta-peers buckets | aligned | | |
| interaction | "Cross-host view" button → showCrossHostView() | ✓ app.js:20061 | ✓ ↔ Cross-host view dialog (`/api/observer/envelopes/all-peers`, 🔗 cross tags) | ✓ ↔ Cross-host view pill → CrossHostSheet (by peer, listen / outbound, 🔗 cross callers) | aligned |  | Android FederatedPeersCard CrossHostDialog; iOS missing |
| interaction | 📊 Snapshot button → showObserverPeerSnapshot modal | ✓ | ✓ 📊 → `PeerSnapshotDialog` (envelopes sorted by CPU; no per-envelope drill-down, like iOS) | ✓ PeerSnapshotSheet (envelopes; no per-envelope process drill-down) | aligned | decided D55a | Android needs 📊 snapshot modal · Android needs 📊 snapshot modal · per D55a (2026-10-04) |
| interaction | × Remove peer (rotates token) with confirm | ✓ removeObserverPeer | ✓ × with confirm → removeObserverPeer (FederatedPeersCard.kt:278) | ✓ alert confirm | aligned | decided D55a |  |
| element | Peer dot colors by last push: green <15 s / amber <60 s / red ≥60 s / grey never | ✓ | ✓ FederatedPeersCard Canvas + relative age (v1.23.4) | ✓ | aligned | | |
| element | Shape badge A/B/C (agent/standalone/cluster) | ✓ grey outline word tag agent/standalone/cluster (app.js:20269) | ✓ neutral word tag agent/standalone/cluster (1 px text2 outline, .55, 11 sp, radius 3) | ✓ | aligned |  | Android colours the tag; PWA neutral; iOS (unverified) · Android-I 2026-10-05: Android aligned |
| string | Empty: "no peers registered" + deploy hint `datawatch-stats --datawatch <url> --name <peer>` | ✓ app.js:20066 | ✓ "no peers registered" + deploy hint (both views); filter-empty → "no peers match the \"x\" filter" | ✓ with deploy hint | aligned |  | Android copy aligned 2026-10-04 |
| element | "attached to ComputeNode" tag on peer | ✓ t('observer_attached_to') | ✓ (observerPeer != null filter, v1.23.4) | ✓ ⇄ node / free tag | aligned | | |
| element | Cluster nodes block (hidden until non-empty) | ✓ loadObserverClusterNodes `/api/observer/stats` | ✓ ClusterNodesCard | ✓ ObserverClusterBlock | aligned | | |
| element | MCP channel bridge status (collapsed chevron block) | ✓ `/api/channel/info` app.js:19608 | ✓ McpChannelCard (ui/about) | ✓ DisclosureGroup | aligned | | |
| element | Channel diagnostics block + refresh button | ✓ `/api/channel/diagnostics` BL362 app.js:19656 | ✓ `ChannelDiagnosticsCard` + ↻ + hints | ✓ + refresh + hints | aligned |  |  |
| element | Communication backends status (from /api/config) + Matrix status + "Test" button | ✓ loadCommBackendsStatus app.js:23538 | ✓ CommBackendsCard: enabled services in PWA order, Matrix row inline with live status + Test; MatrixStatusCard removed | ✓ Matrix inline like PWA | aligned | | Android splits Matrix into its own card · Android-I 2026-10-05: Android aligned (shows "connected" — DTO has no user_id) |
| interaction | Matrix "Test" → POST /api/matrix/test → toast | ✓ | ✓ Matrix row "Test" button in the comms block → dock entry | ✓ toast | aligned |  |  |
| element | Web Search stats: per-provider totals, Total/Today/This week/This month/Cache hits, 14-day bar graph, "History" button | ✓ renderWebSearchStatsHTML app.js:20407 (`/api/websearch/stats?days=14`) | ✓ WebSearchCardV2 (totals, today/week, sparkline) StatsScreen.kt:890 + WebSearchRegistryCard | ✓ (refresh every 3rd 8 s tick) | aligned |  |  |
| interaction | Web search history view (last 50, live/cache/error) | ✓ webSearchOpenHistoryView | ✓ History → last 50 (live / cache / error) | ✓ sheet | aligned |  |  |
| **7.2 Memory Browser** | | | | | | | |
| element | Search input + role filter (All/Manual/Session/Learning/Chunks) + since (All/7d/30d/90d) | ✓ app.js:23402 | ✓ role + since filters on the List tab | ✓ PWA role/since filters | aligned |  | Android lacks role + since filters |
| interaction | Search / List / Export buttons | ✓ | ✓ Text("Search"), Text("Export…"), Text("Test") | ✓ (Export → share sheet) | aligned | | Android adds "Test" |
| element | Memory stats cards: Total Memories / Manual / Session / Learnings / Chunks / DB Size | ✓ loadMemoryStats app.js:14276 | ✓ MemoryCard StatsGrid | ✓ | aligned | | |
| interaction | Add memory dialog (text, tags) | ✗ in Observer | ✓ memory_add_title AlertDialog (MemoryCard.kt:692) | ✓ alert text + tags (D78a) | pwa-missing | decided D78a | → #172 |
| element | Results list max-height 400 scroll | ✓ | n/a (LazyColumn) | ✓ | aligned | | |
| **7.3 Memory Maintenance** | | | | | | | |
| element | 2×2 grid: Similarity-stale eviction (days, Dry-run/Apply), Spellcheck, Extract facts, Schema version check | ✓ app.js:23425–23456 | ✓ eviction dry-run only + "Apply … web UI only" | ✓ Dry-run only ("Apply … web UI only") | aligned | decided D89b | per D89b iOS correct; Android still allows Apply · per D89b (2026-10-04) |
| interaction | Apply eviction confirm() with explanatory text | ✓ app.js:14306 | n/a (D89b dry-run only) | n/a (D89b) | aligned | decided D89b | Android should drop Apply (dry-run only on phones) · per D89b (2026-10-04) |
| string | Card title "Memory Maintenance" vs "Mempalace" | ✓ | ✓ "Memory Maintenance" | ✓ | aligned |  | Android copy |
| **7.4 Scheduled Events** | | | | | | | |
| data | `/api/schedules` list; Android polls 15 s | ✓ one-shot | ✓ one-shot + after mutations | ✓ one-shot on expand | aligned |  | iOS = PWA; Android 15 s poll |
| element | Row: label/command, run_at, cron badge, target | ✓ app.js:20902 | ✓ SchedulesCard `scheduleRowLabel()`: "NEW: <name>" for deferred sessions, else "<session_name or session_id> [<schedule_name>]: <command>"; `cron` badge (bg2) when `cron_expr` set; run_at / cron line; PWA `state` text (uppercase, pending=warning, done=success) | ✓ PWA label: "NEW: <name>" for deferred sessions, else "<session_name or session_id> [<schedule_name>]: <command>"; run_at, cron badge (now from `cron_expr`), state | aligned |  | iOS-H 2026-10-05: ScheduleDto/Schedule gained session_name, schedule_name, type, deferred_session, cron_expr. Android-J 2026-10-05: Android label + cron badge match PWA (ScheduleRowLabelTest). Android status chip still enabled/disabled vs PWA `state` text · Task-O 2026-10-05: Android status chip now shows the PWA `state` text (enabled/disabled only as a fallback for servers without `state`) |
| interaction | Select-all checkbox + "Delete selected" | ✓ | ✓ select-all + Delete selected (confirm) | ✓ | aligned |  |  |
| interaction | Edit (pencil) via browser prompt() ×2 | ✓ editSchedulePrompt | ✓ ✎ → two prompt dialogs (command, ISO time) | ✓ alert with 2 TextFields (prompt equivalent) | aligned | decided D56b | Android needs edit via two prompts; iOS 2-field alert = prompt equivalent · per D56b (2026-10-04) |
| interaction | Delete (🗑) → toast "Deleted" | ✓ | ✓ | ✓ | aligned | | |
| interaction | Pagination Prev/Next | ✓ settingsPagination.schedules | ✓ SchedulesCard 10/page Prev/Next | ✓ | aligned | | already present (verified 2026-10-04) |
| **7.5 Global Cooldown** | | | | | | | |
| element | Status: active until / none | ✓ loadCooldownStatus | ✓ CooldownCard | ✓ | aligned | | |
| interaction | "Set for:" 5m / 15m / 30m / 1h buttons | ✓ 15m/30m/1h/4h/8h/24h + reason (app.js:25545) | ✓ 15m–24h + reason (CooldownCard.kt:126) | ✓ 15m–24h + reason | aligned |  |  |
| interaction | Clear button (red tint) → toast | ✓ | ✓ Text("Clear Cooldown") | ✓ | aligned | | |
| **7.6 Session Analytics** | | | | | | | |
| element | Range selector (days) + table Date / Total / OK / Err / Bar | ✓ loadAnalyticsPanel `/api/analytics?range=Nd` | ✓ SessionAnalyticsCard ("Range:", barColor) | ✓ | aligned | | |
| string | "No sessions in range." | ✓ | ✓ "No sessions in range." SessionAnalyticsCard.kt:109 | ✓ | aligned |  |  |
| **7.7 Audit Log** | | | | | | | |
| element | Filters actor / action + limit 5/20/50/100 + Load | ✓ loadAuditPanel | ✓ AuditLogCard ("Actor", "Action", "Load") | ✓ | aligned | | |
| element | Pipelines live block (8 s, Cancel button) rendered under audit | ✗ in Observer (Settings › Automata pipelinesPanel app.js:7071) | ✓ Settings only (Observer copy dropped) | ✗ in Observer (Settings › Pipeline Manager) | aligned |  | earlier row mis-placed PWA; Android should drop the Observer copy |
| **7.8 Knowledge Graph** | | | | | | | |
| element | Entity query input + Query; triples result | ✓ loadKgPanel `/api/memory/kg/query` | ✓ KnowledgeGraphCard ("Entity", "Query") | ✓ | aligned | | |
| interaction | Add triple (Subject/Predicate/Object) → toast | ✓ `/api/memory/kg/add` | ✓ ("Subject","Predicate","Object","Add triple") | ✓ | aligned | | |
| element | Identity panel (`/api/identity`) under KG | ✗ in Observer (Settings identityPanel app.js:6910) | ✓ Settings only (Observer copy dropped) | ✗ in Observer (Settings › Identity) | aligned |  | earlier row mis-placed PWA; Android should drop the Observer copy |
| **7.9 Daemon Log** | | | | | | | |
| element | Monospace panel, dark bg, max-height 300 | ✓ | ✓ DaemonLogCard | ✓ | aligned | | |
| interaction | Newest / Older (50 lines, offset counter) | ✓ loadDaemonLog | ✓ "Newest" / "Older" + "Showing N of T lines (offset O)" in the button row (DaemonOpsCards.kt) | ✓ Newest/Older | aligned |  | Android copy aligned 2026-10-04 |
| data | Auto-refresh | ✓ setInterval app.js:12513 | ✓ delay(10_000) | ✓ 10 s | aligned | | |
| element | Daemon ops cards: Network interfaces, Kill orphans, Daemon update, Restart daemon, Hot-reload subsystem | ✗ in Observer (PWA: Settings) | ✓ ops/DaemonOpsCards (not mounted in ObserverScreen — Settings) | ✗ | n/a | | belongs to 07 |
| **7.10 Federated Peers (bottom card)** | | | | | | | |
| element | Stats row pills (`/api/observer/stats`) + Config table (`/api/observer/config`) + peer list; "live" dot, 8 s | ✓ renderObserverPeersCard app.js:23712 | ✓ FederatedPeersCard 8 s (monitoring) | ✓ ObserverFederatedPeersCard 8 s | aligned | | |
| element | Federation peers (server-to-server) list: enabled dot, URL, capability group badge, Test / Delete | ✓ Settings › Comms (loadFederationPeersPanel app.js:23776) | ✓ Settings FederationPeersCard (SettingsScreen.kt:353) | ✓ Settings list fed_peers (SettingsCatalog.swift:238) | aligned |  | all three host it in Settings (07); iOS row fields (unverified) |
| interaction | Test peer → toast; Delete peer → confirm | ✓ | ✓ FederationPeersCard | ✓ Settings fed_peers row action Test (OK — latency (version) / FAIL: error) + swipe delete w/ confirm | aligned |  | Android Test (unverified) |
| **iOS-only today** | | | | | | | |
| element | 2×3 metric tiles with icon + value + 4 px bar | ✗ | ✗ | ✗ removed (D30a) | aligned | decided D30a | per D30a |
| element | "Updated Xs ago" line | ✗ | ✗ | ✗ removed | aligned | decided D30a | per D30a |
| string | Empty copy "Add a server in Settings to monitor metrics." | n/a | ✓ "Add a server in Settings to monitor metrics." (observer_no_server) | ✓ | aligned |  | no PWA string; unify app copy (D35a spirit) · Android-I 2026-10-05: Android copy unified with iOS |

## Coverage
rows: 93 · aligned: 88 · ios-missing: 0 · android-missing: 0 · pwa-missing: 4 · misaligned: 0 · n/a: 1

## Decisions (resolved 2026-10-04)
1. Docs links → D26a: per-card docs links on both apps.
2. Collapsible cards → D27a: collapsible with persisted state on both apps.
3. Card order → D28a: apps adopt the PWA order (nested stats block).
4. Stats refresh → D54b: PWA one-shot + WS (no REST re-poll).
5. Colour thresholds → D29a: replicate PWA per-metric values verbatim.
6. Android-only Observer cards → D78a: add to PWA (→ #172) and iOS.
7. Peer row action → D55a: PWA 📊 snapshot modal + × remove on apps.
8. Memory maintenance on phones → D89b: dry-run only.
9. Schedule edit UX → D56b: apps mimic PWA's two prompts.
10. iOS summary tiles → D30a: replaced by the PWA grid + stats panel.
