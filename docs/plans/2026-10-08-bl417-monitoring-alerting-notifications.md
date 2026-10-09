# BL417: Monitoring, alerting and the notification system (placeholder)

**Status:** placeholder, unscheduled. Opened 2026-10-08 at the operator's request.
This records where things stand so that a full plan can be worked out later. It is **not** the plan.

## Why

The operator wanted to know when Apple TestFlight reviewers or testers connect to the demo datawatch server. That showed four gaps:

1. **The daemon has no access log.** Nothing records who connected, from where, or what they did.
2. **An outside monitor can't raise a datawatch alert.**
3. **Operator alerts get lost among session alerts** on the phone.
4. **Notification delivery isn't consistent:** `/api/push/notify` skips SSE registrations, and there is no category or priority routing.

The whole notification system needs a fresh look across the server, PWA, Android (phone, Wear, Auto) and iOS.

## Current state (2026-10-08): interim demo-server visitor monitor

Operator-side and out of the repo. The demo VM address is never committed.

- **On the demo VM:**
  - A ufw `allow log` rule on 8443 logs the source IP of every new connection.
  - A systemd timer samples established 8443 peers once a minute.
  - `dw-visitors-report` puts each peer in a class:
    - **ours:** root SSH login IPs, plus a known list;
    - **apple:** 17.0.0.0/8;
    - **client:** held a connection for 2 or more minutes;
    - **scanner:** everything else.
- **On the operator box:** systemd --user timers.
  - **Every 15 min:** alerts on a first-seen apple or client IP.
  - **Daily at 06:00 UTC:** refreshes the masked-IP issue "demo server: TestFlight reviewer / visitor report".
- **Daily report:** `sca-fix-watch.yml` quotes that issue in the daily security report (#209) under "Demo server visitors (TestFlight review)".
- **Limits:**
  - It only sees IPs. It can't tell whether a client authenticated or which app it was.
  - The push alert reaches 0 devices. There's no create-alert API, and `/api/push/notify` skips the SSE path the Android app uses.
  - Email alerts work: the monitor sends from the operator's Gmail to itself through the local imap-mcp (SMTP) with the subject prefix `[datawatch DEMO VISITOR]`.

## Server dependencies (datawatch)

- **dmz006/datawatch#201:** full access and audit logging.
  - HTTP access log, WebSocket lifecycle, auth events, audit completeness, federation/proxy attribution, retention, and a query API + MCP tool.
  - The datawatch agent will report what is logged and what to monitor.
- **Create-alert API (requested from the datawatch agent):** `POST /api/alerts {level, title, body, category, priority}`.
  - It goes through normal push delivery (SSE / ntfy / APNs) and passes the category through to clients.
  - Ideally the daemon also raises "first-seen IP authenticated" itself.

## Questions for the full plan (operator decides)

- **Alert categories and priorities:** for example session, Automata, system, security/operator. Which ones page and which only list?
- **Per-category notification channels** on Android (sound, importance, Wear vibration), on iOS (interruption level, Focus filters) and in the PWA (Web Push).
- **Delivery paths:** fix `/api/push/notify` vs SSE, and a single fan-out path. The ntfy / UnifiedPush policy stays; FCM is frozen (B6).
- **Volume control:** de-duplication, rate limits, quiet hours, and a digest vs real-time choice per category.
- **Email as a channel**, through the operator's SMTP-capable MCP or the daemon itself.
- **What to monitor besides the demo server:**
  - auth failures and new-IP logins on any daemon;
  - federation peer health;
  - release-pipeline and store review states (today these are session-bound monitors).
- **In-app surfaces:** a "recent connections" or audit view, and alert history with filters per category.
- **Parity:** the PWA leads, and Android (phone, Wear, Auto) and iOS match.

## Next step

Revisit once #201 and the create-alert API ship. Then write the full plan, which replaces this file's "Questions" section, and break it into BL items.
