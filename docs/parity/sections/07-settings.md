# 07 — Settings page

PWA spec §8 · live `app.js` `renderSettingsView()` (A:6666) + `GENERAL/COMMS/LLM_CONFIG_FIELDS` (A:12255–12520) · Android `ui/settings/SettingsScreen.kt` (S) + `ui/configfields/ConfigFieldSchemas.kt` (CFS) + listed packages · iOS `screens/settings/*` (SV = SettingsView, SPL = ServerProfileListView, ASV/ESV = Add/EditServerView, SSV = SettingsSessionView), `security/BiometricGate.swift`.

Ref prefixes: **A** = `datawatch/internal/server/web/app.js`, **S** = `SettingsScreen.kt`, **CFS** = `ConfigFieldSchemas.kt`; other Android refs are composable names (grep-able). Audited 2026-10-04 against PWA v8.38.0, Android v1.23.120, iOS v1.23.120.

## Shell

| Cat | Feature | PWA | Android | iOS | Status | Decision | Notes |
|---|---|---|---|---|---|---|---|
| nav | Settings view (bottom-nav tab, header "Settings") | ✓ A:6666 | ✓ S:SettingsScreen | ✓ SV | aligned | | |
| element | Six-tab bar General · Plugins · Comms · Compute · Automata · About (horizontal scroll) | ✓ A:6697–6703 | ~ S:119 SettingsTab, order General·Comms·Compute·Automata·Plugins·About | ✓ SV grouped list: six PWA groups as collapsible sections, cards as pushed rows (D31b) | aligned | needs-decision | D1 |
| nav | Active tab persisted (`cs_settings_tab`) with legacy-id migration (llm/agents→compute, monitor→general…) | ✓ A:6571–6577 | ✓ S:160–171 | ✓ SV collapsed-group state persisted (`settingsCollapsedGroups`) | aligned | | |
| interaction | Collapsible section cards (chevron, per-section state in localStorage) | ✓ A:6495 toggleSettingsSection | ✗ cards always expanded (`pwaCard`) | ✓ SV group sections collapse, state persisted (D27a) | misaligned | needs-decision | D2 |
| element | Per-card docs link (`settingsSectionHeader(id,title,docs)`) | ✓ A | ~ `Section(docsAnchor)` on some cards only | ✓ SettingsCardScreen per-card DocsLinkButton (PWA defsLink slug) | misaligned | | mechanical: wire per-card anchors |
| element | "Restart needed" after config save | ~ A:13939 inline "Restart now" link | ✓ RestartNeededBanner (persistent banner) | ✓ RestartNeededRow inline "Restart now" (D57b) | misaligned | needs-decision | D3 |
| nav | Deep link into a settings tab (`navigate('plugins' / 'comms' / 'automata')`) | ✓ A:1880–1897 | ✓ DeepLinks.kt → activeTab | ✗ | ios-missing | | |
| token | Compact settings density (11–13 px labels; Android wraps tab in "settings-scale MaterialTheme") | ✓ A | ~ S custom smaller type scale | ✓ native insetGrouped + Dynamic Type (D32) | aligned | needs-decision | D4 |
| element | Per-card loading / error states ("Loading…", red error line) | ✓ A | ✓ common_loading | ✓ SV/SSV ProgressView + error row | aligned | | |
| interaction | Destructive confirmations (delete server / LLM / node / secret, kill orphans) | ✓ showConfirmModal | ✓ AlertDialog | ✓ confirmationDialog on list deletes, restart, kill orphans, update, raw-config overwrite | aligned | | iOS has only the one destructive surface today |
| string | Settings copy localized (PWA `t()` + locale JSON; Android 4 extra locales) | ✓ A:42 loadLocale | ✓ res/values-{de,es,fr,ja} | ✗ hard-coded English, no `.lproj` | ios-missing | | |

## General tab

| Cat | Feature | PWA | Android | iOS | Status | Decision | Notes |
|---|---|---|---|---|---|---|---|
| element | Notifications card (permission status + Request Permission) | ✓ gc_notifs | ✓ NotificationsCard | ✓ SettingsNotificationsCard (UN permission + Request / Open iOS Settings) | aligned | | iOS: APNs permission UX, ties to #185 |
| element | Datawatch card: `session.log_level`, `server.auto_restart_on_config`, `session.backend_family` | ✓ 'dw' | ~ CFS.Datawatch — no `session.backend_family` | ✓ config card gc_dw (all 3 keys) | misaligned | | Android drops one key |
| element | Auto-Update card: `update.enabled/schedule/time_of_day` | ✓ 'autoupdate' | ✓ CFS.AutoUpdate | ✓ config card gc_autoupdate | aligned | | |
| element | Session card (17 keys: max_sessions, reserved_interactive, capacity_wait_seconds, input_idle_timeout, tail_lines, alert_context_lines, default_project_dir, root_path, console_cols/rows, recent_session_minutes, auto_git_init/commit, kill_sessions_on_exit, mcp_max_retries, schedule_settle_ms, suppress_active_toasts) | ✓ 'sess' | ✓ CFS.Session | ✓ config card gc_sess (17 keys) | aligned | | |
| element | Summarizer card: `session.summarizer.enabled / llm_ref / model` + `POST /api/summarizer/test` | ✓ 'summarizer' | ✓ SummarizerCard | ✓ config card gc_summarizer (enabled / llm_ref / model) + Test | aligned | | add `model` field, match picker scope |
| element | Whisper card: `whisper.enabled/backend/model/language/venv_path` + Test | ✓ 'whisper' | ~ CFS.Whisper — no `whisper.backend`; TestWhisperCard | ~ config card gc_whisper (5 keys incl. backend, language read-only); no Test button | misaligned | | |
| element | Docs Search card (query, results, pending + trusted sources, Export YAML) | ✓ docs_search | ✓ DocsSearchCard | ~ SettingsDocsSearchCard (query + results → docs viewer); no pending/trusted sources, no Export | misaligned | | |
| element | Session Templates card (list; Use / Edit / Delete) | ✓ templates | ✓ SessionTemplatesCard | ~ list card (list / add / delete); no Use / Edit | misaligned | | |
| element | Device Aliases card | ✓ device_aliases | ✓ DeviceAliasesCard | ✓ list card (list / add / delete) | aligned | | |
| element | Backend Artifact Lifecycle (Tooling) card + ↻ | ✓ tooling | ✓ ToolingCard | ✓ list card tooling (gitignore / clean up actions) | aligned | | |
| element | File Service card (root path, peers, discussions) | ✓ file_service | ✓ FileServiceCard | ~ list card (root, peers, discussions) read-only; root not editable | misaligned | | |
| element | Discussion Scopes card (scopes list, write message, New Discussion) | ✓ discussion_scopes A:23897 | ✓ DiscussionScopesCard | ~ list card (scopes) read-only; no write / New Discussion | misaligned | | |
| element | Security card: biometric lock toggle | ✗ (platform n/a) | ✓ S:703 BiometricPrompt via FragmentActivity | ~ SV toggle `biometricLockEnabled`; `BiometricLockModifier` never applied anywhere → toggle is a no-op | misaligned | | **iOS bug**: gate not enforced |
| element | Secrets vault status | ✓ inside Secrets Store card (Compute) | ✓ SecretsStatusCard as separate card on General | ✓ vault status row inside Secrets Store list card (PWA placement) | misaligned | needs-decision | D5 |
| element | Config Viewer card (read-only effective config) | ✗ | ✓ ConfigViewerCard | ✓ SettingsConfigViewerCard (D79a) | pwa-missing | decided D79a | D6 · iOS verified 2026-10-04 |
| element | Raw config editor card | ✗ | ✓ RawConfigCard | ✓ SettingsRawConfigCard — diffed dotted-key PUT, masked values never sent (D79a) | pwa-missing | decided D79a | D6 · iOS verified 2026-10-04 |
| element | Encryption status card (local DB cipher / keystore, file list) | ✗ (no local DB) | ✓ EncryptionStatusCard (About tab) | ✓ SettingsEncryptionCard: Data Protection class per file, Keychain accessibility + token count, server secure_mode (D90a) | aligned | needs-decision | D7 |

## Comms tab

| Cat | Feature | PWA | Android | iOS | Status | Decision | Notes |
|---|---|---|---|---|---|---|---|
| element | Authentication card: browser token + Save & Reconnect, `server.token`, `mcp.token` | ✓ comms_auth A:6712 | ~ CFS.CommsAuth (`server.token`, `mcp.token`; browser token n/a) | ✓ config card comms_auth (server.token / mcp.token, secure + never displayed); browser token n/a | aligned | | |
| element | Servers card: connection dot, "This server", server info | ✓ servers A:6735 | ✓ ServersCard (multi-profile list) | ✓ Comms › Servers → SPL | misaligned | | PWA is single-server by nature; apps list profiles |
| element | Remote servers + Federated Peers cards (list / add / test / enable / delete) | ✓ remote_servers A:14523, fedpeers | ✓ FederationPeersCard | ~ list cards remote_servers (list / add / delete) + fedpeers (list / delete); no test / enable / caps edit | misaligned | | |
| interaction | Add server profile (name, https URL, bearer token / no-token, trust) with probe before save | n/a | ✓ AddServerScreen | ✓ ASV | aligned | | PWA n/a |
| interaction | Edit / delete profile ("leave blank to keep" token, delete confirm) | n/a | ✓ EditServerScreen | ✓ ESV | aligned | | |
| interaction | Certificate pinning (TOFU: probe leaf fingerprint → confirm → `trustAnchorSha256`) | ✗ | ✓ Add/Edit `ServerTrustSection` + `probeServerCertificate` + `PinnedTrustManager` (REST, WS, Auto, docs WebView); hostname verification kept | ✓ ASV/ESV ServerTrustSection, CertProbe, IosTls | pwa-missing | decided D91a | D8 · Android done 2026-10-04 |
| interaction | Trust-all certificates toggle (insecure) | n/a | ✓ selfSigned Switch | ✓ ASV/ESV | aligned | | |
| interaction | Download server CA cert (`GET /api/cert`) + install guidance | ~ websrv `_tls_install` hint | ✓ ServersCard menu "Download CA cert" + CertInstallCard | ✓ ESV Download + ShareLink | aligned | | |
| element | Profile-row security badges | n/a | ✓ no auth · trust-all TLS + 🔒 certificate pinned | ✓ NO AUTH + TRUST ALL TLS | aligned | | neither shows a "PINNED" badge · Android done 2026-10-04 (android-missing sweep) |
| element | Web Server card: `server.enabled/host/port/tls/tls_port/tls_auto_generate/tls_cert/tls_key/channel_port` | ✓ websrv | ✓ CFS.WebServer | ✓ config card cc_websrv (interface picker) | aligned | | |
| element | MCP Server card: `mcp.enabled/sse_enabled/sse_host/sse_port/tls_*` | ✓ mcpsrv | ✓ CFS.McpServer | ✓ config card cc_mcpsrv | aligned | | |
| element | Communication Configuration: per-backend cards (Signal, Telegram, Discord, Slack, Matrix, Ntfy, Email, Twilio, GitHub webhook, Webhook, DNS) toggle + config popup | ✓ backends A:6766, A:14028 | ✓ ChannelsCard + Backend/ChannelConfigDialog (ChannelBackendSchemas) | ✓ SettingsCommBackendsCard: per-service toggle + configure (PWA BACKEND_FIELDS) | aligned | | |
| interaction | Signal device linking (Link Device → QR + instructions) | ✓ startLinking | ✓ SignalLinkingDialog | ✓ SignalDeviceSection in Communication Configuration (status + Link Device → sheet; POST /api/link/start + /api/link/stream SSE; `sgnl://` URI rendered with CoreImage CIQRCodeGenerator) | aligned | | |
| element | Proxy Resilience card (`proxy.enabled/request_timeout/health_interval/circuit_breaker_threshold/circuit_breaker_reset/offline_queue_size`) | ✓ proxy A:14230 | ✓ CFS.Proxy | ✓ config card proxy | aligned | | |
| element | Routing Rules card | ✓ routing_rules A:24370 | ✓ RoutingRulesCard | ✓ list card (list / add / delete) | aligned | | |
| element | Channel Routing card | ✓ channel_routing A:24413 | ✓ ChannelRoutingCard | ~ list card read-only | misaligned | | |
| element | Push Notifications card: registrations, Register endpoint URL, Send test, status | ✓ push_notifications A:24512 | ~ PushNotificationsCard + delivery-tier row (UnifiedPush / CommChannel / Background) | ✓ SettingsPushCard: APNs token + server registration status, re-register, send test (D88c) | misaligned | needs-decision | D9 |
| element | Federated Peers card | ✓ fedpeers | ✓ FederationPeersCard | ~ list card fed_peers (list / delete) | misaligned | | |

## Compute tab

| Cat | Feature | PWA | Android | iOS | Status | Decision | Notes |
|---|---|---|---|---|---|---|---|
| element | LLMs registry (rows: name, auto badge, kind, tags, switch, ✏️/×, JSON expand, "In use…" with filter/pagination) | ✓ llms A:9477 | ✓ LlmRegistryCard, LlmDetailDialog, OllamaMarketplaceDialog | ~ list card llms (name, kind·model, tags, enable switch, delete, JSON detail via long-press) + LlmFormSheet add/edit (kind-aware sections, models table w/ node probe, Test); no In-use / YAML view | misaligned | | |
| interaction | LLM create/edit form (§8.9 full field list, kind-dependent sections, ComputeNodes multi-select, Test row, `</> YAML` escape hatch) | ✓ buildLLMForm A:8399 | ~ LlmRegistryDialog (LlmBackendSchemas) — YAML escape hatch not confirmed | ~ LlmFormSheet: full PWA field set, kind-aware SaaS / session-backend / claude-code sections, ordered ComputeNodes multi-select, models table + node model probe, Test row (after save); no `</> YAML` escape hatch | misaligned | | field-by-field check pending |
| element | Compute Nodes panel (rows: name, auto badge, deprecated-Kind ⚠, address, capacity, tags, switch, ✏️/📡/×, dimmed when disabled; Kind-migration banner) | ✓ A:7897 | ~ ComputeNodesCard — migration banner not confirmed | ~ list card compute_nodes (kind·address, auto badge, tags, switch, delete, dimmed) + ComputeNodeFormSheet add/edit (kind, address, routing docker/proxy, observer peer, hardware, computed max, Test Connection); no migration banner / 📡 detail / Ollama models | misaligned | | |
| interaction | Add / Edit ComputeNode panel-modal | ✓ buildComputeNodeForm A:8119 | ✓ ComputeNodeDialog | ✓ ComputeNodeFormSheet (PWA openComputeAddPanel fields incl. routing + hardware; edit overlays GET record); no Ollama models sub-list / YAML | misaligned | | |
| element | Cost Rates card (USD / 1K tokens per model, Save) | ✓ costrates A:25262 | ✓ CostRatesCard | ✓ list card (list / add-or-replace / delete) | aligned | | |
| element | Cluster Profiles card (list, Edit / Delete, form ↔ YAML) | ✓ gc_clusterprofiles A:15770 | ✓ KindProfilesCard(cluster) | ~ list card (list / delete); no edit / YAML | misaligned | | |
| element | Memory card (18 `memory.*` keys) | ✓ LLM 'memory' | ✓ CFS.Memory | ✓ config card lc_memory (PWA 17 fields) | aligned | | |
| element | RTK card (`rtk.enabled/binary/show_savings/auto_init/auto_update/update_check_interval/discover_interval`) | ✓ LLM 'rtk' | ✓ CFS.LlmRtk | ✓ config card lc_rtk | aligned | | |
| element | Web Search: config (`web_search.enabled/cache_enabled/cache_ttl_seconds`) + Providers registry (name, type, SearXNG URL, API-key secret ref, default results, enabled) | ✓ 'web_search' + websearch_providers A:12731 | ~ WebSearchRegistryCard + CFS.WebSearch with legacy keys `engine/url/num_results`, no `cache_*` | ~ config card lc_web_search (PWA keys) + providers list card (switch / test / delete); no provider add/edit | misaligned | | Android config keys drift from PWA |
| element | Goose / OpenCode / Vision LLM config cards (`goose.*`, `opencode.default_model`, `vision.*`) | ✓ LLM goose/opencode/vision | ~ goose via LlmBackendSchemas; no `vision.*` | ✓ config cards lc_goose / lc_opencode / lc_vision | misaligned | | |
| element | Container Workers card (`agents.*` 7 keys) | ✓ gc_agents A:7766 | ✓ CFS.Agents | ✓ config card gc_agents | aligned | | |
| element | Detection Filters card | ✓ detection A:20770 | ✓ DetectionFiltersCard | ✓ config card detection (4 pattern lists + timing) | aligned | | |
| element | Alert Rules card (CRUD + firings) | ✓ alert_rules | ✓ AlertRulesCard | ~ AlertRulesView re-homed to Compute; no edit, no firings | misaligned | | |
| element | Saved Commands card | ✓ cmds | ✓ SavedCommandsCard | ✓ SavedCommandsView re-homed to Compute | aligned | | |
| element | Output Filters card | ✓ filters | ✓ FiltersCard | ✓ FiltersView re-homed to Compute | aligned | | |
| element | Exit Hooks card | ✓ exit_hooks | ✓ `ExitHooksCard` (Compute) | ✗ | ios-missing | | iOS also missing · Android done 2026-10-04 (android-missing sweep) |
| element | Work Queue card | ✓ work_queue | ✓ `WorkQueueCard` (Compute) | ✗ | ios-missing | | iOS also missing · Android done 2026-10-04 (android-missing sweep) |
| element | Tailscale: status + config cards, Generate Auth Key, ACL Generate / Generate & Push | ✓ A:24165, A:24186 | ✓ TailscaleSettingsCard + TailscaleMeshCard | ~ config card tailscale_config + list card Mesh Status (Generate ACL / Generate & Push); no auth-key generation | misaligned | | |
| element | Secrets Store card (vault status, list, add/update: name, value, tags, description, scopes) | ✓ secrets_store A:24137 | ✓ SecretsCard | ✓ list card (vault status row, list, add, delete) | aligned | | |
| element | Federated Observer quicklink (mode + peers, "Open Observer view →") | ✓ observer_quicklink | ✓ ObserverQuicklinkCard | ✗ | ios-missing | | |

## Automata tab

| Cat | Feature | PWA | Android | iOS | Status | Decision | Notes |
|---|---|---|---|---|---|---|---|
| element | Identity card (role, goals, projects, values, focus, notes) + wizard | ✓ identity | ✓ IdentityCard + IdentityWizardSheet | ~ SettingsIdentityCard form (role, goals, projects, values, focus, notes); no wizard | misaligned | | |
| element | Algorithm Mode card | ✓ algorithm | ✓ AlgorithmModeCard | ✓ Settings › Automata › Algorithm Mode (session list, 7-phase strip, output field, Advance / Edit / Abort / Reset) | aligned | | |
| element | Evals card (suites, runs, Run) | ✓ evals | ✓ EvalsCard | ~ list card (suites + Run); no run history | misaligned | | |
| element | Council panel (persona checkboxes, proposal, Quick/Debate, live SSE runs, recent 5, subsystem config, persona modal + 🤖 wizard) | ✓ council A:26111 | ✓ CouncilCard + CouncilPersonaWizardSheet | ~ list card personas (list / delete); no runs / proposal / wizard | misaligned | | |
| element | Project Profiles card (list, Edit / Smoke Test / Delete, form ↔ YAML) | ✓ gc_projectprofiles | ✓ KindProfilesCard(project) + SmokeProgressCard | ~ list card (list / Smoke test / delete); no edit / YAML | misaligned | | |
| element | Pipeline Manager card | ✓ pipelines A:25649 | ✓ PipelineManagerCard | ✓ PipelinesView re-homed to Automata | aligned | | |
| element | Automata Orchestrator (graphs) card | ✓ orchestrator_graphs A:24574 | ✓ OrchestratorGraphsCard | ✓ OrchestratorGraphsView re-homed to Automata | aligned | | |
| element | Guardrail Library card | ✓ automata_scan A:24770 | ✓ GuardrailLibraryCard + ScanConfigCard | ~ list card read-only; scan config lives in the new Autonomous Config card (Scan defaults section) | misaligned | | |
| element | Guardrail Profiles card | ✓ A:24789 | ~ folded into GuardrailLibraryCard (GuardrailProfileRow) | ~ list card (list / delete); no create / edit | misaligned | | |
| element | Autonomous Config card (26 `autonomous.*` keys incl. `verification_backends` editor, quality gates, capacity, per-task/story guardrails, injection guard) | ✓ automata_autonomous | ~ AutonomousConfigCard + CFS.Autonomous: has `decomposition_backend/effort`, `verification_effort`, `stale_task_seconds`; lacks `planning_backend/model/timeout_seconds`, `capacity_*`, `max_recursion_depth`, `auto_approve_children`, `per_task/per_story_guardrails`, `block_on_injection`, `injection_guard`, `verification_model` | ✓ config card gc_autonomous (PWA 27 keys) | misaligned | | verify key set against server config schema |
| element | Pipelines config (`pipeline.max_parallel/default_backend`) | ✓ 'pipeline' | ✓ CFS.Pipelines | ✓ config card gc_pipeline | aligned | | |
| element | Orchestrator config (`orchestrator.enabled/guardrail_backend/guardrail_model/guardrail_timeout_ms/max_parallel_prds`) | ✓ 'orchestrator' | ~ CFS.Orchestrator — no `guardrail_model` | ✓ config card gc_orchestrator (incl. guardrail_model) | misaligned | | |
| element | Skill registries card (list, add/edit, connect, sync, browse) | ✓ automata_skills A:24852 | ✓ SkillRegistriesCard | ~ list card (list / add / Connect / delete); no browse / sync | misaligned | | |
| element | Automata Type Registry card (list, create with label/id/color, delete) | ✓ automata_type_registry A:24702 | ✓ AutomataTypesCard (Settings › Automata) | ✓ Settings › Automata › Type Registry (D25a; removed from Automata tab) | aligned | needs-decision | D10 |
| element | Automata settings panel (defaults: guided, priority, type, skills, read/write dirs) | ✓ A:24627 | ~ partially in AutonomousConfigCard / PRD settings | ✓ Autonomous Config card (automata_autonomous): poll interval, max parallel tasks, auto-fix retries + Scan defaults (/api/autonomous/scan/config toggles, fail-on severity, max findings, fix-loop retries) — matches live PWA loadAutomataSettingsPanel | aligned | | live PWA panel = autonomous config + scan config; the guided/priority/type/skills/dirs list is per-PRD (PRD settings modal) |

## Plugins tab

| Cat | Feature | PWA | Android | iOS | Status | Decision | Notes |
|---|---|---|---|---|---|---|---|
| element | Plugin framework config (`plugins.enabled/dir/timeout_ms`) | ✓ 'plugins' | ✓ CFS.Plugins | ✓ config card gc_plugins | aligned | | |
| element | Plugin Manager (installed list, enable/disable, test, reload, run subcommand) | ✓ plugins_list A:24307 | ~ CommunityPluginsCard (registry browse + install) | ✓ list card plugins (native + subprocess, enable/disable switch, Reload plugins); no test / run subcommand | misaligned | | Android manages installed plugins elsewhere? verify |
| element | Plugins status list (shared with Observer) | ✓ | ✓ PluginsCard (Observer) | ~ Settings › Plugins › Plugin Manager list (Observer copy: section 06) | misaligned | | see section 06 |

## About tab

| Cat | Feature | PWA | Android | iOS | Status | Decision | Notes |
|---|---|---|---|---|---|---|---|
| element | Server version (from `/api/health`, linked to GitHub release) | ✓ aboutVersion A:9938 | ✓ AboutCard "Connected to" + version | ✓ SettingsAboutCard server version (not linked to release) | misaligned | | iOS lacks server version |
| element | Client app version / build | n/a | ✓ "App version" | ✓ SettingsAboutCard App version | aligned | | |
| element | Links: Project · Mobile app · store · Docs | ✓ settings_project / settings_mobile_app | ✓ Project, Mobile app, Play Store, Docs | ~ Project, Mobile app, Docs (no App Store link yet) | misaligned | | iOS: add App Store + Docs |
| element | Sessions count ("in store"), Uptime, Daemon status | ✓ settings_sessions / settings_daemon | ✓ Sessions, Uptime, Connected to | ✓ SettingsAboutCard sessions in store + uptime | aligned | | |
| element | Orphaned tmux sessions + Kill all (N) | ✓ aboutOrphanedTmux A:9950 | ✓ KillOrphansCard | ✓ SettingsAboutCard list + Kill all (N) with confirm | aligned | | |
| element | Language override (auto + en/de/es/fr/ja) | ✓ settings_language / settings_lang_auto | ✓ LanguagePickerCard | ~ About row opens iOS per-app language (system Settings) — native control | misaligned | | iOS has no localization at all |
| element | Theme Dark / Light / System (`cs_theme`) | ✓ themePickerAbout A:16200 | ✓ ThemePickerCard (ThemeMode) | ✗ About row shows Dark only — light palette + root wiring not built | ios-missing | | |
| element | Branding / Splash (tagline + logo path → `session.splash_tagline` / `session.splash_logo_path`) | ✓ A:25502–25534 | — not added | ✗ | n/a | | iOS also missing · PWA removed the Branding/Splash card in v6.12.0 (`loadBrandingPanel` is dead code) |
| element | Update: Check now → Update button + progress overlay | ✓ checkForUpdate / runUpdate A:13596 | ✓ UpdateDaemonCard | ✓ SettingsAboutCard Check now → Update (confirm) | aligned | | |
| element | Restart daemon | ✓ restartDaemon A:14222 | ✓ RestartDaemonCard | ✓ SettingsAboutCard Restart (confirm) | aligned | | |
| element | Subsystem reload card | ✗ | ✓ SubsystemReloadCard | ✓ SettingsSubsystemReloadCard (config / filters / memory) (D80a) | pwa-missing | decided D80a | D11 · iOS verified 2026-10-04 |
| element | API links card (endpoint list) | ✓ 'api' header | ✓ ApiLinksCard | ✓ SettingsApiLinksCard (Swagger, OpenAPI, docs, MCP tools) | aligned | | |
| element | MCP channel card + MCP tools card | ✗ | ✓ McpChannelCard, McpToolsCard | ✓ SettingsMcpChannelCard + SettingsMcpToolsCard (D80a) | pwa-missing | decided D80a | D11 · iOS verified 2026-10-04 |

## Coverage
rows: 97 · aligned: 46 · ios-missing: 6 · android-missing: 0 · pwa-missing: 5 · misaligned: 39 · n/a: 1

iOS Settings rebuilt 2026-10-04 (B26–B32): six PWA groups as a native grouped list; schema-driven config cards ported from PWA `GENERAL/COMMS/LLM_CONFIG_FIELDS`, generic list cards over `IosSettingsLists`, bespoke About/Push/Encryption/raw-config cards. Remaining iOS gaps are the `✗`/`~` rows above (council runs, LLM In-use / YAML views, Ollama model management, etc.). Added 2026-10-04: LLM + ComputeNode add/edit forms, Algorithm Mode, Autonomous Config + scan defaults, Signal device linking (QR), Dark/Light/System theme.

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
