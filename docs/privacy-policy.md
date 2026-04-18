# Privacy Policy — Draft

**Target host:** `https://dmzs.com/datawatch-client/privacy`
**Status:** Draft. User to review and publish to dmzs.com before Play Store submission
(required for Data Safety form).

---

# datawatch — Privacy Policy

*Last updated: 2026-04-18*

## Who we are

`datawatch` (the mobile app listed on Google Play) is published by dmz
(`https://dmzs.com`, contact: `davidzendzian@gmail.com`). It is the client companion to
datawatch server instances that you, the user, operate. We are not a cloud service
provider — all of your data lives on your own infrastructure plus your own device.

Source code: `https://github.com/dmz006/datawatch-app` (public).

## Summary (TL;DR)

- **We do not collect any data about you.**
- **We do not serve ads.**
- **We do not use analytics or tracking SDKs.**
- The app talks only to (1) datawatch servers you configure, (2) Google's push
  notification service (FCM) for wake-up signals, and (3) optionally Google Drive for
  encrypted app backup.
- We have **no servers** that receive your data.
- You can delete your data at any time from your datawatch server(s) and by uninstalling
  the app.

## What data the app handles

### Data stored on your device only

- **Server profiles.** URL, display name, and bearer token for each datawatch server you
  connect to. Tokens are kept in the Android Keystore.
- **Cached session content.** Chat messages, terminal output, timeline events, memory
  snippets — all received from your datawatch server and stored encrypted in a local
  SQLCipher database.
- **Voice recordings pending upload.** Audio blobs you record but have not yet sent to
  your datawatch server are held on device until you send or discard them.
- **Preferences.** Theme, notification settings, reachability profiles.

Everything in this section is encrypted at rest using AES-256 and a key held by your
device's Android Keystore. No one at dmz or any third party has access to it.

### Data sent to datawatch servers you configure

When you use the app, you send to **your own** datawatch server:

- Text and voice input (commands, replies).
- Voice audio blobs for transcription by the server's Whisper pipeline.
- The app's FCM device token (so the server can push wake notifications to your device).

The server operator (you) decides how long this data is retained and who has access.
This app does not retain a server-side copy of anything you send.

### Data sent to Google Firebase Cloud Messaging (FCM)

Google FCM is the standard Android push notification service. When your datawatch server
needs to wake the app (e.g., a coding session is waiting for your input), it sends a
minimal push notification through FCM to your device.

The push payload intentionally contains only:
- The server profile identifier,
- The event kind (e.g. "input_needed", "completed", "rate_limited", "error"),
- A 4-character hint of which session triggered the event,
- A timestamp.

It does **not** contain the content of any session, message, command, or response. The
app fetches the actual content directly from your datawatch server after being woken up.

Google's privacy policy applies to FCM metadata: `https://policies.google.com/privacy`.

### Data sent to Google Drive via Android Auto Backup

If you have Android Auto Backup enabled (the default on most Android devices), your
device periodically uploads an encrypted backup of the app to your personal Google Drive
backup area. This backup contains the SQLCipher-encrypted database plus non-secret
preferences. The encryption key is held in the Android Keystore and is **not** included
in the backup — meaning if someone accessed the backup without your device, they could
not read your data.

You can disable this at any time in **Android Settings → System → Backup → App data**.

### Data that stays inside a messaging app you chose

If you configure the app to fall back to an on-device messaging app (Signal, the default
SMS app, Slack, etc.) when your datawatch server is unreachable, the app hands the
message off to that other app via an Android Intent. The data then flows through that
messenger's infrastructure under its own privacy policy. This app never uploads that
data to our servers (we don't have any).

## What data the app does **not** collect

- No behavioral analytics.
- No crash reporting to third parties (no Crashlytics, Sentry SaaS, Firebase Analytics).
- No advertising IDs.
- No location data.
- No contacts.
- No SMS or call logs.
- No device fingerprinting beyond what Android exposes to every app (e.g. build model,
  needed by the app to render the UI correctly).

## Permissions the app requests

| Permission | Why |
|---|---|
| Internet | To talk to your datawatch server and Google FCM |
| Microphone (RECORD_AUDIO) | Voice capture — only when you press the mic button |
| Post notifications | Show you push events from your datawatch server |
| Foreground service (special use) | Only if you configure the ntfy push fallback |
| Wearable binding | Only if you use the Wear OS companion |

Permissions the app **never** requests: SMS, contacts, location, camera, calendar, files
outside its own storage.

## Children

This app is intended for adults (18+) who operate their own datawatch server. It is not
directed at children.

## Security

- All network connections use TLS with modern cipher suites.
- The app does not disable hostname verification.
- Self-signed certificates are supported but require your explicit per-server trust-anchor
  opt-in.
- All on-device data is encrypted at rest via SQLCipher + Android Keystore.
- Security bug reports: please open an issue at `https://github.com/dmz006/datawatch-app`
  or email `davidzendzian@gmail.com`.

## Your choices

- **Delete server data:** remove a server profile in Settings → Servers. All cached data
  for that server is wiped from the device. Server-side data retention is controlled by
  the datawatch server itself (see the datawatch server's own documentation).
- **Turn off Google Drive backup:** Android Settings → System → Backup → App data → turn
  off for datawatch.
- **Turn off push:** Android Settings → Apps → datawatch → Notifications → disable.
  You can still open the app to view sessions.
- **Uninstall:** removes all on-device data. Google Drive Auto Backup archives persist per
  Google's retention policy; you can delete them from
  `https://drive.google.com/drive/settings`.

## Changes to this policy

We may update this policy. Changes are versioned at
`https://github.com/dmz006/datawatch-app/commits/main/docs/privacy-policy.md` — you can
review the full history there. The current version will always be hosted at this URL.

## Contact

- `davidzendzian@gmail.com`
- `https://github.com/dmz006/datawatch-app/issues`
- `https://dmzs.com`

---

## Deployment notes (for publisher, not part of the public policy)

1. Publish the HTML of everything above the "deployment notes" heading to
   `https://dmzs.com/datawatch-client/privacy`.
2. Keep a copy at `docs/privacy-policy.md` in the repo; the website links to this URL as
   canonical.
3. The Play Store listing must reference the same URL.
4. If dmzs.com uses a static site generator, drop it as a new Markdown/HTML page.
5. The Data Safety form in `data-safety-declarations.md` refers back to this policy.
