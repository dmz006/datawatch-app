#!/usr/bin/env python3
"""Daily security watch over security/accepted-risks.yml.

Security acceptance standard (AGENT.md; operator 2026-10-08, shared with
datawatch). Four checks, each a section of a markdown report on stdout:

1. Stable fixes — for `dependency` entries with `fixed_in`, the newest STABLE
   Maven Central release of `package` (group:artifact; alpha/beta/RC/milestone/
   dev/snapshot/EAP excluded) and whether it is >= the advisory's patched version.
2. Re-review due — entries past `expires`, or expiring within 14 days.
3. Dismissal sync — GitHub dismissed Dependabot + code-scanning alerts (via
   `gh api`) that have no registry entry. A missing permission is a warning,
   not a failure (GITHUB_TOKEN cannot read Dependabot alerts; see the workflow).
4. Bundled JS — xterm.js + add-ons and Mermaid versions, queried against OSV
   (npm); vulnerabilities not accepted by a `bundled-js` entry are reported.

Exit code 0 always (network failures become notes). When $GITHUB_OUTPUT is set
it writes fixable=, rereview=, unregistered=, bundled_vulns=, actionable=.

usage: sca_fix_watch.py [registry] [--today YYYY-MM-DD] [--offline]
"""
import argparse
import datetime
import json
import os
import re
import subprocess
import sys
import urllib.request
import xml.etree.ElementTree as ET

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import accepted_risks  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
XTERM_VERSIONS = os.path.join(ROOT, "composeApp/src/androidMain/assets/xterm/xterm.VERSIONS")
MERMAID_VERSION = os.path.join(ROOT, "composeApp/src/androidMain/assets/mermaid/mermaid.VERSION")
DUE_SOON_DAYS = 14
OSV_BATCH = "https://api.osv.dev/v1/querybatch"
OSV_VULN = "https://api.osv.dev/v1/vulns/"
# xterm.VERSIONS key -> npm names to query (current scoped name first; the
# unscoped legacy name is queried too so advisories filed against it are seen).
XTERM_NPM = {
    "xterm": ["@xterm/xterm", "xterm"],
    "xterm-addon-fit": ["@xterm/addon-fit", "xterm-addon-fit"],
    "xterm-addon-search": ["@xterm/addon-search", "xterm-addon-search"],
}

PRE = re.compile(r"(alpha|beta|rc|cr|-m\d|\.m\d|dev|snapshot|eap|pre|preview)", re.I)


# --------------------------------------------------------------------------- 1. stable fixes

def vkey(v):
    parts = re.split(r"[.\-]", v)
    out = []
    for p in parts:
        out.append((0, int(p), "") if p.isdigit() else (1, 0, p.lower()))
    return out


def ge(a, b):
    # numeric-prefix comparison; a stable X.Y.Z >= pre-release X.Y.Z-Beta1
    na = [int(x) for x in re.findall(r"\d+", a.split("-")[0])]
    nb = [int(x) for x in re.findall(r"\d+", b.split("-")[0])]
    if na != nb:
        return na > nb
    return not PRE.search(a) or vkey(a) >= vkey(b)


def http_get(url, data=None):
    req = urllib.request.Request(url, data=data, headers={
        "User-Agent": "datawatch-app-sca-fix-watch",
        **({"Content-Type": "application/json"} if data else {}),
    })
    with urllib.request.urlopen(req, timeout=30) as r:
        return r.read()


def latest_stable(group, artifact):
    url = "https://repo1.maven.org/maven2/%s/%s/maven-metadata.xml" % (group.replace(".", "/"), artifact)
    root = ET.fromstring(http_get(url))
    vs = [v.text for v in root.iter("version") if v.text and not PRE.search(v.text)]
    vs.sort(key=lambda v: [int(x) for x in re.findall(r"\d+", v)] or [0])
    return vs[-1] if vs else None


def stable_fixes(risks, notes, offline):
    rows, fixable = [], 0
    for r in risks:
        if r.get("kind") != "dependency" or not r.get("fixed_in"):
            continue
        pkg = str(r.get("package", ""))
        group, _, artifact = pkg.partition(":")
        stable = None
        if not offline:
            try:
                stable = latest_stable(group, artifact)
            except Exception as e:  # network / missing metadata — report, don't fail
                notes.append("- %s: Maven Central lookup failed (%s)" % (pkg, e))
        ok = bool(stable) and ge(stable, str(r["fixed_in"]))
        fixable += ok
        verdict = "no"
        if ok:
            verdict = "**YES — bump `%s` in gradle/libs.versions.toml**" % r["catalog"] if r.get("catalog") else "**YES**"
        rows.append("| %s | `%s` | %s | %s | %s |" % (r.get("id"), pkg, r["fixed_in"], stable or "?", verdict))
    out = ["## Stable upstream fixes", ""]
    if rows:
        out += ["| Alert | Package | Fixed in (advisory) | Latest stable | Stable fix? |", "|---|---|---|---|---|"] + rows
    else:
        out.append("No `dependency` entries waiting on an upstream fix.")
    return out, fixable


# --------------------------------------------------------------------------- 2. expiry

def rereview(risks, today):
    """Entries expired or expiring within DUE_SOON_DAYS -> list of (entry, expires, days_left)."""
    due = []
    for r in risks:
        exp = accepted_risks.parse_date(r.get("expires"))
        if exp is None:
            due.append((r, None, None))
            continue
        left = (exp - today).days
        if left <= DUE_SOON_DAYS:
            due.append((r, exp, left))
    return due


def rereview_section(risks, today):
    due = rereview(risks, today)
    out = ["## Re-review due", ""]
    if not due:
        out.append("No entry expires within %d days." % DUE_SOON_DAYS)
    else:
        out += ["| Entry | Package | Expires | Status |", "|---|---|---|---|"]
        for r, exp, left in due:
            if exp is None:
                status = "**invalid/missing expiry**"
            elif left < 0:
                status = "**EXPIRED %d day(s) ago — re-review now**" % -left
            else:
                status = "**re-review due in %d day(s)**" % left
            out.append("| %s | `%s` | %s | %s |" % (r.get("id"), r.get("package"), exp or "?", status))
        out += ["", "Re-review = a fresh impact analysis + new `added`/`expires` (renewing past the "
                "first expiry needs the operator), or adopt the fix and remove the entry."]
    return out, len(due)


# --------------------------------------------------------------------------- 3. dismissal sync

def gh_lines(path):
    """Run `gh api --paginate path --jq '.[] | @json'`; returns (list, error-or-None)."""
    try:
        p = subprocess.run(
            ["gh", "api", "--paginate", "-H", "Accept: application/vnd.github+json", path, "--jq", ".[] | @json"],
            capture_output=True, text=True, timeout=120,
        )
    except (OSError, subprocess.TimeoutExpired) as e:
        return None, str(e)
    if p.returncode != 0:
        return None, (p.stderr or p.stdout).strip().splitlines()[-1:] or ["gh api failed"]
    return [json.loads(l) for l in p.stdout.splitlines() if l.strip()], None


def unregistered_dismissals(alerts, kind, registered):
    out = []
    for a in alerts:
        rid = "%s/%s" % (kind, a.get("number"))
        if rid not in registered:
            out.append((rid, a))
    return out


def describe_alert(kind, a):
    if kind == "dependabot":
        adv = a.get("security_advisory") or {}
        pkg = ((a.get("dependency") or {}).get("package") or {}).get("name", "?")
        sev = adv.get("severity", "?")
        what = "%s (%s)" % (pkg, adv.get("ghsa_id", "?"))
    else:
        rule = a.get("rule") or {}
        sev = rule.get("security_severity_level") or rule.get("severity") or "?"
        path = ((a.get("most_recent_instance") or {}).get("location") or {}).get("path", "?")
        what = "%s in `%s`" % (rule.get("id", "?"), path)
    by = (a.get("dismissed_by") or {}).get("login", "?")
    when = (a.get("dismissed_at") or "?")[:10]
    return "%s | %s | %s by %s on %s" % (what, str(sev).replace("|", "/"), a.get("dismissed_reason") or "?", by, when)


def sync_section(risks, notes, offline):
    out = ["## Dismissed alerts without a registry entry", ""]
    if offline:
        out.append("_Skipped (--offline)._")
        return out, 0
    repo = os.environ.get("GITHUB_REPOSITORY") or "{owner}/{repo}"
    registered = {str(r.get("id")) for r in risks}
    rows, count, failed = [], 0, []
    for kind, endpoint in (("dependabot", "dependabot/alerts"), ("code-scanning", "code-scanning/alerts")):
        alerts, err = gh_lines("repos/%s/%s?state=dismissed&per_page=100" % (repo, endpoint))
        if alerts is None:
            msg = "%s alerts could not be listed (%s) — the token may lack permission; see sca-fix-watch.yml" % (
                kind, err if isinstance(err, str) else " ".join(err))
            notes.append("- WARNING: " + msg)
            print("::warning::" + msg, file=sys.stderr)
            failed.append(kind)
            continue
        for rid, a in unregistered_dismissals(alerts, kind, registered):
            rows.append("| %s | %s |" % (rid, describe_alert(kind, a)))
            count += 1
    if rows:
        out += ["| Alert | Finding | Severity | Dismissal |", "|---|---|---|---|"] + rows
        out += ["", "Add an entry (with impact analysis) to `security/accepted-risks.yml`, or reopen the alert."]
    else:
        out.append("Every dismissed alert that could be listed has a registry entry.")
    if failed:
        out += ["", "**Not checked:** %s alerts (no permission or API error; see Notes)." % " and ".join(failed)]
    return out, count


# --------------------------------------------------------------------------- 4. bundled JS (OSV)

def read_bundled(xterm_path=XTERM_VERSIONS, mermaid_path=MERMAID_VERSION):
    """-> list of (npm name, version, source file)."""
    out = []
    if os.path.exists(xterm_path):
        with open(xterm_path, encoding="utf-8") as f:
            lines = f.read().splitlines()
        for line in lines:
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, ver = (s.strip() for s in line.split("=", 1))
            for name in XTERM_NPM.get(key, [key]):
                out.append((name, ver, os.path.relpath(xterm_path, ROOT)))
    if os.path.exists(mermaid_path):
        with open(mermaid_path, encoding="utf-8") as f:
            ver = f.read().strip().splitlines()[0].strip()
        out.append(("mermaid", ver, os.path.relpath(mermaid_path, ROOT)))
    return out


def accepted_vuln_ids(risks):
    ids = set()
    for r in risks:
        if r.get("kind") != "bundled-js":
            continue
        rid = str(r.get("id", ""))
        if "/" in rid:
            ids.add(rid.split("/", 1)[1].upper())
    return ids


def is_accepted(vuln, accepted):
    return any(x.upper() in accepted for x in [vuln.get("id", "")] + list(vuln.get("aliases") or []))


def fixed_versions(vuln, name):
    fixed = []
    for aff in vuln.get("affected") or []:
        if (aff.get("package") or {}).get("name") != name:
            continue
        for rng in aff.get("ranges") or []:
            fixed += [ev["fixed"] for ev in rng.get("events") or [] if "fixed" in ev]
    return sorted(set(fixed), key=vkey)


def bundled_section(risks, notes, offline):
    out = ["## Bundled JS (OSV, npm)", ""]
    bundled = read_bundled()
    if not bundled:
        out.append("No bundled JS version files found.")
        return out, 0
    if offline:
        out.append("_Skipped (--offline)._ Bundled: " + ", ".join("%s@%s" % (n, v) for n, v, _ in bundled))
        return out, 0
    try:
        q = {"queries": [{"package": {"ecosystem": "npm", "name": n}, "version": v} for n, v, _ in bundled]}
        results = json.loads(http_get(OSV_BATCH, json.dumps(q).encode())).get("results", [])
    except Exception as e:
        notes.append("- OSV query failed (%s)" % e)
        out.append("_OSV query failed; see notes._")
        return out, 0
    accepted = accepted_vuln_ids(risks)
    rows, count, suppressed = [], 0, 0
    for (name, ver, src), res in zip(bundled, results):
        for v in res.get("vulns") or []:
            try:
                vuln = json.loads(http_get(OSV_VULN + v["id"]))
            except Exception as e:
                notes.append("- OSV details for %s failed (%s)" % (v["id"], e))
                vuln = {"id": v["id"]}
            if is_accepted(vuln, accepted):
                suppressed += 1
                continue
            sev = (vuln.get("database_specific") or {}).get("severity", "?")
            cves = [a for a in vuln.get("aliases") or [] if a.startswith("CVE-")]
            rows.append("| `%s@%s` | [%s](https://osv.dev/vulnerability/%s)%s | %s | %s | %s |" % (
                name, ver, vuln["id"], vuln["id"], (" (%s)" % ", ".join(cves)) if cves else "",
                sev, (vuln.get("summary") or "").replace("|", "/"), ", ".join(fixed_versions(vuln, name)) or "?"))
            count += 1
    out.append("Checked: " + ", ".join("`%s@%s`" % (n, v) for n, v, _ in bundled) + ".")
    out.append("")
    if rows:
        out += ["| Bundled | Vulnerability | Severity | Summary | Fixed in |", "|---|---|---|---|---|"] + rows
        out += ["", "Update the bundle (AGENT.md › xterm.js / Mermaid) or accept it with a `bundled-js` "
                "entry (id `osv/<ID>`) and an impact analysis."]
    else:
        out.append("No unaccepted OSV vulnerabilities affect the bundled versions.")
    if suppressed:
        out.append("")
        out.append("%d vulnerabilit%s accepted in the registry." % (suppressed, "y" if suppressed == 1 else "ies"))
    return out, count


# --------------------------------------------------------------------------- main

def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("registry", nargs="?", default="security/accepted-risks.yml")
    ap.add_argument("--today", type=datetime.date.fromisoformat, default=datetime.date.today())
    ap.add_argument("--offline", action="store_true", help="skip network checks (Maven, GitHub, OSV)")
    a = ap.parse_args(argv)
    risks = accepted_risks.load(a.registry)
    notes = []
    sections, counts = [], {}
    for key, fn in (
        ("fixable", lambda: stable_fixes(risks, notes, a.offline)),
        ("rereview", lambda: rereview_section(risks, a.today)),
        ("unregistered", lambda: sync_section(risks, notes, a.offline)),
        ("bundled_vulns", lambda: bundled_section(risks, notes, a.offline)),
    ):
        lines, counts[key] = fn()
        sections.append("\n".join(lines))
    counts["actionable"] = sum(counts.values())
    print("\n\n".join(sections))
    if notes:
        print("\n## Notes\n\n" + "\n".join(notes))
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a") as f:
            for k, v in counts.items():
                f.write("%s=%d\n" % (k, v))
    return 0


if __name__ == "__main__":
    sys.exit(main())
