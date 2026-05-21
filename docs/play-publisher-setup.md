# Gradle Play Publisher (GPP) Setup

Gradle Play Publisher (gradle-play-publisher) is a Gradle plugin that automates Play Store uploads, metadata syncing, and track promotion from the CLI. This removes the need for manual uploads via Play Console UI.

## Prerequisites

- Google Play Developer Account (already created per play-store-registration.md)
- Admin or Release Manager access in Play Console for the app
- Service account with Google Play Developer API access

## Step 1 — Enable Google Play Developer API

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Select the **datawatch** project (or create one if not present)
3. Search for **"Google Play Developer API"** and enable it
4. Create a service account:
   - **IAM & Admin → Service Accounts → Create Service Account**
   - Name: `datawatch-play-publisher`
   - Grant the role: **Service Account User**
   - Click **Create and Continue**
   - Optionally add description: "Used by gradle-play-publisher for automated Play Store uploads"
   - **Create a key → JSON**
5. A JSON key file will download automatically. Keep this file safe (never commit to git).

## Step 2 — Grant Play Console Access

1. Go to [Play Console](https://play.google.com/console)
2. Select the **datawatch** app
3. **Users and permissions → Invite user**
4. Paste the service account email (from the JSON key: `client_email` field)
5. Grant **Release Manager** role (allows uploads, track promotion)
6. Send invitation

## Step 3 — Store the Service Account Key Locally

Place the JSON key file at one of these locations (in order of precedence):

1. **Environment variable:** `PLAY_PUBLISHER_KEY=/path/to/key.json`
   ```bash
   export PLAY_PUBLISHER_KEY=~/.android/datawatch-play-key.json
   ```
2. **Default location:** `~/.android/datawatch-play-key.json` (auto-detected)

**NEVER commit the JSON key to git.** Add it to `.gitignore` if placing it in the repo root.

## Available Gradle Tasks

Once GPP is configured, you can use these tasks from the `composeApp/` directory:

```bash
# Publish the app bundle (AAB) to the specified track
./gradlew publishPublicTrackBundle  # publishes to 'internal' track by default
./gradlew publishPublicTrackBundle --args publishOptions=com.github.triplet.play.internal

# Promote from one track to another (e.g., internal → alpha → beta → production)
./gradlew promotePublicTrackArtifact --args 'fromTrack=internal toTrack=alpha'

# Sync app listing (description, screenshots, store metadata)
./gradlew publishPublicTrackListing

# View current Play Console release status
./gradlew fetchPublicTrackMetadata

# List all configured release tracks
./gradlew printPublicTrackPublishingList
```

**Note:** The `publicTrack` flavor publishes to Play Console. The `dev` flavor is internal-only and does not publish (per ADR-0031).

## Typical Workflow for Release

1. **Build the release AAB:**
   ```bash
   ./gradlew bundlePublicTrackRelease
   ```

2. **Upload to internal testing track:**
   ```bash
   ./gradlew publishPublicTrackBundle
   ```

3. **Verify in Play Console**, then promote:
   ```bash
   ./gradlew promotePublicTrackArtifact --args 'fromTrack=internal toTrack=alpha'
   ```

4. **Wait for testers to validate**, then promote to beta:
   ```bash
   ./gradlew promotePublicTrackArtifact --args 'fromTrack=alpha toTrack=beta'
   ```

5. **After beta validation, promote to production with a staged rollout:**
   ```bash
   ./gradlew promotePublicTrackArtifact --args 'fromTrack=beta toTrack=production'
   ```

## Troubleshooting

| Issue | Solution |
|-------|----------|
| "Missing or invalid serviceAccountCredentials" | Verify JSON key path and `PLAY_PUBLISHER_KEY` env var |
| "Service account does not have permission" | Check Play Console → Users → service account has **Release Manager** role |
| "Package name mismatch" | Ensure flavor is `publicTrack` (app ID: `com.dmzs.datawatchclient`) |
| "Conflict with existing release" | A release is already in progress on that track; wait or rollback via Console |

## Security Considerations

- Store the JSON key in a password manager or encrypted local file, never in git
- Rotate the service account key annually
- Audit Play Console access via **Users and permissions** quarterly
- Consider using a CI/CD pipeline (GitHub Actions) for automated releases with the key stored as a secret

## References

- [gradle-play-publisher Documentation](https://github.com/Triple-T/gradle-play-publisher)
- [Google Play Developer API Docs](https://developers.google.com/android-publisher)
- [ADR-0031 — App Signing & Release Strategy](docs/decisions/0031-release-tracks.md)
