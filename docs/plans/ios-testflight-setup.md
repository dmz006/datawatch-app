# iOS TestFlight Setup

**Status (2026-10-04):** Apple Developer account exists. Pipeline is fully wired and
dormant until the secrets below are added and `IOS_SIGNING_CONFIGURED=true` is set.
No Mac is required — certificate generation and signing both run on the hosted
`macos-15` GitHub runner using App Store Connect API-key auth.

## What's already in the repo

| Piece | Where |
|---|---|
| Simulator compile check on every push/PR | `.github/workflows/ios-build.yml` |
| One-shot certificate + profile generation | `.github/workflows/ios-match-init.yml` (manual dispatch) |
| Tag release → test gate → sign → TestFlight → IPA on GitHub release | `.github/workflows/release.yml`, job `testflight` |
| fastlane lanes (`match_init`, `beta`) | `iosApp/fastlane/Fastfile`, `iosApp/fastlane/Matchfile` |
| Bundle ID | `com.dmzs.datawatchclient` (`iosApp/project.yml`) |

## Your part — about 20 minutes, all in a browser

### 1. App ID + app record
1. developer.apple.com → Certificates, Identifiers & Profiles → **Identifiers** → `+` → App IDs → App.
   Bundle ID (explicit): `com.dmzs.datawatchclient`. Capabilities: Push Notifications (for #185 later).
2. appstoreconnect.apple.com → **Apps** → `+` New App → iOS, name `datawatch`, that bundle ID, SKU anything.

### 2. App Store Connect API key
appstoreconnect.apple.com → Users and Access → **Integrations** → App Store Connect API → **Team Keys** → `+`.
Name `datawatch-ci`, role **App Manager**. Download the `.p8` (one-time download). Note the **Key ID** and the **Issuer ID** shown at the top of the page.

```bash
base64 -w0 AuthKey_XXXXXXXXXX.p8      # Linux   → APP_STORE_CONNECT_API_KEY_BASE64
base64 -i AuthKey_XXXXXXXXXX.p8 | tr -d '\n'   # macOS
```

### 3. Private certs repo for fastlane match
1. Create an **empty private** GitHub repo, e.g. `dmz006/datawatch-match-certs`.
2. Create a GitHub **personal access token** (classic, `repo` scope, or fine-grained with
   Contents: read/write on that one repo). Encode it with your GitHub username:
   ```bash
   printf '%s:%s' 'dmz006' '<PAT>' | base64 -w0     # → MATCH_GIT_BASIC_AUTHORIZATION
   ```
3. Pick a passphrase for the certs repo encryption → `MATCH_PASSWORD`.

### 4. GitHub secrets + variable
Repo → Settings → Secrets and variables → Actions.

| Secret | Value |
|---|---|
| `APP_STORE_CONNECT_API_KEY_ID` | 10-char Key ID |
| `APP_STORE_CONNECT_API_ISSUER_ID` | Issuer UUID |
| `APP_STORE_CONNECT_API_KEY_BASE64` | base64 of the `.p8` |
| `MATCH_GIT_URL` | `https://github.com/dmz006/datawatch-match-certs.git` |
| `MATCH_GIT_BASIC_AUTHORIZATION` | base64 of `user:PAT` from step 3 |
| `MATCH_PASSWORD` | your passphrase |
| `KEYCHAIN_PASSWORD` | `openssl rand -base64 24` |

Variable (the **Variables** tab, not a secret): `IOS_SIGNING_CONFIGURED` = `true` — set this
**after** step 5 succeeds; it is what turns on the `testflight` job in `release.yml`.

### 5. Generate the certificate + profile (once)
Actions → **iOS signing — match init (one-shot)** → Run workflow → type `appstore`.
It creates the Apple Distribution certificate and the App Store provisioning profile
through the API and pushes them, encrypted, to the certs repo. Takes ~2 minutes.
Re-run only to rotate (Apple distribution certs are valid for one year).

### 6. Ship
Set `IOS_SIGNING_CONFIGURED=true`, then the next `git push origin vX.Y.Z` tag runs:
test gate → Android bundles → Play internal track **and** iOS archive → TestFlight.
The IPA is also attached to the GitHub release. Testers see the build in TestFlight
once Apple finishes processing (~10–30 min). First upload only: fill in export
compliance in App Store Connect — `ITSAppUsesNonExemptEncryption=false` is already in
`Info.plist`, so it is a one-time confirmation.

## Notes
- Apple ID / password are never used; everything is API-key based. `FASTLANE_USER` is
  only needed for interactive local runs and is not committed.
- Rotating: delete the cert in the Developer portal, re-run the match-init workflow.
- APNs (#185) needs an APNs auth key (`.p8`, Keys section) on the **server**, not here.
