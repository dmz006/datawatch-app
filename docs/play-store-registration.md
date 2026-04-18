# Google Play Console — Account Recreation + App Submission

The previous developer account on `davidzendzian@gmail.com` was closed for inactivity.
This doc walks through recreating the account, enrolling the Datawatch Client app, and
shipping to production. Start in Sprint 0 — several items have lead times longer than a
sprint.

## Phase 1 — Account recreation (Sprint 0, Day 1–3)

### Step 1 — Confirm Google account status

1. Sign in at `https://accounts.google.com` with `davidzendzian@gmail.com`.
2. Verify the account is active, has 2-Step Verification enabled (required), and has a
   recovery phone + recovery email configured.

### Step 2 — Go to Play Console

1. Visit `https://play.google.com/console/signup`.
2. Select **Create a new developer account**. (The prior closed account cannot be
   reactivated per Google policy after 12+ months of closure.)
3. Choose **Yourself** — personal account (per ADR-0033; dmz006 is not a business entity).
4. Enter:
   - **Developer name (public):** `dmz` — this is what appears on the Store listing under
     the app title. Confirm this is the desired name; alternative is `Datawatch` or
     `dmzs.com`.
   - **Contact email:** `davidzendzian@gmail.com`.
   - **Contact phone:** your personal mobile.
   - **Website:** `https://dmzs.com`.

### Step 3 — Identity verification

Google requires government-ID verification for new personal developer accounts as of 2023.
You will need:
- A photo ID (passport or driver's license).
- A recent utility bill or bank statement with the same name and address as the ID.
- Verification is automated initially; may escalate to human review (1–5 business days).

### Step 4 — Pay the one-time developer fee

- **$25 USD** one-time. Paid via Google Pay at sign-up.
- Tax information form completed (W-9 for US individuals; Google prompts you through it).

### Step 5 — Enable 2SV + set recovery

- Confirm 2-Step Verification is on (Google Authenticator or hardware key preferred over
  SMS).
- Configure account recovery: recovery email ≠ developer email, recovery phone, backup
  codes stored offline.

### Step 6 — Accept Developer Distribution Agreement + Content Policy

- Agree to the latest DDA and Google Play Program Policies.
- Review the Target API Level policy (must target at least API 34 in 2026; we target 35
  per ADR-0002).

### Step 7 — App signing setup (defer until Step 10)

Google Play App Signing is the recommended path (ADR-0003):
- Google holds the production release signing key.
- You hold the upload key (generated locally).
- If the upload key is ever compromised, Google can reset it without breaking existing
  installs.

Upload key generation (later, in Sprint 0 scaffold):
```bash
keytool -genkey -v \
  -keystore ~/.android/datawatch-client-upload.jks \
  -alias datawatch-upload \
  -keyalg RSA -keysize 4096 -validity 36500 \
  -storetype PKCS12
# Store password in 1Password / Bitwarden; commit NOTHING to git.
```

A second, distinct upload key is generated for the `.dev` internal variant.

## Phase 2 — App creation in Play Console (Sprint 1)

### Step 8 — Create the app (public)

1. Play Console → **Create app**.
2. Fields:
   - **App name:** `Datawatch Client` (ADR-0030).
   - **Default language:** English (United States).
   - **App or game:** App.
   - **Free or paid:** Free.
   - **Declarations:** meets Developer Program Policies ✓, US Export laws ✓.
3. Set the package name later via the first AAB upload; it must be `com.dmzs.datawatchclient`
   and cannot be changed after the first upload.

### Step 9 — Create the internal variant app

Play Console treats each `applicationId` as a separate app. Repeat Step 8 for:
- **App name:** `Datawatch Client (Dev)`.
- **Package name:** `com.dmzs.datawatchclient.dev`.
- This app stays in Internal Testing track forever (ADR-0031). Do not submit for
  production.

### Step 10 — Enroll in Play App Signing

For each of the two apps:
1. Play Console → **Setup → App integrity**.
2. **Use Play App Signing**: Yes.
3. Choose **Option 1: Export and upload a key from Android Studio or gradle** — generates
   a new key in Play's HSM; you upload your generated upload key.
4. Upload the upload-key certificate (`.pem`).

Document the SHA-256 fingerprint of both the upload and app-signing keys in
`docs/operations.md` (redacted copy in commit history, full copy in offline notes).

## Phase 3 — Store listing content (Sprint 5)

Follow `store-listing.md`. Fields filled in Play Console:

- Short description (80 chars max).
- Full description (4000 chars max).
- App icon (512×512 PNG).
- Feature graphic (1024×500 PNG).
- Phone screenshots (2–8).
- Optional tablet, Wear, Auto screenshots.
- Categorization: **Tools** (primary).
- Tags: `tools`, `productivity`, `developer-tools`.
- Contact details: `https://dmzs.com` + email.
- Privacy policy URL: `https://dmzs.com/datawatch-client/privacy` (hosted before
  submission).

## Phase 4 — Content rating + audiences (Sprint 5)

### Content rating

Complete the IARC questionnaire. Expected rating: Everyone. No violence, sexual content,
gambling, drugs, or UGC by app users.

### Target audience

- **Age:** 18+ (productivity app, no child-directed content).
- **Ads:** None.
- **In-app purchases:** None.
- **Sensitive permissions:** RECORD_AUDIO, POST_NOTIFICATIONS — declared.

### Data safety form

Follow `data-safety-declarations.md` verbatim. This form is legally binding.

### Government apps / News apps

Not applicable.

## Phase 5 — Release tracks (Sprints 2 onward)

### Step 11 — Internal Testing track

- Add dmz's Google account(s) to the internal tester list (max 100 testers).
- Upload the first AAB via Play Console → Testing → Internal testing → Create new release.
- Release notes short: "Initial internal build."
- Available within ~1 hour.

### Step 12 — Closed Testing (Sprint 5)

- Add a tester group (5–10 people) by email.
- Testers opt in via a URL Google provides.

### Step 13 — Open Testing (Sprint 6)

- Public opt-in URL.
- Good for catching last-mile issues.

### Step 14 — Production (Sprint 6)

- Release notes: "Initial public release."
- Staged rollout: **1% → 10% → 50% → 100%** over 72 hours.
- Monitor Play Console → Statistics + Pre-launch report for crashes.

## Phase 6 — Android Auto submission (Sprint 5, in parallel)

- Play Console → **Advanced settings → Automotive**.
- Declare: **Designed for cars** → Messaging app.
- Submit for Google's Driver Distraction Evaluation:
  - Provide demonstration video of the Auto UI.
  - Provide testing instructions for the Google reviewer.
  - Confirm the app does not display disallowed UI while in motion.
- Expected review: 1–3 weeks.
- Approved apps appear in the in-car Play Store.
- The internal `.dev` build is **not submitted** — passenger-full-UI never ships publicly
  (ADR-0031).

## Phase 7 — Wear OS submission (Sprint 5)

- Play Console → **Advanced settings → Wear OS**.
- Confirm Wear AAB is embedded in the phone AAB (standard for KMP + Wear modules using
  bundled delivery).
- Screenshots: 384×384 square + round previews.
- No separate review — ships with the phone app.

## Phase 8 — Post-launch operations

- Set up release alerts: email on new crashes, ANRs, crash rate > 0.5%.
- Weekly: review Play Console → Statistics; respond to user reviews within 3 business days.
- Monthly: review Android Vitals; address any thermal, battery, or ANR regressions.
- Quarterly: re-verify target SDK meets current Play policy (Play raises the floor yearly;
  API 35 locked for 2026, 2027 floor TBD).

## Other Google programs (optional, not required for launch)

- **App Actions console:** needed for ASSIST-intent quality certification. Submit after
  v1.0.0 is stable.
- **Pre-launch report opt-out:** do NOT opt out. The crawler catches layout and security
  issues before live users do.
- **Google Play Developer API:** enable if using `fastlane supply` for automated AAB
  uploads. Recommended by Sprint 5.

## iOS App Store Connect (future, post-v1 Android)

Not required for v1. When starting iOS:
1. Apple Developer Program: **$99/year**.
2. Account creation: `appleid.apple.com` → enrol.
3. App Store Connect → My Apps → Create.
4. TestFlight for beta, App Store for production.
5. Privacy nutrition labels similar in intent to Play's Data Safety form (separate filing).

## Contacts to record privately

- Google Play Developer support email chain
- Google Play App Signing recovery key escrow location
- dmzs.com DNS + hosting login
- Upload keystore location + passphrase (offline password manager)

Do **not** commit any of these values to the repo.
