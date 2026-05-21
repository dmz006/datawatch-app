# Play Store Release Workflow

This guide covers the complete process for testing, building, and releasing the datawatch client to Google Play Store.

---

## Overview

The release process follows this progression:

1. **Version Management** — Increment version codes and names
2. **Build Signed Release APK/AAB** — Create release-signed binaries
3. **Test on Device** — Validate core flows
4. **Upload to Play Console** — Push to staging tracks
5. **Promote Through Tracks** — Internal → Beta → Production
6. **Staged Rollout** — Progressive 5% → 10% → 25% → 100%
7. **Monitor Post-Release** — Watch crash reports and ANRs

---

## 1. Version Management

### Gradle Properties

Versions are sourced from gradle properties in `composeApp/build.gradle.kts`:

```kotlin
appVersion = findProperty("DATAWATCH_APP_VERSION") ?: "1.0.0"
appVersionCode = findProperty("DATAWATCH_APP_VERSION_CODE")?.toInt() ?: 1
```

### Updating Versions

Before each release, update the version in one of these places:

**Option A: gradle.properties** (recommended for team use)
```properties
DATAWATCH_APP_VERSION=1.0.1
DATAWATCH_APP_VERSION_CODE=2
```

**Option B: Command-line override**
```bash
./gradlew build \
  -PDATAWATCH_APP_VERSION=1.0.1 \
  -PDATAWATCH_APP_VERSION_CODE=2
```

**Option C: Environment variables** (CI/CD systems)
```bash
export DATAWATCH_APP_VERSION=1.0.1
export DATAWATCH_APP_VERSION_CODE=2
./gradlew build
```

### Version Semantics

- **versionCode**: Integer, always increasing. Play Store rejects releases with code ≤ previous version.
- **versionName**: Semantic versioning (MAJOR.MINOR.PATCH), e.g., "1.0.0", "1.0.1", "1.1.0".
- **Increment versionCode by 1 for each release**, regardless of versionName change.

---

## 2. Build Signed Release APK/AAB

### Prerequisites

- Android Keystore file (signing key) — typically at `~/.android/datawatch-release-key.jks`
- Keystore password and key alias password
- Ensure `composeApp/build.gradle.kts` has `signingConfigs` configured (already present per ADR-0031)

### Build via Android Studio GUI

1. **Build → Generate Signed Bundle/APK**
2. Select **APK** (for direct device testing) or **Bundle** (for Play Store upload)
3. Select keystore file and passwords
4. Choose **publicTrackRelease** build variant
5. Finish

Output location:
- APK: `composeApp/build/outputs/apk/publicTrack/release/`
- Bundle: `composeApp/build/outputs/bundle/publicTrackRelease/`

### Build via Command Line

For APK:
```bash
./gradlew :composeApp:assemblePublicTrackRelease \
  -Pandroid.injected.signing.store.file=/path/to/keystore.jks \
  -Pandroid.injected.signing.store.password='<keystore-password>' \
  -Pandroid.injected.signing.key.alias=release \
  -Pandroid.injected.signing.key.password='<key-password>'
```

For AAB (Android App Bundle):
```bash
./gradlew :composeApp:bundlePublicTrackRelease \
  -Pandroid.injected.signing.store.file=/path/to/keystore.jks \
  -Pandroid.injected.signing.store.password='<keystore-password>' \
  -Pandroid.injected.signing.key.alias=release \
  -Pandroid.injected.signing.key.password='<key-password>'
```

**Note**: AAB is required for new Play Store releases (APK-only uploads deprecated).

---

## 3. Test on Device

### Install Release APK

```bash
adb install -r composeApp/build/outputs/apk/publicTrack/release/composeApp-publicTrack-release.apk
```

### Test Critical Flows

- **Settings → Add Server**: Configure a test datawatch instance
- **Sessions Tab**: List, filter, and drill into active/past sessions
- **Automata Tab**: View PRD status, algorithm mode OODA loop
- **Alerts Tab**: Check guardrail verdicts and alerts
- **Observer Tab**: Monitor CPU/memory/disk across servers
- **Dashboard Tab**: Create/remove cards; verify sorting and persistence
- **Wear & Auto**: If supported, test on Wear OS and Android Auto emulators

### Verify Permissions

- Check Settings → Permissions for any missing declarations
- Ensure Network, Location, Storage permissions are stated in store listing

### Check Crash Logs

```bash
adb logcat | grep -i "crash\|exception\|error"
```

---

## 4. Upload to Play Console

### Prerequisites

- Google Play Developer Account (one-time $25)
- **datawatch app** created in Play Console
- Service account with Google Play Developer API access (see `play-publisher-setup.md`)
- `PLAY_PUBLISHER_KEY` environment variable or `~/.android/datawatch-play-key.json`

### Via Gradle Play Publisher (Automated)

```bash
export PLAY_PUBLISHER_KEY=~/.android/datawatch-play-key.json

./gradlew :composeApp:publishPublicTrackBundle
```

This uploads the bundle to the **internal** track by default.

### Via Play Console UI (Manual)

1. Go to [Play Console](https://play.google.com/console)
2. Select **datawatch** app
3. **Release → Create new release**
4. Choose release type: **Internal testing**, **Closed testing**, **Open testing**, or **Production**
5. Click **Create release**
6. Upload AAB file from `composeApp/build/outputs/bundle/publicTrackRelease/app-publicTrack-release.aab`
7. Add release notes and review store listing
8. Click **Review release**

---

## 5. Release Tracks and Progression

Play Store offers 4 release tracks, each with different visibility and audience:

| Track | Visibility | Audience | Use |
|-------|-----------|----------|-----|
| **Internal Testing** | Private | Up to 100 internal testers | Quick QA, smoke tests |
| **Closed Testing** | Private | Opt-in users (you control list) | Beta testers, controlled feedback |
| **Open Testing** | Public | Anyone with link or via Play Store listing | Public beta, wider feedback |
| **Production** | Public | All users on Play Store | Live release (use staged rollout) |

### Typical Progression

```
Internal Testing (1-2 days)
    ↓ [Manual approval]
Closed Testing / Beta (3-7 days)
    ↓ [Tester feedback, bug fixes]
Open Testing (optional, 1-3 days)
    ↓ [Wider audience validation]
Production (staged rollout: 5% → 10% → 25% → 100%)
```

### Promote Between Tracks (Gradle)

```bash
# Promote from internal to closed beta
./gradlew :composeApp:promotePublicTrackArtifact \
  --args 'fromTrack=internal toTrack=closed'

# Promote from closed to production
./gradlew :composeApp:promotePublicTrackArtifact \
  --args 'fromTrack=closed toTrack=production'
```

---

## 6. Staged Rollout (Production Only)

A staged rollout reduces risk by deploying to a small percentage of users first:

1. **Day 1**: 5% rollout (fast feedback loop; rollback if critical issues)
2. **Day 2**: 10% rollout
3. **Day 3**: 25% rollout
4. **Day 4**: 100% rollout

**To enable staged rollout in Play Console**:

1. Go to **Release → Production**
2. In the release details, toggle **Staged rollout**
3. Set percentages: 5%, 10%, 25%, 100%
4. Accept release at each stage

Or via gradle-play-publisher (note: v7.0+ required):
```bash
./gradlew :composeApp:publishPublicTrackBundle \
  --args 'userFraction=0.05'  # 5% rollout
```

---

## 7. Monitor Post-Release

### Crash Reports

- **Play Console → Monitoring → Crashes & ANRs**
- Watch for spike in crashes post-release
- If critical crash (>5% of sessions), initiate rollback or hotfix

### Performance Metrics

- **Monitoring → Performance**
- Check ANR (Application Not Responding) rates
- Watch for memory leaks, excessive battery drain
- Monitor frame drops in UI

### User Reviews

- **Store listing → Reviews**
- Respond to 1-star reviews mentioning bugs
- Prioritize issues mentioned in multiple reviews

### Rollback (Emergency)

If critical issue is discovered post-release:

1. Go to Play Console → Release → Production
2. Click **Halt rollout** (stops further progression)
3. Go to prior release tab, click **Resume rollout** or **Make this version current**
4. Create a hotfix: increment versionCode, rebuild, and re-upload

---

## 8. Store Listing Requirements

Before uploading to production, ensure these are complete in Play Console:

### App Details
- [ ] App title (30 chars max)
- [ ] Short description (80 chars max)
- [ ] Full description (4000 chars max; include features, privacy note)
- [ ] Category (Productivity or Tools)
- [ ] Content rating questionnaire (ESRB)

### Branding
- [ ] App icon (512×512 PNG, all platforms)
- [ ] Feature graphic (1024×500 PNG)
- [ ] Screenshots (2–8 per device type)
  - Phone: portrait 1080×1920
  - Tablet: 1440×2560
  - Wear: 384×384
  - Auto: 1280×720

### Privacy & Security
- [ ] Privacy policy URL (required; datawatch terms link)
- [ ] Data safety declarations (see `data-safety-declarations.md`)
- [ ] Permissions justification (Network, Location, Storage)

### Settings
- [ ] Email for support
- [ ] Website (if applicable)
- [ ] Pricing: Free or paid (currently free)

---

## Checklist: Release Readiness

Before uploading to Play Console:

- [ ] Versions match: `gradle.properties` ↔ `Version.kt` ↔ git tag
- [ ] All P0/P1 bugs closed (check Linear or issue tracker)
- [ ] E2E tests pass on device (Sessions, Automata, Alerts, Observer, Dashboard all functional)
- [ ] Screenshots updated (phone, wear, auto if applicable)
- [ ] Release notes written and accurate
- [ ] Privacy policy reviewed and linked
- [ ] Data safety declarations completed
- [ ] Store listing metadata (title, description, category) final

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| "Service account does not have permission" | Check Play Console → Users & permissions; service account must have **Release Manager** role |
| "Conflict with existing release" | Another release is in progress on that track; wait or close via Play Console |
| "APK rejected: versionCode must be greater than X" | Increment versionCode; Play Store never allows decreasing or duplicate codes |
| "Bundle signature mismatch" | Ensure same keystore was used; never switch keystores for the same package ID |
| "Crash on launch after release" | Immediately halt staged rollout; investigate logs; push hotfix with incremented versionCode |
| "Screenshots not visible in store" | Ensure correct dimensions (1080×1920 for phone, etc.); PNG format required |

---

## References

- [gradle-play-publisher](https://github.com/Triple-T/gradle-play-publisher)
- [Play Store Console Help](https://support.google.com/googleplay/android-developer)
- [Google Play Developer API](https://developers.google.com/android-publisher)
- `play-publisher-setup.md` — Service account configuration
- `ADR-0031` — App signing & release strategy

---

## CI/CD Integration (Future)

For automated releases via GitHub Actions or similar:

1. Store keystore file and passwords as repository secrets
2. Create workflow: `on: push to release branch`
3. Build signed bundle
4. Upload to internal track via `publishPublicTrackBundle`
5. Auto-promote to beta if tests pass
6. Manual approval gate before production

See `decisions/0031-release-tracks.md` for architecture decision.
