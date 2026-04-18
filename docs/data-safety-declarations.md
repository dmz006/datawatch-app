# Play Data Safety Form — Declarations

Legally binding answers for the Play Console Data Safety section. Submitted at Sprint 5.
Any future feature that changes a declaration requires updating this doc AND resubmitting
the form BEFORE shipping that feature.

## Section 1 — Data collection and security

### Does your app collect or share any of the required user data types?

**No**, for the purposes of data Play tracks as "collected or shared with another company."

Rationale: the app transmits data only between the user's device and the user's own
datawatch server(s). Per Google's definition, data sent to a service that is end-user
controlled and not used by the app developer for any purpose is not "collected" by the
app for Play's data-safety purposes. Google FCM metadata is transmitted for push delivery
only; Google is a **service provider** role, disclosed separately below.

### Is all of the user data collected by your app encrypted in transit?

**Yes.** All connections use TLS 1.2 or higher with modern cipher suites.

### Do you provide a way for users to request that their data is deleted?

**Yes.** Users control all data:
- Remove a server profile in Settings → Servers — wipes local cache + preferences for
  that server.
- Uninstall — wipes everything on device except the Google Drive backup archive (deletable
  by the user via `https://drive.google.com/drive/settings`).
- Server-side data deletion: handled by the user's own datawatch server (not this app).

## Section 2 — Data types

No data types selected as **collected** or **shared**. Brief reason per category (completed
to avoid ambiguity during review):

| Category | Collected? | Shared? | Notes |
|---|---|---|---|
| Personal info (name, email, user IDs, address, phone, race, sex, etc.) | No | No | App never asks for any of these |
| Financial info | No | No | |
| Health & fitness | No | No | |
| Messages | No | No | Session messages travel between user's device and user's own server only |
| Photos & videos | No | No | |
| Audio files | No | No | Voice recordings go to user's own server for transcription; treated under "Messages" framing by Google for user-controlled backends |
| Files & docs | No | No | |
| Calendar | No | No | |
| Contacts | No | No | |
| App activity (interactions, search history, installed apps) | No | No | |
| Web browsing | No | No | |
| App info and performance | No | No | No crash reporting to third parties |
| Device or other IDs | No | No | No advertising ID; no custom device ID collected |

## Section 3 — Data purposes

Not applicable — nothing declared as collected.

## Section 4 — Third-party data practices

- **Google Firebase Cloud Messaging (FCM)** — service provider role. Transmits wake
  metadata (profile ID, event kind, 4-char session hint, timestamp). No content of user
  messages passes through FCM. Declared in `privacy-policy.md`.
- **Google Drive Auto Backup** — standard Android platform feature. Uploads a
  SQLCipher-encrypted archive; encryption key is not included. Listed as a third-party
  Google service on the device side; not used as a data sink by the developer.

## Section 5 — Security practices

Check the following boxes on the form:

- ☑ Data is encrypted in transit.
- ☑ You provide a way for users to request that data be deleted.
- ☑ The app follows Google Play's Families Policy (default: not child-directed).

## Section 6 — Independent security review

Not claimed. The app is open-source (`github.com/dmz006/datawatch-app`) — community audit
welcome. No third-party security audit has been commissioned for v1.0.0.

## Section 7 — Ads declaration

- **Does your app contain ads?** No.

## Section 8 — App access

- **Does your app require users to create an account?** Yes — but it is an account on a
  datawatch server the user runs themselves, not an account on our service. Explained in
  the listing's full description.
- **Does your app restrict features behind payment?** No. App is free. No in-app purchases.

## Section 9 — Target audience

- **Target age:** 18+.
- **Ads:** none.
- **Appealing to children?** No.
- **Collect data from children?** No.

## Section 10 — News apps / Government apps

Not applicable.

## Section 11 — Health apps / Financial apps

Not applicable.

## Change control

This declaration is reviewed and re-submitted when:

1. A new permission is added that touches user data.
2. A new transport/backend is added that sends user data off-device to somewhere other
   than the user's datawatch server or Google FCM / Drive.
3. A new third-party SDK is added (shouldn't happen per ADR-0007, but if it ever does).

Any such change triggers an ADR and a re-certification of the full form.
