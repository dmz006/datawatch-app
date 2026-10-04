# 06 — Observer page

**PWA reference:** `renderObserverView()` app.js:23323 (view id `observer`, header "Observer", FAB hidden). Cards are collapsible `settingsSectionHeader()` sections with per-key persisted state and a docs link per header. Loaders fire on view render (app.js:23523–23533): `loadSystemStatsGrid`, `loadStatsPanel`, `loadSchedulesList`, `loadCooldownStatus`, `loadAnalyticsPanel`, `loadAuditPanel`, `loadKgPanel`, `loadCommBackendsStatus`, `renderObserverPeersCard`.
**Android:** `ui/observer/ObserverScreen.kt:92–110` (flat column of 19 cards; `StatsScreenContent` embeds 13 more sub-cards). **iOS:** `screens/observer/ObserverView.swift` (one 2×3 metric grid + uptime).
Spec §7 is stale: live PWA adds per-system grid (BL379), eBPF/network, plugins, peer resources, observer peers, cluster, channel bridge + diagnostics, comm backends, Matrix, web-search stats, RTK, episodic-memory stats inside §7.1.

| Cat | Feature | PWA | Android | iOS | Status | Decision | Notes |
|---|---|---|---|---|---|---|---|
| nav | Header title "Observer", no FAB | ✓ app.js:23323 | ✓ ObserverScreen TopAppBar (server picker title) | ✓ HeaderView "Observer" | aligned | | PWA title is static; apps show server picker — see 01 |
| nav | Server picker in header (multi-profile) | ✗ single server | ✓ SingleServerPickerTitle | ✓ segmented Picker (profilePicker) | pwa-missing | | PWA is single-server by nature → n/a candidate |
| nav | Per-card docs link (settingsSectionHeader 3rd arg) | ✓ e.g. `flow/observer-flow.md`, `memory.md` | ~ one DocsLinkAction in TopAppBar | ~ one DocsLinkButton in toolbar | misaligned | needs-decision (D1) | PWA links per card; apps link once |
| interaction | Collapsible cards with disclosure chevron, state persisted | ✓ `secContent(key)` + localStorage | ✗ PwaSectionTitle static | ✗ | misaligned | needs-decision (D2) | |
| motion | Chevron rotate on collapse `transition:transform 0.15s` | ✓ app.js:23371 | ✗ | ✗ | misaligned | | follows D2 |
| element | Card order | grid → System Statistics → Memory Browser → Memory Maintenance → Scheduled Events → Global Cooldown → Session Analytics → Audit Log → Knowledge Graph → Daemon Log → Federated Peers | grid → Stats block (13 cards) → eBPF status → eBPF network → Cluster → Peer Resources → Federated peers → Plugins → MCP channel → Comm backends → Matrix → Memory → Mempalace → Schedules → Cooldown → Analytics → Audit → KG → Daemon log | metrics grid → uptime → updated | misaligned | needs-decision (D3) | |
| element | Loading state per card ("Loading…" placeholder) | ✓ per block | ~ DatawatchLoadingContent for stats block; per-card spinners vary | ~ LoadingIndicator "Loading stats…" (whole page) | misaligned | | |
| element | Empty/no-server state | n/a (always connected) | ✓ banner "No enabled server…" | ✓ emptyStateView eye.slash "No server connected" | aligned | | |
| **7.1 System Statistics** | | | | | | | |
| data | `/api/stats` fetch for statistics panel | ✓ loadStatsPanel app.js:19587 | ✓ StatsViewModel.doRefresh | ✓ ServiceLocatorAsync.getStats | aligned | | |
| data | WS `stats` frame → live overlay | ✓ `case 'stats'` app.js:590 → state.statsData | ✓ StatsHub.flow (v1.23.113) | ✓ subscribeGlobalStream onStats (v1.23.116) | aligned | | |
| data | REST fallback cadence | one-shot on view render (+WS) | 30 s while visible | 30 s while visible | misaligned | needs-decision (D4) | PWA never re-polls /api/stats; relies on WS |
| data | Per-system grid refresh | 8 s setInterval app.js:19896 | 10 s delay SystemStatsGridCard:56 | ✗ | misaligned | | 8 vs 10 s |
| element | Per-system grid (local + each observer peer, one card) | ✓ `#perSystemGrid` app.js:19773 | ✓ SystemStatsGridCard (LocalSystemCard, PeerSystemCard) | ✗ | ios-missing | | |
| element | Grid card: name + "local" badge + dot | ✓ sysCard() | ✓ Text("local") | ✗ | ios-missing | | |
| element | Grid CPU bar: pct · load1/5/15 | ✓ app.js:19822 | ✓ StatBar "CPU" loadStr | ~ MetricCard CPU pct only | misaligned | | iOS lacks load averages |
| token | Grid CPU color thresholds | >80 error / >50 warning / success | ≥80 / ≥50 (cpuBarColor:314) | ≥90 / ≥70 / primary | misaligned | needs-decision (D5) | three schemes |
| element | Grid RAM bar used/total | ✓ `bar('RAM')` >85 error | ✓ StatBar "RAM" | ~ MetricCard "Memory" pct | misaligned | | iOS no absolute bytes |
| element | Grid GPU util / temp / power / VRAM per GPU | ✓ app.js:19857–19872 | ✓ labels "GPU util/temp/VRAM" | ~ single "VRAM" pct | misaligned | | iOS shows gpuPct as "VRAM" label — mislabeled |
| token | GPU temp thresholds ≥80 error / ≥60 warning | ✓ | ✓ (PeerResources/grid) | ✗ | ios-missing | | |
| element | Statistics panel bars: CPU Load (load/cores), Memory, Disk, Swap (if >0), GPU util+temp, GPU VRAM | ✓ renderStatsData app.js:20473–20486 | ✓ SystemStatisticsCard | ~ CPU/Memory/Disk/VRAM tiles | misaligned | | iOS omits Swap, GPU util/temp, load/cores |
| token | Statistics panel thresholds: CPU >80/>50, Memory >85, Disk >90, GPU >80 | ✓ app.js:20475–20486 | ~ pctColor ≥90/≥70 for all (StatsScreen:860) | ~ ≥90/≥70 for all | misaligned | needs-decision (D5) | |
| element | GPU probe failed card (red, grid-column 1/-1) | ✓ app.js:20492 | ✗ | ✗ | android-missing / ios-missing | | shows when probe exists but last poll failed |
| element | Network card "(datawatch)" vs "(system)" label by ebpf_active, ↓ Download / ↑ Upload | ✓ app.js:20498 | ✓ NetworkCard | ✗ | ios-missing | | |
| element | Daemon card: Memory RSS, Uptime (h m / m s) | ✓ app.js:20507 | ✓ DaemonCard | ~ uptimeRow only (d h m) | misaligned | | iOS format differs, no RSS |
| element | Infrastructure card | ✓ app.js:20519 | ✓ InfrastructureCard | ✗ | ios-missing | | |
| element | RTK Token Savings (version, saved tokens, update badge → copy cmd) | ✓ app.js:20542 | ✓ RtkCard (shown if rtkInstalled) | ✗ | ios-missing | | |
| interaction | RTK update badge click → copy upgrade command (toast) | ✓ data-cmd BL223 | ~ verify RtkCard | ✗ | ios-missing | | |
| element | Episodic Memory stats inside stats panel | ✓ app.js:20555 | ✓ MemoryStatsCard | ✗ | ios-missing | | |
| element | Server info card (hostname, version, host, port) | ✗ (lives in Settings → About) | ✓ ServerInfoCard | ✗ | pwa-missing | needs-decision (D6) | Android places it atop Observer |
| element | Session Statistics ring (total/max_sessions) + running/waiting/complete/failed counts | ✗ no ring; counts text only | ✓ SessionStatisticsCard ring ≥0.9 error ≥0.7 warning | ~ Running/Waiting tiles only | pwa-missing | needs-decision (D6) | app idea candidate |
| data | `session.max_sessions` from /api/config for ring denominator | ✓ state._maxSessions (used elsewhere) | ✓ StatsViewModel maxSessions | ✗ | ios-missing | | |
| element | Ollama Server card | ✗ in Observer | ✓ OllamaStatsCard | ✗ | pwa-missing | needs-decision (D6) | |
| element | Process envelopes card | ✗ | ✓ EnvelopesCard | ✗ | pwa-missing | needs-decision (D6) | |
| element | Backend health card | ✗ | ✓ BackendHealthCard | ✗ | pwa-missing | needs-decision (D6) | |
| element | eBPF "Degraded" banner (built without eBPF / not active) | ✗ (status line only) | ✓ StatsScreen:67–92 | ✗ | pwa-missing | | app idea candidate |
| element | eBPF status line (live / configured+cap / cap missing / off, colored dot) | ✓ loadEBPFStatus app.js:19905 (`/api/stats?v=2`) | ✓ EBpfStatusCard | ✗ | ios-missing | | |
| element | Network Traffic per-process table (Process / In / Out) | ✓ loadEBPFNetworkTraffic | ✓ EBpfNetworkCard "Network (by process)" | ✗ | ios-missing | | |
| string | "No eBPF data available" | ✓ t('ebpf_no_data') | ✓ stats_ebpf_configured_not_active | ✗ | ios-missing | | wording differs |
| element | Installed plugins list (version + status) | ✓ loadPluginsStatus `/api/plugins` | ✓ PluginsCard | ✗ | ios-missing | | |
| element | Peer Resources: per peer CPU %, Mem used/total, GPU util/temp/power/VRAM chips, shape tag, "no snapshot" | ✓ loadPeerResourceOverview app.js:19701 | ✓ PeerResourcesCard (obs_cn_* strings) | ✗ | ios-missing | | |
| data | Peer snapshots `/api/observer/peers/{name}/stats` in parallel | ✓ | ✓ | ✗ | ios-missing | | |
| element | Peer Resources refresh 8 s | ✓ | ✓ delay(8_000) | ✗ | ios-missing | | aligned cadence |
| interaction | Peer row tap → compute node / peer detail | ✗ (snapshot modal instead) | ✓ onClick → compute node detail (v1.23.66) | ✗ | pwa-missing | needs-decision (D7) | Android navigates; PWA opens modal |
| element | Observer peers block: filter pills with counts (all/free/attached…) | ✓ loadObserverPeers app.js:20048 | ~ FederatedPeersCard (observer_free string) | ✗ | misaligned | | verify Android pill set |
| interaction | Group-by-compute-node toggle | ✓ togglePeerGroupByNode | ✓ peer_group_by_node | ✗ | ios-missing | | |
| interaction | "Cross-host view" button → showCrossHostView() | ✓ app.js:20061 | ✗ verify | ✗ | android-missing | | |
| interaction | 📊 Snapshot button → showObserverPeerSnapshot modal | ✓ | ✗ (tap navigates instead) | ✗ | misaligned | needs-decision (D7) | |
| interaction | × Remove peer (rotates token) with confirm | ✓ removeObserverPeer | ✗ verify | ✗ | android-missing | | |
| element | Peer dot colors by last push: green <15 s / amber <60 s / red ≥60 s / grey never | ✓ | ✓ FederatedPeersCard Canvas + relative age (v1.23.4) | ✗ | ios-missing | | |
| element | Shape badge A/B/C (agent/standalone/cluster) | ✓ | ~ verify | ✗ | ios-missing | | |
| string | Empty: "no peers registered" + deploy hint `datawatch-stats --datawatch <url> --name <peer>` | ✓ app.js:20066 | ~ obs_peer_no_peers (no hint) | ✗ | misaligned | | |
| element | "attached to ComputeNode" tag on peer | ✓ t('observer_attached_to') | ✓ (observerPeer != null filter, v1.23.4) | ✗ | ios-missing | | |
| element | Cluster nodes block (hidden until non-empty) | ✓ loadObserverClusterNodes `/api/observer/stats` | ✓ ClusterNodesCard | ✗ | ios-missing | | |
| element | MCP channel bridge status (collapsed chevron block) | ✓ `/api/channel/info` app.js:19608 | ✓ McpChannelCard (ui/about) | ✗ | ios-missing | | |
| element | Channel diagnostics block + refresh button | ✓ `/api/channel/diagnostics` BL362 app.js:19656 | ~ verify inside McpChannelCard | ✗ | ios-missing | | |
| element | Communication backends status (from /api/config) + Matrix status + "Test" button | ✓ loadCommBackendsStatus app.js:23538 | ✓ CommBackendsCard + separate MatrixStatusCard | ✗ | misaligned | | Android splits Matrix into its own card |
| interaction | Matrix "Test" → POST /api/matrix/test → toast | ✓ | ~ verify MatrixStatusCard | ✗ | ios-missing | | |
| element | Web Search stats: per-provider totals, Total/Today/This week/This month/Cache hits, 14-day bar graph, "History" button | ✓ renderWebSearchStatsHTML app.js:20407 (`/api/websearch/stats?days=14`) | ✓ WebSearchCard + WebSearchRegistryCard (BL391 #205) | ✗ | ios-missing | | |
| interaction | Web search history view (last 50, live/cache/error) | ✓ webSearchOpenHistoryView | ~ verify | ✗ | ios-missing | | |
| **7.2 Memory Browser** | | | | | | | |
| element | Search input + role filter (All/Manual/Session/Learning/Chunks) + since (All/7d/30d/90d) | ✓ app.js:23402 | ~ MemoryCard search + research query (memory_research_query_hint) | ✗ | misaligned | | filter set differs — verify |
| interaction | Search / List / Export buttons | ✓ | ✓ Text("Search"), Text("Export…"), Text("Test") | ✗ | ios-missing | | Android adds "Test" |
| element | Memory stats cards: Total Memories / Manual / Session / Learnings / Chunks / DB Size | ✓ loadMemoryStats app.js:14276 | ✓ MemoryCard StatsGrid | ✗ | ios-missing | | |
| interaction | Add memory dialog (text, tags) | ✗ in Observer | ✓ memory_add_title AlertDialog | ✗ | pwa-missing | needs-decision (D6) | |
| element | Results list max-height 400 scroll | ✓ | n/a (LazyColumn) | ✗ | ios-missing | | |
| **7.3 Memory Maintenance** | | | | | | | |
| element | 2×2 grid: Similarity-stale eviction (days, Dry-run/Apply), Spellcheck, Extract facts, Schema version check | ✓ app.js:23425–23456 | ✓ MempalaceActionsCard ("dry-run", "Older than (days)", "Extract", "Check") | ✗ | ios-missing | needs-decision (D8) | destructive ops on phone? |
| interaction | Apply eviction confirm() with explanatory text | ✓ app.js:14306 | ~ verify | ✗ | ios-missing | | |
| string | Card title "Memory Maintenance" vs "Mempalace" | ✓ | ~ "Mempalace" | ✗ | misaligned | | copy |
| **7.4 Scheduled Events** | | | | | | | |
| data | `/api/schedules` list; Android polls 15 s | ✓ one-shot | ✓ SCHEDULES_POLL_MS 15 s | ✗ | ios-missing | | |
| element | Row: label/command, run_at, cron badge, target | ✓ app.js:20902 | ✓ SchedulesCard | ✗ | ios-missing | | |
| interaction | Select-all checkbox + "Delete selected" | ✓ | ~ verify | ✗ | ios-missing | | |
| interaction | Edit (pencil) via browser prompt() ×2 | ✓ editSchedulePrompt | ~ sheet/dialog (IconButton ×5) | ✗ | misaligned | needs-decision (D9) | PWA uses native prompt() |
| interaction | Delete (🗑) → toast "Deleted" | ✓ | ✓ | ✗ | ios-missing | | |
| interaction | Pagination Prev/Next | ✓ settingsPagination.schedules | ✗ verify | ✗ | android-missing | | |
| **7.5 Global Cooldown** | | | | | | | |
| element | Status: active until / none | ✓ loadCooldownStatus | ✓ CooldownCard | ✗ | ios-missing | | |
| interaction | "Set for:" 5m / 15m / 30m / 1h buttons | ✓ | ~ verify (has "Reason (optional)" field) | ✗ | misaligned | | Android adds reason field PWA lacks |
| interaction | Clear button (red tint) → toast | ✓ | ✓ Text("Clear Cooldown") | ✗ | ios-missing | | |
| **7.6 Session Analytics** | | | | | | | |
| element | Range selector (days) + table Date / Total / OK / Err / Bar | ✓ loadAnalyticsPanel `/api/analytics?range=Nd` | ✓ SessionAnalyticsCard ("Range:", barColor) | ✗ | ios-missing | | |
| string | "No sessions in range." | ✓ | ~ verify | ✗ | ios-missing | | |
| **7.7 Audit Log** | | | | | | | |
| element | Filters actor / action + limit 5/20/50/100 + Load | ✓ loadAuditPanel | ✓ AuditLogCard ("Actor", "Action", "Load") | ✗ | ios-missing | | |
| element | Pipelines live block (8 s, Cancel button) rendered under audit | ✓ loadPipelinesPanel app.js:25656 | ✗ verify | ✗ | android-missing | | |
| **7.8 Knowledge Graph** | | | | | | | |
| element | Entity query input + Query; triples result | ✓ loadKgPanel `/api/memory/kg/query` | ✓ KnowledgeGraphCard ("Entity", "Query") | ✗ | ios-missing | | |
| interaction | Add triple (Subject/Predicate/Object) → toast | ✓ `/api/memory/kg/add` | ✓ ("Subject","Predicate","Object","Add triple") | ✗ | ios-missing | | |
| element | Identity panel (`/api/identity`) under KG | ✓ app.js:25751 | ✗ verify | ✗ | android-missing | | |
| **7.9 Daemon Log** | | | | | | | |
| element | Monospace panel, dark bg, max-height 300 | ✓ | ✓ DaemonLogCard | ✗ | ios-missing | | |
| interaction | Newest / Older (50 lines, offset counter) | ✓ loadDaemonLog | ✓ "Newer"/"Older"/"Refresh" | ✗ | misaligned | | button labels differ (Newest vs Newer+Refresh) |
| data | Auto-refresh | ✓ setInterval app.js:12513 | ✓ delay(10_000) | ✗ | ios-missing | | |
| element | Daemon ops cards: Network interfaces, Kill orphans, Daemon update, Restart daemon, Hot-reload subsystem | ✗ in Observer (PWA: Settings) | ✓ ops/DaemonOpsCards (not mounted in ObserverScreen — Settings) | ✗ | n/a | | belongs to 07 |
| **7.10 Federated Peers (bottom card)** | | | | | | | |
| element | Stats row pills (`/api/observer/stats`) + Config table (`/api/observer/config`) + peer list; "live" dot, 8 s | ✓ renderObserverPeersCard app.js:23712 | ✓ FederatedPeersCard 8 s (monitoring) | ✗ | ios-missing | | |
| element | Federation peers (server-to-server) list: enabled dot, URL, capability group badge, Test / Delete | ✓ loadFederationPeersPanel app.js:23588 | ✓ federation/FederationPeersCard (URL label, Delete icon) | ✗ | ios-missing | | Android mounts in Settings? verify placement |
| interaction | Test peer → toast; Delete peer → confirm | ✓ | ~ verify | ✗ | ios-missing | | |
| **iOS-only today** | | | | | | | |
| element | 2×3 metric tiles with icon + value + 4 px bar | ✗ | ✗ | ✓ MetricCard/SessionMetricCard | pwa-missing | needs-decision (D10) | replace with PWA layout or keep as summary header |
| element | "Updated Xs ago" line | ✗ | ✗ | ✓ lastUpdatedString | pwa-missing | | |
| string | Empty copy "Add a server in Settings to monitor metrics." | n/a | "No enabled server. Add or enable one in Settings." | ✓ | misaligned | | unify copy |

## Coverage
rows: 93 · aligned: 5 · ios-missing: 52 · android-missing: 5 · pwa-missing: 10 · misaligned: 20 · n/a: 1

## Decisions needed
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
