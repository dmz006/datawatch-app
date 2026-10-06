#!/usr/bin/env python3
"""Check the bundled xterm.js (+ fit / search add-ons) against upstream.

xterm.js is bundled in composeApp/src/androidMain/assets/xterm (shared with iOS).
Updating it is a manual step that needs a live test against a real session
(DATAWATCH-APP-CONTEXT.md › Updating xterm.js), so this only *reports*:

- the newest stable npm release that has been out >= 72 h (AGENT.md dependency
  rule) — xterm moved from `xterm` / `xterm-addon-*` to `@xterm/*` at 5.4;
- the version the web UI bundles (the PWA is the parity reference), read from
  the public datawatch repo's internal/server/web/*.min.js headers.

Prints a markdown report. First line is "UPDATES_AVAILABLE" or "UP_TO_DATE".
"""
import datetime
import json
import os
import re
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
VERSIONS = os.path.join(ROOT, "composeApp/src/androidMain/assets/xterm/xterm.VERSIONS")
MIN_AGE_H = float(os.environ.get("MIN_AGE_HOURS", "72"))
PWA_RAW = "https://raw.githubusercontent.com/dmz006/datawatch/main/internal/server/web/"

# bundled key -> (current npm package, web UI file to read the version from or None)
PACKAGES = {
    "xterm": ("@xterm/xterm", "xterm.min.js"),
    "xterm-addon-fit": ("@xterm/addon-fit", "xterm-addon-fit.min.js"),
    "xterm-addon-search": ("@xterm/addon-search", None),
}


def get(url: str) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": "datawatch-app-xterm-check"})
    with urllib.request.urlopen(req, timeout=30) as r:
        return r.read()


def key(v: str):
    return [int(x) for x in v.split(".")]


def latest_stable(pkg: str):
    d = json.loads(get("https://registry.npmjs.org/" + pkg.replace("/", "%2F")))
    now = datetime.datetime.now(datetime.timezone.utc)
    ok = []
    for v, ts in d["time"].items():
        if not re.fullmatch(r"\d+\.\d+\.\d+", v) or v not in d["versions"]:
            continue
        age = (now - datetime.datetime.fromisoformat(ts.replace("Z", "+00:00"))).total_seconds() / 3600
        if age >= MIN_AGE_H:
            ok.append(v)
    return max(ok, key=key) if ok else None


def pwa_version(fname):
    if not fname:
        return None
    try:
        head = get(PWA_RAW + fname)[:400].decode("utf-8", "replace")
    except Exception:
        return None
    m = re.search(r"@(\d+\.\d+\.\d+)", head)
    return m.group(1) if m else None


def main():
    bundled = {}
    for line in open(VERSIONS):
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            k, v = line.split("=", 1)
            bundled[k.strip()] = v.strip()
    rows, behind = [], False
    for k, (pkg, pwa_file) in PACKAGES.items():
        cur = bundled.get(k, "?")
        npm = latest_stable(pkg) or "?"
        pwa = pwa_version(pwa_file) or "—"
        newer_npm = npm != "?" and cur != "?" and key(npm) > key(cur)
        newer_pwa = pwa not in ("—",) and cur != "?" and key(pwa) > key(cur)
        behind = behind or newer_npm or newer_pwa
        flag = []
        if newer_pwa:
            flag.append("behind the web UI")
        if newer_npm:
            flag.append("newer upstream")
        rows.append(f"| {k} | {cur} | {pwa} | {pkg} {npm} | {', '.join(flag) or 'current'} |")
    print("UPDATES_AVAILABLE" if behind else "UP_TO_DATE")
    print()
    print("| Bundled file | App version | Web UI version | Latest stable on npm (≥72 h) | Status |")
    print("|---|---|---|---|---|")
    print("\n".join(rows))
    print()
    print("Updating is manual (DATAWATCH-APP-CONTEXT.md › Updating xterm.js): match the web UI's version first "
          "(PWA is the parity reference), replace the files in composeApp/src/androidMain/assets/xterm, update "
          "xterm.VERSIONS, then live-test a real session on Android and iOS (connect, typing, resize/keyboard, "
          "scroll mode, search) before merging. Note the package rename to @xterm/* at 5.4 and any major-version "
          "API changes (e.g. 6.x).")


if __name__ == "__main__":
    main()
