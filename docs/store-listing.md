# Play Store Listing — Copy

Drafts for all text fields in Play Console. Final-pass before submission.

## App name (50 chars max)

```
datawatch
```

Lowercase per ADR-0041. Matches parent project brand. Play Store accepts lowercase app
names; no capitalization enforcement on this field.

## Short description (80 chars max)

```
Your datawatch on the go — sessions, prompts, and voice from phone, watch, or car.
```

(79 chars.)

## Full description (4000 chars max)

```
datawatch (mobile client) is the official companion app to the datawatch server — the
open-source daemon that bridges AI coding sessions across machines and messaging
platforms.

Stay in control of your coding agents from anywhere:

• See every active session across every datawatch server you run.
• Reply to prompts from the lock screen, by typing, or by voice.
• Stream the live terminal, logs, timeline, and memory for any session.
• Trigger any of the 37 datawatch MCP tools — session management, memory recall, knowledge
  graph, stats — with one tap or a voice command.
• Invoke sessions by voice and let the server transcribe them via Whisper.

Built for multi-machine setups:

• Add every server — laptop, workstation, VPS, Raspberry Pi — as a profile.
• Drill through proxy chains with a breadcrumb that shows exactly which host a session
  belongs to.
• Unified "all servers" view when you want to see everything at once.

Works on every screen you wear or carry:

• Phone (Android 10+, Material You).
• Wear OS — watchface complication shows active session count, notifications support voice
  reply and approve/deny actions.
• Android Auto — hear prompts read aloud while driving, reply by voice, start a new
  session with a voice command.

Connects the way you do:

• Direct over Tailscale or local Wi-Fi — datawatch is never public, and neither is this
  app.
• DNS TXT covert channel and messaging-backend relay (Signal, SMS, Slack, Telegram,
  Matrix, ntfy) work as fallbacks when the direct path is down.
• Your bearer token stays in the Android Keystore; self-signed certificates are supported
  per-server.

Private by design:

• No ads. No analytics. No crash-reporting-as-a-service.
• All on-device data is encrypted at rest with SQLCipher.
• Push notifications contain no session content — only a wake-up ping.
• Google Drive Auto Backup protects your configuration without exposing your tokens.

Requires: an existing datawatch server you operate. Get datawatch at
https://github.com/dmz006/datawatch — Polyform Noncommercial.

Source: https://github.com/dmz006/datawatch-app
License: Polyform Noncommercial 1.0.0
Privacy policy: https://dmzs.com/datawatch-client/privacy
```

(Character count with the trailing blank lines: ~2,050. Well under 4,000.)

## Category + tags

- **Primary category:** Tools
- **Tags:** `tools`, `developer tools`, `productivity`

## Keyword hints (used by Play discoverability algorithms)

`datawatch`, `tmux`, `claude`, `codex`, `remote`, `SSH alternative`, `AI coding`,
`developer terminal`, `wear OS terminal`, `messaging bot client`.

## Contact details

- **Email:** davidzendzian@gmail.com
- **Website:** https://dmzs.com
- **Privacy policy:** https://dmzs.com/datawatch-client/privacy

## Graphics

See `branding.md` for the full asset list. At minimum:

- App icon (hi-res) 512×512 PNG.
- Feature graphic 1024×500 PNG.
- Phone screenshots: 4 for MVP — Home (sessions list), Session detail with terminal sheet,
  Voice capture, Settings → Servers.
- Wear screenshots: 2 — watchface complication + rich app session list.
- Auto screenshots: 1 — messaging read-aloud view.
- Tablet screenshots (post-MVP, once the two-pane layout ships).

## Release notes format

First release:
```
Initial public release of datawatch (mobile client).

• Full session management for one or many datawatch servers.
• Real-time terminal, memory, and knowledge-graph access.
• Voice input with server-side Whisper transcription.
• Wear OS companion: complication + notifications + dictation reply.
• Android Auto: hear prompts, reply by voice.
```

Subsequent releases: conventional-commits-style bullet list auto-generated from
CHANGELOG.md between the previous and new version.

## Localization (v1)

English only in v0.10.0. Backlog: locale files for DE, ES, FR, JA (post-launch).

## Promotional text — Android Auto feature

Per Google's Auto listing format: one-liner that appears in the in-car Play Store.

```
Hear datawatch prompts and reply by voice while you drive.
```
