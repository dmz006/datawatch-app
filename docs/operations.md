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

APNs push requires the datawatch server to hold an APNs Auth Key and send to Apple's
push gateway. The iOS client sends `kind=apns` to `/api/devices/register` (see
`DeviceRegisterDto` in `shared/.../transport/dto/DeviceDtos.kt`).

Setup steps (run once, then stored in datawatch daemon config — not in this repo):

1. In Apple Developer Portal → Certificates, Identifiers & Profiles → Keys → (+):
   - Name: `datawatch APNs Auth Key`
   - Enable **Apple Push Notifications service (APNs)**
   - Download the `.p8` file **once** — it cannot be re-downloaded; store offline
   - Record: **Key ID** (10-char), **Team ID** (from top-right of portal)
2. Provide the server with: `.p8` contents, Key ID, Team ID, Bundle ID (`com.dmzs.datawatchclient`)
3. Update the datawatch daemon config per its APNs setup doc
4. In `iosApp/iosApp/notifications/NotificationService.swift` `didRegister(tokenData:)`:
   - Replace the `// TODO` stub with a call to `IosServiceLocator.registerDevice(token:kind:.apns)`
   - Mirror the Android `PushRegistrationCoordinator` pattern (per-profile, idempotent)

---

## Apple Developer Program enrollment

**Cost:** $99 USD/year (Individual account — no D-U-N-S number required).

### Enrollment steps

1. Go to `developer.apple.com/programs/enroll/`
2. Sign in with the Apple ID tied to `davidzendzian@gmail.com` (or create a dedicated one)
3. Choose **Individual / Sole Proprietor** — sufficient for TestFlight + App Store
4. Pay the $99 annual fee via credit card
5. Enrollment is usually approved within minutes for individuals; may take up to 48 h

### After enrollment — one-time setup

1. **Create App ID** in Developer Portal → Identifiers:
   - Bundle ID: `com.dmzs.datawatchclient` (Explicit)
   - Capabilities to enable: **Push Notifications**, **Associated Domains** (if needed)
2. **Create Distribution Certificate**:
   - Certificates → (+) → Apple Distribution
   - Generate a CSR on your Mac (Keychain Access → Certificate Assistant → Request a
     Certificate from a Certificate Authority — save to disk)
   - Upload CSR, download `.cer`, double-click to install in Keychain
   - Export as `.p12` with a passphrase; base64-encode for CI:
     ```
     base64 -i Distribution.p12 | pbcopy
     ```
   - Add to GitHub secret: `IOS_DISTRIBUTION_CERT_P12` (base64), `IOS_DISTRIBUTION_CERT_PASSWORD`
3. **Create App Store Provisioning Profile**:
   - Profiles → (+) → App Store Connect
   - Select the `com.dmzs.datawatchclient` App ID + the Distribution cert
   - Download `.mobileprovision`; base64-encode for CI:
     ```
     base64 -i datawatch.mobileprovision | pbcopy
     ```
   - Add to GitHub secret: `IOS_PROVISION_PROFILE`
4. **Create App Store Connect App**:
   - `appstoreconnect.apple.com` → Apps → (+) → New App
   - Platform: iOS, Bundle ID: `com.dmzs.datawatchclient`, SKU: `datawatchclient`
5. **Create App Store Connect API Key** (for CI upload without 2FA):
   - App Store Connect → Users and Access → Integrations → App Store Connect API → (+)
   - Role: **Developer** (sufficient for TestFlight upload)
   - Download the `.p8` key file **once** — record Issuer ID and Key ID
   - Add to GitHub secret: `APP_STORE_CONNECT_API_KEY` (base64 of `.p8`),
     `APP_STORE_CONNECT_KEY_ID`, `APP_STORE_CONNECT_ISSUER_ID`
6. Once secrets are set, update `ios-build.yml` to enable the archive + `xcrun altool` upload step

---

## Mac development environment (for interactive Simulator testing)

GitHub Actions `macos-15` handles CI builds but provides no interactive session.
For Simulator testing, Xcode UI work, or TestFlight device runs you need a Mac.

### Option A — MacStadium (recommended for sustained use)

- **URL:** `macstadium.com`
- **What:** Dedicated Mac Mini or Mac Pro hosted in a data centre; full SSH + VNC access
- **Pricing (2026):** ~$99–149/mo for Mac Mini M2; hourly orka.io plans available for
  short bursts (~$0.75–1.50/h)
- **Good for:** extended dev sprints, always-on CI agent, running Simulator 24/7
- **Setup:** provision via web portal, SSH in, install Xcode from App Store (use `xcodes`
  CLI to manage versions), `git clone` the repo, run `./gradlew` as normal

### Option B — MacInCloud (recommended for occasional/hourly use)

- **URL:** `macincloud.com`
- **What:** Shared or dedicated macOS instances, billed by the hour
- **Pricing (2026):** from ~$1/h (shared M1) to ~$3/h (dedicated M2)
- **Good for:** one-off builds, TestFlight archive, occasional Simulator smoke-tests
- **Setup:** browser-based remote desktop or SSH; Xcode pre-installed on most plans

### Option C — GitHub Actions (already in use — no extra cost)

`macos-15` runners are already used by `ios-build.yml`. Xcode 16 is pre-installed.
Use this path when you only need a build/test result and not an interactive session.
To add Simulator UI automation, add `xcrun simctl` steps to the existing workflow.

### Choosing between options

| Need | Use |
|------|-----|
| Quick smoke build / CI parity check | GitHub Actions (free) |
| One-off Simulator run, TestFlight archive | MacInCloud hourly |
| Weekly iOS dev, persistent environment | MacStadium monthly |
| Interactive Xcode + Simulator while developing | MacInCloud or MacStadium |

## Incident response

- Security: see [SECURITY.md](../SECURITY.md).
- User-visible outage caused by a bad release: rollback the Play track to the previous
  AAB via Play Console → Release → Rollout → Halt + New Release of the previous version.
- Do not delete the tag — add a `vX.Y.Z-yanked` note in CHANGELOG, cut `vX.Y.Z+1` with
  the fix, release.
