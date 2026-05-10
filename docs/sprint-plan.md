# Sprint Plan — MVP → Production → Parity

*Last updated 2026-04-22 for v0.33.0.*

**Historical sprints 0–6** below delivered v0.10.0 (MVP scope close
per ADR-0042). Since then, v0.11.0–v0.33.0 have been rapid feature
sprints (Sprints 7+ / letter-named A..CC) closing PWA parity. The
v1.0.0 tag is reserved for the release that closes the last ⏳ / 🚧
rows in [parity-plan.md](parity-plan.md) per ADR-0043.

Team: solo + Claude. Sprint length: historically 2 weeks; v0.11+
sprints are auto-cadenced (phase-end + green tests → patch bump →
continue) per memory rule `feedback_phase_commit_cadence.md`.

## Gantt snapshot

```
Week:        1   2   3   4   5   6   7   8   9  10  11  12
Sprint:     |--S0---|--S1---|--S2---|--S3---|--S4---|--S5---|--S6---|
Phase:      Design  MVP1    MVP2    MVP3    MVP4    Harden  Release
                                                  ↑                ↑
                                       MVP → Internal           Public prod
```

## Definition of Done (every sprint)

- All new code has Kotlin tests with close to 100% coverage on new/changed logic.
- `./gradlew test connectedCheck detekt ktlint lintRelease` all green.
- Live-tested against a running parent datawatch server on at least one real device per
  affected surface.
- `docs/testing-tracker.md` updated with Tested + Validated rows.
- `CHANGELOG.md` updated under `[Unreleased]`.
- Version bumped per AGENT.md rules; all three Version locations matched.
- Branch merged to `main`; internal AAB built and uploaded to Play Console Internal Testing
  track (from Sprint 1 onward).

## Sprint 0 — Design + scaffold (Week 1–2, ending 2026-05-01)

Goal: convert design package into a committed, buildable skeleton published at
`github.com/dmz006/datawatch-app`.

- [ ] Review + approve design package (this folder) — user sign-off on all ADRs.
- [ ] Icon concept pick (A / B / C from `branding.md`) → produce full icon set.
- [ ] Register new Google Play Console developer account
      (`play-store-registration.md` steps 1–4).
- [ ] Draft + publish privacy policy at `https://dmzs.com/datawatch-client/privacy`.
- [ ] Scaffold repo with Gradle + KMP + Android + Wear + Auto + iOS skeleton modules.
- [ ] Commit AGENT.md, LICENSE, README, CHANGELOG, SECURITY.md, docs/ tree.
- [ ] GitHub Actions CI: build + test + detekt + ktlint + lint green on first push.
- [ ] `gh repo create dmz006/datawatch-app --public --license "Polyform-NC-1.0.0"` — push.
- [ ] First hollow AAB builds from CI; tagged `v0.1.0` (pre-MVP).

Exit criteria: an app that launches into a placeholder screen, signed with the upload key,
build artifacts flow through CI, docs framework in place.

## Sprint 1 — Connectivity + sessions read path (Week 3–4, ending 2026-05-15)

Goal: user can add a server profile, see session list, see session detail.

- [ ] `shared`: `TransportClient` REST impl with bearer auth, OkHttp + Ktor.
- [ ] `shared`: `SQLDelight` schema v1, SQLCipher open, master key in Keystore.
- [ ] `shared`: `ServerProfileRepository`, `SessionRepository`.
- [ ] `composeApp`: onboarding + add-server + bottom-nav shell + Sessions tab.
- [ ] Reachability indicator + online/offline banner (ADR-0013).
- [ ] Network security config for self-signed cert trust anchors.
- [ ] Cert pinning opt-in UI.
- [ ] Tests: MockWebServer round-trips; SQLDelight migration test; encryption roundtrip.
- [ ] Version: `v0.2.0` — first Play Console Internal Testing upload.

## Sprint 2 — Session detail + WebSocket + chat (Week 5–6, ending 2026-05-29)

Goal: full real-time session interaction from the phone.

- [ ] WebSocket transport for `/ws?session=`, reconnect + backoff.
- [ ] Session detail screen, chat messages, pending-prompt highlight.
- [ ] Reply composer (text only; voice in Sprint 3).
- [ ] Bottom-sheet pattern (Terminal sheet is a stub; Memory/Logs/Timeline placeholders).
- [ ] xterm.js WebView embedded, streaming ANSI frames to terminal sheet.
- [ ] Memory tab MVP: recall + remember via REST.
- [ ] Schedules tab read-only view.
- [ ] Stats tab read-only dashboard.
- [ ] FCM device token registration (pending parent endpoint; ntfy fallback wired).
- [ ] Tests: WebSocket replay, terminal render, Compose UI tests for all screens.
- [ ] Version: `v0.3.0` — Internal Testing; user live-tests daily driving.

## Sprint 3 — Voice + MCP + all-servers view (Week 7–8, ending 2026-06-12 = MVP)

Goal: feature parity stops feeling partial.

- [ ] Voice capture component (phone): FAB + composer mic + quick-tile + ASSIST intent.
- [ ] Voice upload + Whisper server endpoint (or Telegram-path workaround).
- [ ] Prefix auto-send logic + preview flow.
- [ ] MCP SSE client; tool invocation from quick commands + tool picker.
- [ ] 37 MCP tools surfaced in a searchable picker (power-user entry point).
- [ ] All-servers view toggle in server drawer; parallel fan-out.
- [ ] Proxy drill-down: breadcrumb primary variant + chips variant, both behind a toggle
      during pre-MVP review.
- [ ] Notifications: MessagingStyle with full action set; mute / mute-10m.
- [ ] Intent-handoff relay to Signal/SMS apps (ADR-0004).
- [ ] Tests + live device pass.
- [ ] Version: `v0.4.0` → MVP tag. Promote Internal build to a wider test group if the user
      wants (optional).

### MVP exit criteria (end of Sprint 3)

- Every ✅ item in `api-parity.md` functional on at least the Android phone surface.
- All ADR-0019 🔐 confirms implemented.
- Daily driving with 1 week of clean telemetry shows no P0 bugs.

## Sprint 4 — Wear + internal Auto (Week 9–10, ending 2026-06-26)

Goal: finish the three-surface experience, lock in the internal Auto build.

- [ ] Wear module: notification + complication + rich app (W1, W3, W4).
- [ ] Wearable Data Layer bridging; token stays on phone.
- [ ] Android Auto — internal build (`.dev` flavor) with full passenger UI.
- [ ] Android Auto — public build skeleton with Messaging template (behavior, not submitted
      for review yet).
- [ ] DHU testing for both Auto builds.
- [ ] Version: `v0.5.0`.

## Sprint 5 — Harden + public Auto submission + closed testing (Week 11, ending 2026-07-03)

Goal: freeze features, open Play Console Closed Testing track, submit public Auto for
Google Driver Distraction Evaluation.

- [ ] Final accessibility audit (TalkBack, dynamic type, contrast).
- [ ] Performance pass: cold start < 1.5 s, session detail open < 400 ms warm.
- [ ] OWASP `dependencyCheckAnalyze` clean.
- [ ] Penetration walk against personal datawatch server (intent fuzz, cert tamper, FCM
      flood).
- [ ] Play Store listing assets final (icon, feature graphic, screenshots).
- [ ] Privacy policy review.
- [ ] Data Safety form submitted as defined in `data-safety-declarations.md`.
- [ ] Closed Testing track opened with 5-user target (friends/family/self).
- [ ] Android Auto — public build submitted for **Driver Distraction Evaluation**.
- [ ] Version: `v0.9.0` (RC-equivalent).

## Sprint 6 — Open testing → production (Week 12, ending 2026-07-10 = PRODUCTION)

Goal: public release.

- [ ] Open Testing track public (subset of features if Auto review is still pending).
- [ ] Pre-launch report on Play Console reviewed; all Critical / Warning fixed.
- [ ] Auto review signed off by Google (if running long, ship phone+Wear to Production
      and stage Auto behind a feature flag).
- [ ] Production track release at 1% → 10% → 100% rollout over 72 hours.
- [ ] Version: `v0.10.0` (ADR-0042 scope close; **v1.0.0 is reserved
      for the release that reaches full PWA parity — see ADR-0043**).

## Risks + mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Google Driver Distraction review takes > 3 weeks | Auto not in v0.10.0 | Phone + Wear ship; Auto behind feature flag, promoted in v0.10.1 |
| Parent datawatch endpoints not ready (dmz006/datawatch#1, #2, #3) | MVP blocked | Workarounds pre-Sprint 2: ntfy fallback for #1, Telegram-path reuse for #2, per-profile loop for #3 |
| Play Console $25 fee + ID verification delay | Sprint 0 slip | Start immediately in Sprint 0 day 1; do not block scaffolding on it |
| Icon review ambiguity | Sprint 0 slip | Pick Concept A as default — parent-identity-preserving; re-theme if user prefers B/C |
| xterm.js perf on low-end phones | Sprint 2 slip | Fallback: native Compose text view showing last 200 lines; full xterm behind a toggle |
| FCM vs ntfy battery drain on Android 15 foreground-service tighten-up | Sprint 3 | Test both; pick per-profile; ntfy is best-effort only |

## Scope change — ADR-0042 (2026-04-18)

Five items **promoted from post-MVP to v0.10.0 requirements** per user
direction (originally labelled "v1.0.0" before ADR-0043's label
correction). Each is folded into the nearest-fit sprint; timeline holds:

| Item | Sprint | ID |
|------|--------|----|
| 3-finger swipe-up server picker (HA-style gesture) | 2 | BL9 |
| Home-screen widget (session count + voice quick action) | 3 | BL6 |
| Wear Tile (W2) — at-a-glance session state | 4 | BL4 |
| Android Auto Tile — parked-state dashboard (dev flavor only) | 4 | BL10 |
| Biometric unlock (optional, opt-in) | 5 | BL2 |

If any of the five threatens the 2026-06-12 MVP or 2026-07-10 production
target, the 3-finger gesture slips back to post-MVP first (it's the most
easily replaceable — standard tap-to-open tree drawer stays either way).

## v0.11–v0.33 feature sprints (post-MVP, parity-closing)

These shipped after v0.10.0's ADR-0042 scope closed. They are the
content of `CHANGELOG.md`'s `[0.11.0]`–`[0.33.0]` sections;
summarised here for sprint-plan continuity.

| Version | Sprint label | Highlights |
|---------|--------------|-----------|
| v0.11.0 | Session power-user parity | rename / restart / delete (single + bulk), connection dot, About hostname, CA cert download, terminal search + copy, new-session form, backend picker |
| v0.12.0 | Channels + schedules + files | channels read, schedules CRUD, file picker, session preferences, timeline viewer, model pickers, daemon log + interfaces + restart |
| v0.13.0–v0.13.1 | Memory + daemon config | memory CRUD, daemon config read-only viewer, per-session schedules strip |
| v0.14.0–v0.14.2 | Sessions list PWA toolbar | text search + backend chips + show-history toggle, Quick Commands sheet, last-response viewer, Sort dropdown |
| v0.15.0 | Voice + profiles | voice-to-new-session prefix, profile picker on New Session |
| v0.16.0 | Daemon monitoring | logs card, interfaces card, restart card |
| v0.17.0 | Memory + KG | stats grid, KG timeline, KG graph read-only |
| v0.18.0 | Channels | list / test / enable / disable, per-channel Switch |
| v0.19.0 | Schedules + active-session suppress | alert row schedule action, ForegroundSessionTracker |
| v0.20.0 | Behaviour preferences + federation | recent-window / max-concurrent / scrollback, FederationPeersCard |
| v0.21.0 | LLM backend config | BackendConfigDialog (structured fields per ADR-0019) |
| v0.22.0–v0.22.1 | Input/output mode + update daemon | BehaviourPreferencesCard input_mode / output_mode, memory export SAF, `/api/update` integration |
| v0.23.0 | Terminal parity | resize_term WS + pane_capture throttle + freeze-on-done + watchdog + configCols |
| v0.24.0–v0.25.0 | Terminal polish + ops | Fit / Jump-to-bottom, additional ops cards |
| v0.26.0 | ConfigFieldsPanel | 12-section PWA Settings renderer |
| v0.27.0 | Filters CRUD + new-session | filters, session-type fields, polish |
| v0.28.0 | Project + cluster profiles + proxy resilience | profile CRUD stubs |
| v0.29.0 | About + notifications | API links card, Notifications card |
| v0.30.0 | Auto live data | Auto session counts via AutoServiceLocator |
| v0.31.0 | Session reorder | ⇅ toggle + Custom sort via POST /api/sessions/reorder |
| v0.32.0 | Close PWA-parity gap | DetectionFiltersCard, McpToolsCard, CertInstallCard, ProfileEditDialog, chat bubble rendering, Quick Commands arrow keys, update progress bar, recent-sessions backlog grid |
| v0.33.0 | Auto bundling fix + docs | composeApp → auto dependency + missingDimensionStrategy → CarAppService actually ships; android-auto.md + README refresh |
| v0.33.1–v0.33.25 | Auto + Wear hardening + widget fixes | Auto Messaging + Monitor, AA publicMessaging / devPassenger split, Wear Monitor tile, home-screen widget, 3-finger server cycle, voice routing |
| v0.34.0–v0.34.9 | Schema-driven config + P0/P1 parity | Channels/LLM dialogs schema-driven, P0 session-id contract fix, P1 Alerts rebuild, Settings restart affordance, G6 drag-drop reorder |
| v0.35.0–v0.35.6 | Final PWA-parity milestone + Wear feature arc + composer reshuffle | LLM backend config paths fixed, inline rename, state-override dropdown, Response composer button, Sessions list UX (search collapse + right-side reachability dot + lower FAB), Wear round bezel cards, release workflow fix, R8 `-dontwarn` hardening, Wear Monitor colour gauges (CPU/Mem/Disk/GPU with thresholds), Wear Sessions tap-popup with voice-to-text reply via transient WS, composer Saved-Commands under mic + badge-row toggle for terminal toolbar, voice-reply routed to session's own profile, Kill Orphans moved to About. **Parity milestone closed 2026-04-24 at v0.35.2**; v0.35.3–v0.35.6 ship post-milestone UX + Wear feature polish per [`docs/plans/audit-2026-04-23/README.md`](plans/audit-2026-04-23/README.md). |
| v0.35.7 | PWA v5.1.0–v5.2.0 alignment + data freshness | Terminal toolbar always renders (reverts v0.35.6 toggle), History label rename, tmux arrow-key row, About + Play Store row, ConfigViewerCard removed from About, live `last_response` refetch on Response tap, Input-Required banner refresh on bulk WS state changes. Closes [#8](https://github.com/dmz006/datawatch-app/issues/8) and the v5.1.0–v5.2.0 portions of [#9](https://github.com/dmz006/datawatch-app/issues/9). |
| v0.35.8 | Wear voice fix + popup polish | Wear sessions row prefers `lastResponse` over `lastPrompt`; popup mic anchored to right edge with Send chip on left edge; `RecognizerIntent` replaced with new `WearVoiceRecorder` + phone-relayed Whisper via `/datawatch/audio` and `/datawatch/transcript` MessageClient paths. Reuses v0.35.5 reply plumbing for the final send_input forward. |
| v0.35.9 | Session detail layout rebuild | Badges row moves above the tmux/channel tabs; new unified quick-actions strip above the composer (Last-Response + Saved-Commands + ANSI arrow chips) replaces both the standalone arrow `LazyRow` and the under-mic Saved-Commands stack; composer reordered to Send → Schedule → Mic; Last Response icon (`Description`) is now the single canonical glyph across both the badge bar and the quick-actions row. PWA-aligned per user direction 2026-04-28. |
| v0.35.10 | Session detail force-refresh on open | VM `init` triggers `refreshFromServer()` so the screen never displays the 5-second-stale list cache. |
| v0.36.0 | Federated monitoring suite | Four new Settings → Monitor cards: Federated peers (with All/Standalone/Cluster/Agents filter), Cluster nodes (Shape C w/ pressure flags + bars), eBPF status (3-flag block + message), Plugins (subprocess + native rows w/ kind badges). New `/api/observer/{stats,peers}` + `/api/plugins` endpoints. Closes [#2](https://github.com/dmz006/datawatch-app/issues/2)–[#6](https://github.com/dmz006/datawatch-app/issues/6). |
| v0.36.1 | Picker mkdir + response-noise filter | `FilePickerDialog` "+ New folder" + `TransportClient.mkdir()`; new shared `ResponseNoiseFilter` strips TUI noise from `LastResponseSheet` (full unit-test coverage). Closes [#14](https://github.com/dmz006/datawatch-app/issues/14) + [#15](https://github.com/dmz006/datawatch-app/issues/15). |
| v0.36.2 | Connection resilience completions | Reachability dot pulses while probing (1.0 ↔ 1.4 on 900 ms reverse loop); `AppRoot` registers `ON_RESUME` lifecycle observer that pings every enabled profile on screen-unlock. |
| v0.37.0 | Mempalace surfaces | Settings → Monitor "Mempalace" card with Sweep stale (older-than + dry-run), Spellcheck (Levenshtein), Extract facts (SVO triples). New `memoryPin / memorySweepStale / memorySpellcheck / memoryExtractFacts / memoryWakeup` transport methods. Closes [#21](https://github.com/dmz006/datawatch-app/issues/21). |
| v0.38.0 | Autonomous tab foundation | New "PRDs" bottom-nav tab. `AutonomousScreen` lists PRDs from `/api/autonomous/prds`. `NewPrdDialog` with the unified Profile dropdown (`__dir__` sentinel + project profiles) + Cluster sub-dropdown when profile is selected. Routes `POST /api/autonomous/prds` per mode. Closes [#11](https://github.com/dmz006/datawatch-app/issues/11). |
| v0.38.1 | Autonomous tab features | Filter pill row behind 🔍 toggle (FAB hides while detail is open); `PrdDetailDialog` with story description rendering + ✎ edit modal, Approve / Reject buttons (Reject prompts for reason), file pills (📝 blue=planned + green=touched) + ✎ files edit modal. New `editStory / editFiles` transport. Closes [#12](https://github.com/dmz006/datawatch-app/issues/12), [#13](https://github.com/dmz006/datawatch-app/issues/13), [#18](https://github.com/dmz006/datawatch-app/issues/18), [#19](https://github.com/dmz006/datawatch-app/issues/19). |
| v0.39.0 | Orchestrator PRD-DAG graph + observer_summary | New `OrchestratorGraphDialog` reachable from `PrdDetailDialog` via 📊 Graph button. Renders nodes as a list with per-node observer_summary badge (CPU%, RSS MB, envelope count); outgoing edges render as `→ targetId (kind)` lines. New `orchestratorGraph(id)` transport. Closes [#7](https://github.com/dmz006/datawatch-app/issues/7). |
| v0.39.1 | New Session unified Profile + cluster routing | Cluster sub-dropdown on `NewSessionScreen` when a project profile is selected (first option = local-service-instance sentinel). Start button branches: project profile → `POST /api/agents` via new `startAgent` transport; no profile → historic `POST /api/sessions/start`. Closes [#20](https://github.com/dmz006/datawatch-app/issues/20). |
| v0.40.0–v0.40.2 | Wear + Auto PRD actions + test coverage | Wear PRDs page (approve/reject glance via DataLayer), Auto `WaitingPrdsScreen` + `PrdActionScreen`, 11 DTO round-trip tests |
| v0.41.0–v0.41.1 | ProfileResolver shim + VM unit tests | `ProfileResolver` interface; 20 unit tests for `AutonomousViewModel` + 6 monitoring VMs; PRDs nav icon → SmartToy |
| v0.42.0–v0.42.12 | Compact terminal UI + hardening + P0 fix | Compact tmux/channel tabs, inline terminal toolbar, Wear session popup last-response, Container Workers ⬡ pill, P0 session-id contract fix, toolbar Scroll + About PWA-aligned |
| v0.50.0 | Per-core CPU strip in Monitor | BL5 — per-core `CpuCoreStrip` in StatsScreen |
| v0.52.0–v0.53.0 | Terminal stability + Wear monitor redesign + i18n | BL15 Wear OS locale, BL22 Wear active-server indicator, BL23 i18n wave-2 strings, BL24 MCP channel bridge card; Wear Monitor redesign; PRD UI polish |
| v0.55.0–v0.55.3 | Input banner gate + patches | Gate input banner on `needsInput` field; 3 incremental patches |
| v0.56.0 | Wear feature arc | W-2 W-4 W-7 W-8 W-9 W-10 BL27 — watchface complications, notification improvements |
| v0.57.0 | eBPF network card + session stats panel | `EBpfNetworkCard` in Settings → Monitor (B11/B63 ✅), `SessionStatsPanel` in session detail; filed [datawatch#34](https://github.com/dmz006/datawatch/issues/34) |
| v0.58.0 | Quick-commands from API + PRD card colors + reconnect refresh | BL249/BL250 reconnect refresh, #31 quick-commands API, #41 PRD status border colors |
| v0.59.0 | Settings Automata + Plugins tabs | PWA v6.5.1 tab structure alignment (#48), workspace label (#47), detekt/ktlint clean |
| v0.60.0 | Language picker + Whisper sync | #40 `LanguagePickerCard` in About; `whisper.language` config binding |
| v0.61.0 | Template Store UI — BL221 Phase 2 | Templates tab in Autonomous, `CreateEditTemplateSheet`, `InstantiateTemplateDialog`, 7 transport methods, 6 DTOs. Closes [#44](https://github.com/dmz006/datawatch-app/issues/44). |
| v0.62.0 | Security scan — BL221 Phase 3 | `ScanResultCard` + `ScanConfigCard` in Settings → Automata, 6 transport methods, 4 DTOs. Closes [#45](https://github.com/dmz006/datawatch-app/issues/45). |
| v0.63.0 | Type registry + Guided Mode + Skills — BL221 Phase 4 | `AutomataTypesCard`, type badge on `PrdRow`, Guided Mode toggle, Skills chips in create/detail; 3 transport methods. Closes [#43](https://github.com/dmz006/datawatch-app/issues/43). |
| v0.64.0 | Signal device-linking — BL21 | `SignalLinkingDialog` with SSE QR frame stream, DB migration 6, `startSignalLinking` SSE transport. Closes [datawatch#31](https://github.com/dmz006/datawatch/issues/31). |
| v0.65.0 | i18n full sync — BL252 | 375 missing keys across EN/DE/ES/FR/JA + 22 Wear keys; Signal strings wired. Closes [#46](https://github.com/dmz006/datawatch-app/issues/46). |
| v0.66.0 | Skill Registries — BL255 | `SkillRegistriesCard` in Settings → Automata; connect/browse/sync flow, Add Default (PAI), synced-skills summary; 10 transport methods, 6 DTOs, 45 locale keys. Closes [#50](https://github.com/dmz006/datawatch-app/issues/50). |

## v1.0.0 parity roadmap

**All rows ✅ as of v0.66.0 — v1.0.0 is ready to tag.**

Previously blocking items (both shipped):
- `POST /api/channels` add/remove — shipped v0.33.11 (upstream [datawatch#18](https://github.com/dmz006/datawatch/issues/18) closed)
- Per-process eBPF network viewer — shipped v0.57.0 (`EBpfNetworkCard`)
- Skill Registries (BL255) — shipped v0.66.0 (`SkillRegistriesCard`)

All parity rows in [parity-plan.md](parity-plan.md) are ✅.

## Post-ADR-0042-scope backlog

| ID | Item | Status |
|----|------|--------|
| BL1 | Split `decisions/README.md` into per-ADR MADR files | **shipped** (all 43 ADRs in individual MADR files since ≤v0.35.0) |
| BL3 | Tablet two-pane session list + detail | **shipped** AppRoot.kt ≥600 dp two-pane (v0.33.x) |
| BL5 | iOS app content — beyond skeleton | **frozen** (2026-05-05) |
| BL7 | Foldable support — Pixel Fold / Galaxy Z Fold | **shipped** same 600 dp two-pane as BL3 (v0.33.x) |
| BL11 | Full schedule editor CRUD | **shipped** v0.68.0 — edit dialog + PATCH transport |
| BL12 | KG Add / Timeline / Research deeper views | **shipped** v0.69.0 — Add button, Timeline tab, Research tab |
| BL13 | Adjustable terminal dimensions | **shipped** v0.67.0 — TerminalDimensionsCard + SharedPreferences |
| BL14 | Raw YAML config editor, gated behind biometric + confirm | **frozen** (2026-05-05) |
| BL15 | Localization — DE, ES, FR, JA | **shipped** v0.52.0 + v0.65.0 |
| BL21 | Signal device-linking | **shipped** v0.64.0 |
| BL221 | Automata BL221 phases 2–4 (Templates, Scan, Type/Skills) | **shipped** v0.61.0–v0.63.0 |
| BL252 | i18n full sync | **shipped** v0.65.0 |
| BL255 | Skill Registries | **shipped** v0.66.0 |

## v0.70+ sprint plan (post-parity upgrade arc)

*Last updated 2026-05-09. Derived from triage of all 42 open issues on `dmz006/datawatch-app`
and alignment with the parent datawatch server's v6.11+ feature surface. Sprint 0 is a
placeholder for the "one more batch" the user mentioned before implementation begins.*

*Version targets below are indicative — each sprint is a minor bump with as many patch
releases as needed.*

### Sprint overview

| Sprint | Version target | Issues | Theme |
|--------|---------------|--------|-------|
| Sprint 0 | v0.70.0 | (TBD — incoming batch) | Pre-arc batch placeholder |
| Sprint 1 | v0.70.x | #51, #52, #57, #59, #72, #73, #80 | Quick wins + UX alignment |
| Sprint 2 | v0.71.x | #53, #56, #58, #74, #75 | Identity + Council Mode |
| Sprint 3 | v0.72.x | #54, #55, #77, #82 | Algorithm + Evals + Vault |
| Sprint 4 | v0.73.x | #83, #84–#90 | Docs-as-MCP (BL274) |
| Sprint 5 | v0.74.x | #91, #92 | Sprint A close + Council wizard |
| PWA-only | — | #60, #61, #62–#70, #76, #78, #79, #81 | No mobile action required |

---

### Sprint 0 — Incoming batch (v0.70.0)

*Placeholder — user indicated one more batch of changes before the arc begins.*

- TBD: capture any urgent parity fixes, locale gaps, or pre-arc tech-debt items here.
- Exit criteria: main is clean; all known pre-arc P0/P1 issues addressed.

---

### Sprint 1 — Quick wins + UX alignment (~v0.70.x)

Goal: rename `PRD → Automaton` everywhere, spread bottom nav, add Agents tab, and clear
accumulated UX debt.

| Issue | Description | Mobile work |
|-------|-------------|-------------|
| #59 | Rename "PRD" → "Automaton" throughout the UI | `AutonomousScreen`, all tab labels, strings, `PrdRow → AutomatonRow`, DTOs |
| #51 | Bottom-nav spread — 5-item nav bar evenly spaced | `AppRoot.kt` bottom nav spacing |
| #52 | Add Settings → Agents tab (separate from Automata) | New `AgentsTab` composable + nav entry; BL257–BL260 card stubs |
| #57 | Card padding consistency (`pwaCard` inner padding) | Audit all `pwaCard()` call sites for consistent 16 dp inner padding |
| #72 | Done-state sessions render at 60 % opacity in list | `SessionRow` alpha based on status |
| #73 | Multi-select action bar for session list bulk ops | Selection state + `ActionBar` composable above the list |
| #80 | Move theme picker from Settings → General to About | `ThemePickerCard` relocated to `AboutScreen` |

---

### Sprint 2 — Identity + Council Mode (~v0.71.x)

Goal: implement BL257 Identity and BL260 Council Mode surface; align Automata/Agents tab split.

| Issue | Description | Mobile work |
|-------|-------------|-------------|
| #53 | BL257 — Identity feature | `IdentityCard` in Settings → Agents; name/avatar/role fields; `identityGet/Set` transport |
| #56 | BL260 — Council Mode toggle | `CouncilModeCard`; toggle + member-list; `councilModeGet/Set/listMembers` transport |
| #75 | Stories redesign — Skills field, Council affordance | `NewPrdDialog` / `PrdDetailDialog`: Skills multi-select, Council indicator chip |
| #74 | Peer stale-date badge + docs viewer in Federation card | `PeerRow` shows last-seen age; `DocsViewerSheet` bottom sheet |
| #58 | Move BL257–BL260 cards from Agents → Automata tab | Reverse the stub placement from Sprint 1 per v6.11.1 move |

---

### Sprint 3 — Algorithm + Evals + Vault (~v0.72.x)

Goal: implement BL258 Algorithm Mode, BL259 Evals, BL267 Vault; add theme toggle.

| Issue | Description | Mobile work |
|-------|-------------|-------------|
| #54 | BL258 — Algorithm Mode | `AlgorithmModeCard`; mode picker + config; `algorithmModeGet/Set` transport |
| #55 | BL259 — Evals | `EvalsCard` with run/review/export; `evalsList/Run/Results` transport |
| #82 | BL267 — Vault | `VaultCard` in Settings → Security; secret CRUD + lock/unlock; `vaultList/Get/Set/Delete` transport |
| #77 | Theme toggle (light/dark/system) | Move from About (Sprint 1) to a persistent toggle; `ThemePreference` in SharedPreferences |

---

### Sprint 4 — Docs-as-MCP / BL274 (~v0.73.x)

Goal: expose the datawatch server's embedded docs through the mobile client (BL274).

| Issues | Description |
|--------|-------------|
| #83 | Reconnect resilience — exponential backoff + state machine visibility |
| #84–#90 | BL274 Docs-as-MCP in 6 sub-sprints: (84) browse docs tree, (85) full-text search, (86) offline cache, (87) markdown renderer in sheet, (88) cross-links, (89) version badge, (90) MCP tool passthrough |

---

### Sprint 5 — Sprint A close + wizard (~v0.74.x)

Goal: close the Sprint A feature arc (mic auto-attach, terminal hints, Council persona wizard).

| Issue | Description | Mobile work |
|-------|-------------|-------------|
| #91 | Mic auto-attach on input-required + terminal hints overlay | `SessionDetailScreen` auto-launches mic on `needs_input`; terminal overlay hint strip |
| #92 | BL297 — Council persona wizard | Multi-step `CouncilPersonaWizard` sheet; guided identity + member + skills setup |

---

### PWA-only — no mobile action

These issues track server-side or PWA-only changes. No mobile code required; monitor for
any server contract changes that require transport updates.

| Issues |
|--------|
| #60, #61, #62–#70, #76, #78, #79, #81 |

---

## v0.87+ parity arc — datawatch v7.0.0-alpha sprint track

*Last updated 2026-05-10. Tracks the server's alpha.24–alpha.32+ feature surface closing.*
*All issues in `dmz006/datawatch-app` repo. Parent datawatch repo is at `/home/dmz/workspace/datawatch`.*

### Sprint rules (every sprint in this arc)

**Version sync** (CI `check-version` rejects mismatch):
```
gradle.properties: DATAWATCH_APP_VERSION=X.Y.Z + DATAWATCH_APP_VERSION_CODE=N
shared/.../Version.kt: VERSION = "X.Y.Z" + VERSION_CODE = N
```

**Required per sprint**:
1. All new public logic has a corresponding Kotlin unit test (`commonTest` or `androidUnitTest`)
2. `rtk ./gradlew :composeApp:assembleDevDebug :wear:assembleDebug` → BUILD SUCCESSFUL
3. Locale strings added to all 5 bundles: `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-ja/`
4. `CHANGELOG.md` `[Unreleased]` block updated (or new version header if releasing)
5. `docs/testing-tracker.md` updated with new rows
6. `docs/sprint-plan.md` status updated (this file)
7. Commit + push per sprint — no local hoarding

**Test validation checklist** (per sprint):
- [ ] `rtk ./gradlew :shared:testDebugUnitTest` — shared logic tests pass
- [ ] `rtk ./gradlew :composeApp:testDevDebugUnitTest` — composeApp VM tests pass
- [ ] `rtk ./gradlew :composeApp:assembleDevDebug :wear:assembleDebug` — builds clean
- [ ] New DTOs: add `RestTransportTest` or `DtoRoundTripTest` round-trip cases
- [ ] New ViewModel state: add `ViewModelTest` state-machine coverage
- [ ] Locale: verify no missing keys (`rtk ./gradlew :composeApp:lintDevDebug | grep "MissingTranslation"`)
- [ ] Version parity: `grep -E 'DATAWATCH_APP_VERSION=|VERSION\s*=\s*"' gradle.properties shared/src/commonMain/kotlin/com/dmzs/datawatchclient/Version.kt`

---

### Sprint 17 — alpha.27 BackendFamily rename (v0.87.0/165) ✅ DONE

**Issue**: #112 | **Merged**: 2026-05-10 | **Server ref**: alpha.27

| Task | Status | Notes |
|------|--------|-------|
| `Dtos.kt`: add `backendFamily` field, keep `llmBackend` for deserialization compat | ✅ | `@SerialName("backend_family")` |
| `Mappers.kt`: `backend = backendFamily ?: llmBackend` | ✅ | Null-safe fallback |
| Version bump 0.87.0/165 | ✅ | |

**Tests needed / status**:
- [ ] `DtoRoundTripTest`: `backendFamily` takes precedence over `llmBackend`; `llmBackend` fallback when `backendFamily` absent → **not yet written**
- [ ] `MapperTest`: verify `toSession()` uses backendFamily field → **not yet written**

---

### Sprint 18 — alpha.24 Observer by-node + FederationMeta (v0.88.0/166) ✅ DONE

**Issue**: #111 | **Merged**: 2026-05-10 | **Server ref**: alpha.24

| Task | Status | Notes |
|------|--------|-------|
| `Dtos.kt`: `ObserverPeersByNodeDto`, `MetaPeersDto`, `MetaNodeBucketDto`, `MetaObserverEntryDto` | ✅ | |
| `Dtos.kt`: `ObserverPeerDto.computeNode` field | ✅ | Drops need for second compute-node fetch |
| `TransportClient` + `RestTransport`: `getObserverPeersByNode()`, `getFederationMetaPeers()` | ✅ | |
| `FederatedPeersCard`: "Group by ComputeNode" toggle, by-node bucket view, `peer.computeNode` binding badge | ✅ | Compile fix: `padding(horizontal,bottom)` → `padding(start,end,bottom)` |
| `SettingsScreen`: move `SecretsCard` + `ObserverQuicklinkCard` to Compute tab | ✅ | |
| Locale: `peer_group_by_node`, `peer_group_by_node_tip`, `peer_group_unbound` (5 bundles) | ✅ | |
| Version bump 0.88.0/166 | ✅ | |

**Tests needed / status**:
- [ ] `ObserverPeersByNodeDto` JSON round-trip (by_node map + unbound list) → **not yet written**
- [ ] `MetaPeersDto` JSON round-trip (nested buckets) → **not yet written**
- [ ] `FederatedPeersCardViewModel`: groupByNode toggle state → **not yet written**

---

### Sprint 19 — alpha.28 Agent-settings multi-model (v0.89.0/167) ✅ DONE

**Issue**: #113 | **Merged**: 2026-05-10 | **Server ref**: alpha.28

| Task | Status | Notes |
|------|--------|-------|
| `Dtos.kt`: `AgentSettingsDto` with `opencodeModels: List<String>` | ✅ | Keeps `opencodeModel` singular for compat |
| `TransportClient` + `RestTransport`: `patchProjectAgentSettings()` | ✅ | PATCH `/api/profiles/projects/{name}/agent-settings` |
| `KindProfilesCard`: agent-settings section in `ProfileEditDialog` for project kind | ✅ | 4 OutlinedTextFields; comma-separated input → JsonArray |
| Locale: `profile_ollama_models_label`, `profile_ollama_models_ph` (5 bundles) | ✅ | |
| Version bump 0.89.0/167 | ✅ | |

**Tests needed / status**:
- [ ] `AgentSettingsDto` JSON round-trip (opencodeModels list) → **not yet written**
- [ ] `RestTransport.patchProjectAgentSettings` MockWebServer test → **not yet written**

---

### Sprint 20 — alpha.29 Alert dock overlay (v0.90.0/168) ✅ DONE

**Issue**: #114 (alert dock phone side) | **Merged**: 2026-05-10 | **Server ref**: alpha.29

| Task | Status | Notes |
|------|--------|-------|
| `AlertDockOverlay.kt`: collapsed pill + category chips + expand/collapse | ✅ | 260dp max-width; anchored TopEnd |
| `AppRoot.kt`: dock visible when ≥2 active alerts, dismiss/mute state | ✅ | dismiss resets when count drops below threshold |
| Locale: `alert_dock_dismiss`, `alert_dock_mute`, `alert_pill_tip`, `alert_dock_one`, `alert_dock_many` (5 bundles) | ✅ | |
| Version bump 0.90.0/168 | ✅ | |

**Tests needed / status**:
- [ ] `AlertDockOverlay`: dismiss callback fires on ✕ tap → **not yet written**
- [ ] `AlertDockOverlay`: mute callback fires on 🔕 tap → **not yet written**
- [ ] `AppRoot` dock visibility logic: appears at 2 alerts, hides when dismissed, re-appears when count resets → **not yet written**

---

### Sprint 21 — Wear OS alerts tile + complication (v0.91.0/169) ✅ DONE

**Issue**: #114 (Wear OS side) | **Merged**: 2026-05-10

| Task | Status | Notes |
|------|--------|-------|
| `AlertsComplicationService.kt`: SHORT_TEXT `!N` / `alrt` title, reads `/datawatch/alerts` | ✅ | Follow `AutomataComplicationService` pattern |
| `AlertsTileService.kt`: 30s tile, total/input/err rows, health dot, tap→WearMainActivity | ✅ | Follow `MonitorTileService` pattern |
| `WearSyncService.fetchAndPublishDashboard()`: call `listAlerts()` → publish `/datawatch/alerts` DataItem | ✅ | `total`, `needsInput`, `errors`, `ts` keys |
| `WearSyncService`: `ALERTS_PATH` constant + `AlertsCountSnapshot` data class + `publishAlerts()` | ✅ | |
| `wear/AndroidManifest.xml`: register both new services | ✅ | |
| Wear locale: `tile_alerts_label`, `complication_alerts_label` (5 Wear bundles) | ✅ | |
| Version bump 0.91.0/169 | ✅ | |

**Tests needed / status**:
- [ ] `AlertsComplicationService.readAlerts()`: returns (0,0,0) when no DataItem → **not yet written**
- [ ] `AlertsTileService.readAlerts()`: parses DataItem keys correctly → **not yet written**
- [ ] `WearSyncService.publishAlerts()`: puts correct keys in DataMap → **not yet written**

---

### Sprint 22 — alpha.30 Alerts screen redesign + header badge (v0.92.0/170) ✅ DONE

**Issue**: #115 | **Merged**: 2026-05-10 | **Server ref**: alpha.30

| Task | Status | Notes |
|------|--------|-------|
| `AlertsViewModel`: `ChipFilter` + `SortMode` enums; `_chipFilter`/`_sortMode`/`_search` flows | ✅ | Nested combine pattern |
| `AlertsViewModel`: `setChipFilter()`, `setSortMode()`, `setSearch()`, `dismissAll()` | ✅ | `dismissAll` calls `markAlertRead(all=true)` |
| `AlertsViewModel.UiState`: `chipFilter`, `sortMode`, `searchQuery`, `flatChronoAlerts` | ✅ | |
| `AlertsScreen`: custom `AlertsTopBar` (chips + sort toggle + dismiss-all + search) | ✅ | Replaces `TopAppBar` |
| `AlertsScreen`: flat chronological `LazyColumn` when `sortMode == Chronological` | ✅ | No group headers |
| `AlertsScreen`/`AlertCard`: PROMPT type amber tint bg, ERROR red tint bg | ✅ | `isPromptType = type.contains("input")` |
| `BottomNavBar`: always-on badge (dimmed at 0, muted=🔕), `alertsMuted` param | ✅ | |
| `AppRoot.kt` (`HomeShell`): pass `alertsMuted = dockMuted` | ✅ | |
| Locale (11 keys): `alert_chip_*`, `alert_search_ph`, `alert_sort_*`, `alert_session_system`, `alert_dismiss_all_tip` (5 bundles) | ✅ | |
| Version bump 0.92.0/170 | ✅ | |

**Tests needed / status**:
- [ ] `AlertsViewModelTest`: chip filter reduces active list to matching severity/type → **not yet written**
- [ ] `AlertsViewModelTest`: sort=Chronological produces flat list newest-first → **not yet written**
- [ ] `AlertsViewModelTest`: search filters title+body case-insensitive → **not yet written**
- [ ] `AlertsViewModelTest`: dismissAll calls markAlertRead(all=true) → **not yet written**
- [ ] `BottomNavBarTest`: badge renders even when count=0 (dimmed) → **not yet written**

---

### Sprint 23 — Watch toggle opt-out (issue #116) ⏳ PLANNED

**Issue**: #116 | **Target version**: v0.93.0/171

Per-session and per-automaton "Watch" toggle. Default = OFF (opt-out). App-side filter only — no daemon change. Persists per-server-profile in local DB (new `watched_sessions` + `watched_automata` tables or flags).

| Task | Status | Notes |
|------|--------|-------|
| DB migration: `watched_sessions` set per profile (or boolean flag on `sessions` table) | ⏳ | New SQLDelight migration file |
| `SessionRepository`: `setWatched(id, watched)` + `isWatched(id)` | ⏳ | |
| Sessions list: per-row Watch toggle icon (🔔/🔕); default = unwatched | ⏳ | |
| Session detail: header-bar Watch toggle | ⏳ | |
| Automata list: per-row Watch toggle | ⏳ | |
| Automata detail: Watch toggle | ⏳ | |
| Alert dock / `AlertsViewModel`: filter out alerts from unwatched sessions | ⏳ | Core filtering logic |
| `BottomNavBar` badge: count only watched-session alerts | ⏳ | |
| Locale: `session_watch_toggle`, `session_watch_on`, `session_watch_off` (5 bundles) | ⏳ | |
| Version bump 0.93.0/171 | ⏳ | |

**Tests required**:
- [ ] `SessionRepositoryTest`: `setWatched` + `isWatched` round-trip
- [ ] `AlertsViewModelTest`: unwatched session alerts excluded from active/count
- [ ] `SessionsViewModelTest`: Watch toggle state persists across VM recreation

---

### Sprint 24 — alpha.31 Automata browse redesign + alert dock shrink (issue #117) ⏳ PLANNED

**Issue**: #117 | **Target version**: v0.94.0/172 | **Server ref**: alpha.31

| Task | Status | Notes |
|------|--------|-------|
| Automata list: pin button per row (📌/📍); persist `pinned_automata: Set<id>` per profile | ⏳ | SharedPreferences or new DB table |
| Automata list: sort order — pinned → state-rank (waiting/needs_review → blocked → running → planning → done) → last-activity desc | ⏳ | |
| Automata list: inline `Open` / `Cancel` / `Approve` buttons per card | ⏳ | `Approve` highlighted amber when `needs_review`/`revisions_asked`/`waiting_input` |
| Automata list: bigger cards (more padding, larger title, last-activity timestamp top-right) | ⏳ | |
| `AlertDockOverlay`: shrink `width(260.dp)` → `widthIn(max = 260.dp)` on narrow screens | ⏳ | Use `min(260dp, screenWidth - 16dp)` |
| Locale (12 keys): `automata_action_*` (open/pause/resume/cancel/approve/pin + _tip variants) (5 bundles) | ⏳ | From server `locales/*.json` |
| Version bump 0.94.0/172 | ⏳ | |

**Tests required**:
- [ ] `AutomataViewModelTest`: pinned automata sort before unpinned
- [ ] `AutomataViewModelTest`: approve action only enabled when status allows
- [ ] `AlertDockOverlayTest`: max-width respects screen width

---

### Sprint 25 — alpha.32 Per-session Stats redesign (issue #118) ⏳ PLANNED

**Issue**: #118 | **Target version**: v0.95.0/173 | **Server ref**: alpha.32

Per-session Stats sub-tab: single card → sectioned cards (Host, Container, ComputeNode, LLM).

| Task | Status | Notes |
|------|--------|-------|
| `SessionStatsPanel` or `SessionDetailScreen` Stats tab: sectioned cards layout | ⏳ | Host always; Container/ComputeNode/LLM conditional |
| Host card: CPU + RSS rows, threads, FDs, net (already have data; just re-layout) | ⏳ | Sparklines: show numbers without trend line (no chart lib) |
| Container card: render only when `env.container_id` present | ⏳ | `containerInfo` DTO field |
| ComputeNode card: render when `sess.compute_node_ref` present; GPU stats; click→Compute panel | ⏳ | Already have `computeNode` from alpha.24 |
| LLM card: render when `sess.llm_ref` present; backend family; click→LLM panel | ⏳ | `backendFamily` from alpha.27 |
| Locale: `stats_card_*`, `stats_field_*`, `stats_open_*`, `stats_llm_more_soon` (5 bundles) | ⏳ | Pull from server `locales/*.json` |
| Version bump 0.95.0/173 | ⏳ | |

**Tests required**:
- [ ] `SessionStatsPanelTest`: Container card hidden when `container_id` null
- [ ] `SessionStatsPanelTest`: ComputeNode card hidden when `compute_node_ref` null
- [ ] `SessionStatsPanelTest`: LLM card hidden when `llm_ref` null

---

### Test debt backlog (all sprints 17–22)

The following tests were deferred during rapid parity sprints. Required before v1.0.0 or full GH release.

| Sprint | Component | Test class | Coverage target |
|--------|-----------|------------|-----------------|
| 17 | `Dtos.kt` backendFamily fallback | `DtoRoundTripTest` | `backendFamily ?: llmBackend` path |
| 17 | `Mappers.kt` session mapping | `SessionMapperTest` | backendFamily → Session.backend |
| 18 | `ObserverPeersByNodeDto` | `DtoRoundTripTest` | by_node map + unbound deserialization |
| 18 | `MetaPeersDto` | `DtoRoundTripTest` | nested bucket deserialization |
| 18 | `FederatedPeersCard` VM | `FederatedPeersViewModelTest` | groupByNode toggle transitions |
| 19 | `AgentSettingsDto` | `DtoRoundTripTest` | opencodeModels list round-trip |
| 19 | `RestTransport.patchProjectAgentSettings` | `RestTransportTest` | PATCH body + 200 success |
| 20 | `AlertDockOverlay` | `AlertDockTest` (Compose UI) | dismiss/mute callbacks |
| 20 | `AppRoot` dock logic | `AppRootTest` | dock visibility threshold |
| 21 | `AlertsComplicationService` | `AlertsComplicationTest` | DataItem parse + fallback |
| 21 | `AlertsTileService` | `AlertsTileTest` | layout branches (hasData=false, errors>0) |
| 21 | `WearSyncService.publishAlerts` | `WearSyncServiceTest` | DataMap key/value correctness |
| 22 | `AlertsViewModel` filter | `AlertsViewModelTest` | chip filter per severity type |
| 22 | `AlertsViewModel` sort | `AlertsViewModelTest` | chronological flat list order |
| 22 | `AlertsViewModel` search | `AlertsViewModelTest` | title+body case-insensitive match |
| 22 | `AlertsViewModel` dismissAll | `AlertsViewModelTest` | markAlertRead(all=true) called |
| 22 | `BottomNavBar` badge | `BottomNavBarTest` | renders at count=0 (dimmed) |

**To run existing tests**:
```bash
rtk ./gradlew :shared:testDebugUnitTest
rtk ./gradlew :composeApp:testDevDebugUnitTest
```

**To add new tests**: place Kotlin test files in:
- `shared/src/commonTest/kotlin/` (KMP JVM tests)
- `composeApp/src/test/kotlin/` (Android unit tests — use `@Test` + MockWebServer)
- `composeApp/src/androidTest/kotlin/` (instrumented Compose UI tests)
