# Privacy Policy — Draft

**Target host:** `https://dmzs.com/datawatch-client/privacy`
**Status:** Draft. User to review and publish to dmzs.com before Play Store submission
(required for Data Safety form).

---

# datawatch — Privacy Policy

*Last updated: 2026-10-06*

## Who we are

`datawatch` (the mobile app listed on Google Play, and on Apple's App Store and TestFlight as
"datawatch ai orchestration") is published by dmz
(`https://dmzs.com`, contact: `davidzendzian@gmail.com`). It is the client companion to
datawatch server instances that you, the user, operate. We are not a cloud service
provider — all of your data lives on your own infrastructure plus your own device.

Source code: `https://github.com/dmz006/datawatch-app` (public).

## Summary (TL;DR)

- **We do not collect any data about you.**
- **We do not serve ads.**
- **We do not use analytics or tracking SDKs.**
- The app talks only to (1) datawatch servers you configure, (2) the platform push
  service for wake-up signals — Google FCM on Android, Apple Push Notification service
  (APNs) on iPhone and iPad — (3) on Android, optionally Google Drive for encrypted app
  backup, and (4) on iPhone and iPad, the jsDelivr CDN to load the diagram library when
  an automaton description contains a diagram (no personal data is sent).
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

On Android, everything in this section is encrypted at rest using AES-256 and a key held
by your device's Android Keystore. No one at dmz or any third party has access to it.

On iPhone and iPad:

- **Bearer tokens** are stored in the iOS **Keychain**, readable only while the device
  is unlocked and only on this device (they are not synced to iCloud Keychain and are not
  restored onto another device).
- **Cached session content and preferences** are stored in the app's private storage,
  protected by iOS Data Protection (they cannot be read while the device is locked). Like
  any app's data, they can be part of your encrypted iCloud or computer backup; tokens
  are not.
- **Face ID / Touch ID** (optional app lock) is handled entirely by iOS. The app only
  learns whether authentication succeeded; it never receives or stores biometric data.

### Data sent to datawatch servers you configure

When you use the app, you send to **your own** datawatch server:

- Text and voice input (commands, replies).
- Voice audio blobs for transcription by the server's Whisper pipeline. Audio is recorded
  only while you are recording a voice reply.
- On iPhone and iPad, photos you take with the camera to attach to a reply.
- The app's push token — the FCM token on Android, the APNs device token on iPhone and
  iPad — so the server can push wake notifications to your device.

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

### Data sent through Apple Push Notification service (APNs) — iPhone and iPad

On iPhone and iPad, push notifications use Apple's push service instead of FCM. The app
registers with APNs and sends the resulting device token only to the datawatch servers
you configure. Your server then sends notifications through APNs to your device. As with
FCM, notifications are meant to tell you that a session needs attention; the app loads
the details directly from your server. Apple's privacy policy applies to APNs:
`https://www.apple.com/legal/privacy/`.

### Diagram library (iPhone and iPad)

When you open an automaton whose description contains a diagram, the iOS app loads the
open-source Mermaid library from the jsDelivr content delivery network to draw it. That
request contains no account, server or session data; like any web request it reveals
your IP address to the CDN. jsDelivr's privacy policy: `https://www.jsdelivr.com/terms/privacy-policy-jsdelivr-net`.

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

Android:

| Permission | Why |
|---|---|
| Internet | To talk to your datawatch server and Google FCM |
| Microphone (RECORD_AUDIO) | Voice capture — only when you press the mic button |
| Post notifications | Show you push events from your datawatch server |
| Foreground service (special use) | Only if you configure the ntfy push fallback |
| Wearable binding | Only if you use the Wear OS companion |

Permissions the Android app **never** requests: SMS, contacts, location, camera, calendar,
files outside its own storage.

On iPhone and iPad, iOS asks before each of these is used:

| Permission | Why |
|---|---|
| Notifications | Show you alerts from your datawatch server |
| Microphone | Voice capture — only when you press the mic button |
| Camera | Only when you take a photo to attach to a session reply |
| Face ID | Optional app lock; handled by iOS, no biometric data reaches the app |
| Local Network | Reach datawatch servers on your local network |

The iOS app **never** requests: location, contacts, calendars, photo library, health,
Bluetooth, or tracking permission (App Tracking Transparency).

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
- **Turn off push:** Android Settings → Apps → datawatch → Notifications → disable; on
  iPhone and iPad, iOS Settings → datawatch → Notifications. You can still open the app
  to view sessions. iOS Settings → datawatch also turns microphone, camera and local
  network access on or off.
- **Uninstall:** removes all on-device data. On iPhone and iPad, iOS may keep Keychain
  items after an app is deleted, so remove your server profiles first (Settings › Comms ›
  Servers › Delete), which deletes their tokens from the Keychain. Google Drive Auto Backup archives persist per
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
3. The Play Store listing and the App Store / TestFlight listing must reference the same URL.
4. If dmzs.com uses a static site generator, drop it as a new Markdown/HTML page.
5. The Data Safety form in `data-safety-declarations.md` refers back to this policy.
