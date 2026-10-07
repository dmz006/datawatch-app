# App Store Connect listing — iOS

Every field the operator fills or confirms in App Store Connect (ASC) for the iOS app,
with the values to use. The Android listing is in [store-listing.md](store-listing.md);
the user guide is [ios.md](ios.md).

The listing text lives in the repo as fastlane `deliver` files, so it is versioned and
length-checked:

```
iosApp/fastlane/metadata/
├── copyright.txt  primary_category.txt  secondary_category.txt
├── en-US/          name, subtitle, description, keywords, promotional_text,
│                   release_notes, support_url, marketing_url, privacy_url
├── review_information/notes.txt        App Review notes (no credentials)
└── beta/           TestFlight: description, what_to_test, review_notes, feedback_email
```

Check lengths after any edit:

```bash
scripts/check-ios-metadata.sh
```

Values entered in ASC by hand must match these files. The release pipeline
(`.github/workflows/release.yml` → `iosApp/fastlane` lane `beta`) uploads the build to
TestFlight on every `vX.Y.Z` tag; it does **not** push listing metadata. To push the
App Store listing from the files instead of typing it, run from `iosApp/` with the App
Store Connect API key in the environment:

```bash
fastlane deliver --skip_binary_upload --skip_screenshots --force   # metadata only
```

Never commit demo-server URLs, tokens, phone numbers or personal email addresses to these
files — enter them only in ASC.

---

## 1. App record (App Information)

| Field | Value | Status |
|---|---|---|
| Platform | iOS | set |
| Name | `datawatch ai orchestration` (26 / 30) | set |
| Subtitle | `AI coding sessions on the go` (28 / 30) | confirm |
| Primary language | English (U.S.) | set |
| Bundle ID | `com.dmzs.datawatchclient` | set |
| SKU | `datawatch` | set |
| Apple ID | 6774403201 | set (read-only) |
| Primary category | Developer Tools (`DEVELOPER_TOOLS`) | confirm |
| Secondary category | Productivity (`PRODUCTIVITY`) | confirm |
| Content rights | **No**, the app does not contain, show or access third-party content (see §6) | confirm |
| Age rating | Complete the questionnaire per §5 — expected **4+** | operator |
| Privacy Policy URL | `https://dmzs.com/datawatch-client/privacy` (live) | set |
| Privacy Choices URL | leave empty (nothing to opt out of) | — |
| License agreement | Apple's standard EULA | confirm |

The Home Screen name stays `datawatch` (`CFBundleDisplayName`); only the store name is
longer.

## 2. Pricing and availability

| Field | Value | Status |
|---|---|---|
| Price | Free | operator |
| In-app purchases / subscriptions | None | — |
| Availability | All countries or regions (operator may restrict) | operator |
| EU Digital Services Act trader status | Declare in ASC › Business. A **trader** must publish an address, phone and email on the EU product page; a non-commercial individual developer can declare **non-trader**. | operator |
| Distribution to Apple silicon Macs | Operator choice; untested — recommend **off** until tested | operator |
| Apple Vision Pro | off (untested) | operator |

## 3. Version page (1.28.0)

| Field | Source file | Count / limit |
|---|---|---|
| Promotional text | `en-US/promotional_text.txt` | 154 / 170 |
| Description | `en-US/description.txt` | 2,927 / 4,000 |
| Keywords | `en-US/keywords.txt` | 95 / 100 |
| Support URL | `en-US/support_url.txt` — `https://github.com/dmz006/datawatch-app/issues` | — |
| Marketing URL | `en-US/marketing_url.txt` — `https://github.com/dmz006/datawatch-app` | — |
| What's New | `en-US/release_notes.txt` (not shown for the very first App Store version) | 415 / 4,000 |
| Copyright | `copyright.txt` — `2026 dmzs.com` | confirm holder name |
| Version | `1.28.0` (from the tag; must match the build) | — |
| Build | pick the TestFlight build uploaded by the `v1.28.0` tag | operator |
| Screenshots | see §8 | operator |
| App Review: sign-in required | **Yes** — demo server URL as *User name*, demo token as *Password* | operator |
| App Review: contact | first name, last name, phone, email | operator |
| App Review: notes | `review_information/notes.txt` (402 / 4,000) — full text in [App Review notes](#app-review-notes) | set |
| Release method | Manually release this version (recommended for the first release) | operator |

Copy choices:

- **No other platforms in the iOS copy.** App Review guideline 2.3.10 rejects metadata that
  mentions other mobile platforms, so the iOS description leaves out Android, Wear OS and
  Android Auto.
- **No third-party trademarks in keywords.** Apple's keyword guidance rejects other
  companies' trademarks, so the keywords avoid agent and VPN product names. The
  description mentions compatible agents by name only as a factual statement.
- **Automaton / Automata terminology** in all copy (AGENT.md Terminology Rule; checked by the script).
- The app name and subtitle words are indexed already, so keywords do not repeat
  `datawatch`, `ai` or `orchestration`.

### App Review notes

Demo access cannot be invented: the operator must run a demo datawatch server that App
Review can reach from the internet (for example behind HTTPS on a public host), with a
token that is easy to revoke, and with some sessions, alerts and an automaton already
present so the reviewer sees real content. Enter its URL and token in the *Sign-in
information* fields. Without a reachable demo server the app is likely to be rejected
under guideline 2.1 (incomplete information / unable to review).

The long-form notes for the reviewer are in `iosApp/fastlane/metadata/beta/review_notes.txt`
(used for TestFlight Beta App Review) and the short version in
`review_information/notes.txt` (App Store review). They explain:

1. The app is a client for a self-hosted, open-source server; there is no developer
   backend and no account system.
2. Where to put the URL and token: Settings › Comms › Servers › **+** → Base URL, Bearer
   token → **Add**.
3. What to try on each tab.
4. Why each permission is requested.
5. Encryption is standard HTTPS/TLS + Keychain only.

## 4. App Privacy ("nutrition label")

**Answer: "No, we do not collect data from this app."**

Apple defines *collect* as transmitting data off the device so that the developer and/or
its third-party partners can access it beyond servicing the request in real time. The
app sends data only to datawatch servers that the **user** operates and adds; the
developer has no access to them and runs no backend. Verified in the code
(2026-10-06):

- The iOS app has no Swift packages or CocoaPods; the shared Kotlin framework's iOS
  dependencies are Ktor (Darwin engine), SQLDelight and kotlinx libraries only.
- No analytics, crash-reporting, advertising or attribution SDK. A search of `iosApp/` and
  the shared module's iOS and common sources for Firebase, Crashlytics, Sentry,
  Amplitude, Mixpanel, Bugsnag, App Center, Segment, Datadog and TelemetryDeck found
  nothing. (The "Session Analytics" screen shows the user's own server's data.)
- `PrivacyInfo.xcprivacy` declares no collected data types, no tracking and no tracking
  domains; the only required-reason API is UserDefaults (`CA92.1`, on-device preferences).

What leaves the device, and where it goes (for the operator's own understanding — none
of it is "collected" by the developer):

| Data | Destination | Why |
|---|---|---|
| Typed replies, commands, settings changes | the user's datawatch server | the app's function |
| Microphone audio (only when the user records a voice reply) | the user's datawatch server | transcription by the server's Whisper backend |
| Photos the user takes to attach | the user's datawatch server | attachment |
| APNs device token + environment | the user's datawatch server (`/api/devices/register`) | so that server can send push notifications |
| Push notifications | Apple Push Notification service → device | delivery (sent by the user's server) |

Tracking: **No.** No IDFA, no App Tracking Transparency prompt, no data brokers.

The app makes no other network requests: Mermaid (diagrams) and xterm.js (terminal) are
bundled in the app (ADR-0050, ADR-0051).

## 5. Age rating

Expected result: **4+**. Suggested answers to the ASC questionnaire:

| Question group | Answer | Why |
|---|---|---|
| Parental controls / age assurance (in-app controls) | No | — |
| Unrestricted web access | **No** | No in-app browser. External links (project pages) open in Safari. |
| User-generated content shared with others | **No** | The user only sees content from their own server; nothing is published to other users. |
| Messaging and chat between users | **No** | Replies go to the user's own coding sessions, not to other people. |
| Advertising | No | — |
| Profanity, horror, mature themes, drugs/alcohol, sexual content, violence | None | The app ships no such content. |
| Medical or wellness | No | — |
| Gambling, contests, loot boxes | None | — |

Why 4+ and not higher: the app's own content is developer tooling. Text shown in a session
comes from AI agents that the user runs on their own server, comparable to a terminal or
SSH client, which are rated 4+. If the questionnaire version in ASC asks specifically about
AI-generated content or chatbots, answer truthfully that the app displays output from AI
models the user configures on their own server; that may raise the rating, which is fine.
The privacy policy states the app is intended for adults who run their own server; that
is an audience statement and does not need a higher rating.

Made for Kids: **No.**

## 6. Content rights

"Does your app contain, show, or access third-party content?" — **No.** Everything shown
is the user's own data from their own server. Bundled open-source components (xterm.js,
JetBrains Mono under SIL OFL 1.1, Mermaid) are libraries, not content, and their licences
ship with the source.

## 7. Export compliance

Declared in the build: `ITSAppUsesNonExemptEncryption = false`
(`iosApp/project.yml` → Info.plist). The app uses only encryption built into iOS — HTTPS
(TLS via URLSession), the Keychain, iOS Data Protection — plus SHA-256 hashing for
certificate pinning. That is exempt, so ASC does not ask the export questions for each
build and no encryption documentation is uploaded. Revisit only if the app adds its own
cryptography.

## 8. Screenshots

Required sizes (portrait). One set per device class covers all smaller sizes:

| Device class | Size (px) | Required? |
|---|---|---|
| iPhone 6.9" | **1320 × 2868** (1290 × 2796 also accepted) | Yes |
| iPad 13" | **2064 × 2752** (2048 × 2732 also accepted) | Yes — the app runs on iPad |

1–10 screenshots per class; recommended set and order (same subjects for both classes):

| # | Screen | README image (any size) | App Store file |
|---|---|---|---|
| 1 | Sessions list | `docs/media/ios/ios-sessions.png` | `01-sessions.png` |
| 2 | Session terminal | `docs/media/ios/ios-terminal.png` | `02-terminal.png` |
| 3 | Automata | `docs/media/ios/ios-automata.png` | `03-automata.png` |
| 4 | Alerts | `docs/media/ios/ios-alerts.png` | `04-alerts.png` |
| 5 | Observer | `docs/media/ios/ios-observer.png` | `05-observer.png` |
| 6 | Settings | `docs/media/ios/ios-settings.png` | `06-settings.png` |

For `deliver`, put the App Store files in `iosApp/fastlane/screenshots/en-US/` (it detects
the device class from the pixel size; prefix iPad files, e.g. `ipad-01-sessions.png`, to
keep names unique). Capture on the iPhone 16/17 Pro Max and iPad Pro 13" simulators.
Before uploading, check every image shows **no real hostnames, IP addresses, Tailscale
names or tokens** — use a demo server named like `datawatch.example`.

App preview videos: optional, not planned.

## 9. TestFlight

### Internal testing (no review)

Up to 100 members of the ASC team. Each tagged build appears in TestFlight after
processing; add it to the internal group. Export compliance is pre-answered by the build.

### External testing (needs Beta App Review)

Fill **TestFlight › Test Information** once:

| Field | Source | Status |
|---|---|---|
| Beta App Description | `beta/description.txt` (940 / 4,000) | set |
| Feedback Email | `beta/feedback_email.txt` — placeholder `FEEDBACK_EMAIL` | **operator** |
| Marketing URL | `https://github.com/dmz006/datawatch-app` | set |
| Privacy Policy URL | `https://dmzs.com/datawatch-client/privacy` | set |
| Beta App Review contact | first name, last name, phone, email | **operator** |
| Sign-in required | Yes — demo server URL (user name) + token (password) | **operator** |
| Review notes | `beta/review_notes.txt` (1,737 / 4,000) | set |
| License agreement | Apple standard | — |

Per build: **What to Test** from `beta/what_to_test.txt` (1,431 / 4,000) — update it for
each version.

Then:

1. Create an **external group** (e.g. "Beta testers").
2. Add the build to the group and **Submit for Review**. The first build of each version
   is reviewed (usually within a day); later builds of the same version are often
   approved automatically.
3. Invite testers by email, and/or enable a **public link** for the group (optionally cap
   the number of testers). Share the link from the README.
4. Builds expire after 90 days; each new tag uploads a fresh build.

### Automated: `ios-testflight-setup.yml`

Actions › **iOS — external TestFlight setup** › Run workflow (fastlane lane
`testflight_setup`, same API key as the release job). Safe to re-run. It:

- fills Test Information from `beta/description.txt`, `beta/review_notes.txt` and the
  marketing / privacy URLs, and marks sign-in as required;
- sets **What to Test** on the build for the current version code from
  `beta/what_to_test.txt`;
- creates the external group (input `group`, default "Beta testers");
- `upload_listing`: also uploads the App Store listing text and screenshots with
  `deliver` (never submits the App Store version);
- `submit_for_beta_review`: adds the build to the group and submits it for Beta App
  Review — run only after the contact and sign-in fields are filled;
- `enable_public_link`: turns on the group's public link (Apple allows it once a build in
  the group is approved).

Feedback email, review contact and demo sign-in come from optional repository secrets
(`BETA_FEEDBACK_EMAIL`, `BETA_REVIEW_CONTACT_FIRST_NAME` / `_LAST_NAME` / `_PHONE` /
`_EMAIL`, `BETA_REVIEW_DEMO_URL`, `BETA_REVIEW_DEMO_TOKEN`, set with `gh secret set`).
When a secret is unset the value typed into App Store Connect is kept, so the operator can
enter them in ASC instead. Never commit them.

## 10. Operator checklist

- [ ] Replace `FEEDBACK_EMAIL` (in ASC; update the file too if a public address is fine).
- [ ] Beta App Review / App Review contact: name, phone, email.
- [ ] Stand up a reachable **demo datawatch server** with sample sessions, alerts and an
      automaton; create a revocable token; enter URL + token as sign-in information.
- [ ] Confirm subtitle, keywords, categories and copyright holder (`2026 dmzs.com`).
- [ ] Price (Free) and availability; EU trader status.
- [ ] Age rating questionnaire (§5); App Privacy: "No data collected" (§4); content rights:
      No (§6).
- [ ] Screenshots: 6 × iPhone 6.9" and 6 × iPad 13" (§8), plus the README images in
      `docs/media/ios/`.
- [ ] Create the external TestFlight group, submit the v1.28.0 build for Beta App Review,
      enable the public link.
- [x] Mermaid bundled in the app (v1.28.x) — no CDN request (§4).
