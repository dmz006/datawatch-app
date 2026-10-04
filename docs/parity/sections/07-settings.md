# 07 — Settings page

PWA spec §8 · live `app.js` `renderSettingsView()` (A:6666) + `GENERAL/COMMS/LLM_CONFIG_FIELDS` (A:12255–12520) · Android `ui/settings/SettingsScreen.kt` (S) + `ui/configfields/ConfigFieldSchemas.kt` (CFS) + listed packages · iOS `screens/settings/*` (SV = SettingsView, SPL = ServerProfileListView, ASV/ESV = Add/EditServerView, SSV = SettingsSessionView), `security/BiometricGate.swift`.

Ref prefixes: **A** = `datawatch/internal/server/web/app.js`, **S** = `SettingsScreen.kt`, **CFS** = `ConfigFieldSchemas.kt`; other Android refs are composable names (grep-able). Audited 2026-10-04 against PWA v8.38.0, Android v1.23.120, iOS v1.23.120.

## Shell

| Cat | Feature | PWA | Android | iOS | Status | Decision | Notes |
|---|---|---|---|---|---|---|---|
| nav | Settings view (bottom-nav tab, header "Settings") | ✓ A:6666 | ✓ S:SettingsScreen | ✓ SV | aligned | | |
| element | Six-tab bar General · Plugins · Comms · Compute · Automata · About (horizontal scroll) | ✓ A:6697–6703 | ~ S:119 SettingsTab, order General·Comms·Compute·Automata·Plugins·About | ✗ SV flat list: Servers / Session / Alert Rules · Saved Commands · Filters / Automata (Orchestrator, Pipelines) / Security / About | misaligned | needs-decision | D1 |
| nav | Active tab persisted (`cs_settings_tab`) with legacy-id migration (llm/agents→compute, monitor→general…) | ✓ A:6571–6577 | ✓ S:160–171 | ✗ | ios-missing | | |
| interaction | Collapsible section cards (chevron, per-section state in localStorage) | ✓ A:6495 toggleSettingsSection | ✗ cards always expanded (`pwaCard`) | ✗ | misaligned | needs-decision | D2 |
| element | Per-card docs link (`settingsSectionHeader(id,title,docs)`) | ✓ A | ~ `Section(docsAnchor)` on some cards only | ~ one header-level DocsLinkButton | misaligned | | mechanical: wire per-card anchors |
| element | "Restart needed" after config save | ~ A:13939 inline "Restart now" link | ✓ RestartNeededBanner (persistent banner) | ✗ | misaligned | needs-decision | D3 |
| nav | Deep link into a settings tab (`navigate('plugins' / 'comms' / 'automata')`) | ✓ A:1880–1897 | ✓ DeepLinks.kt → activeTab | ✗ | ios-missing | | |
| token | Compact settings density (11–13 px labels; Android wraps tab in "settings-scale MaterialTheme") | ✓ A | ~ S custom smaller type scale | ~ SV system `.insetGrouped` sizes | misaligned | needs-decision | D4 |
| element | Per-card loading / error states ("Loading…", red error line) | ✓ A | ✓ common_loading | ✓ SV/SSV ProgressView + error row | aligned | | |
| interaction | Destructive confirmations (delete server / LLM / node / secret, kill orphans) | ✓ showConfirmModal | ✓ AlertDialog | ~ ESV delete-server only | aligned | | iOS has only the one destructive surface today |
| string | Settings copy localized (PWA `t()` + locale JSON; Android 4 extra locales) | ✓ A:42 loadLocale | ✓ res/values-{de,es,fr,ja} | ✗ hard-coded English, no `.lproj` | ios-missing | | |

## General tab

| Cat | Feature | PWA | Android | iOS | Status | Decision | Notes |
|---|---|---|---|---|---|---|---|
| element | Notifications card (permission status + Request Permission) | ✓ gc_notifs | ✓ NotificationsCard | ✗ | ios-missing | | iOS: APNs permission UX, ties to #185 |
| element | Datawatch card: `session.log_level`, `server.auto_restart_on_config`, `session.backend_family` | ✓ 'dw' | ~ CFS.Datawatch — no `session.backend_family` | ✗ | misaligned | | Android drops one key |
| element | Auto-Update card: `update.enabled/schedule/time_of_day` | ✓ 'autoupdate' | ✓ CFS.AutoUpdate | ✗ | ios-missing | | |
| element | Session card (17 keys: max_sessions, reserved_interactive, capacity_wait_seconds, input_idle_timeout, tail_lines, alert_context_lines, default_project_dir, root_path, console_cols/rows, recent_session_minutes, auto_git_init/commit, kill_sessions_on_exit, mcp_max_retries, schedule_settle_ms, suppress_active_toasts) | ✓ 'sess' | ✓ CFS.Session | ✗ | ios-missing | | |
| element | Summarizer card: `session.summarizer.enabled / llm_ref / model` + `POST /api/summarizer/test` | ✓ 'summarizer' | ✓ SummarizerCard | ~ SSV: enabled + llm_ref + Test; no `model`; Ollama-only picker | misaligned | | add `model` field, match picker scope |
| element | Whisper card: `whisper.enabled/backend/model/language/venv_path` + Test | ✓ 'whisper' | ~ CFS.Whisper — no `whisper.backend`; TestWhisperCard | ✗ | misaligned | | |
| element | Docs Search card (query, results, pending + trusted sources, Export YAML) | ✓ docs_search | ✓ DocsSearchCard | ✗ | ios-missing | | |
| element | Session Templates card (list; Use / Edit / Delete) | ✓ templates | ✓ SessionTemplatesCard | ✗ | ios-missing | | |
| element | Device Aliases card | ✓ device_aliases | ✓ DeviceAliasesCard | ✗ | ios-missing | | |
| element | Backend Artifact Lifecycle (Tooling) card + ↻ | ✓ tooling | ✓ ToolingCard | ✗ | ios-missing | | |
| element | File Service card (root path, peers, discussions) | ✓ file_service | ✓ FileServiceCard | ✗ | ios-missing | | |
| element | Discussion Scopes card (scopes list, write message, New Discussion) | ✓ discussion_scopes A:23897 | ✓ DiscussionScopesCard | ✗ | ios-missing | | |
| element | Security card: biometric lock toggle | ✗ (platform n/a) | ✓ S:703 BiometricPrompt via FragmentActivity | ~ SV toggle `biometricLockEnabled`; `BiometricLockModifier` never applied anywhere → toggle is a no-op | misaligned | | **iOS bug**: gate not enforced |
| element | Secrets vault status | ✓ inside Secrets Store card (Compute) | ✓ SecretsStatusCard as separate card on General | ✗ | misaligned | needs-decision | D5 |
| element | Config Viewer card (read-only effective config) | ✗ | ✓ ConfigViewerCard | ✗ | pwa-missing | needs-decision | D6 |
| element | Raw config editor card | ✗ | ✓ RawConfigCard | ✗ | pwa-missing | needs-decision | D6 |
| element | Encryption status card (local DB cipher / keystore, file list) | ✗ (no local DB) | ✓ EncryptionStatusCard (About tab) | ✗ (Data Protection, no card) | ios-missing | needs-decision | D7 |

## Comms tab

| Cat | Feature | PWA | Android | iOS | Status | Decision | Notes |
|---|---|---|---|---|---|---|---|
| element | Authentication card: browser token + Save & Reconnect, `server.token`, `mcp.token` | ✓ comms_auth A:6712 | ~ CFS.CommsAuth (`server.token`, `mcp.token`; browser token n/a) | ✗ | ios-missing | | |
| element | Servers card: connection dot, "This server", server info | ✓ servers A:6735 | ✓ ServersCard (multi-profile list) | ✓ SV → SPL | misaligned | | PWA is single-server by nature; apps list profiles |
| element | Remote servers + Federated Peers cards (list / add / test / enable / delete) | ✓ remote_servers A:14523, fedpeers | ✓ FederationPeersCard | ✗ | ios-missing | | |
| interaction | Add server profile (name, https URL, bearer token / no-token, trust) with probe before save | n/a | ✓ AddServerScreen | ✓ ASV | aligned | | PWA n/a |
| interaction | Edit / delete profile ("leave blank to keep" token, delete confirm) | n/a | ✓ EditServerScreen | ✓ ESV | aligned | | |
| interaction | Certificate pinning (TOFU: probe leaf fingerprint → confirm → `trustAnchorSha256`) | ✗ | ✗ hex pin ignored, sentinel only | ✓ ASV/ESV ServerTrustSection, CertProbe, IosTls | android-missing | needs-decision | D8 |
| interaction | Trust-all certificates toggle (insecure) | n/a | ✓ selfSigned Switch | ✓ ASV/ESV | aligned | | |
| interaction | Download server CA cert (`GET /api/cert`) + install guidance | ~ websrv `_tls_install` hint | ✓ ServersCard menu "Download CA cert" + CertInstallCard | ✓ ESV Download + ShareLink | aligned | | |
| element | Profile-row security badges | n/a | ~ TRUST ALL only | ✓ NO AUTH + TRUST ALL TLS | android-missing | | neither shows a "PINNED" badge |
| element | Web Server card: `server.enabled/host/port/tls/tls_port/tls_auto_generate/tls_cert/tls_key/channel_port` | ✓ websrv | ✓ CFS.WebServer | ✗ | ios-missing | | |
| element | MCP Server card: `mcp.enabled/sse_enabled/sse_host/sse_port/tls_*` | ✓ mcpsrv | ✓ CFS.McpServer | ✗ | ios-missing | | |
| element | Communication Configuration: per-backend cards (Signal, Telegram, Discord, Slack, Matrix, Ntfy, Email, Twilio, GitHub webhook, Webhook, DNS) toggle + config popup | ✓ backends A:6766, A:14028 | ✓ ChannelsCard + Backend/ChannelConfigDialog (ChannelBackendSchemas) | ✗ | ios-missing | | |
| interaction | Signal device linking (Link Device → QR + instructions) | ✓ startLinking | ✓ SignalLinkingDialog | ✗ | ios-missing | | |
| element | Proxy Resilience card (`proxy.enabled/request_timeout/health_interval/circuit_breaker_threshold/circuit_breaker_reset/offline_queue_size`) | ✓ proxy A:14230 | ✓ CFS.Proxy | ✗ | ios-missing | | |
| element | Routing Rules card | ✓ routing_rules A:24370 | ✓ RoutingRulesCard | ✗ | ios-missing | | |
| element | Channel Routing card | ✓ channel_routing A:24413 | ✓ ChannelRoutingCard | ✗ | ios-missing | | |
| element | Push Notifications card: registrations, Register endpoint URL, Send test, status | ✓ push_notifications A:24512 | ~ PushNotificationsCard + delivery-tier row (UnifiedPush / CommChannel / Background) | ✗ | misaligned | needs-decision | D9 |
| element | Federated Peers card | ✓ fedpeers | ✓ FederationPeersCard | ✗ | ios-missing | | |

## Compute tab

| Cat | Feature | PWA | Android | iOS | Status | Decision | Notes |
|---|---|---|---|---|---|---|---|
| element | LLMs registry (rows: name, auto badge, kind, tags, switch, ✏️/×, JSON expand, "In use…" with filter/pagination) | ✓ llms A:9477 | ✓ LlmRegistryCard, LlmDetailDialog, OllamaMarketplaceDialog | ✗ | ios-missing | | |
| interaction | LLM create/edit form (§8.9 full field list, kind-dependent sections, ComputeNodes multi-select, Test row, `</> YAML` escape hatch) | ✓ buildLLMForm A:8399 | ~ LlmRegistryDialog (LlmBackendSchemas) — YAML escape hatch not confirmed | ✗ | misaligned | | field-by-field check pending |
| element | Compute Nodes panel (rows: name, auto badge, deprecated-Kind ⚠, address, capacity, tags, switch, ✏️/📡/×, dimmed when disabled; Kind-migration banner) | ✓ A:7897 | ~ ComputeNodesCard — migration banner not confirmed | ✗ | misaligned | | |
| interaction | Add / Edit ComputeNode panel-modal | ✓ buildComputeNodeForm A:8119 | ✓ ComputeNodeDialog | ✗ | ios-missing | | |
| element | Cost Rates card (USD / 1K tokens per model, Save) | ✓ costrates A:25262 | ✓ CostRatesCard | ✗ | ios-missing | | |
| element | Cluster Profiles card (list, Edit / Delete, form ↔ YAML) | ✓ gc_clusterprofiles A:15770 | ✓ KindProfilesCard(cluster) | ✗ | ios-missing | | |
| element | Memory card (18 `memory.*` keys) | ✓ LLM 'memory' | ✓ CFS.Memory | ✗ | ios-missing | | |
| element | RTK card (`rtk.enabled/binary/show_savings/auto_init/auto_update/update_check_interval/discover_interval`) | ✓ LLM 'rtk' | ✓ CFS.LlmRtk | ✗ | ios-missing | | |
| element | Web Search: config (`web_search.enabled/cache_enabled/cache_ttl_seconds`) + Providers registry (name, type, SearXNG URL, API-key secret ref, default results, enabled) | ✓ 'web_search' + websearch_providers A:12731 | ~ WebSearchRegistryCard + CFS.WebSearch with legacy keys `engine/url/num_results`, no `cache_*` | ✗ | misaligned | | Android config keys drift from PWA |
| element | Goose / OpenCode / Vision LLM config cards (`goose.*`, `opencode.default_model`, `vision.*`) | ✓ LLM goose/opencode/vision | ~ goose via LlmBackendSchemas; no `vision.*` | ✗ | misaligned | | |
| element | Container Workers card (`agents.*` 7 keys) | ✓ gc_agents A:7766 | ✓ CFS.Agents | ✗ | ios-missing | | |
| element | Detection Filters card | ✓ detection A:20770 | ✓ DetectionFiltersCard | ✗ | ios-missing | | |
| element | Alert Rules card (CRUD + firings) | ✓ alert_rules | ✓ AlertRulesCard | ~ AlertRulesView: list, add, enable/disable, swipe delete; no edit, no firings | misaligned | | |
| element | Saved Commands card | ✓ cmds | ✓ SavedCommandsCard | ✓ SavedCommandsView (add / edit+rename / delete) | aligned | | |
| element | Output Filters card | ✓ filters | ✓ FiltersCard | ✓ FiltersView (add / edit / toggle / delete) | aligned | | |
| element | Exit Hooks card | ✓ exit_hooks | ✗ | ✗ | android-missing | | iOS also missing |
| element | Work Queue card | ✓ work_queue | ✗ | ✗ | android-missing | | iOS also missing |
| element | Tailscale: status + config cards, Generate Auth Key, ACL Generate / Generate & Push | ✓ A:24165, A:24186 | ✓ TailscaleSettingsCard + TailscaleMeshCard | ✗ | ios-missing | | |
| element | Secrets Store card (vault status, list, add/update: name, value, tags, description, scopes) | ✓ secrets_store A:24137 | ✓ SecretsCard | ✗ | ios-missing | | |
| element | Federated Observer quicklink (mode + peers, "Open Observer view →") | ✓ observer_quicklink | ✓ ObserverQuicklinkCard | ✗ | ios-missing | | |

## Automata tab

| Cat | Feature | PWA | Android | iOS | Status | Decision | Notes |
|---|---|---|---|---|---|---|---|
| element | Identity card (role, goals, projects, values, focus, notes) + wizard | ✓ identity | ✓ IdentityCard + IdentityWizardSheet | ✗ | ios-missing | | |
| element | Algorithm Mode card | ✓ algorithm | ✓ AlgorithmModeCard | ✗ | ios-missing | | |
| element | Evals card (suites, runs, Run) | ✓ evals | ✓ EvalsCard | ✗ | ios-missing | | |
| element | Council panel (persona checkboxes, proposal, Quick/Debate, live SSE runs, recent 5, subsystem config, persona modal + 🤖 wizard) | ✓ council A:26111 | ✓ CouncilCard + CouncilPersonaWizardSheet | ✗ | ios-missing | | |
| element | Project Profiles card (list, Edit / Smoke Test / Delete, form ↔ YAML) | ✓ gc_projectprofiles | ✓ KindProfilesCard(project) + SmokeProgressCard | ✗ | ios-missing | | |
| element | Pipeline Manager card | ✓ pipelines A:25649 | ✓ PipelineManagerCard | ✓ PipelinesView | aligned | | |
| element | Automata Orchestrator (graphs) card | ✓ orchestrator_graphs A:24574 | ✓ OrchestratorGraphsCard | ✓ OrchestratorGraphsView | aligned | | |
| element | Guardrail Library card | ✓ automata_scan A:24770 | ✓ GuardrailLibraryCard + ScanConfigCard | ✗ | ios-missing | | |
| element | Guardrail Profiles card | ✓ A:24789 | ~ folded into GuardrailLibraryCard (GuardrailProfileRow) | ✗ | ios-missing | | |
| element | Autonomous Config card (26 `autonomous.*` keys incl. `verification_backends` editor, quality gates, capacity, per-task/story guardrails, injection guard) | ✓ automata_autonomous | ~ AutonomousConfigCard + CFS.Autonomous: has `decomposition_backend/effort`, `verification_effort`, `stale_task_seconds`; lacks `planning_backend/model/timeout_seconds`, `capacity_*`, `max_recursion_depth`, `auto_approve_children`, `per_task/per_story_guardrails`, `block_on_injection`, `injection_guard`, `verification_model` | ✗ | misaligned | | verify key set against server config schema |
| element | Pipelines config (`pipeline.max_parallel/default_backend`) | ✓ 'pipeline' | ✓ CFS.Pipelines | ✗ | ios-missing | | |
| element | Orchestrator config (`orchestrator.enabled/guardrail_backend/guardrail_model/guardrail_timeout_ms/max_parallel_prds`) | ✓ 'orchestrator' | ~ CFS.Orchestrator — no `guardrail_model` | ✗ | misaligned | | |
| element | Skill registries card (list, add/edit, connect, sync, browse) | ✓ automata_skills A:24852 | ✓ SkillRegistriesCard | ✗ | ios-missing | | |
| element | Automata Type Registry card (list, create with label/id/color, delete) | ✓ automata_type_registry A:24702 | ✓ AutomataTypesCard (Settings › Automata) | ✓ AutomataView "Types" section (Automata tab) | misaligned | needs-decision | D10 |
| element | Automata settings panel (defaults: guided, priority, type, skills, read/write dirs) | ✓ A:24627 | ~ partially in AutonomousConfigCard / PRD settings | ✗ | ios-missing | | verify Android coverage |

## Plugins tab

| Cat | Feature | PWA | Android | iOS | Status | Decision | Notes |
|---|---|---|---|---|---|---|---|
| element | Plugin framework config (`plugins.enabled/dir/timeout_ms`) | ✓ 'plugins' | ✓ CFS.Plugins | ✗ | ios-missing | | |
| element | Plugin Manager (installed list, enable/disable, test, reload, run subcommand) | ✓ plugins_list A:24307 | ~ CommunityPluginsCard (registry browse + install) | ✗ | misaligned | | Android manages installed plugins elsewhere? verify |
| element | Plugins status list (shared with Observer) | ✓ | ✓ PluginsCard (Observer) | ✗ | ios-missing | | see section 06 |

## About tab

| Cat | Feature | PWA | Android | iOS | Status | Decision | Notes |
|---|---|---|---|---|---|---|---|
| element | Server version (from `/api/health`, linked to GitHub release) | ✓ aboutVersion A:9938 | ✓ AboutCard "Connected to" + version | ~ SV shows app version only | misaligned | | iOS lacks server version |
| element | Client app version / build | n/a | ✓ "App version" | ✓ SV Version | aligned | | |
| element | Links: Project · Mobile app · store · Docs | ✓ settings_project / settings_mobile_app | ✓ Project, Mobile app, Play Store, Docs | ~ Project, Mobile app only | misaligned | | iOS: add App Store + Docs |
| element | Sessions count ("in store"), Uptime, Daemon status | ✓ settings_sessions / settings_daemon | ✓ Sessions, Uptime, Connected to | ✗ | ios-missing | | |
| element | Orphaned tmux sessions + Kill all (N) | ✓ aboutOrphanedTmux A:9950 | ✓ KillOrphansCard | ✗ | ios-missing | | |
| element | Language override (auto + en/de/es/fr/ja) | ✓ settings_language / settings_lang_auto | ✓ LanguagePickerCard | ✗ | ios-missing | | iOS has no localization at all |
| element | Theme Dark / Light / System (`cs_theme`) | ✓ themePickerAbout A:16200 | ✓ ThemePickerCard (ThemeMode) | ✗ forced `.preferredColorScheme(.dark)` | ios-missing | | |
| element | Branding / Splash (tagline + logo path → `session.splash_tagline` / `session.splash_logo_path`) | ✓ A:25502–25534 | ✗ (splash consumed, not edited) | ✗ | android-missing | | iOS also missing |
| element | Update: Check now → Update button + progress overlay | ✓ checkForUpdate / runUpdate A:13596 | ✓ UpdateDaemonCard | ✗ | ios-missing | | |
| element | Restart daemon | ✓ restartDaemon A:14222 | ✓ RestartDaemonCard | ✗ | ios-missing | | |
| element | Subsystem reload card | ✗ | ✓ SubsystemReloadCard | ✗ | pwa-missing | needs-decision | D11 |
| element | API links card (endpoint list) | ✓ 'api' header | ✓ ApiLinksCard | ✗ | ios-missing | | |
| element | MCP channel card + MCP tools card | ✗ | ✓ McpChannelCard, McpToolsCard | ✗ | pwa-missing | needs-decision | D11 |

## Coverage
rows: 97 · aligned: 12 · ios-missing: 53 · android-missing: 5 · pwa-missing: 4 · misaligned: 23 · n/a: 0

iOS reaches ~10 % of the Settings surface: server profiles, summarizer, a (non-functional) biometric toggle and an About stub. Everything config-driven (≈120 config keys across 20 cards) is absent.

## Decisions needed
1. **iOS Settings structure** — PWA/Android use six tabs; iOS is a flat 4-row list. Options: (a) adopt the six-tab bar on iOS (PWA rule), (b) keep the native grouped list but mirror the six groups as sections, (c) tabs on iPad / list on iPhone. Refs A:6697, S:119, SV.
2. **Collapsible section cards** — PWA cards collapse with remembered state; Android/iOS always expanded. Options: (a) add collapse to both apps, (b) declare n/a on mobile (scroll is cheap), (c) PWA drops collapse. Refs A:6495.
3. **Restart-needed signalling** — PWA inline "Restart now" link; Android persistent banner (arguably better). Options: (a) PWA adopts banner, (b) apps adopt inline link, (c) keep per-platform. Refs A:13939, RestartNeededBanner.
4. **Settings density** — PWA 11–13 px compact labels, Android custom small scale, iOS stock sizes. Options: (a) iOS compact scale matching PWA, (b) PWA/Android move to platform-native density. Refs S "settings-scale MaterialTheme".
5. **Secrets vault status placement** — PWA: inside Secrets Store (Compute); Android: separate card on General. Options: (a) Android moves it into SecretsCard, (b) PWA splits it out. Refs SecretsStatusCard, A:24137.
6. **Config Viewer + Raw config editor** — Android-only. Options: (a) add to PWA (and iOS), (b) keep as mobile-only ops tools, (c) remove. Refs ConfigViewerCard, RawConfigCard.
7. **Encryption status card on iOS** — Android shows SQLCipher/keystore status; iOS relies on Data Protection with no surface. Options: (a) iOS card showing Data Protection class + Keychain state, (b) n/a on iOS. Refs EncryptionStatusCard.
8. **Certificate pinning (TOFU)** — iOS-only today; Android ignores hex `trustAnchorSha256` and only offers trust-all. Options: (a) Android adopts pinning (same handler pattern), (b) iOS-only, (c) also surface "PINNED" badge on both. Refs IosTls.kt, ServerTrustSection, AndroidWsHttpClient.kt.
9. **Push card** — Android adds a delivery-tier row (UnifiedPush / CommChannel / Background) the PWA lacks; iOS needs an APNs variant. Options: (a) PWA shows tier too, (b) tier is app-only, (c) iOS card = APNs registration status + test only. Refs PushNotificationsCard, A:24512.
10. **Automata Type Registry placement** — PWA/Android: Settings › Automata; iOS: Automata tab "Types" section. Options: (a) iOS moves it under Settings (PWA rule), (b) all clients expose it in both places, (c) keep iOS placement. Refs AutomataTypesCard, AutomataView.swift.
11. **Subsystem reload + MCP channel/tools cards** — Android-only About items. Options: (a) add to PWA About, (b) app-only, (c) drop. Refs SubsystemReloadCard, McpChannelCard, McpToolsCard.
