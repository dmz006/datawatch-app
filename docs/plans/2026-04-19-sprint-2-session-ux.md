# Sprint 2 — Session UX: detail, streaming, reply, push, multi-server, 3-finger

- **Date started:** 2026-04-19
- **Version at plan time:** v0.2.1 (entering 0.3.0-alpha.x during the sprint)
- **Target ship version:** v0.3.0
- **Target completion:** 2026-05-29 (end of Sprint 2)
- **Feature IDs:** F2, BL9
- **Scope-change tracking:** ADR-0042 (promoted items landing here: BL9)

## Scope

The `:shared` module + `:composeApp` go from "Sessions list renders" to
"full session interaction loop." By end of sprint, a user can:

1. Tap a session → see chat + terminal + logs + memory sheets
2. Reply via the composer (text only; voice in Sprint 3)
3. Kill a session from the detail screen (confirm dialog)
4. Mute / unmute per session
5. Filter sessions list by state
6. See live output arriving via WebSocket `/ws`
7. Receive FCM wake pushes (with ntfy fallback)
8. Multi-server: picker on Sessions top bar, edit existing servers, 3-finger gesture
9. Alerts tab renders pending prompts + mute actions

## Phases

### Phase 1 — WebSocket transport + session detail foundation + reply

**Status:** In progress.

- [ ] `shared/.../transport/ws/WebSocketTransport.kt` — interface + Ktor impl
- [ ] `SessionWireEvent` sealed class: Output, StateChange, PromptDetected,
      RateLimited, Completed, Error
- [ ] `SessionEventRepository` — caches last N events per session, streams
      live updates
- [ ] `SessionDetailViewModel`
- [ ] `SessionDetailScreen` — chat spine + reply composer + state pill +
      kebab actions (kill, mute, state override)
- [ ] Navigation: Sessions list row → SessionDetailScreen
- [ ] Tests: MockWebServer WS round-trip, event deserialization

### Phase 2 — Multi-server picker + edit server + 3-finger gesture (BL9)

**Status:** Planned.

- [ ] Sessions TopAppBar: server pill shows active server name + dropdown
- [ ] Server tree drawer with active-indicator + status dots
- [ ] EditServerScreen (reuses AddServer form with prefill + save/delete)
- [ ] 3-finger-upward-swipe gesture on any screen → server picker modal

### Phase 3 — FCM wake + notifications + ntfy fallback

**Status:** Planned.

- [ ] FirebaseMessagingService wired
- [ ] Notification channel setup (input needed, rate limited, completed, error)
- [ ] Tap notification → deep link to session detail
- [ ] Inline reply action on "input needed" notifications
- [ ] ntfy subscription fallback service (for servers without FCM)

### Phase 4 — xterm.js terminal

**Status:** Planned.

- [ ] Vendor xterm.js bundle under `composeApp/src/androidMain/assets/xterm/`
- [ ] WebView with xterm host page + JS bridge
- [ ] Stream WS `output` frames into xterm via postMessage
- [ ] Terminal sheet in SessionDetailScreen

### Phase 5 — Filters + alerts skeleton + mute

**Status:** Planned.

- [ ] Sessions tab: filter chips (All / Running / Waiting / Completed / Error)
- [ ] Swipe-to-mute on session rows
- [ ] Alerts tab renders sessions with `needsInput = true`
- [ ] Badge counter on bottom-nav Alerts tab

### Phase 6 — Tests + docs + v0.3.0 release

**Status:** Planned.

- [ ] MockWebServer tests: WS output streaming, reconnect, backoff
- [ ] Repository tests: SessionEventRepository, upsert/observe round-trips
- [ ] Compose UI tests for SessionDetailScreen (via Robolectric where feasible)
- [ ] Architecture + data-flow diagrams refreshed for WS path
- [ ] `docs/parity-status.md` matrix rows flipped ⏳ → ✅
- [ ] Version bump 0.3.0-alpha.N → 0.3.0, tag, internal Play upload

## Parity matrix items this sprint delivers

From `docs/parity-status.md`:

| Row | Sprint |
|-----|--------|
| Open session detail | 2 ✓ |
| Live chat / message stream | 2 ✓ |
| Reply to pending prompt | 2 ✓ |
| Kill session | 2 ✓ |
| Override session state | 2 ✓ |
| Filter sessions by state | 2 ✓ |
| Mute / un-mute session | 2 ✓ |
| Bulk delete completed sessions | 2 ✓ |
| WebSocket /ws stream | 2 ✓ |
| xterm.js terminal view | 2 ✓ |
| FCM push wake | 2 ✓ (depends on parent dmz006/datawatch#1; fallback ntfy) |
| Multi-server picker | 2 ✓ |
| Edit server profile | 2 ✓ |
| 3-finger-swipe gesture (BL9) | 2 ✓ (ADR-0042) |
| Alerts tab skeleton | 2 ✓ |
| Daemon version in About | 2 ✓ |
| Connection status indicator | 2 ✓ |

## Risks

| Risk | Mitigation |
|------|-----------|
| WS reconnect storms on flaky tailnet | Exponential backoff capped at 60 s, jittered |
| xterm.js bundle size (~900 KB) | Ship gzipped; WebView caches between sessions |
| FCM depends on unshipped parent endpoint (#1) | ntfy fallback path ships first; FCM flips on when parent lands |
| 3-finger gesture conflicts with system gestures | Trigger only on `Modifier.pointerInput` within bottom-nav bounds, not fullscreen |
| Sprint 2 scope is biggest of any sprint | Phase 6 release is soft — if we're at 80 % Phase 5 we still tag v0.3.0 with remaining deferred to v0.3.1 mid-sprint-3 |

## Exit criteria

- All Sprint 2 parity rows ✅ in `docs/parity-status.md`
- All new code has unit tests with close-to-100 % coverage on changed logic
- Architecture + data-flow diagrams updated
- Version bumped to v0.3.0 with CHANGELOG entry and tag
- APK installed on user's phone; live session detail + reply verified against
  real datawatch server
