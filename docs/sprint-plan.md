# Sprint Plan — MVP → Production

Timeline per ADR-0032: MVP on Play Console Internal track by **2026-06-12** (8 weeks from
2026-04-17). Public Production by **2026-07-10** (+4 weeks of closed/open testing). Team:
solo + Claude. Sprint length: 2 weeks.

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
