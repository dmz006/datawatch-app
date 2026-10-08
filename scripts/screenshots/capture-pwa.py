#!/usr/bin/env python3
"""Capture README screenshots of the datawatch web UI (PWA) served by the demo
server (see .github/workflows/android-screenshots.yml, job `pwa`).

  capture-pwa.py [--viewport desktop|mobile|both]

  DW_URL    demo server URL (default https://127.0.0.1:18443; self-signed TLS
            is accepted for this browser context only)
  DW_TOKEN  demo bearer token (default: the documented public test token)
  OUT_DIR   output root; writes pwa/1-sessions.png … (desktop 1280x800) and
            pwa-mobile/1-sessions.png … (mobile 390x844)
  SETTLE    seconds to wait after each navigation (default 4)

The token goes where app.js reads it (localStorage `cs_token`); the theme is
forced dark (`cs_theme`) and the once-a-day splash is marked as already shown
(`cs_splash_time`); desktop uses the full-width layout (`cs_pwa_expanded`), all via an init script before app.js runs.
"""
import argparse
import os
import sys
import time

from playwright.sync_api import sync_playwright

PAGES = [
    ("sessions", "1-sessions"),
    ("autonomous", "2-automata"),
    ("alerts", "3-alerts"),
    ("observer", "4-observer"),
    ("dashboard", "5-dashboard"),
    ("settings", "6-settings"),
]
VIEWPORTS = {
    "desktop": ("pwa", {"width": 1280, "height": 800}, 1, False),
    "mobile": ("pwa-mobile", {"width": 390, "height": 844}, 3, True),
}


def init_script(token, expanded):
    # json-ish quoting via repr is enough: the token is a plain ASCII string.
    return f"""
try {{
  localStorage.setItem('cs_token', {token!r});
  localStorage.setItem('cs_pwa_expanded', '{1 if expanded else 0}');
  localStorage.setItem('cs_theme', 'dark');
  localStorage.setItem('cs_splash_time', String(Date.now()));
  localStorage.setItem('cs_v7_migration_dismissed', '1');
  localStorage.removeItem('cs_active_session');
}} catch (e) {{}}
"""


def capture(pw, url, token, out_root, name, settle):
    folder, viewport, scale, mobile = VIEWPORTS[name]
    out = os.path.join(out_root, folder)
    os.makedirs(out, exist_ok=True)
    browser = pw.chromium.launch()
    ctx = browser.new_context(
        viewport=viewport,
        device_scale_factor=scale,
        is_mobile=mobile,
        has_touch=mobile,
        ignore_https_errors=True,  # demo server's self-signed certificate
        color_scheme="dark",
        locale="en-US",
        timezone_id="UTC",
    )
    ctx.add_init_script(init_script(token, expanded=not mobile))
    page = ctx.new_page()
    errors = []
    page.on("pageerror", lambda e: errors.append(str(e)))
    page.goto(url + "/", wait_until="domcontentloaded", timeout=60_000)
    try:
        page.wait_for_function("typeof navigate === 'function'", timeout=30_000)
        page.wait_for_selector("#splash", state="detached", timeout=20_000)
    except Exception as e:  # keep going: a late splash still yields a usable shot
        print(f"[{name}] warning: {e}", file=sys.stderr)
    time.sleep(settle)
    ok = 0
    for view, base in PAGES:
        page.evaluate("v => navigate(v)", view)
        time.sleep(settle)
        path = os.path.join(out, base + ".png")
        page.screenshot(path=path)
        print(f"[{name}] {path}")
        ok += 1
    if errors:
        print(f"[{name}] page errors: {errors[:5]}", file=sys.stderr)
    browser.close()
    return ok


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--viewport", choices=["desktop", "mobile", "both"], default="both")
    a = ap.parse_args()
    url = os.environ.get("DW_URL", "https://127.0.0.1:18443").rstrip("/")
    token = os.environ.get("DW_TOKEN", "dw-test-token-12345")
    out_root = os.environ["OUT_DIR"]
    settle = float(os.environ.get("SETTLE", "4"))
    names = ["desktop", "mobile"] if a.viewport == "both" else [a.viewport]
    with sync_playwright() as pw:
        total = sum(capture(pw, url, token, out_root, n, settle) for n in names)
    return 0 if total == len(PAGES) * len(names) else 1


if __name__ == "__main__":
    sys.exit(main())
