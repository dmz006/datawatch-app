# 06 — Observer page

**PWA reference:** `renderObserverView()` app.js:23323 (view id `observer`, header "Observer", FAB hidden). Cards are collapsible `settingsSectionHeader()` sections with per-key persisted state and a docs link per header. Loaders fire on view render (app.js:23523–23533): `loadSystemStatsGrid`, `loadStatsPanel`, `loadSchedulesList`, `loadCooldownStatus`, `loadAnalyticsPanel`, `loadAuditPanel`, `loadKgPanel`, `loadCommBackendsStatus`, `renderObserverPeersCard`.
**Android:** `ui/observer/ObserverScreen.kt:92–110` (flat column of 19 cards; `StatsScreenContent` embeds 13 more sub-cards). **iOS:** `screens/observer/ObserverView.swift` + `ObserverStatsSection` / `ObserverPeersViews` / `ObserverOpsSections` / `ObserverComponents` (parity B20–B25, PWA card order) backed by `shared/.../di/IosObserver.kt`.
Spec §7 is stale: live PWA adds per-system grid (BL379), eBPF/network, plugins, peer resources, observer peers, cluster, channel bridge + diagnostics, comm backends, Matrix, web-search stats, RTK, episodic-memory stats inside §7.1.

| Cat | Feature | PWA | Android | iOS | Status | Decision | Notes |
|---|---|---|---|---|---|---|---|
| nav | Header title "Observer", no FAB | ✓ app.js:23323 | ✓ ObserverScreen TopAppBar (server picker title) | ✓ HeaderView "Observer" | aligned | | PWA title is static; apps show server picker — see 01 |
| nav | Server picker in header (multi-profile) | ✗ single server | ✓ SingleServerPickerTitle | ✓ segmented Picker (profilePicker) | pwa-missing | | PWA is single-server by nature → n/a candidate |
| nav | Per-card docs link (settingsSectionHeader 3rd arg) | ✓ e.g. `flow/observer-flow.md`, `memory.md` | ✓ per-card "?" link in the shared PwaCard header (PWA defsLink slug), page link kept in TopAppBar | ✓ per-card DocsLinkButton (ObsSection, defsLink slug) | aligned | D26a | |
| interaction | Collapsible cards with disclosure chevron, state persisted | ✓ `secContent(key)` + localStorage | ✓ PwaCard + PwaCardCollapseStore (SharedPreferences, PWA keys; default expanded) | ✓ ObsSection + ObserverCollapseStore (UserDefaults) | aligned | D27a | |
| motion | Chevron rotate on collapse `transition:transform 0.15s` | ✓ app.js:23371 | ✓ PwaCardHeader chevron rotate tween 150 ms | ✓ chevron.right rotate easeInOut 0.15 s | aligned | | follows D2 |
| element | Card order | grid → System Statistics → Memory Browser → Memory Maintenance → Scheduled Events → Global Cooldown → Session Analytics → Audit Log → Knowledge Graph → Daemon Log → Federated Peers | grid → Stats block (13 cards) → eBPF status → eBPF network → Cluster → Peer Resources → Federated peers → Plugins → MCP channel → Comm backends → Matrix → Memory → Mempalace → Schedules → Cooldown → Analytics → Audit → KG → Daemon log | ✓ PWA order, nested stats block (ObserverView.observerContent) | misaligned | D28a | Android still flattened |
| element | Loading state per card ("Loading…" placeholder) | ✓ per block | ~ DatawatchLoadingContent for stats block; per-card spinners vary | ✓ per-block "Loading…" (ObsMuted) | misaligned | | |
| element | Empty/no-server state | n/a (always connected) | ✓ banner "No enabled server…" | ✓ emptyStateView eye.slash "No server connected" | aligned | | |
| **7.1 System Statistics** | | | | | | | |
| data | `/api/stats` fetch for statistics panel | ✓ loadStatsPanel app.js:19587 | ✓ StatsViewModel.doRefresh | ✓ ServiceLocatorAsync.getStats | aligned | | |
| data | WS `stats` frame → live overlay | ✓ `case 'stats'` app.js:590 → state.statsData | ✓ StatsHub.flow (v1.23.113) | ✓ subscribeGlobalStream onStats (v1.23.116) | aligned | | |
| data | REST fallback cadence | one-shot on view render (+WS) | 30 s while visible | one-shot + WS (D54b) | misaligned | D54b | PWA never re-polls /api/stats; relies on WS; Android still 30 s |
| data | Per-system grid refresh | 8 s setInterval app.js:19896 | 10 s delay SystemStatsGridCard:56 | 8 s (ObserverViewModel.liveTask) | misaligned | | 8 vs 10 s; iOS = PWA; Android 10 s |
| element | Per-system grid (local + each observer peer, one card) | ✓ `#perSystemGrid` app.js:19773 | ✓ SystemStatsGridCard (LocalSystemCard, PeerSystemCard) | ✓ ObserverSystemGrid ← IosObserver.loadSystems | aligned | | |
| element | Grid card: name + "local" badge + dot | ✓ sysCard() | ✓ Text("local") | ✓ SystemCardView | aligned | | |
| element | Grid CPU bar: pct · load1/5/15 | ✓ app.js:19822 | ✓ StatBar "CPU" loadStr | ✓ pct · load1/5/15 | aligned | | |
| token | Grid CPU color thresholds | >80 error / >50 warning / success | ≥80 / ≥50 (cpuBarColor:314) | ✓ >80 / >50 / success (PWA verbatim) | misaligned | D29a | three schemes; iOS = PWA; Android uses ≥ |
| element | Grid RAM bar used/total | ✓ `bar('RAM')` >85 error | ✓ StatBar "RAM" | ✓ used/total, >85 error | aligned | | |
| element | Grid GPU util / temp / power / VRAM per GPU | ✓ app.js:19857–19872 | ✓ labels "GPU util/temp/VRAM" | ✓ util/temp/power/VRAM per GPU | aligned | | |
| token | GPU temp thresholds ≥80 error / ≥60 warning | ✓ | ✓ (PeerResources/grid) | ✓ tempTone | aligned | | |
| element | Statistics panel bars: CPU Load (load/cores), Memory, Disk, Swap (if >0), GPU util+temp, GPU VRAM | ✓ renderStatsData app.js:20473–20486 | ✓ SystemStatisticsCard | ✓ IosObserver.buildStatsPanel bars | aligned | | |
| token | Statistics panel thresholds: CPU >80/>50, Memory >85, Disk >90, GPU >80 | ✓ app.js:20475–20486 | ~ pctColor ≥90/≥70 for all (StatsScreen:860) | ✓ PWA per-metric values (D29a) | misaligned | D29a | iOS = PWA; Android ≥90/≥70 |
| element | GPU probe failed card (red, grid-column 1/-1) | ✓ app.js:20492 | ✓ `GpuProbeFailedCard` (red border, monospace error) | ✓ GpuProbeFailedCard | aligned | | shows when probe exists but last poll failed · Android done 2026-10-04 (android-missing sweep) |
| element | Network card "(datawatch)" vs "(system)" label by ebpf_active, ↓ Download / ↑ Upload | ✓ app.js:20498 | ✓ NetworkCard | ✓ | aligned | | |
| element | Daemon card: Memory RSS, Uptime (h m / m s) | ✓ app.js:20507 | ✓ DaemonCard | ✓ RSS, goroutines, FDs, uptime h m / m s | aligned | | |
| element | Infrastructure card | ✓ app.js:20519 | ✓ InfrastructureCard | ✓ | aligned | | |
| element | RTK Token Savings (version, saved tokens, update badge → copy cmd) | ✓ app.js:20542 | ✓ RtkCard (shown if rtkInstalled) | ✓ (latest version from raw /api/stats) | aligned | | |
| interaction | RTK update badge click → copy upgrade command (toast) | ✓ data-cmd BL223 | ~ verify RtkCard | ✓ tap → UIPasteboard + toast | misaligned | | Android to verify |
| element | Episodic Memory stats inside stats panel | ✓ app.js:20555 | ✓ MemoryStatsCard | ✓ | aligned | | |
| element | Server info card (hostname, version, host, port) | ✗ (lives in Settings → About) | ✓ ServerInfoCard | ✓ D78a (IosObserver.loadServerContext) | pwa-missing | D78a | Android places it atop Observer |
| element | Session Statistics ring (total/max_sessions) + running/waiting/complete/failed counts | ✗ no ring; counts text only | ✓ SessionStatisticsCard ring ≥0.9 error ≥0.7 warning | ✓ donut active/max + counts (PWA success colour) | misaligned | D78a | app idea candidate; PWA renderStatsData does draw a donut (app.js:20659); Android colours by threshold |
| data | `session.max_sessions` from /api/config for ring denominator | ✓ state._maxSessions (used elsewhere) | ✓ StatsViewModel maxSessions | ✓ loadServerContext | aligned | | |
| element | Ollama Server card | ✗ in Observer | ✓ OllamaStatsCard | ✓ inside stats panel | aligned | D78a | PWA renderStatsData includes Ollama (app.js:20605) |
| element | Process envelopes card | ✗ | ✓ EnvelopesCard | ✓ Process Envelopes card (D78a) | pwa-missing | D78a | |
| element | Backend health card | ✗ | ✓ BackendHealthCard | ✓ Backend Health card (D78a) | pwa-missing | D78a | |
| element | eBPF "Degraded" banner (built without eBPF / not active) | ✗ (status line only) | ✓ StatsScreen:67–92 | ✓ EbpfBanner | aligned | D78a | app idea candidate; PWA renderStatsData has the banner (app.js:20672) |
| element | eBPF status line (live / configured+cap / cap missing / off, colored dot) | ✓ loadEBPFStatus app.js:19905 (`/api/stats?v=2`) | ✓ EBpfStatusCard | ✓ ObserverEbpfBlocks | aligned | | |
| element | Network Traffic per-process table (Process / In / Out) | ✓ loadEBPFNetworkTraffic | ✓ EBpfNetworkCard "Network (by process)" | ✓ NetTrafficTable (top 10) | aligned | | |
| string | "No eBPF data available" | ✓ t('ebpf_no_data') | ✓ stats_ebpf_configured_not_active | ✓ PWA wording | misaligned | | wording differs; Android wording differs |
| element | Installed plugins list (version + status) | ✓ loadPluginsStatus `/api/plugins` | ✓ PluginsCard | ✓ ObserverPluginsBlock | aligned | | |
| element | Peer Resources: per peer CPU %, Mem used/total, GPU util/temp/power/VRAM chips, shape tag, "no snapshot" | ✓ loadPeerResourceOverview app.js:19701 | ✓ PeerResourcesCard (obs_cn_* strings) | ✓ ObserverPeerResourcesBlock | aligned | | |
| data | Peer snapshots `/api/observer/peers/{name}/stats` in parallel | ✓ | ✓ | ✓ shared with grid (loadSystems) | aligned | | |
| element | Peer Resources refresh 8 s | ✓ | ✓ delay(8_000) | ✓ | aligned | | aligned cadence |
| interaction | Peer row tap → compute node / peer detail | ✗ (snapshot modal instead) | ✓ onClick → compute node detail (v1.23.66) | ✗ (D55a: snapshot sheet) | pwa-missing | D55a | Android navigates; PWA opens modal |
| element | Observer peers block: filter pills with counts (all/free/attached…) | ✓ loadObserverPeers app.js:20048 | ~ FederatedPeersCard (observer_free string) | ✓ All/Agents/Standalone/Cluster + counts, cs_peer_filter | misaligned | | verify Android pill set; Android to verify |
| interaction | Group-by-compute-node toggle | ✓ togglePeerGroupByNode | ✓ peer_group_by_node | ✓ meta-peers buckets | aligned | | |
| interaction | "Cross-host view" button → showCrossHostView() | ✓ app.js:20061 | ✓ ↔ Cross-host view dialog (`/api/observer/envelopes/all-peers`, 🔗 cross tags) | ✗ | ios-missing | | iOS ✗ too — not in B22 scope · Android done 2026-10-04 (android-missing sweep) |
| interaction | 📊 Snapshot button → showObserverPeerSnapshot modal | ✓ | ✗ (tap navigates instead) | ✓ PeerSnapshotSheet (envelopes; no per-envelope process drill-down) | misaligned | D55a | |
| interaction | × Remove peer (rotates token) with confirm | ✓ removeObserverPeer | ✓ × with confirm → `removeObserverPeer`, result to dock | ✓ alert confirm | aligned | D55a | Android done 2026-10-04 (android-missing sweep) |
| element | Peer dot colors by last push: green <15 s / amber <60 s / red ≥60 s / grey never | ✓ | ✓ FederatedPeersCard Canvas + relative age (v1.23.4) | ✓ | aligned | | |
| element | Shape badge A/B/C (agent/standalone/cluster) | ✓ | ~ verify | ✓ | misaligned | | Android to verify |
| string | Empty: "no peers registered" + deploy hint `datawatch-stats --datawatch <url> --name <peer>` | ✓ app.js:20066 | ~ obs_peer_no_peers (no hint) | ✓ with deploy hint | misaligned | | |
| element | "attached to ComputeNode" tag on peer | ✓ t('observer_attached_to') | ✓ (observerPeer != null filter, v1.23.4) | ✓ ⇄ node / free tag | aligned | | |
| element | Cluster nodes block (hidden until non-empty) | ✓ loadObserverClusterNodes `/api/observer/stats` | ✓ ClusterNodesCard | ✓ ObserverClusterBlock | aligned | | |
| element | MCP channel bridge status (collapsed chevron block) | ✓ `/api/channel/info` app.js:19608 | ✓ McpChannelCard (ui/about) | ✓ DisclosureGroup | aligned | | |
| element | Channel diagnostics block + refresh button | ✓ `/api/channel/diagnostics` BL362 app.js:19656 | ~ verify inside McpChannelCard | ✓ + refresh + hints | misaligned | | Android to verify |
| element | Communication backends status (from /api/config) + Matrix status + "Test" button | ✓ loadCommBackendsStatus app.js:23538 | ✓ CommBackendsCard + separate MatrixStatusCard | ✓ Matrix inline like PWA | misaligned | | Android splits Matrix into its own card |
| interaction | Matrix "Test" → POST /api/matrix/test → toast | ✓ | ~ verify MatrixStatusCard | ✓ toast | misaligned | | Android to verify |
| element | Web Search stats: per-provider totals, Total/Today/This week/This month/Cache hits, 14-day bar graph, "History" button | ✓ renderWebSearchStatsHTML app.js:20407 (`/api/websearch/stats?days=14`) | ✓ WebSearchCard + WebSearchRegistryCard (BL391 #205) | ✓ (refresh every 3rd 8 s tick) | aligned | | |
| interaction | Web search history view (last 50, live/cache/error) | ✓ webSearchOpenHistoryView | ~ verify | ✓ sheet | misaligned | | Android to verify |
| **7.2 Memory Browser** | | | | | | | |
| element | Search input + role filter (All/Manual/Session/Learning/Chunks) + since (All/7d/30d/90d) | ✓ app.js:23402 | ~ MemoryCard search + research query (memory_research_query_hint) | ✓ PWA role/since filters | misaligned | | filter set differs — verify |
| interaction | Search / List / Export buttons | ✓ | ✓ Text("Search"), Text("Export…"), Text("Test") | ✓ (Export → share sheet) | aligned | | Android adds "Test" |
| element | Memory stats cards: Total Memories / Manual / Session / Learnings / Chunks / DB Size | ✓ loadMemoryStats app.js:14276 | ✓ MemoryCard StatsGrid | ✓ | aligned | | |
| interaction | Add memory dialog (text, tags) | ✗ in Observer | ✓ memory_add_title AlertDialog | ✓ alert text + tags (D78a) | pwa-missing | D78a | |
| element | Results list max-height 400 scroll | ✓ | n/a (LazyColumn) | ✓ | aligned | | |
| **7.3 Memory Maintenance** | | | | | | | |
| element | 2×2 grid: Similarity-stale eviction (days, Dry-run/Apply), Spellcheck, Extract facts, Schema version check | ✓ app.js:23425–23456 | ✓ MempalaceActionsCard ("dry-run", "Older than (days)", "Extract", "Check") | ~ Dry-run only (no Apply), Spellcheck, Extract, Schema | misaligned | D89b | destructive ops on phone? |
| interaction | Apply eviction confirm() with explanatory text | ✓ app.js:14306 | ~ verify | n/a (D89b) | n/a | D89b | |
| string | Card title "Memory Maintenance" vs "Mempalace" | ✓ | ~ "Mempalace" | ✓ | misaligned | | copy |
| **7.4 Scheduled Events** | | | | | | | |
| data | `/api/schedules` list; Android polls 15 s | ✓ one-shot | ✓ SCHEDULES_POLL_MS 15 s | ✓ one-shot on expand | misaligned | | iOS = PWA; Android 15 s |
| element | Row: label/command, run_at, cron badge, target | ✓ app.js:20902 | ✓ SchedulesCard | ~ run_at, cron badge, state; label = session id (domain lacks session_name) | misaligned | | |
| interaction | Select-all checkbox + "Delete selected" | ✓ | ~ verify | ✓ | misaligned | | Android to verify |
| interaction | Edit (pencil) via browser prompt() ×2 | ✓ editSchedulePrompt | ~ sheet/dialog (IconButton ×5) | ✓ alert with 2 TextFields (prompt equivalent) | misaligned | needs-decision (D9) | PWA uses native prompt() |
| interaction | Delete (🗑) → toast "Deleted" | ✓ | ✓ | ✓ | aligned | | |
| interaction | Pagination Prev/Next | ✓ settingsPagination.schedules | ✓ SchedulesCard 10/page Prev/Next | ✓ | aligned | | already present (verified 2026-10-04) |
| **7.5 Global Cooldown** | | | | | | | |
| element | Status: active until / none | ✓ loadCooldownStatus | ✓ CooldownCard | ✓ | aligned | | |
| interaction | "Set for:" 5m / 15m / 30m / 1h buttons | ✓ | ~ verify (has "Reason (optional)" field) | ✓ 15m/30m/1h/4h/8h/24h + reason | misaligned | | Android adds reason field PWA lacks; PWA actually offers 15m–24h + reason (app.js:25398) |
| interaction | Clear button (red tint) → toast | ✓ | ✓ Text("Clear Cooldown") | ✓ | aligned | | |
| **7.6 Session Analytics** | | | | | | | |
| element | Range selector (days) + table Date / Total / OK / Err / Bar | ✓ loadAnalyticsPanel `/api/analytics?range=Nd` | ✓ SessionAnalyticsCard ("Range:", barColor) | ✓ | aligned | | |
| string | "No sessions in range." | ✓ | ~ verify | ✓ | misaligned | | Android to verify |
| **7.7 Audit Log** | | | | | | | |
| element | Filters actor / action + limit 5/20/50/100 + Load | ✓ loadAuditPanel | ✓ AuditLogCard ("Actor", "Action", "Load") | ✓ | aligned | | |
| element | Pipelines live block (8 s, Cancel button) rendered under audit | ✓ loadPipelinesPanel app.js:25656 | ✓ `PipelineManagerCard(liveRefreshMs = 8 s)` under audit log | ✗ | ios-missing | | iOS ✗ too — not in B25 scope · Android done 2026-10-04 (android-missing sweep) |
| **7.8 Knowledge Graph** | | | | | | | |
| element | Entity query input + Query; triples result | ✓ loadKgPanel `/api/memory/kg/query` | ✓ KnowledgeGraphCard ("Entity", "Query") | ✓ | aligned | | |
| interaction | Add triple (Subject/Predicate/Object) → toast | ✓ `/api/memory/kg/add` | ✓ ("Subject","Predicate","Object","Add triple") | ✓ | aligned | | |
| element | Identity panel (`/api/identity`) under KG | ✓ app.js:25751 | ✓ `IdentityCard` under KG | ✗ | ios-missing | | iOS ✗ too · Android done 2026-10-04 (android-missing sweep) |
| **7.9 Daemon Log** | | | | | | | |
| element | Monospace panel, dark bg, max-height 300 | ✓ | ✓ DaemonLogCard | ✓ | aligned | | |
| interaction | Newest / Older (50 lines, offset counter) | ✓ loadDaemonLog | ✓ "Newer"/"Older"/"Refresh" | ✓ Newest/Older | misaligned | | button labels differ (Newest vs Newer+Refresh) |
| data | Auto-refresh | ✓ setInterval app.js:12513 | ✓ delay(10_000) | ✓ 10 s | aligned | | |
| element | Daemon ops cards: Network interfaces, Kill orphans, Daemon update, Restart daemon, Hot-reload subsystem | ✗ in Observer (PWA: Settings) | ✓ ops/DaemonOpsCards (not mounted in ObserverScreen — Settings) | ✗ | n/a | | belongs to 07 |
| **7.10 Federated Peers (bottom card)** | | | | | | | |
| element | Stats row pills (`/api/observer/stats`) + Config table (`/api/observer/config`) + peer list; "live" dot, 8 s | ✓ renderObserverPeersCard app.js:23712 | ✓ FederatedPeersCard 8 s (monitoring) | ✓ ObserverFederatedPeersCard 8 s | aligned | | |
| element | Federation peers (server-to-server) list: enabled dot, URL, capability group badge, Test / Delete | ✓ loadFederationPeersPanel app.js:23588 | ✓ federation/FederationPeersCard (URL label, Delete icon) | ✗ | ios-missing | | Android mounts in Settings? verify placement; PWA hosts this in Settings → Comms; belongs to B28 |
| interaction | Test peer → toast; Delete peer → confirm | ✓ | ~ verify | ✗ | ios-missing | | B28 |
| **iOS-only today** | | | | | | | |
| element | 2×3 metric tiles with icon + value + 4 px bar | ✗ | ✗ | ✗ removed (D30a) | aligned | D30a | replace with PWA layout or keep as summary header |
| element | "Updated Xs ago" line | ✗ | ✗ | ✗ removed | aligned | | |
| string | Empty copy "Add a server in Settings to monitor metrics." | n/a | "No enabled server. Add or enable one in Settings." | ✓ | misaligned | | unify copy |

## Coverage
rows: 93 · aligned: 49 · ios-missing: 5 · android-missing: 3 · pwa-missing: 6 · misaligned: 28 · n/a: 2

## Decisions needed
_Resolved for iOS (B20–B25): 1→D26a, 2→D27a, 3→D28a, 4→D54b, 5→D29a, 6→D78a, 7→D55a, 8→D89b, 10→D30a. Android rows still carry the old behaviour where noted._

1. **Docs links** — PWA has a docs link per card header; apps have one per page. Options: (a) per-card links on Android/iOS (b) keep single page link (c) drop per-card links in PWA — refs app.js:23330, ObserverScreen.kt:DocsLinkAction.
2. **Collapsible cards** — PWA cards collapse with persisted state; Android/iOS are static. Options: (a) add collapsible + persistence to both apps (b) keep static on mobile (c) remove collapse in PWA — refs app.js `secContent()`, PwaSectionTitle.
3. **Card order** — PWA: grid → statistics → memory → maintenance → schedules → cooldown → analytics → audit → KG → daemon log → federated peers. Android inserts eBPF/cluster/peer-resources/federated/plugins/MCP/comm/Matrix as standalone cards after the stats block and ends with daemon log. Options: (a) apps adopt PWA order (b) PWA adopts Android's flattened order (c) new agreed order — refs app.js:23323–23400, ObserverScreen.kt:92–110.
4. **Stats refresh model** — PWA loads /api/stats once per view render and relies on the WS `stats` frame; apps poll 30 s + WS while visible; PWA grid 8 s vs Android 10 s. Options: (a) all WS-first with 30 s fallback and 8 s grid (b) keep PWA one-shot (c) 10 s grid everywhere — refs app.js:19896, SystemStatsGridCard.kt:56, StatsViewModel.kt:195.
5. **Color thresholds** — PWA is inconsistent (grid CPU >80/>50; Memory >85; Disk >90; GPU >80; session stats ≥90/≥70); Android grid ≥80/≥50 but stats ≥90/≥70; iOS ≥90/≥70 everywhere. Options: (a) replicate PWA per-metric values verbatim (b) unify all clients incl. PWA on ≥70 warning / ≥90 error (c) ≥50/≥80 — refs app.js:19808,20475–20486, StatsScreen.kt:860, SystemStatsGridCard.kt:314, ObserverView.swift MetricCard.
6. **Android-only Observer cards** (Server info, Session Statistics ring + max_sessions, Ollama server, Process envelopes, Backend health, eBPF Degraded banner, Add-memory dialog) — "app had a better idea"? Options: (a) add to PWA Observer (b) keep mobile-only, mark n/a for PWA (c) remove from Android — refs StatsScreen.kt:468–645, MemoryCard.kt.
7. **Peer row action** — PWA: 📊 snapshot modal + × remove; Android: tap navigates to compute node detail, no remove. Options: (a) adopt PWA modal + remove on apps (b) PWA adopts navigation to node detail (c) both — refs app.js:20079, PeerResourcesCard.kt onClick.
8. **Memory maintenance on phones** — eviction Apply / spellcheck / extract / schema check are destructive or heavy. Options: (a) full parity on iOS (b) dry-run only on mobile (c) desktop/PWA only (mark n/a) — refs app.js:23425, MempalaceActionsCard.kt.
9. **Schedule edit UX** — PWA uses two browser `prompt()`s; Android uses a sheet/dialog. Options: (a) PWA gets a proper edit modal (Android idea wins) (b) apps mimic two-step prompt — refs app.js:20934, SchedulesCard.kt.
10. **iOS summary tiles** — iOS's current 2×3 tiles (CPU/Memory/Disk/VRAM/Running/Waiting) don't exist elsewhere. Options: (a) replace with the PWA grid + statistics panel (b) keep as a header above the PWA layout (c) promote to PWA/Android — refs ObserverView.swift metricsGrid.
