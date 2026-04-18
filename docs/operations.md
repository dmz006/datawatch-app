# Operations

Per [AGENT.md](../AGENT.md), this doc covers anything that affects deployment, signing,
release, or on-device diagnostics. Lives next to the security model because most
operational choices have a security impact.

## Release workflow

See [AGENT.md § Release workflow](../AGENT.md#release-workflow-every-version-bump) and
[play-store-registration.md](play-store-registration.md).

## Upload key storage

- **Upload keystore (public variant):** `~/.android/datawatch-upload.jks` — generated
  with `keytool` during Sprint 0 Day 1. Password lives in the personal password manager,
  never in Git. Backup to offline media recommended.
- **Upload keystore (dev variant):** `~/.android/datawatch-dev-upload.jks` — distinct
  keystore; distinct alias.
- Base64-encoded copies are loaded into GitHub Actions secrets
  (`UPLOAD_KEYSTORE_BASE64`, `UPLOAD_KEY_PASSWORD`, `UPLOAD_KEY_ALIAS`,
  `UPLOAD_KEYSTORE_PASSWORD`) for CI release jobs.

## Certificate fingerprints

| Variant | Upload SHA-256 | App-Signing SHA-256 (from Play) |
|---------|---------------|--------------------------------|
| `com.dmzs.datawatchclient` | _pending Sprint 0_ | _pending Play enrollment_ |
| `com.dmzs.datawatchclient.dev` | _pending Sprint 0_ | _pending Play enrollment_ |

Update after keystore generation and after Play App Signing enrollment. Runtime integrity
check in the app compares the installed signature against these values.

## Diagnostic data

Per ADR-0007 (closed-loop telemetry), no crash data leaves the device. Users can:

- Export encrypted logs: `Settings → Diagnostics → Export logs` (tokens redacted).
- Share logs with us via a **user-initiated** attach-to-issue flow on GitHub.

## Incident response

- Security: see [SECURITY.md](../SECURITY.md).
- User-visible outage caused by a bad release: rollback the Play track to the previous
  AAB via Play Console → Release → Rollout → Halt + New Release of the previous version.
- Do not delete the tag — add a `vX.Y.Z-yanked` note in CHANGELOG, cut `vX.Y.Z+1` with
  the fix, release.
