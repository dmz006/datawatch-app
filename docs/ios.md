# datawatch for iPhone and iPad

The iOS app is a native SwiftUI client for [datawatch](https://github.com/dmz006/datawatch),
the server that runs and supervises AI coding sessions (Claude Code, Aider and other
agents) on machines you own. It has the same tabs and features as the Android app and the
datawatch web app (PWA), laid out for iPhone and iPad.

The app has **no cloud service of its own**. It talks only to the datawatch servers you
add. You need to run your own server and be able to reach it from your device.

- App Store name: **datawatch ai orchestration** (shown on the Home Screen as *datawatch*)
- Distribution today: **TestFlight** (beta). App Store release follows.
- Privacy policy: <https://dmzs.com/datawatch-client/privacy>

---

## What the app does

| Tab | What you get |
|---|---|
| **Sessions** | Every session on every server you add, with live state, search, filters, a tree view for child sessions, and press-and-hold drag to reorder. Open a session for its live **terminal** (xterm, JetBrains Mono, sized from the server's console settings), **chat** transcript, **channel** messages and **stats** (CPU, memory, network, GPU). Reply by typing, by **voice**, or with saved **quick commands**; the key bar and quick commands give you arrow keys, Page Up/Down, Esc and the tmux prefix (Ctrl-b). Attach a camera photo to a reply. Start new sessions from templates or a project folder on the server; stop, restart, rename or delete them. |
| **Automata** | Create, review, approve and run automata (multi-story plans the server breaks into tasks). Story and task progress, the dependency graph, verdicts, decisions, memory, scan results, templates, guided mode and per-story / per-task model overrides. |
| **Alerts** | One inbox for all servers: input needed, completed, rate limited, errors, guardrail verdicts. Reply in place, open the session, mute the alert dock, dismiss all. |
| **Observer** | Host and process metrics, GPU, federated peers, compute nodes, cooldowns, session analytics, audit log, knowledge graph and daemon log. |
| **Dashboard** | Live cards you choose and arrange; expand a card to full screen. |
| **Settings** | The server's configuration, the same pages as the web app: General, Plugins, Comms (servers, channels, routing, push), Compute (LLM backends and models, compute nodes), Automata, and About. Includes agent **profiles**, the **council** of reviewer personas (with live runs), guardrails, alert rules, skills, docs search and raw config. |

Also:

- **Several servers.** Add as many as you like. Switch with the server picker, or swipe up
  with three fingers anywhere. **All servers** shows every enabled server's sessions in one
  list.
- **iPad.** A split layout on regular-width screens, every orientation.
- **Languages.** English, German, Spanish, French and Japanese (follows the device language).
- **Deep links.** `datawatch://session/<id>`, `datawatch://alert/<id>` and
  `datawatch://<tab>` open the app at that place.
- **App lock.** Optional Face ID / Touch ID (falls back to your passcode).

---

## Requirements

- iPhone or iPad on **iOS / iPadOS 16.0 or later**.
- A **datawatch server** you run, reachable from the device over HTTPS:
  - same Wi-Fi / LAN as the server, or
  - a VPN into that network, or
  - [Tailscale](https://tailscale.com) (install the Tailscale iOS app and sign in to the
    same tailnet).
- The server's **API bearer token** (see the server's documentation; it is set in the
  server config or its environment).
- For voice replies: voice input (Whisper) enabled on the server.
- The **TestFlight** app from the App Store, while the app is in beta.

The app never needs the server to be on the public internet.

---

## Install with TestFlight

1. **Get an invite.** Either:
   - an **email invite** from the project (sent to the address you gave), or
   - a **public TestFlight link** (`https://testflight.apple.com/join/…`) shared by the project.
2. On your iPhone or iPad, install **TestFlight** from the App Store.
3. **Email invite:** open the email on the device and tap **View in TestFlight**, then
   **Accept**. **Public link:** open the link on the device and tap **Accept**.
4. In TestFlight, tap **Install**. The app appears on your Home Screen as **datawatch**.
5. New builds arrive in TestFlight automatically (turn on **Automatic Updates** in the app's
   TestFlight page). Each build stays installable for 90 days.

To send feedback, take a screenshot inside the app and tap **Share Beta Feedback**, or use
**Send Beta Feedback** in TestFlight. Include your iOS version, device and datawatch server
version. **Never include your server address or token** in feedback or screenshots.

---

## First run

1. **Open the app.** Allow notifications if you want alerts (you can change this later in
   iOS Settings).
2. **Add your server:** Settings tab › **Comms** › **Servers** › **+**.
   - **Display name**: anything, e.g. "workstation".
   - **Base URL**: `https://<host>:<port>` of your server. The app accepts `https://` only.
   - **Bearer token**: the server's API token. "No bearer token" exists for local test
     servers only.
3. **Choose how to trust the server's certificate:**
   - **Publicly trusted certificate** (for example Tailscale HTTPS certificates or a
     certificate from a public CA): nothing to do.
   - **Self-signed certificate (recommended path): Pin server certificate…** The app shows
     the certificate's subject and SHA-256 fingerprint. Compare it with the fingerprint on
     the server, then tap **Trust & pin**. From then on the app trusts exactly that
     certificate. If the server's certificate is replaced, connections fail until you
     pin again.
   - **Trust all certificates (insecure)**: turns certificate checking off for that server.
     Use it only on a trusted local network for testing. Prefer pinning.
   - **Alternative: install the server's CA certificate** on the device. After adding the
     server, edit it (Settings › Comms › Servers › the server) and use **Server CA
     certificate** to download it, open the file to install the profile, then enable it in
     iOS Settings › General › About › Certificate Trust Settings.
4. Tap **Add**, then open the **Sessions** tab. The server's sessions appear within a few
   seconds; the status dot in the header turns green when the live connection is up.
5. Optional: **Settings › General › Security** to turn on the Face ID / Touch ID lock.

Your token is stored in the iOS **Keychain** on this device only (it is not synced to
iCloud and is not restored onto another device).

---

## Permissions

iOS asks for each permission the first time a feature needs it. All are optional; the
rest of the app works without them.

| Permission | Used for | If you decline |
|---|---|---|
| **Notifications** | Alerts when a session needs input or finishes. | No banners; the Alerts tab still works. |
| **Microphone** | Recording a voice reply. The audio goes only to **your** datawatch server for transcription. | Mic button cannot record; type instead. |
| **Camera** | Taking a photo to attach to a session reply. | No photo attachments. |
| **Face ID** | The optional app lock. Face data never leaves the device; the app only receives "passed / failed" from iOS. | Lock falls back to your passcode, or leave it off. |
| **Local Network** | Reaching a server on your LAN by its local address. | Servers on your LAN are unreachable (VPN / Tailscale addresses still work). |

Change any of them in iOS **Settings › datawatch**.

---

## Known limitations

- **Push notifications while the app is closed are coming with a datawatch server
  update.** The app already asks permission and registers with Apple's push service, and
  the server needs its Apple push sender before notifications can be delivered. Until
  then, alerts appear only while the app is open: a session that waits for input for
  about 45 seconds raises a local notification.
- **Reordering sessions:** press and hold a session, then drag it (a quick swipe scrolls
  instead). The session's menu also has **Move up** / **Move down**.
- **No Siri shortcuts, home-screen widgets, Live Activities or Apple Watch app yet.**

---

## Troubleshooting

**"Could not connect" / the status dot stays red**
- Open the server URL in Safari on the same device. If Safari can't reach it, the problem
  is the network path: check Wi-Fi, your VPN, or that Tailscale is connected.
- For a LAN address, make sure **Local Network** is on in iOS Settings › datawatch.
- Check the port and that the URL starts with `https://`.

**"Token rejected" / 401 errors**
- Copy the token again from the server config; watch for a trailing space.
- If the server's token was rotated, edit the server (Settings › Comms › Servers) and
  paste the new one.

**Certificate errors after it used to work**
- The server's certificate changed. Edit the server, tap **Remove pin**, then pin the new
  certificate after checking its fingerprint.

**No mic button in a session**
- Voice input is off on the server. Turn it on in Settings › General › **Voice Input
  (Whisper)**, or in the server's config.

**No alerts**
- Notifications must be allowed in iOS Settings › datawatch › Notifications.
- See *Known limitations*: for now alerts appear only while the app is open.

**Face ID lock keeps failing**
- Use your device passcode when prompted, then turn the lock off and on again in
  Settings › General › Security.

**TestFlight says the build has expired**
- Open TestFlight and install the newest build. Builds expire after 90 days.

**Still stuck?** Open an issue at
<https://github.com/dmz006/datawatch-app/issues> with the app version (Settings › About),
iOS version and server version. Remove your server address and token from anything you
attach.

---

## Privacy

- The app sends nothing to the developer. There are **no ads, no analytics, no tracking
  and no third-party crash reporting**.
- Data goes only to the datawatch servers you add: your replies, voice recordings for
  transcription, photos you attach, and the device's push token (so your server can notify
  you).
- Tokens live in the iOS Keychain; cached session data is protected by iOS Data
  Protection and cannot be read while the device is locked.
- Diagrams in automaton descriptions are drawn with the Mermaid library, which the app
  loads from the jsDelivr CDN when such a diagram is shown.
- Full policy: [privacy-policy.md](privacy-policy.md) ·
  <https://dmzs.com/datawatch-client/privacy>

## See also

- [README](../README.md) — all platforms
- [Installation](installation.md) — Android, Wear OS, Android Auto
- [Security model](security-model.md)
