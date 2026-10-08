#!/usr/bin/env python3
"""CI lint for security/accepted-risks.yml (AGENT.md § Security acceptance standard).

Fails (exit 1) when an entry is missing a required field (including
`first_added`, `version` for dependency/bundled-js/container, `path` for
code-scanning, and impact.method when traced), has an invalid id / kind /
severity / date, has first_added after added, expires more than 90 days
(traced) or 30 days (not traced) after `added`, lacks impact.reachable +
impact.analysis, or has already expired.

Prints an `ESCALATE:` line — and still passes — when an entry needs the
operator: reachable=yes on a HIGH/CRITICAL finding, or a renewal
(added > first_added) that is past its first expiry window.

With --base REF it also fails when an entry's immutable `first_added` differs
from the registry at REF (git show REF:<registry>); a REF that cannot be read
is skipped with a note.

usage: check_accepted_risks.py [registry] [--today YYYY-MM-DD] [--base REF]
"""
import argparse
import datetime
import os
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import accepted_risks  # noqa: E402


def base_risks(ref, registry):
    """Registry entries at git REF, or None when it cannot be read."""
    try:
        p = subprocess.run(["git", "show", "%s:%s" % (ref, registry)], capture_output=True, text=True, timeout=30)
    except (OSError, subprocess.TimeoutExpired):
        return None
    if p.returncode != 0:
        return None
    return (accepted_risks.loads(p.stdout) or {}).get("risks") or []


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("registry", nargs="?", default="security/accepted-risks.yml")
    ap.add_argument("--today", type=datetime.date.fromisoformat, default=None)
    ap.add_argument("--base", default=None, help="git ref to check first_added immutability against")
    a = ap.parse_args(argv)
    try:
        risks = accepted_risks.load(a.registry)
    except (OSError, ValueError) as e:
        print("::error file=%s::cannot read registry: %s" % (a.registry, e))
        return 1
    errors, escalations = accepted_risks.validate(risks, a.today)
    if a.base:
        old = base_risks(a.base, a.registry)
        if old is None:
            print("note: registry not readable at %s; first_added immutability not checked" % a.base)
        else:
            errors += ["%s (first_added is immutable)" % c for c in accepted_risks.first_added_changes(old, risks)]
    for w in escalations:
        print("ESCALATE: %s" % w)
        print("::warning file=%s::ESCALATE: %s" % (a.registry, w))
    for e in errors:
        print("::error file=%s::%s" % (a.registry, e))
    print("%s: %d entr%s, %d error(s), %d escalation(s)" % (
        a.registry, len(risks), "y" if len(risks) == 1 else "ies", len(errors), len(escalations)))
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
