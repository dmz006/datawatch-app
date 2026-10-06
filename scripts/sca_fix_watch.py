#!/usr/bin/env python3
"""Check accepted security risks for a STABLE upstream fix (Maven Central).

Reads security/accepted-risks.yml; for each entry finds the newest stable
version of group:artifact (excluding alpha/beta/RC/milestone/dev/snapshot/EAP)
and reports whether it is >= the advisory's first patched version.
Writes a markdown report to stdout; exit code 0 always. Sets GitHub output
`fixable=<count>` when $GITHUB_OUTPUT is set.
"""
import os
import re
import sys
import urllib.request
import xml.etree.ElementTree as ET

PRE = re.compile(r"(alpha|beta|rc|cr|-m\d|\.m\d|dev|snapshot|eap|pre|preview)", re.I)


def load_risks(path):
    # Tiny YAML subset reader (list of flat maps with folded strings) — no deps.
    risks, cur, key = [], None, None
    for raw in open(path, encoding="utf-8"):
        line = raw.rstrip("\n")
        if not line.strip() or line.lstrip().startswith("#") or line.strip() == "risks:":
            continue
        m = re.match(r"^\s*-\s+(\w+):\s*(.*)$", line)
        if m:
            cur = {}
            risks.append(cur)
            key, val = m.group(1), m.group(2)
            cur[key] = val.strip()
            continue
        m = re.match(r"^\s+(\w+):\s*(.*)$", line)
        if m and cur is not None:
            key, val = m.group(1), m.group(2).strip()
            cur[key] = "" if val in (">-", ">", "|") else val
            continue
        if cur is not None and key:
            cur[key] = (cur[key] + " " + line.strip()).strip()
    return risks


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


def latest_stable(group, artifact):
    url = "https://repo1.maven.org/maven2/%s/%s/maven-metadata.xml" % (group.replace(".", "/"), artifact)
    with urllib.request.urlopen(url, timeout=30) as r:
        root = ET.fromstring(r.read())
    vs = [v.text for v in root.iter("version") if v.text and not PRE.search(v.text)]
    vs.sort(key=lambda v: [int(x) for x in re.findall(r"\d+", v)] or [0])
    return vs[-1] if vs else None


def main():
    risks = load_risks(sys.argv[1] if len(sys.argv) > 1 else "security/accepted-risks.yml")
    fixable = 0
    print("| Alert | Package | Fixed in (advisory) | Latest stable | Stable fix? |")
    print("|---|---|---|---|---|")
    notes = []
    for r in risks:
        coord = "%s:%s" % (r.get("group"), r.get("artifact"))
        try:
            stable = latest_stable(r["group"], r["artifact"])
        except Exception as e:  # network / missing metadata — report, don't fail
            stable = None
            notes.append("- %s: lookup failed (%s)" % (coord, e))
        ok = bool(stable) and ge(stable, r.get("fixed_in", "999"))
        fixable += 1 if ok else 0
        print("| %s | `%s` | %s | %s | %s |" % (
            r.get("id"), coord, r.get("fixed_in"), stable or "?",
            "**YES — bump `%s` in gradle/libs.versions.toml**" % r["catalog"] if ok and r.get("catalog") else ("**YES**" if ok else "no"),
        ))
    if notes:
        print("\n" + "\n".join(notes))
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a") as f:
            f.write("fixable=%d\n" % fixable)


if __name__ == "__main__":
    main()
