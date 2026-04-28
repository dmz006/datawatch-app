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
| v0.35.10 | Session detail force-refresh on open | VM init triggers `refreshFromServer()` immediately after starting the WS stream so detail screen never displays the 5-second-stale list cache. |
| v0.36.0 | Federated monitoring suite | Four new cards on Settings → Monitor: Federated peers (with All/Standalone/Cluster/Agents filter pills), Cluster nodes (Shape C with pressure flags + CPU/Mem bars), eBPF status (3-flag block + message), Plugins (subprocess + native rows with kind badges). Closes [#2](https://github.com/dmz006/datawatch-app/issues/2)–[#6](https://github.com/dmz006/datawatch-app/issues/6); [#7](https://github.com/dmz006/datawatch-app/issues/7) deferred to v0.36.2. New `/api/observer/{stats,peers}` and `/api/plugins` endpoints on `TransportClient`. |
| v0.36.1 | Picker mkdir + response-noise filter | File picker gains "+ New folder" affordance via new `TransportClient.mkdir()` (closes [#14](https://github.com/dmz006/datawatch-app/issues/14), PWA v5.26.46). New shared `ResponseNoiseFilter` strips TUI noise (box-drawing borders, labeled borders, status timers, spinner counters, pure digit lines, broadened noise patterns) before `LastResponseSheet` renders the captured response — closes [#15](https://github.com/dmz006/datawatch-app/issues/15) (PWA v5.26.31). Filter unit-tested in `:shared:testDebugUnitTest`. |

## v1.0.0 parity roadmap

Open rows in [parity-plan.md](parity-plan.md) blocking v1.0.0:

- **🚧** `POST /api/channels` add/remove (blocked upstream:
  [dmz006/datawatch#18](https://github.com/dmz006/datawatch/issues/18))
- **⏳** Per-process eBPF network read-only viewer (deferred per
  ADR-0019; server exposes data, mobile UI not built)

Everything else in `parity-plan.md` is ✅ as of v0.33.0.

## Post-ADR-0042-scope backlog (v0.11+)

- Tablet layout with two-pane session list + detail (BL3)
- iOS app content — beyond skeleton (BL5)
- Foldable support — Pixel Fold / Galaxy Z Fold (BL7)
- Full schedule editor CRUD (BL11)
- KG Add / Timeline / Research deeper views (BL12)
- Adjustable terminal dimensions (BL13)
- Raw YAML config editor, gated behind biometric + confirm (BL14)
- Localization — DE, ES, FR, JA (BL15)
- Split `decisions/README.md` into per-ADR MADR files (BL1)
