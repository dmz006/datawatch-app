# Sprint 28 — UnifiedPush Android Integration

**Date:** 2026-09-14
**Version at planning:** v1.9.3
**Target version:** v2.0.0 (minor — new transport surface)
**Scope:** `shared/transport`, `composeApp/push`, `composeApp/ui/settings`,
           `composeApp/src/androidMain/AndroidManifest.xml`, locale × 5
**Blocker:** datawatch#39 (server-side UnifiedPush provider + ntfy SSE delivery) —
             **do not start Phase 3+ until server signals ready**

---

## Context

Sprint 28 (alpha.35) adds UnifiedPush as an alternative push delivery path alongside
the existing FCM path. The push transport stubs already exist:
- `TransportClient.registerPush(PushRegistrationDto): Result<Unit>`
- `RestTransport` implementation at `POST /api/push/register`
- `PushRegistrationDto` DTO in `Dtos.kt`

`PushNotificationsCard.kt` currently shows **WebPush** (browser push endpoint) management
only. UnifiedPush is a separate Android push protocol that requires a distributor app
(e.g. ntfy) or micro-distributor to receive messages without Google.

The goal: when a UnifiedPush distributor is available on the device, prefer it over FCM
for datawatch "input needed" alerts. If not available, fall back to FCM (Tier 2) and
then poll-on-resume (Tier 3).

---

## Delivery Tiers

| Tier | Transport | Requirement | Battery |
|------|-----------|-------------|---------|
| 1 | UnifiedPush via distributor | Distributor app installed | Low |
| 2 | FCM (existing) | Google Play Services | Low |
| 3 | Poll-on-resume (existing) | None | None (no background) |

---

## Architecture

```
datawatch server
    │  POST to push endpoint URL
    ▼
Distributor app (ntfy / Gotify / etc.)
    │  UP_MESSAGE broadcast
    ▼
UnifiedPushReceiver (new BroadcastReceiver in :composeApp)
    │  parse message JSON → Event
    ▼
NotificationPoster.post(event)
    │  existing notification rendering
    ▼
User sees alert notification
```

Registration flow:
```
App start / BootReceiver
    │  UnifiedPush.register(context, "datawatch")
    ▼
Distributor sends UP_ENDPOINT broadcast
    ▼
UnifiedPushReceiver.onNewEndpoint(endpoint, instance)
    │  transport.registerPush(PushRegistrationDto(endpoint, clientId, …))
    ▼
datawatch server stores endpoint → pushes to it when alerts fire
```

---

## Phases

### Phase 1 — UnifiedPushReceiver + registration  Status: Planned

**Files to create/modify:**

1. `composeApp/src/androidMain/kotlin/com/dmzs/datawatchclient/push/UnifiedPushReceiver.kt` (new)
   - Extend `UnifiedPushReceiver` from `org.unifiedpush.android.connector` library
   - Handle `onNewEndpoint(endpoint, instance)` → call `registerPush()`
   - Handle `onMessage(message, instance)` → parse JSON → call `NotificationPoster.post()`
   - Handle `onUnregistered(instance)` → clear stored endpoint + fall back to FCM

2. `composeApp/src/androidMain/AndroidManifest.xml`
   - Declare `UnifiedPushReceiver` with `org.unifiedpush.android.connector.PUSH_EVENT` intent-filter
   - Declare `RECEIVE_BOOT_COMPLETED` permission (already present for FCM?)

3. `composeApp/src/androidMain/kotlin/com/dmzs/datawatchclient/push/PushTierManager.kt` (new)
   - Singleton: tracks current tier (1 / 2 / 3), stored in `SharedPreferences`
   - `init(context)`: checks if UnifiedPush distributor available; registers if so
   - `currentTier: StateFlow<Int>` — observed by `PushNotificationsCard` for tier display

4. `composeApp/build.gradle.kts`
   - Add `org.unifiedpush.android:connector:<version>` dependency

**Dependency note:** `org.unifiedpush.android:connector` is on Maven Central. Must be
≥72 hours old before adding per pre-release dependency audit rule. Verify license
(Apache 2.0 compatible with Polyform Noncommercial 1.0.0).

---

### Phase 2 — PushNotificationsCard — tier display  Status: Planned

Extend `composeApp/src/androidMain/kotlin/com/dmzs/datawatchclient/ui/settings/PushNotificationsCard.kt`:
- Add tier status row at top: "Push delivery: Tier 1 — UnifiedPush (ntfy)" / "Tier 2 — FCM" / "Tier 3 — Poll"
- Show distributor app name when Tier 1 active
- Show "Register distributor" guidance if no distributor found
- Keep existing WebPush endpoint management section (for browser clients)

Locale keys (5 bundles):
- `push_tier_1_label` — "UnifiedPush (active)"
- `push_tier_2_label` — "FCM (active)"
- `push_tier_3_label` — "Poll on resume (fallback)"
- `push_tier_header` — "Push delivery"
- `push_no_distributor` — "No UnifiedPush distributor found. Install ntfy or similar to enable Tier 1."
- `push_distributor_label` — "Distributor"
- `push_endpoint_registered` — "Endpoint registered with server"
- `push_endpoint_pending` — "Awaiting endpoint from distributor"

---

### Phase 3 — Server integration (blocked on datawatch#39)  Status: Blocked

**Do not start until datawatch#39 is resolved.**

When server ships UnifiedPush provider:
- Verify `POST /api/push/register` accepts `PushRegistrationDto` correctly
- Verify server delivers messages to the registered endpoint when alerts fire
- Verify `onMessage` payload matches `NotificationPoster.Event` shape
- Add `GET /api/push/registrations` (if server adds it) to show active endpoints

---

### Phase 4 — Tests  Status: Planned

**Unit tests** (can be written in Phase 1 even before server is live):

`composeApp/src/androidUnitTest/.../push/UnifiedPushReceiverTest.kt`:
- [ ] `onNewEndpoint calls registerPush with endpoint and stable clientId`
- [ ] `onMessage with valid JSON calls NotificationPoster.post`
- [ ] `onMessage with invalid JSON logs warning, does not crash`
- [ ] `onUnregistered sets tier to 2 (FCM fallback)`

`composeApp/src/androidUnitTest/.../push/PushTierManagerTest.kt`:
- [ ] `tier is 1 when distributor available`
- [ ] `tier is 2 when no distributor`
- [ ] `tier persists across restarts via SharedPreferences`

`shared/src/androidUnitTest/.../transport/rest/RestTransportTest.kt` (extend):
- [ ] `registerPush posts correct JSON body to /api/push/register`
- [ ] `registerPush URL-encodes endpoint correctly`

---

### Phase 5 — Documentation  Status: Planned

- [ ] `docs/transports.md` — add UnifiedPush section: when to use, prerequisites (distributor app),
      fallback behavior (FCM → poll), limitations (distributor must be installed), security notes
- [ ] `docs/testing-tracker.md` — add rows for Tier 1 / Tier 2 / Tier 3 surfaces
- [ ] `CHANGELOG.md` — UnifiedPush entry under new minor version
- [ ] `README.md` — mention UnifiedPush in push notifications section
- [ ] `docs/plans/README.md` — mark Sprint 28 ✅ shipped

---

## Per-Sprint AGENT.md Checklist

- [ ] AGENT.md rules re-read; applicable rules noted in sprint commit message
- [ ] `docs/testing-tracker.md` updated for UnifiedPush surface row
- [ ] Locale gate: all 5 bundles (EN/DE/ES/FR/JA) updated for `push_*` keys
- [ ] Version bump: `gradle.properties` + `Version.kt` in sync (minor bump — new transport)
- [ ] New dependency in `CHANGELOG.md` with version pinned
- [ ] `./gradlew build` passes clean (zero new lint/detekt/ktlint warnings)
- [ ] `./gradlew test` passes (unit tests green)
- [ ] `CHANGELOG.md` updated under `[Unreleased]`
- [ ] `README.md` current-release line updated
- [ ] `docs/plans/README.md` backlog refactored
- [ ] New transport documented in `docs/transports.md` (AGENT.md §New transport rule)

## Security Notes

- Push endpoint URL delivered by distributor — treat as opaque; never log it
- `clientId` must be stable, non-PII, derived from installation (e.g. random UUID stored
  in EncryptedSharedPreferences on first launch)
- `PushRegistrationDto.token` is the server auth token — must not appear in logs
  (existing `ServerProfile.token` log-redaction rules apply)
- UnifiedPush message payload arrives from distributor — validate JSON shape before parsing;
  ignore unknown fields; never eval or execute payload content

## Reuse Audit

- Notification rendering: reuse existing `NotificationPoster.post(event)` — no new notification code
- Push token store: reuse `EncryptedSharedPreferences` pattern from `KeystoreManager`
- Tier StateFlow: reuse `MutableStateFlow` singleton pattern from `AlertDockChannel`
- Settings card: extend `PushNotificationsCard` — do not create a separate card
