# Launch and tester promotion plan

**Status: drafts only. Hold all public posts** until datawatch 9.0.0, the iOS TestFlight public link and the Android closed test are ready. They launch together as one announcement (operator, 2026-10-07).

## Goal

- **Android:** reach **12 testers who stay opted in for 14 days** in Play closed testing. That's the gate for applying for production access on a personal developer account.
- **iOS:** fill the public TestFlight group.
- Get real feedback, which Google asks about in the production application.

## Join links (used everywhere)

| What | Link |
|---|---|
| Tester group (required for Android) | https://groups.google.com/g/datawatch-testers |
| Android opt-in | https://play.google.com/apps/testing/com.dmzs.datawatchclient |
| iOS TestFlight | public link after Beta App Review approval (TBD) |
| Server | https://github.com/dmz006/datawatch |
| App | https://github.com/dmz006/datawatch-app |

## Where to post, best first

Ranked by how likely each place is to produce testers who already run, or will install, a self-hosted datawatch server.

| # | Where | Why | Notes |
|---|---|---|---|
| 1 | **GitHub**: release notes for datawatch 9.0.0 and the app, pinned Discussion in `dmz006/datawatch`, README banners | Existing users already run a server | Pin a "Join the beta" Discussion; link it from both READMEs and the web UI About page |
| 2 | **r/selfhosted** | Biggest self-hosted audience; the app is useless without your own server, which suits this crowd | Follow the self-promotion rules: post as the developer, answer every comment, once per major release |
| 3 | **r/LocalLLaMA** | datawatch drives local models (Ollama, compute nodes); strong overlap | Lead with the local-LLM / Automata angle, not the phone app |
| 4 | **r/ClaudeAI / r/ChatGPTCoding / r/ClaudeCode** | Monitoring and replying to AI coding sessions from a phone is the core use case | Show a short screen recording of reply-from-phone |
| 5 | **Hacker News "Show HN"** | Good reach for dev tools; one shot | Post the server (datawatch 9.0.0) with the apps as part of it; weekday morning US time |
| 6 | **Mastodon** (fosstodon.org, #selfhosted, #homelab) and **Bluesky** | Self-hosting and FOSS communities | Short post plus screenshots; reuse for each release |
| 7 | **r/androidapps, r/AndroidAuto, r/WearOS** | Android Auto and Wear support is unusual for a dev tool | Smaller and less technical; good for the 12-tester count |
| 8 | **r/homelab, r/tailscale** | Many run Tailscale / LAN setups like the docs describe | Angle: "monitor your AI agents from anywhere over Tailscale" |
| 9 | **Lobsters** (needs an invite), **dev.to / Hashnode** write-up | Longer-form "how it works" post | A dev.to article can be linked from all the short posts |
| 10 | **Product Hunt** | Visibility, but less technical | Only after public store listings exist (production / App Store) |

**Practical rules**
- Post a pinned GitHub Discussion first, so every other post links to one place that stays current.
- Reddit: one subreddit per day, not all at once; reply to every comment in the first few hours.
- Always say up front that you need your own datawatch server. It avoids disappointed installs and bad reviews.
- Ask testers to stay opted in for 14 days. Leaving early resets nothing for them, but it drops below the 12 Google counts.

## Draft: GitHub Discussion (pin in dmz006/datawatch)

> **Title:** Join the datawatch mobile beta (Android + iOS) 🧪
>
> datawatch 9.0.0 is out, and the Android and iPhone apps are in public beta.
>
> **What you get:** every session on every server you run, with live terminal and chat, alerts when a session needs input, replies by keyboard or voice, Automata, Observer and Dashboard. Plus Android Auto, Wear OS, iOS widgets and Siri.
>
> **Android (Google Play closed test):**
> 1. Join https://groups.google.com/g/datawatch-testers
> 2. Opt in: https://play.google.com/apps/testing/com.dmzs.datawatchclient
> 3. Install from Play, and please stay opted in for 14 days. We need 12 testers to unlock the public Play release.
>
> **iPhone / iPad (TestFlight):** <public link>
>
> You need your own datawatch server (LAN, VPN or Tailscale). Feedback: https://github.com/dmz006/datawatch-app/issues

## Draft: Reddit (r/selfhosted)

> **Title:** datawatch 9.0.0: self-hosted supervisor for AI coding sessions, now with Android + iOS apps (beta testers wanted)
>
> I've been building datawatch, an open-source daemon that runs and supervises AI coding agents (Claude Code, OpenCode, Aider, Goose, Ollama and others) on machines you own. 9.0.0 is out, and the phone apps are in beta.
>
> - See every session across your servers; reply from the phone (typing or voice); get an alert when an agent is waiting for input.
> - Automata: multi-step plans the server runs with your agents, which you approve from the phone.
> - Observer and Dashboard: host and process metrics, compute nodes, federation.
> - No cloud: the apps talk only to your server (LAN, VPN or Tailscale). Let's Encrypt is built in.
>
> Android needs 12 testers for 14 days before Google allows a production release. Join: <group link> → <opt-in link>. iOS: <TestFlight link>.
>
> Repo: https://github.com/dmz006/datawatch. Happy to answer anything.

## Draft: Show HN

> **Title:** Show HN: datawatch – self-hosted supervisor for AI coding agents, with phone apps
>
> datawatch runs AI coding sessions (Claude Code, OpenCode, Aider, Goose, local models through Ollama) in tmux on your own machines. It watches for prompts, alerts you, and lets you reply from a web UI, Signal and other channels, or the Android/iOS apps. It also plans and runs multi-step "Automata" with guardrails, and exposes everything over MCP.
> https://github.com/dmz006/datawatch

## Draft: Mastodon / Bluesky

> datawatch 9.0.0 is out 🎉 Self-hosted supervisor for AI coding agents, now with Android (Auto + Wear) and iOS (widgets + Siri) apps in beta. Your server, your keys, no cloud.
> Beta: <group link> · <TestFlight link>
> #selfhosted #homelab #LocalLLM #opensource

## Checklist before posting

- [ ] datawatch 9.0.0 released; README and web UI About show the beta links
- [ ] Google Group `datawatch-testers` exists and is attached to the Play closed track (`alpha`)
- [ ] Closed track has the current build (the release pipeline promotes each tag)
- [ ] TestFlight public link live (after Beta App Review approval); added to both READMEs
- [ ] Short screen recording or GIF of reply-from-phone and an alert
- [ ] Pinned GitHub Discussion live; other posts link to it
