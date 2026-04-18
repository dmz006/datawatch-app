# Architecture

Datawatch Client is a thin, multi-surface Kotlin client that exposes a user's datawatch
server(s) across phone, watch, and car. All domain logic lives in a shared KMP module;
surface-specific modules (Android phone, Wear OS, Android Auto, iOS skeleton) consume it.

## C4 — System Context

```mermaid
C4Context
    title Datawatch Client — System Context
    Person(user, "User", "dmz — single-user v1")

    System(mobile, "Datawatch Client", "KMP mobile app — phone, watch, car, iOS-skel")
    System_Ext(dwServer, "datawatch server(s)", "Go daemon — one or many, may be proxied")
    System_Ext(fcm, "Google FCM", "Push wake transport")
    System_Ext(gdrive, "Google Drive", "Auto Backup of encrypted DB")
    System_Ext(tailscale, "Tailscale", "Overlay network — assumed pre-installed on phone")
    System_Ext(signalApp, "Signal / SMS / etc. apps on device", "Intent-handoff fallback")

    Rel(user, mobile, "Uses")
    Rel(mobile, dwServer, "REST + WebSocket + MCP-SSE", "HTTPS/WSS over tailnet / LAN")
    Rel(mobile, fcm, "Receives push wake", "HTTPS")
    Rel(mobile, gdrive, "Backs up encrypted DB", "HTTPS (platform)")
    Rel(mobile, tailscale, "Routes via")
    Rel(mobile, signalApp, "Hands off commands as fallback", "Android Intent")
    Rel(dwServer, fcm, "Sends wake notifications", "HTTPS")
```

## C4 — Container View

```mermaid
C4Container
    title Datawatch Client — Containers
    Person(user, "User")

    Container_Boundary(app, "Datawatch Client") {
        Container(phoneApp, "Phone app", "Kotlin + Compose", "Main UI — sessions, chat, terminal, settings")
        Container(wearApp, "Wear app", "Wear Compose", "Rich watch app + complication + notifications")
        Container(autoApp, "Auto messaging surface", "Car App Library", "Public build: TTS + voice reply; Internal build: full UI")
        Container(shared, "Shared core", "KMP", "Domain, transport, storage, voice, MCP")
        Container(db, "Encrypted DB", "SQLCipher + SQLDelight", "Profiles, cache, logs")
        Container(ks, "Android Keystore", "Platform", "Bearer tokens, SQLCipher master key")
    }

    System_Ext(dw, "datawatch server")
    System_Ext(fcm, "FCM")

    Rel(user, phoneApp, "Interacts with")
    Rel(user, wearApp, "Voice + tap")
    Rel(user, autoApp, "Voice + TTS")

    Rel(phoneApp, shared, "Calls")
    Rel(wearApp, shared, "Calls (via Wearable Data Layer)")
    Rel(autoApp, shared, "Calls")
    Rel(shared, db, "Reads/writes (encrypted)")
    Rel(shared, ks, "Retrieves keys from")
    Rel(shared, dw, "REST / WSS / MCP-SSE")
    Rel(dw, fcm, "Pushes")
    Rel(fcm, phoneApp, "Wakes")
```

## C4 — Component View (shared KMP module)

```mermaid
C4Component
    title Shared module components

    Container_Boundary(shared, "shared (KMP)") {
        Component(domain, "Domain", "Kotlin", "ServerProfile, Session, Principal, Prompt, Memory, KGNode")
        Component(useCase, "Use cases", "Kotlin", "ListSessions, StartSession, ReplyToPrompt, FetchStats, InvokeMcpTool, RecordVoice, RestoreBackup")

        Component(transport, "Transport", "Ktor", "TransportClient interface + HTTP/WS/SSE + Tailscale/LAN/DNS-TXT impls + Intent-relay impl")
        Component(mcp, "MCP client", "Kotlin", "MCP-over-HTTP-SSE client, tool invocation with streaming")
        Component(push, "Push backend", "Kotlin", "FCM handler + ntfy subscriber + dispatch to domain")
        Component(voice, "Voice capture", "Kotlin", "Audio recorder, upload, progress events")
        Component(storage, "Storage", "SQLDelight + SQLCipher", "Queries, migrations, encrypted data")
        Component(keystore, "Keystore", "Android", "Token + master key")
        Component(relay, "Backend relay", "Kotlin", "Android Intent handoff to Signal/SMS/Slack apps")
        Component(config, "Config", "Kotlin", "Typed settings, export/import, reachability profiles")
    }

    Rel(useCase, domain, "uses")
    Rel(useCase, transport, "uses")
    Rel(useCase, mcp, "uses")
    Rel(useCase, voice, "uses")
    Rel(useCase, storage, "uses")
    Rel(useCase, relay, "falls back to")
    Rel(push, useCase, "dispatches events")
    Rel(transport, keystore, "retrieves token")
    Rel(storage, keystore, "retrieves master key")
```

## Module / source tree

```
datawatch-app/
├── AGENT.md
├── README.md
├── CHANGELOG.md
├── LICENSE (Polyform Noncommercial 1.0.0)
├── SECURITY.md
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/libs.versions.toml
├── composeApp/                        # Android phone app
│   ├── build.gradle.kts               # applicationId com.dmzs.datawatchclient
│   ├── src/androidMain/
│   │   ├── kotlin/com/dmzs/datawatchclient/
│   │   │   ├── DatawatchApp.kt
│   │   │   ├── MainActivity.kt
│   │   │   ├── ui/                    # Compose screens
│   │   │   ├── voice/                 # Android voice capture + tile + ASSIST handler
│   │   │   ├── push/                  # FirebaseMessagingService
│   │   │   └── relay/                 # Intent-handoff integrations
│   │   ├── assets/xterm/              # vendored xterm.js bundle
│   │   └── AndroidManifest.xml
│   └── src/androidDevMain/            # com.dmzs.datawatchclient.dev flavor (internal)
├── wear/                              # Wear OS module
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/dmzs/datawatchclient/wear/
│       ├── NotificationListener.kt
│       ├── Complication.kt
│       └── WearApp.kt
├── auto/                              # Android Auto module
│   ├── build.gradle.kts
│   ├── src/publicMain/                # Messaging template — Play-compliant
│   └── src/devMain/                   # Full passenger UI (internal only)
├── shared/                            # KMP core
│   ├── build.gradle.kts
│   └── src/commonMain/kotlin/com/dmzs/datawatchclient/
│       ├── domain/
│       ├── usecase/
│       ├── transport/                 # TransportClient, RestTransport, WebSocketTransport, McpSseTransport, DnsTxtTransport
│       ├── mcp/
│       ├── storage/                   # SQLDelight db definitions
│       ├── voice/
│       ├── push/
│       ├── relay/
│       ├── config/
│       └── Version.kt
│   ├── src/commonTest/                # Shared unit tests
│   ├── src/androidMain/               # Android-specific impls
│   └── src/iosMain/                   # iOS-specific stubs (skeleton-only)
├── iosApp/                            # iPhone skeleton — pre-wired to :shared
│   ├── iosApp.xcodeproj/
│   └── iosApp/
├── docs/
│   ├── README.md
│   ├── decisions/                     # ADR files (from design/decisions.md)
│   ├── plans/
│   ├── testing.md
│   ├── testing-tracker.md
│   ├── implementation.md
│   ├── config-reference.md
│   ├── operations.md
│   ├── setup.md
│   ├── security-model.md
│   ├── architecture.md                # promoted from design/
│   ├── data-flow.md
│   └── ...
├── .github/
│   ├── workflows/
│   │   ├── ci.yml                     # build + test + detekt + ktlint + lint
│   │   ├── release.yml                # tag → bundleRelease + gh release
│   │   └── security.yml               # OWASP dep check, weekly
│   ├── ISSUE_TEMPLATE/
│   └── PULL_REQUEST_TEMPLATE.md
├── fastlane/                          # Play Console upload automation
└── .gitignore
```

## Key interfaces (signatures, not implementations)

```kotlin
// shared/.../transport/TransportClient.kt
interface TransportClient {
    val profile: ServerProfile
    suspend fun <T> get(path: String, deser: (ByteArray) -> T): Result<T>
    suspend fun <T> post(path: String, body: ByteArray, deser: (ByteArray) -> T): Result<T>
    fun webSocket(path: String): Flow<WsEvent>
    fun mcpSse(): McpSseSession
    suspend fun ping(): Boolean                           // reachability check
}

// shared/.../mcp/McpSseSession.kt
interface McpSseSession {
    suspend fun listTools(): List<McpTool>
    fun invoke(tool: String, args: JsonObject): Flow<McpStream>   // streaming tool output
    fun close()
}

// shared/.../push/PushBackend.kt
interface PushBackend {
    val kind: Kind                                        // FCM or NTFY
    suspend fun registerDevice(profile: ServerProfile): DeviceToken
    fun incoming(): Flow<PushEvent>                       // wake + payload
}

// shared/.../voice/VoiceCapture.kt
interface VoiceCapture {
    fun start(): Flow<VoiceEvent>                         // streaming while recording
    suspend fun stop(): RecordedAudio
    // upload handled by use case with fail-fast ADR-0013 + retry buffer ADR-0027
}

// shared/.../relay/BackendRelay.kt
// Implements ADR-0004 Intent-handoff fallback
interface BackendRelay {
    fun launchCompose(profile: ServerProfile, message: String, channel: RelayChannel)
}
```

## Build variants

Each Android module has three flavors:

| Flavor | applicationId | Purpose | Distribution |
|---|---|---|---|
| `publicRelease` | `com.dmzs.datawatchclient` | Play Store public | Play Store tracks |
| `devRelease` | `com.dmzs.datawatchclient.dev` | Full-UI internal build | Play Console Internal Testing only |
| `debug` | `com.dmzs.datawatchclient.debug` | Local dev builds | sideload only |

All three are installable simultaneously on the same device (distinct applicationIds).

## Dependency ceiling (proposed — subject to approval before adding)

```
org.jetbrains.kotlin:kotlin-stdlib               (stdlib)
org.jetbrains.kotlinx:kotlinx-coroutines         (async)
org.jetbrains.kotlinx:kotlinx-serialization-json (JSON)
org.jetbrains.kotlinx:kotlinx-datetime           (time)
io.ktor:ktor-client-core/okhttp                  (HTTP/WS)
app.cash.sqldelight:runtime                      (DB)
net.zetetic:sqlcipher-android                    (encryption)
androidx.security:security-crypto                (EncryptedSharedPreferences)
androidx.compose.*                               (UI)
androidx.car.app:app-automotive                  (Auto)
androidx.wear.compose:compose-material           (Wear UI)
com.google.android.gms:play-services-wearable    (Data Layer API)
com.google.firebase:firebase-messaging           (FCM)
io.modelcontextprotocol:kotlin-sdk               (MCP, if published; else custom SSE client)
```

No analytics, no crashlytics, no ads, no third-party SDKs beyond the above. Any addition
requires an ADR.
