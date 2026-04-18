# Security Model

This doc states *how* the app protects user data, not *what threats we care about*.
Threats and residual risks live in `threat-model.md`.

## Trust boundaries

```
 ┌────────────────────────────────────────────────────────┐
 │  Device (user owns)                                    │
 │  ┌──────────────────────────────────────────────────┐  │
 │  │  App process                                     │  │
 │  │  ┌──────────────┐   ┌────────────────────────┐   │  │
 │  │  │ Compose UI   │   │ Shared KMP core        │   │  │
 │  │  └──────────────┘   └────────────────────────┘   │  │
 │  │       │                    │                    │  │
 │  │       ▼                    ▼                    │  │
 │  │  ┌──────────────────────────────────────────┐   │  │
 │  │  │ SQLCipher DB (on internal storage)       │   │  │
 │  │  └──────────────────────────────────────────┘   │  │
 │  │       │                                          │  │
 │  │       ▼                                          │  │
 │  │  ┌──────────────────────────────────────────┐   │  │
 │  │  │ Android Keystore (hardware-backed if TEE)│   │  │
 │  │  └──────────────────────────────────────────┘   │  │
 │  └──────────────────────────────────────────────────┘  │
 └────────────────────────────────────────────────────────┘
      │                                  ▲
      │ TLS 1.3                          │ FCM push (metadata only,
      │ bearer token                     │ no session content)
      ▼                                  │
 ┌──────────────────────┐        ┌──────────────────┐
 │ datawatch server(s)  │        │ Google FCM       │
 │ (user-owned)         │        │ (Google infra)   │
 └──────────────────────┘        └──────────────────┘
      ▲
      │ Google Drive Auto Backup
      │ (encrypted blob, Google key)
      ▼
 ┌──────────────────────┐
 │ Google Drive         │
 │ (Google infra)       │
 └──────────────────────┘
```

## Key hierarchy

```
Master bootstrap (1 per app install)
  └─ Android Keystore alias: dw.master
     ├─ AES-256-GCM (hardware-backed via StrongBox where available)
     └─ unlockable only by the app's UID; no user-auth gate in v1

SQLCipher DB key (derived)
  └─ HMAC-SHA256(master, "db:v1") → 32 bytes
     Applied via PRAGMA key at open time.

Per-profile bearer token
  └─ Stored in EncryptedSharedPreferences
     ├─ Key wraps: keystore alias dw.profile.<profile_id>
     └─ Lookup: server_profile.bearer_token_ref = "dw.profile.<id>"
```

At no point does the plaintext token transit through SQLCipher — only the alias is stored
in the DB. This lets Auto Backup restore the DB on a new device without compromising the
token (Keystore material is platform-excluded from backup; user re-enters each token once).

## Authentication to datawatch server

- Bearer token per server, sent via `Authorization: Bearer <token>` on HTTPS/WSS/SSE.
- Token presentation is logged server-side per parent datawatch; client logs never include
  the token.
- For servers using self-signed certificates, users opt in to a per-profile trust anchor
  stored as a SHA-256 fingerprint. NetworkSecurityConfig applies the anchor only for that
  profile's hostname. Hostname verification is never disabled.
- Optional cert pinning (per profile) adds the leaf cert's public key hash to NSC's pin set.

## Transport security

| Transport | TLS / auth | Notes |
|---|---|---|
| REST (HTTPS) | TLS 1.2+ (1.3 preferred), bearer token | OkHttp min TLS enforced |
| WebSocket (WSS) | Same as REST | Upgrade from HTTPS; single-connection per session view |
| MCP-SSE (HTTPS) | Same as REST | Server-sent events |
| DNS TXT | HMAC-SHA256 on message + replay nonce | Per parent datawatch DNS channel spec |
| Intent handoff (Signal/SMS/Slack) | Platform app's own E2EE / transport | Trust inherited from the app the user chose |

## Secret handling

- Tokens, audio blobs, and session content never appear in logs (debug or release).
- ProGuard / R8 rules strip log statements in release builds except `SecurityLog` (only
  non-sensitive security-event category).
- Crash reports are disabled by default (ADR-0007). If the user opts in to local crash
  capture, the dump is encrypted with the SQLCipher key and stored in the DB — never sent
  to any third party.
- Exports (Settings → Export config) ask the user to confirm and offer a "redact tokens"
  toggle, defaulted on.
- Share sheet does not expose session content; screenshots are disabled on screens that
  show raw tokens (FLAG_SECURE on settings token-reveal screens).

## At-rest protection

- SQLCipher 4.x, PRAGMA cipher settings from SQLCipher defaults (AES-256-CBC with HMAC-
  SHA256, 256,000 PBKDF2 rounds derived from key schedule).
- EncryptedSharedPreferences for non-DB settings (AES-256-GCM with Jetpack Security).
- FLAG_SECURE applied on all screens showing tokens, paired devices, and config exports.

## In-transit protection

- All HTTPS requests pin to modern cipher suites; weak suites disabled in OkHttp's
  `ConnectionSpec` (MODERN_TLS only).
- WebSocket heartbeats every 30 s with automatic reconnect exponential backoff capped at 60 s.
- DNS TXT mode uses the parent's HMAC + nonce scheme verbatim.

## Permissions requested (Android manifest)

| Permission | Used for | Request timing |
|---|---|---|
| `INTERNET` | Everything | Normal |
| `ACCESS_NETWORK_STATE` | Reachability indicator | Normal |
| `RECORD_AUDIO` | Voice capture | Runtime, on first voice use |
| `POST_NOTIFICATIONS` | Push + local notifications | Runtime on Android 13+ |
| `USE_BIOMETRIC` | Reserved for post-v1 biometric unlock | Not requested in v1 |
| `FOREGROUND_SERVICE` | ntfy long-poll fallback | Only if ntfy backend selected |
| `FOREGROUND_SERVICE_SPECIAL_USE` | ntfy fallback (Android 14+) | Same |
| `BIND_CAR_APP_SERVICE` | Android Auto | Manifest-declared |
| `com.google.android.wearable.service.BIND_LISTENER` | Wear OS | Manifest-declared |

**Not requested:**
- `READ/SEND_SMS` — user uses the default SMS app via Intent handoff only.
- `READ_CONTACTS` — never needed.
- `ACCESS_FINE_LOCATION` — never needed.
- `READ_EXTERNAL_STORAGE` — session exports go to scoped app storage only.

## Update + tamper integrity

- Play App Signing guarantees the release key; the upload key is held by dmz personally.
- The internal build (`.dev`) uses a separate upload key; its signature is distinct and
  the two installs cannot collide.
- `PackageManager` integrity checks at startup verify the signing key matches a baked-in
  fingerprint; if not, the app refuses to unlock the DB (defense in depth vs repack attacks).

## FCM payload content

Per ADR-0012, the server pushes a **dumb ping** with no session content. Only these fields
are in the FCM data payload:

```json
{
  "profile_id": "srv-abc123",
  "event": "input_needed" | "rate_limited" | "completed" | "error",
  "session_hint": "a3f2",
  "ts": 1744924800
}
```

All actual content is fetched from the server over the bearer-authenticated channel when
the app wakes. This avoids session text ever traversing Google infrastructure beyond a
4-char session hint.

## Audit + review cadence

- Monthly: `./gradlew dependencyCheckAnalyze` (OWASP) and review findings.
- Per release: ProGuard rules audit, manifest diff audit, new permission review.
- Quarterly: re-scan threat model for new attack paths (jailbreak/root tools, accessibility
  service abuse, new Android platform changes).
