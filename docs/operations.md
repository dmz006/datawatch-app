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

## iOS build pipeline

### CI (GitHub Actions — `ios-build.yml`)

Triggered on every push to `main` and all PRs. Runs on `macos-15`.

Steps:
1. Checkout + cache Gradle
2. Build KMP XCFramework: `./gradlew :shared:assembleDatawatchSharedDebugXCFramework`
3. Resolve Swift packages: `xcodebuild -resolvePackageDependencies`
4. Build Simulator target: `xcodebuild build -scheme iosApp -destination 'platform=iOS Simulator,name=iPhone 16'`

The XCFramework task name is **`assembleDatawatchSharedDebugXCFramework`** — the short form `assembleDebugXCFramework` is ambiguous and must not be used.

### Release signing

iOS App Store distribution requires:
- **Apple Developer Program enrollment** (paid — pending)
- **Distribution certificate** (`.p12`) stored as GitHub secret `IOS_DISTRIBUTION_CERT_P12` (base64-encoded)
- **Provisioning profile** stored as GitHub secret `IOS_PROVISION_PROFILE` (base64-encoded)
- **App Store Connect API key** for Fastlane/altool upload

None of these are currently active (no Apple enrollment as of v1.0.4). The `ios-build.yml` CI job validates compilation only; archive + upload to App Store is a manual step until enrollment completes.

### Re-signing procedure (future)

1. Rotate the distribution cert in Apple Developer Portal.
2. Export new `.p12`, base64-encode (`base64 -i new.p12`), update `IOS_DISTRIBUTION_CERT_P12` secret in GitHub repo settings.
3. Download new provisioning profile from portal, base64-encode, update `IOS_PROVISION_PROFILE` secret.
4. Re-run the release workflow or archive manually via Xcode → Product → Archive.

### APNs configuration

APNs push is blocked on server support (datawatch#107). When the server ships:
1. Generate APNs auth key (`.p8`) in Apple Developer Portal.
2. Record Team ID, Key ID, Bundle ID.
3. Store credentials in the datawatch daemon config (not in this app's repo).
4. Update `AppDelegate.swift` to call the registration endpoint instead of logging only.

## Incident response

- Security: see [SECURITY.md](../SECURITY.md).
- User-visible outage caused by a bad release: rollback the Play track to the previous
  AAB via Play Console → Release → Rollout → Halt + New Release of the previous version.
- Do not delete the tag — add a `vX.Y.Z-yanked` note in CHANGELOG, cut `vX.Y.Z+1` with
  the fix, release.
