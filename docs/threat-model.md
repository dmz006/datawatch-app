# Threat Model — STRIDE

Scope: the Datawatch Client app (phone + Wear + Auto) and its interactions with user-owned
datawatch servers, Google FCM, Google Drive Auto Backup, and on-device messenger apps.

Out of scope: the datawatch server itself (see parent repo `SECURITY.md`), the user's
operating system, the user's physical device security.

## Assets

| Asset | Why it matters |
|---|---|
| Bearer tokens (per server) | Grant full control of a datawatch server |
| Session content | Contains code, secrets, commercial IP |
| Voice recordings | Spoken commands may include secrets before transcription |
| User's relationship graph (server list) | Reveals the user's infrastructure |
| FCM device token | Could redirect push wakes to another device |
| SQLCipher master key | Unlocks everything on-device |

## Trust actors

| Actor | Assumed trust | Notes |
|---|---|---|
| User (dmz) | Full | Device owner |
| User's datawatch servers | Full | Same owner |
| Google FCM | Partial | Delivery only, no content (ADR-0012) |
| Google Drive Auto Backup | Partial | Encrypted blob only, Google holds Google-side key |
| On-device messenger apps (Signal, SMS) | Partial | Relayed via Intent handoff, not joined |
| Other apps on the device | Untrusted | Standard Android sandbox boundary |
| Cell carrier / ISP | Untrusted | TLS protects content |

## STRIDE analysis

### Spoofing

| Threat | Mitigation |
|---|---|
| Malicious server impersonating user's datawatch | Cert pinning (opt-in per profile) + user-confirmed trust-anchor fingerprint for self-signed; hostname verification always on |
| Malicious push impersonating FCM | FCM sender ID verified; payload scheme validated before dispatch; unrecognized schemas dropped silently with telemetry event |
| Repackaged app masquerading as Datawatch Client | PackageManager integrity check at startup against baked-in signing fingerprint; refuses to unlock DB if signature doesn't match |
| ASSIST intent hijack by another voice app | Intent filter requires our signature permission; queries to ASSIST dispatcher honor user's default and we don't auto-dispatch |
| Malicious Wear watch app mimicking ours | Wear Data Layer uses package name + signature; listener only accepts messages from our phone package |

### Tampering

| Threat | Mitigation |
|---|---|
| On-device modification of cached sessions | SQLCipher; any write forces HMAC validation; corruption → DB recreation with user-visible state loss banner |
| In-transit session reply modification | TLS 1.2+, modern cipher suites only |
| Malicious xterm.js bundle swap | Vendored bundle served from read-only assets; SHA-256 pinned in code; `addJavascriptInterface` disabled |
| Backup archive tampering | Restore flow re-verifies DB integrity + refuses to open if HMAC fails |
| Intent relay crafting commands to wrong recipient | Profile stores recipient (Signal ID / phone) per channel; user confirms every send via the relay app's own UI |

### Repudiation

| Threat | Mitigation |
|---|---|
| User denies issuing a voice command | Voice blob + transcript stored in `PendingUpload` (SQLCipher) until user clears; server also logs per its audit policy |
| Attacker denies injected action after steal | Out of scope at app layer — server bearer token compromise is handled at the server |

### Information disclosure

| Threat | Mitigation |
|---|---|
| Logcat shows tokens or session content | ProGuard/R8 strips `Log.*` in release; custom `SecurityLog` never accepts sensitive categories |
| Screenshot by another app / screen record | FLAG_SECURE on token-reveal, server-edit, and export screens |
| Clipboard leaks tokens | Copy-token requires explicit tap; clipboard cleared after 30 s via `ClipData.Description` `IS_SENSITIVE` flag on Android 13+ |
| Backup restore on hostile device | Keystore material excluded by platform; user re-enters token on first run on new device |
| FCM payload leak | Dumb-ping scheme; only profile_id + event kind + 4-char session hint |
| Accessibility service abuse | Not a user-controlled mitigation; documented risk (same as any app); FLAG_SECURE reduces impact on critical screens |
| Debug builds leak content in logs | Debug builds default to `com.dmzs.datawatchclient.debug`; signing differs; `Release.isDebuggable = false` |
| xterm.js WebView JS injection | No user-provided HTML rendered; all data postMessage'd through a typed bridge; CSP locked |
| Voice recording intercepted by another app | `AudioRecord` source MIC is single-consumer at the OS layer while active |

### Denial of service

| Threat | Mitigation |
|---|---|
| Malicious push flood drains battery | Rate-limit per profile_id on the client: max 1 push/second; excess dropped; telemetry event on flood |
| Attacker-controlled DNS TXT channel floods | Nonce replay protection per parent spec; rate-limited; falls back to alternate transport after N failures |
| xterm.js output bomb (OOM) | WebView memory cap; terminal frame trim after 5,000 rows in cache; server is source of truth |
| Voice recorder held indefinitely | Hard cap 2 minutes per capture; releases MIC and fails the upload if exceeded |

### Elevation of privilege

| Threat | Mitigation |
|---|---|
| Malicious deep link invokes privileged action | Deep links require signature permission or explicit user confirmation dialog |
| Auto Car session on head unit shows phone-only data | Public Auto build is messaging-template only; no privileged surfaces (ADR-0031) |
| Another app uses our content provider / exported service | No exported providers or services; explicit `android:exported="false"` on all manifest components except messaging Car App service |

## Residual risks (accepted)

| Risk | Why accepted |
|---|---|
| If the user's device is rooted + compromised, all bets are off | App-layer mitigation has diminishing returns; we don't advertise nation-state resistance |
| Google FCM can observe push metadata (timing, profile_id, event kind) | User chose ADR-0012 hybrid; payload is deliberately minimal |
| Google Drive can observe backup frequency and size | User chose ADR-0017; can disable in Settings → Backup |
| Third-party keyboard can log typed tokens during pairing | Standard Android concern; we mitigate by supporting paste from clipboard |

## Per-surface additional threats

- **Wear OS:** Wearable Data Layer is bluetooth-authenticated by the pair; lost watch on
  wrist of another person has access to session reply notifications. Auto-wipe on wrist-off
  requires heart-rate sensor — considered for post-MVP under a separate ADR.
- **Android Auto:** Public build never shows session text, only TTS-readable subset +
  voice reply. Internal build never enters Play Store, so its broader surface isn't
  publicly exploitable.

## Review cadence

Quarterly review. Any new dependency, permission, or surface triggers an ad-hoc re-score.
