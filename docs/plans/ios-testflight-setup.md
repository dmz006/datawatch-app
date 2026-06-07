# iOS TestFlight CI Setup Plan

**Status:** Blocked on credentials — Apple Developer enrolled, secrets not yet added to GitHub.

## What needs to happen

The `testflight-upload` job in `.github/workflows/ios-build.yml` is commented out.
Uncomment it + add these 6 GitHub secrets and every version tag push will build a
real IPA and ship it to TestFlight automatically.

---

## Step 1 — App Store Connect API Key

1. Go to **appstoreconnect.apple.com → Users and Access → Integrations → App Store Connect API**
2. Click **+** → name: `datawatch-ci`, role: **App Manager**
3. Download the `.p8` file (one-time download)
4. Note the **Key ID** (10-char) and **Issuer ID** (UUID at top of page)
5. Base64-encode the key for the secret:
   ```bash
   base64 -i AuthKey_XXXXXXXXXX.p8 | pbcopy
   ```

| GitHub Secret | Where to get it |
|---|---|
| `APP_STORE_CONNECT_API_KEY_ID` | Key ID column on ASC API keys page |
| `APP_STORE_CONNECT_API_ISSUER_ID` | Issuer ID at top of ASC API keys page |
| `APP_STORE_CONNECT_API_KEY_BASE64` | base64 of the downloaded .p8 file |

---

## Step 2 — fastlane match (signing certs repo)

fastlane match stores your distribution cert + provisioning profile encrypted in a
private git repo. CI fetches them at build time.

1. Create a **new private GitHub repo**: e.g. `dmz006/datawatch-match-certs` (empty)
2. On your Mac, install fastlane and run match once to populate it:
   ```bash
   cd iosApp
   gem install fastlane
   fastlane match appstore --app-identifier com.dmzs.datawatchclient
   ```
   Enter the password you choose when prompted — this encrypts everything in the repo.
3. The first run creates the cert + profile and pushes them to the private repo.

| GitHub Secret | Value |
|---|---|
| `MATCH_GIT_URL` | `https://github.com/dmz006/datawatch-match-certs.git` |
| `MATCH_PASSWORD` | The password you chose during `match init` |

---

## Step 3 — CI keychain password

Any strong random string — used to create a temporary keychain in the macOS CI runner.
Does not need to match anything else.

```bash
openssl rand -base64 24   # copy the output
```

| GitHub Secret | Value |
|---|---|
| `KEYCHAIN_PASSWORD` | Random string from above |

---

## Step 4 — Add secrets to GitHub

**Repo → Settings → Secrets and variables → Actions → New repository secret**

Add all 6 secrets listed above.

---

## Step 5 — Uncomment the workflow job

Once secrets are in place, open `.github/workflows/ios-build.yml` and uncomment the
`testflight-upload` job (it's fully written, just commented out). Also ensure
`iosApp/ExportOptions.plist` exists — create it if not:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>method</key>
    <string>app-store</string>
    <key>uploadBitcode</key>
    <false/>
    <key>uploadSymbols</key>
    <true/>
</dict>
</plist>
```

---

## Result

Every `git push origin v1.x.y` tag will:
1. Build simulator (existing, unchanged)
2. Build + archive real IPA with App Store signing
3. Upload to TestFlight automatically

Testers get the build in TestFlight within ~30 min of the tag push.

---

## Bundle ID

`com.dmzs.datawatchclient` — must match the App ID registered in Apple Developer portal.
