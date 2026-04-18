# Android Auto Surfaces

Dual-track (ADR-0031): public Messaging-template app (Play Store compliant) + internal
full-passenger build (never promoted publicly). Both installable simultaneously via
distinct `applicationId`s.

## Module structure

```
auto/
├── build.gradle.kts
├── src/publicMain/       # Messaging template, Play-compliant
│   ├── AndroidManifest.xml
│   └── kotlin/com/dmzs/datawatchclient/auto/messaging/
│       └── DatawatchMessagingService.kt
├── src/devMain/          # Full passenger UI (com.dmzs.datawatchclient.dev only)
│   ├── AndroidManifest.xml
│   └── kotlin/com/dmzs/datawatchclient/auto/dev/
│       ├── MainScreen.kt
│       ├── SessionListScreen.kt
│       ├── SessionDetailScreen.kt
│       ├── TerminalScreen.kt
│       └── VoiceScreen.kt
└── src/commonMain/       # shared helpers
```

The `publicMain` source set is compiled into the public applicationId only; `devMain` only
into `.dev`. Build variants enforce this via `sourceSets` in Gradle.

## Public build — Messaging template

Uses `androidx.car.app:app:1.7.x` in Messaging mode. This is the **only** Play-compliant
Auto experience for a non-messaging app that needs inbound/outbound voice — the Car App
Library's Driver Distraction review approves it for apps surfacing chat-style content.

### Required manifest entries (public)

```xml
<application>
    <service
        android:name=".auto.messaging.DatawatchMessagingService"
        android:exported="true"
        android:foregroundServiceType="connectedDevice">
        <intent-filter>
            <action android:name="androidx.car.app.CarAppService"/>
            <category android:name="androidx.car.app.category.MESSAGING"/>
        </intent-filter>
    </service>

    <meta-data
        android:name="com.google.android.gms.car.application"
        android:resource="@xml/automotive_app_desc"/>
    <meta-data
        android:name="androidx.car.app.minCarApiLevel"
        android:value="1"/>
</application>
```

### Surface capabilities (public)

- **Inbound:** receives prompt/state-change events from the phone app. Creates a
  `ConversationItem` per session; each prompt becomes a `CarMessage` read via TTS.
- **Outbound:** voice dictation via Car App Library's voice reply intent → produces a
  transcript; phone app calls `session_reply`.
- **Explicit exclusions (Google policy):**
  - No terminal, no logs, no stats table, no config UI.
  - No free-form text entry; voice-only.
  - No images or attachments (messaging template forbids for driving-safety).

### Quality-tier review

Messaging apps require a Driver Distraction Evaluation by Google at Play Console submission.
This is a free but time-boxed review (typically 1–3 weeks). The app must pass before it can
be distributed to Auto head units. Details in `play-store-registration.md`.

## Internal build — full passenger UI

Uses the full Car App Library (`androidx.car.app:app:1.7.x` + `app-automotive`) with custom
screens. Since this goes through Play Console **Internal Testing track only** and never to
production, Google's driver-distraction review is not required for distribution (you verify
the internal list manually during upload). Note: even on internal testing, you are
responsible for safe use — this is why it's labeled "passenger" and ships only to the user
himself.

### Screens

- Session list (same as phone, adapted to car templates).
- Session detail (chat + voice reply).
- Terminal (minified xterm renderer; scrolling disabled while moving → overlay appears).
- Voice quick actions.

### Car App Library templates used (internal only)

| Template | Use |
|---|---|
| `ListTemplate` | Session list, server picker |
| `PaneTemplate` | Session summary, stats |
| `MessageTemplate` | Prompt content |
| `NavigationTemplate` (read-only) | Breadcrumb display |
| `LongMessageTemplate` | Full chat log (parked-state recommended) |

### Interaction model

- Voice-first: every screen has a primary voice action; tap is secondary.
- All interactions require < 2 taps before completing.
- Any screen with > 6 list items uses scrolling with explicit "More" affordance.

## Per-variant code guard

`BuildConfig.AUTO_MODE` = `"public"` or `"dev"`. Public variant throws at compile time if
any `.devMain` class is referenced, preventing accidental policy-violating code in the
public AAB.

## Testing

- **DHU (Desktop Head Unit):** required for Auto development. `play-store-registration.md`
  includes DHU setup instructions.
- **Real head unit** tested before marking Validated=Yes for MVP.
- **Public build review:** Google's Driver Distraction Evaluation before the app appears
  on Auto. Start early — review can take 2+ weeks.

## Distribution

- Public AAB: uploaded to Play Console Production with Automotive-capable declared in the
  manifest meta-data.
- Internal AAB: uploaded to Play Console Internal Testing track only. Distribution list
  includes only dmz's own devices.

## Open item

- **App Actions for Auto voice** — Google's in-car voice routing depends on App Actions
  BuiltInIntents. `CREATE_MESSAGE` and custom `datawatch.REPLY` need approval. Apply
  immediately after Play Console account creation; approval lead time runs in parallel
  with development.
