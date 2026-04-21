# Android Auto — current state + in-car test guide

*Last updated 2026-04-22 for v0.32.0+.*

## What ships in the APK

As of v0.32.0 the `:auto` module is bundled into the `composeApp`
APK via `missingDimensionStrategy("surface", "publicMessaging")` in
the `publicTrack` flavor. The CarAppService + manifest metadata
merge automatically; no separate Auto APK.

### Three-screen navigation

1. **`AutoSummaryScreen`** — driver home screen. Shows Running /
   Waiting input / Total counts for the first-enabled server
   profile. Polls `GET /api/sessions` every 15 s.
2. **`WaitingSessionsScreen`** — tap "Waiting input" to see the
   list of sessions blocking on the user. Each row shows
   `name` + a one-line body preview. Polls every 15 s.
3. **`SessionReplyScreen`** — tap a session to reply with one of
   four quick actions: **Yes / No / Continue / Stop**. Each
   button fires `POST /api/sessions/reply`.

Templates used: `ListTemplate` + `MessageTemplate` only. No free-
form UI per ADR-0031 Play-compliance.

### Dependency injection

Auto can't depend on `:composeApp` (library → app is a layering
violation), so the `:auto` module has its own
`AutoServiceLocator`. It rebuilds the same dependency graph the
phone uses — `KeystoreManager` → `DatabaseFactory` →
`ServerProfileRepository` → `SessionRepository` → `RestTransport` —
all pulled from `:shared`. Same SQLCipher DB, same bearer tokens.

Init happens in `DatawatchMessagingService.onCreate()` before any
`Screen` is constructed.

### Host validation (ADR-0031)

| Build | Validator |
|---|---|
| Debug | `HostValidator.ALLOW_ALL_HOSTS_VALIDATOR` — DHU-friendly |
| Release | strict allowlist from `R.array.hosts_allowlist` — Google Automotive + Android Auto + DHU + automotive emulator signing certs |

The allowlist XML is at `auto/src/publicMain/res/values/hosts_allowlist.xml`.

## Testing in your car

### Requirements

- Phone running Android Auto (app installed + up to date).
- Car with Android Auto (or Android Auto head unit / DHU).
- APK installed on the phone. Debug builds use the default Android
  debug key, which is fine for `adb install -r`.
- Auto **developer mode** enabled for side-loaded apps (one-time):
  - Open the Android Auto app on the phone.
  - Tap the version number at the bottom of the Settings screen
    ~10 times until the "Developer Mode" menu unlocks.
  - In the now-visible developer settings, enable
    **"Unknown sources"**. Without this, Auto won't expose side-
    loaded debug apps to the car.

### Connect + test

1. `adb install -r composeApp/build/outputs/apk/publicTrack/debug/composeApp-publicTrack-debug.apk`
2. Force-close any running datawatch instance:
   `adb shell am force-stop com.dmzs.datawatchclient.debug`
3. Connect the phone to the car (USB-C or wireless Auto).
4. On the car display, the app drawer should show the datawatch
   icon under the Messaging category.
5. Tap to open → Summary screen with real counts.
6. Tap "Waiting input" row → Waiting list.
7. Tap a session → Reply screen with Yes/No/Continue/Stop.

### DHU (no car available)

Google's **Desktop Head Unit** emulates Auto on a desktop PC
connected to the phone over USB + ADB port forward.

1. Install DHU: `sdkmanager "extras;google;auto"`
2. Run: `$ANDROID_HOME/extras/google/auto/desktop-head-unit`
3. With Auto developer mode on + phone connected, DHU mirrors the
   Auto projection.

## Known gaps for in-car use

| Item | State |
|---|---|
| Session counts live | ✅ via `AutoServiceLocator` |
| Reply via quick actions | ✅ Yes/No/Continue/Stop |
| **TTS announcement of new prompts** | ❌ `NotificationPoster` posts phone-side notifications but doesn't route through the Car Message API for in-car TTS. Drivers see visual notifications but don't hear them spoken. |
| **RemoteInput voice reply from Auto notification** | ❌ — the `Reply` action uses `RemoteInput` for wrist/phone; Auto's own TTS-reply path is separate |
| **Hosts allowlist verified against Play-submit** | ⚠️ — values from Google's public docs but not tested against an actual signing-cert chain |
| **DHU simulator runtime smoke** | ❌ — code compiles cleanly; never exercised in DHU |

## Architectural notes

- **Process model.** The `CarAppService` may run in the same
  process as `MainActivity` or a separate one depending on the
  Auto host's decision. `AutoServiceLocator` is lazy-init'd in
  `onCreate`, so either way the first `Screen` has a working DB +
  transport.
- **No shared state with the phone UI.** The screens poll their
  own 15 s loop rather than subscribing to `SessionRepository`'s
  flow, because the phone-side `SessionsViewModel` may or may not
  be live at the time Auto binds.
- **Play submission prerequisites** (not done):
  - APK signed with release key
  - `HostValidator` strict allowlist verified with actual cert chains
  - Messaging template flow passes Auto's conformance lint
  - App listing + Data Safety declarations updated
